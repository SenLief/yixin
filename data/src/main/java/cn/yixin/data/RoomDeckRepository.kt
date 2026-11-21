package cn.yixin.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import cn.yixin.core.Category
import cn.yixin.core.DeckState
import cn.yixin.core.FocusProgress
import cn.yixin.core.Preferences
import cn.yixin.core.Repository
import cn.yixin.core.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomDeckRepository internal constructor(
    private val database: YixinDatabase,
    private val newId: () -> String,
) : Repository, AutoCloseable {
    constructor(context: Context, databaseName: String = "yixin.db", newId: () -> String) : this(
        Room.databaseBuilder(context.applicationContext, YixinDatabase::class.java, databaseName).build(),
        newId,
    )

    private val dao = database.deck()
    private val mutex = Mutex()
    private val committed = MutableStateFlow<DeckState?>(null)
    override val state = committed.asStateFlow()

    override suspend fun open() {
        mutex.withLock {
            if (committed.value != null) return
            committed.value = database.withTransaction {
                val device = dao.device()
                if (device != null) dao.read(device)
                else {
                    check(dao.tasks().isEmpty() && dao.categories().isEmpty()) { "数据库缺少数据集信息。" }
                    val initial = DeckState(newId())
                    dao.persist(null, initial)
                    initial
                }
            }
        }
    }

    override suspend fun change(transform: (DeckState) -> DeckState): DeckState =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val previous = checkNotNull(committed.value)
                val next = transform(previous)
                if (previous != next) withContext(NonCancellable) {
                    database.withTransaction { dao.persist(previous, next) }
                    committed.value = next
                }
                next
            }
        }

    override fun close() = database.close()
}

private suspend fun DeckDao.read(device: DeviceRow) = DeckState(
    datasetId = device.datasetId,
    tasks = tasks().map { it.toTask() },
    categories = categories().map { Category(it.id, it.name, it.color, it.deleted) },
    manualOrder = order().map { it.taskId },
    currentId = device.currentId,
    pinnedId = device.pinnedId,
    shuffleSeed = device.shuffleSeed,
    preferences = Preferences(
        device.theme, device.sortMode, device.ritual, device.homeView,
        device.focusMode, device.focusMinutes, device.focusStyle, device.reduceMotion,
    ),
    progress = progress().map { FocusProgress(it.taskId, it.mode, it.durationMs, it.elapsedMs) },
)

private suspend fun DeckDao.persist(previous: DeckState?, next: DeckState) {
    val removedCategories = if (previous?.categories !== next.categories) {
        val oldRows = previous?.categories.orEmpty().mapIndexed { index, item -> item.toRow(index) }
        val newRows = next.categories.mapIndexed { index, item -> item.toRow(index) }
        val updates = changed(oldRows, newRows, CategoryRow::id)
        if (updates.isNotEmpty()) putCategories(updates)
        oldRows.map { it.id }.toSet() - newRows.map { it.id }.toSet()
    } else emptySet()
    if (previous?.tasks !== next.tasks) {
        val rows = rowChanges(previous?.tasks.orEmpty(), next.tasks, Task::id, Task::toRow)
        if (rows.inserts.isNotEmpty()) insertTasks(rows.inserts)
        if (rows.updates.isNotEmpty()) updateTasks(rows.updates)
    }
    removedCategories.forEach { removeCategory(it) }
    if (previous?.manualOrder != next.manualOrder) {
        val oldPositions = previous?.manualOrder.orEmpty().withIndex().associate { it.value to it.index }
        val newOrder = next.manualOrder.mapIndexedNotNull { index, id ->
            OrderRow(id, index).takeIf { oldPositions[id] != index }
        }
        if (newOrder.isNotEmpty()) putOrder(newOrder)
    }
    if (previous?.progress !== next.progress) {
        persistProgress(previous?.progress.orEmpty(), next.progress)
    }
    val device = next.toDevice()
    if (previous == null) insertDevice(device)
    else if (previous.toDevice() != device) updateDevice(device)
}

private suspend fun DeckDao.persistProgress(previous: List<FocusProgress>, next: List<FocusProgress>) {
    val rows = rowChanges(previous, next, FocusProgress::taskId, FocusProgress::toRow)
    rows.removedIds.forEach { removeProgress(it) }
    if (rows.inserts.isNotEmpty()) insertProgress(rows.inserts)
    if (rows.updates.isNotEmpty()) updateProgress(rows.updates)
}

private class RowChanges<R>(val inserts: List<R>, val updates: List<R>, val removedIds: Set<String>)

private fun <T : Any, R> rowChanges(previous: List<T>, next: List<T>, id: (T) -> String, toRow: (T) -> R): RowChanges<R> {
    val previousById = previous.associateByTo(mutableMapOf(), id)
    val inserts = mutableListOf<R>()
    val updates = mutableListOf<R>()
    for (item in next) {
        val old = previousById.remove(id(item))
        if (old == null) inserts.add(toRow(item))
        else if (old != item) updates.add(toRow(item))
    }
    return RowChanges(inserts, updates, previousById.keys)
}

private fun <T> changed(previous: List<T>, next: List<T>, key: (T) -> String): List<T> {
    val byId = previous.associateBy(key)
    return next.filter { byId[key(it)] != it }
}

private fun FocusProgress.toRow() = ProgressRow(taskId, mode, durationMs, elapsedMs)
private fun Category.toRow(position: Int) = CategoryRow(id, name, color, deleted, position)
private fun Task.toRow() = TaskRow(id, title, note, dueDate, estimatedMinutes, energy, category, createdAt, updatedAt, completedAt)
private fun TaskRow.toTask() = Task(id, title, note, dueDate, estimatedMinutes, energy, category, createdAt, updatedAt, completedAt)

private fun DeckState.toDevice() = DeviceRow(
    datasetId = datasetId, currentId = currentId, pinnedId = pinnedId, shuffleSeed = shuffleSeed,
    theme = preferences.theme, sortMode = preferences.sortMode, ritual = preferences.ritual,
    homeView = preferences.homeView, focusMode = preferences.focusMode, focusMinutes = preferences.focusMinutes,
    focusStyle = preferences.focusStyle, reduceMotion = preferences.reduceMotion,
)
