package cn.yixin.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
enum class Sort(val label: String) {
    manual("我的顺序"), due("日期优先"), created("先记先做"), shortest("轻事先做"), random("随心一叠")
}

@Serializable
enum class Mood(val label: String) {
    sage("苔庭"), paper("纸间"), night("夜航"), rose("薄樱"), galaxy("银河"), aurora("极光"), woodland("林夜")
}

@Serializable
enum class Ritual(val label: String) {
    stamp("轻轻盖章"), quiet("安静收起"), dissolve("粒子消散")
}

@Serializable
enum class Energy(val label: String) {
    low("轻松做"), medium("投入一点"), high("需要专注")
}

@Serializable
enum class TagColor(val label: String, val rgb: Int) {
    blue("雾蓝", 0x7897bd), green("苔绿", 0x8ba88b), purple("浅紫", 0xaa99be),
    amber("琥珀", 0xc3a16a), rose("蔷薇", 0xc58c9d), slate("青灰", 0x8a9ba4)
}

@Serializable
enum class FocusStyle(val label: String) {
    quiet("留白"), orbit("星轨"), tide("潮汐"), glow("浮光"),
    galaxy("银河"), aurora("极光"), woodland("林夜")
}

@Serializable
enum class FocusMode(val label: String) {
    countdown("倒计时"), countup("正计时")
}

@Serializable
enum class HomeView(val label: String) {
    cards("卡片"), calendar("日历")
}

@Serializable
data class Preferences(
    val theme: Mood = Mood.sage,
    val sortMode: Sort = Sort.manual,
    val ritual: Ritual = Ritual.stamp,
    val homeView: HomeView = HomeView.cards,
    val focusMode: FocusMode = FocusMode.countdown,
    val focusMinutes: Int = 25,
    val focusStyle: FocusStyle = FocusStyle.quiet,
    val reduceMotion: Boolean = false,
)

@Serializable
data class Category(val id: String, val name: String, val color: TagColor, val deleted: Boolean = false)

val defaultCategories = listOf(
    Category("work", "工作", TagColor.blue),
    Category("life", "生活", TagColor.green),
    Category("learn", "学习", TagColor.purple),
    Category("idea", "灵感", TagColor.amber),
)

@Serializable
data class Task(
    val id: String,
    val title: String,
    val note: String,
    val dueDate: String?,
    val estimatedMinutes: Int?,
    val energy: Energy,
    @Required val category: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @Required val completedAt: Long? = null,
) {
    fun draft() = Draft(title, note, dueDate, estimatedMinutes, energy, category)
}

data class Draft(
    val title: String,
    val note: String = "",
    val dueDate: String? = null,
    val estimatedMinutes: Int? = null,
    val energy: Energy = Energy.medium,
    val category: String? = null,
) {
    fun validate() {
        require(title.isNotBlank() && title.length <= 120 && title.hasPairedSurrogates()) {
            "请填写标题，最多 120 个 UTF-16 码元。"
        }
        require(note.length <= 1000 && note.hasPairedSurrogates()) {
            "备注最多 1000 个 UTF-16 码元。"
        }
        require(dueDate == null || validDate(dueDate)) { "日期无效，请使用 YYYY-MM-DD。" }
        require(estimatedMinutes == null || estimatedMinutes in 1..1440) {
            "预计用时须为 1 至 1440 分钟。"
        }
    }
}

internal fun String.hasPairedSurrogates(): Boolean = indices.all { index ->
    val char = this[index]
    when (char) {
        in '\uD800'..'\uDBFF' -> getOrNull(index + 1) in '\uDC00'..'\uDFFF'
        in '\uDC00'..'\uDFFF' -> getOrNull(index - 1) in '\uD800'..'\uDBFF'
        else -> true
    }
}

private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val customCategoryPattern = Regex("category-[A-Za-z0-9_-]{1,80}")

private fun validDate(value: String): Boolean =
    datePattern.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess

internal fun validCategoryId(id: String): Boolean =
    defaultCategories.any { it.id == id } || customCategoryPattern.matches(id)

internal fun Category.validate() {
    require(validCategoryId(id) && name == name.trim() && name.length in 1..20 && name.hasPairedSurrogates()) {
        "类别名称须为 1 至 20 个 UTF-16 码元，且不能包含首尾空格。"
    }
}

@Serializable
data class FocusProgress(
    val taskId: String,
    val mode: FocusMode,
    val durationMs: Long,
    @Required val elapsedMs: Long = 0,
) {
    val finished get() = mode == FocusMode.countdown && elapsedMs >= durationMs

    fun advance(deltaMs: Long): FocusProgress {
        val elapsed = elapsedMs + deltaMs.coerceIn(0, Long.MAX_VALUE - elapsedMs)
        return copy(elapsedMs = if (mode == FocusMode.countdown) elapsed.coerceAtMost(durationMs) else elapsed)
    }
}

@Serializable
data class DeckState(
    val datasetId: String,
    val tasks: List<Task> = emptyList(),
    val categories: List<Category> = defaultCategories,
    val manualOrder: List<String> = emptyList(),
    val currentId: String? = null,
    val pinnedId: String? = null,
    val shuffleSeed: Long = 1,
    val preferences: Preferences = Preferences(),
    val progress: List<FocusProgress> = emptyList(),
) {
    fun activeCategory(id: String?) = categories.firstOrNull { it.id == id && !it.deleted }
    fun task(id: String?) = tasks.firstOrNull { it.id == id }
    private fun pinAllows(id: String) = pinnedId == null || pinnedId == id
    fun canSelect(id: String): Boolean =
        pinAllows(id) && task(id)?.let { it.completedAt == null } == true
    fun canSelect(task: Task): Boolean = pinAllows(task.id) && task.completedAt == null
    fun current(activeOrder: List<Task>? = null): Task? =
        task(pinnedId ?: currentId)?.takeIf { it.completedAt == null } ?: (activeOrder ?: ordered()).firstOrNull()
}

interface Repository {
    val state: StateFlow<DeckState?>
    suspend fun open()
    suspend fun change(transform: (DeckState) -> DeckState): DeckState
}
