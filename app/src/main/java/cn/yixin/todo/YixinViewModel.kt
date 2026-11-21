package cn.yixin.todo

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.yixin.core.Backup
import cn.yixin.core.Category
import cn.yixin.core.Completion
import cn.yixin.core.DeckState
import cn.yixin.core.Draft
import cn.yixin.core.FocusProgress
import cn.yixin.core.ImportOptions
import cn.yixin.core.ImportPreview
import cn.yixin.core.Preferences
import cn.yixin.core.Repository
import cn.yixin.core.Ritual
import cn.yixin.core.RunningFocus
import cn.yixin.core.add
import cn.yixin.core.addCategory
import cn.yixin.core.complete
import cn.yixin.core.decodeBackup
import cn.yixin.core.deleteCategory
import cn.yixin.core.draw
import cn.yixin.core.edit
import cn.yixin.core.encodeBackup
import cn.yixin.core.maxBackupBytes
import cn.yixin.core.merge
import cn.yixin.core.move
import cn.yixin.core.navigate
import cn.yixin.core.newProgress
import cn.yixin.core.preview
import cn.yixin.core.restore
import cn.yixin.core.saveProgress
import cn.yixin.core.select
import cn.yixin.core.togglePin
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Stack : Screen
    data object Archive : Screen
    data object Categories : Screen
    data object Backups : Screen
    data class Detail(val id: String) : Screen
    data class Editor(val id: String?, val defaultDueDate: String? = null) : Screen
    data class Draw(val ids: List<String>, val deal: Long) : Screen
    data class Focus(val id: String) : Screen
    data class Review(val backup: Backup, val baseline: DeckState, val preview: ImportPreview) : Screen
}

sealed interface Feedback {
    data class Message(val text: String) : Feedback
    data class Completed(val token: Completion, val ritual: Ritual) : Feedback
    data class Share(val intent: Intent) : Feedback
}

class YixinViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as YixinApplication
    private val repo: Repository = application.repository
    val state = repo.state
    private val pages = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val screens = pages.asStateFlow()
    private val active = MutableStateFlow<RunningFocus?>(null)
    val running = active.asStateFlow()
    private val bootError = MutableStateFlow<String?>(null)
    val startupError = bootError.asStateFlow()
    private val progress = MutableStateFlow(false)
    val busy = progress.asStateFlow()
    private val events = Channel<Feedback>(Channel.UNLIMITED)
    val feedback = events.receiveAsFlow()
    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var foreground = false
    private var deal = 0L

    init {
        application.scope.launch {
            openStore()
            for (action in actions) {
                try {
                    action()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    message(if (error is IllegalArgumentException) error.message ?: "输入无效。" else "操作失败，请重试。")
                }
            }
        }
        viewModelScope.launch {
            active.collectLatest { session ->
                if (session == null) return@collectLatest
                while (true) {
                    delay(4_000)
                    requestCheckpoint(session)
                }
            }
        }
    }

    fun retry() = dispatch { openStore() }

    private suspend fun openStore() {
        try {
            bootError.value = null
            repo.open()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            bootError.value = "暂时无法打开本地数据库。已保留原有数据。"
        }
    }

    fun foreground() { foreground = true }

    fun background() {
        foreground = false
        val now = SystemClock.elapsedRealtime()
        if (pages.value.last() is Screen.Focus) pages.value = listOf(Screen.Home)
        dispatch { pause(now) }
    }

    override fun onCleared() {
        background()
        actions.close()
        super.onCleared()
    }

    fun open(screen: Screen) = dispatch {
        if (screen is Screen.Editor && pages.value.last() is Screen.Detail) replaceTop(screen)
        else push(screen)
    }

    fun back() {
        val now = SystemClock.elapsedRealtime()
        dispatch {
            val top = pages.value.last()
            if (top == Screen.Home) return@dispatch
            pop()
            if (top is Screen.Focus) pause(now)
        }
    }

    fun select(id: String, details: Boolean = false) = dispatch {
        val deck = current()
        if (!deck.canSelect(id)) return@dispatch
        repo.change { it.select(id) }
        if (details) push(Screen.Detail(id))
        else if (pages.value.last() == Screen.Stack) pop()
    }

    fun showCurrent() = dispatch {
        current().current()?.let { push(Screen.Detail(it.id)) }
    }

    fun navigateFrom(id: String, direction: Int, onSettled: (Boolean) -> Unit = {}) = dispatch {
        var switched = false
        try {
            if (pages.value.last() != Screen.Home) return@dispatch
            val deck = repo.change { it.navigate(direction, fromId = id) }
            switched = deck.current()?.id != id
        } finally {
            onSettled(switched)
        }
    }
    fun move(id: String, direction: Int) = change { it.move(id, direction) }
    fun pin(id: String) = dispatch {
        val before = current().pinnedId
        val after = repo.change { it.togglePin(id) }.pinnedId
        if (before != after) message(if (after == null) "已松开，可以切换了" else "已钉住，安心做完这一件")
    }

    fun save(editor: Screen.Editor, draft: Draft) = dispatch {
        if (pages.value.last() != editor) return@dispatch
        val at = System.currentTimeMillis()
        val id = editor.id
        repo.change { if (id == null) it.add(uniqueId(), draft, at) else it.edit(id, draft, at) }
        pop()
        message(if (id == null) "新的一件已放入卡片叠" else "这张卡片已更新")
    }

    fun complete(id: String) = dispatch {
        val before = current()
        if (before.task(id)?.let { it.completedAt == null } != true) return@dispatch
        val at = System.currentTimeMillis()
        repo.change { it.complete(id, at) }
        if (active.value?.base?.taskId == id) active.value = null
        pages.value = pages.value.dropLastWhile {
            (it is Screen.Focus && it.id == id) || (it is Screen.Detail && it.id == id)
        }
        events.trySend(Feedback.Completed(Completion(id, at), before.preferences.ritual))
    }

    fun restore(id: String, undo: Completion? = null) = change { it.restore(id, System.currentTimeMillis(), undo) }

    fun preferences(transform: (Preferences) -> Preferences) =
        change { deck -> deck.copy(currentId = deck.current()?.id, preferences = transform(deck.preferences)) }

    fun shuffle() = change {
        it.copy(currentId = it.current()?.id, shuffleSeed = Random.nextLong(0, 4_294_967_296L))
    }

    fun addCategory(name: String, color: cn.yixin.core.TagColor, onSaved: () -> Unit) = dispatch {
        repo.change { it.addCategory(Category("category-${uniqueId()}", name.trim(), color)) }
        onSaved()
    }

    fun deleteCategory(id: String) = change { it.deleteCategory(id) }

    fun draw() = dispatch {
        val deck = current()
        val ids = withContext(Dispatchers.Default) { deck.draw(Random.Default) }
        if (ids.isEmpty()) return@dispatch
        val screen = Screen.Draw(ids, ++deal)
        if (pages.value.last() is Screen.Draw) replaceTop(screen)
        else push(screen)
    }

    fun focus(id: String) = dispatch { enterFocus(id) }

    fun chooseDraw(round: Screen.Draw, id: String, onRejected: () -> Unit) = dispatch {
        if (pages.value.last() != round || id !in round.ids) return@dispatch
        var opened = false
        try {
            opened = enterFocus(id)
        } finally {
            if (!opened) onRejected()
        }
    }

    private suspend fun enterFocus(id: String): Boolean {
        if (!foreground || !current().canSelect(id)) return false
        val now = SystemClock.elapsedRealtime()
        pause(now)
        val deck = repo.change { state ->
            val saved = state.progress.find { it.taskId == id } ?: newProgress(id, state.preferences)
            state.select(id).saveProgress(saved)
        }
        if (!foreground) return false
        val saved = deck.progress.first { it.taskId == id }
        when (pages.value.last()) {
            is Screen.Draw, is Screen.Detail -> replaceTop(Screen.Focus(id))
            else -> push(Screen.Focus(id))
        }
        if (!saved.finished) active.value = RunningFocus(saved, SystemClock.elapsedRealtime())
        return true
    }

    fun toggleTimer(id: String) = dispatch {
        if (!foreground || pages.value.last() != Screen.Focus(id)) return@dispatch
        val now = SystemClock.elapsedRealtime()
        if (active.value != null) pause(now)
        else current().progress.find { it.taskId == id && !it.finished }?.let {
            active.value = RunningFocus(it, now)
        }
    }

    fun restartTimer(id: String) = dispatch {
        if (!foreground || pages.value.last() != Screen.Focus(id)) return@dispatch
        val next = newProgress(id, current().preferences)
        repo.change { it.saveProgress(next) }
        if (foreground && pages.value.last() == Screen.Focus(id)) {
            active.value = RunningFocus(next, SystemClock.elapsedRealtime())
        }
    }

    fun requestCheckpoint(session: RunningFocus) = dispatch {
        if (foreground && active.value === session) checkpoint(SystemClock.elapsedRealtime())
    }

    private suspend fun pause(now: Long) {
        val session = active.value ?: return
        active.value = null
        val saved = session.at(now)
        repo.change { it.saveProgress(saved) }
    }

    private suspend fun checkpoint(now: Long) {
        val session = active.value ?: return
        val saved = session.at(now)
        repo.change { it.saveProgress(saved) }
        if (saved.finished) active.value = null
    }

    fun readBackup(uri: Uri) = dispatch {
        withFileOperation {
            val backup = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                    val data = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        require(data.size() + read <= maxBackupBytes) { "文件超过 48 MiB，请检查是否为一心备份。" }
                        data.write(buffer, 0, read)
                    }
                    data.toByteArray()
                } ?: throw IllegalArgumentException("无法读取这个文件。")
                decodeBackup(bytes.decodeToString(throwOnInvalidSequence = true))
            }
            val deck = current()
            if (pages.value.last() == Screen.Backups) push(Screen.Review(backup, deck, deck.preview(backup)))
        }
    }

    fun applyBackup(review: Screen.Review, options: ImportOptions) = dispatch {
        if (pages.value.last() != review) return@dispatch
        val deck = current()
        if (deck != review.baseline) {
            replaceTop(Screen.Review(review.backup, deck, deck.preview(review.backup)))
            message("本机数据已有变化，请重新确认预览。")
            return@dispatch
        }
        repo.change { it.merge(review.backup, options) }
        pop()
        message("备份已导入")
    }

    fun exportBackup(uri: Uri? = null) = dispatch {
        withFileOperation {
            val data = active.value?.let { current().saveProgress(it.at(SystemClock.elapsedRealtime())) } ?: current()
            val intent = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val bytes = encodeBackup(data).encodeToByteArray()
                require(bytes.size <= maxBackupBytes) { "备份超过 48 MiB，无法生成文件。" }
                if (uri != null) {
                    val stream = app.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalArgumentException("无法写入所选位置。")
                    stream.use { it.write(bytes) }
                    null
                } else {
                    val root = File(app.cacheDir, "shared-backups").apply { mkdirs() }
                    val expired = System.currentTimeMillis() - 86_400_000
                    root.listFiles().orEmpty().filter { it.lastModified() < expired }.forEach { it.deleteRecursively() }
                    val directory = File(root, uniqueId()).apply { mkdirs() }
                    val file = File(directory, "一心-${LocalDate.now()}.json").apply { writeBytes(bytes) }
                    val fileUri = FileProvider.getUriForFile(app, "${app.packageName}.backup", file)
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        clipData = ClipData.newRawUri("一心备份", fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            if (intent == null) message("备份已保存") else events.trySend(Feedback.Share(intent))
        }
    }

    private suspend fun withFileOperation(block: suspend () -> Unit) {
        progress.value = true
        try { block() } finally { progress.value = false }
    }

    private fun current(): DeckState = checkNotNull(state.value)
    private fun message(text: String) { events.trySend(Feedback.Message(text)) }
    private fun push(screen: Screen) {
        if (pages.value.last() != screen) pages.value += screen
    }
    private fun replaceTop(screen: Screen) { pages.value = pages.value.dropLast(1) + screen }
    private fun pop() { if (pages.value.size > 1) pages.value = pages.value.dropLast(1) }

    private fun change(transform: (DeckState) -> DeckState) = dispatch { repo.change(transform) }
    private fun dispatch(action: suspend () -> Unit) { actions.trySend(action) }
    private fun uniqueId() = UUID.randomUUID().toString()
}
