package cn.yixin.core

import kotlin.random.Random

// JavaScript for…of walks code points, but charCodeAt(0) hashes only the first UTF-16
// code unit of each. Int multiplication wraps like Math.imul, ushr is JS >>>.
fun legacyRandomKey(id: String, seed: Long): UInt {
    var hash = 2166136261u.toInt()
    val value = "$seed:$id"
    var index = 0
    while (index < value.length) {
        val unit = value[index++]
        hash = (hash xor unit.code) * 16777619
        if (unit in '\uD800'..'\uDBFF' && value.getOrNull(index) in '\uDC00'..'\uDFFF') index++
    }
    hash = hash xor (hash ushr 16)
    hash *= 0x85ebca6bu.toInt()
    return (hash xor (hash ushr 13)).toUInt()
}

fun DeckState.ordered(): List<Task> {
    val active = tasks.filter { it.completedAt == null }
    val chosen = when (preferences.sortMode) {
        Sort.manual, Sort.today -> {
            val byId = active.associateBy(Task::id)
            return buildList(active.size) {
                pinnedId?.let(byId::get)?.let(::add)
                for (id in manualOrder) if (id != pinnedId) byId[id]?.let(::add)
            }
        }
        Sort.due -> compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate }
        Sort.created -> compareBy { it.createdAt }
        Sort.shortest -> compareBy { it.estimatedMinutes ?: Int.MAX_VALUE }
        Sort.random -> {
            val keys = active.associate { it.id to legacyRandomKey(it.id, shuffleSeed) }
            compareBy { keys.getValue(it.id) }
        }
    }
    val position = manualOrder.withIndex().associate { (index, id) -> id to index }
    return active.sortedWith(
        compareByDescending<Task> { it.id == pinnedId }.then(chosen).thenBy { position.getValue(it.id) },
    )
}

fun DeckState.cardsFor(today: String, activeOrder: List<Task> = ordered()): List<Task> =
    if (preferences.sortMode == Sort.today) activeOrder.filter { it.dueDate == today } else activeOrder

fun DeckState.currentCard(cards: List<Task>): Task? {
    val selectedId = pinnedId ?: currentId
    return cards.firstOrNull { it.id == selectedId } ?: cards.firstOrNull().takeIf { pinnedId == null }
}

sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Unclassified : CategoryFilter
    data class Tagged(val id: String) : CategoryFilter
}

fun DeckState.matches(task: Task, filter: CategoryFilter): Boolean = when (filter) {
    CategoryFilter.All -> true
    CategoryFilter.Unclassified -> activeCategory(task.category) == null
    is CategoryFilter.Tagged -> activeCategory(task.category)?.id == filter.id
}

fun DeckState.add(id: String, draft: Draft, at: Long): DeckState {
    draft.validate()
    require(task(id) == null) { "任务编号重复，请重试。" }
    val newTask = Task(
        id, draft.title.trim(), draft.note, draft.dueDate, draft.estimatedMinutes,
        draft.energy, activeCategory(draft.category)?.id, at, at,
    )
    return copy(tasks = tasks + newTask, manualOrder = manualOrder + id, currentId = current()?.id ?: id)
}

fun DeckState.edit(id: String, draft: Draft, at: Long): DeckState {
    draft.validate()
    return copy(tasks = tasks.map {
        if (it.id == id) it.copy(
            title = draft.title.trim(), note = draft.note, dueDate = draft.dueDate,
            estimatedMinutes = draft.estimatedMinutes, energy = draft.energy,
            category = activeCategory(draft.category)?.id, updatedAt = at,
        ) else it
    })
}

fun DeckState.select(id: String) = if (canSelect(id)) copy(currentId = id) else this

fun DeckState.navigate(direction: Int, fromId: String? = null, today: String? = null): DeckState {
    if (pinnedId != null) return this
    val deck = today?.let { cardsFor(it) } ?: ordered()
    if (deck.size < 2) return this
    val index = deck.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    if (fromId != null && deck[index].id != fromId) return this
    return copy(currentId = deck[(index + direction + deck.size) % deck.size].id)
}

fun DeckState.togglePin(id: String): DeckState = when {
    current()?.id != id -> this
    pinnedId == id -> copy(pinnedId = null)
    else -> copy(currentId = id, pinnedId = id)
}

fun DeckState.complete(id: String, at: Long, today: String? = null): DeckState {
    val todayOrder = today?.takeIf { preferences.sortMode == Sort.today && task(id)?.dueDate == it }
        ?.let { cardsFor(it) }
    val deck = todayOrder ?: ordered()
    val index = deck.indexOfFirst { it.id == id }
    if (index < 0) return this
    val next = if (deck.size > 1) deck[(index + 1) % deck.size].id else null
    val currentTaskId = todayOrder?.let { currentCard(it)?.id } ?: current(deck)?.id
    return copy(
        tasks = tasks.map { if (it.id == id) it.copy(completedAt = at, updatedAt = at) else it },
        progress = progress.filterNot { it.taskId == id },
        pinnedId = pinnedId.takeUnless { it == id },
        currentId = if (currentTaskId == id) next else currentTaskId,
    )
}

data class Completion(val id: String, val at: Long)

fun DeckState.restore(id: String, at: Long, undo: Completion? = null): DeckState {
    val archivedAt = task(id)?.completedAt ?: return this
    if (undo != null && (undo.id != id || undo.at != archivedAt)) return this
    return copy(
        tasks = tasks.map { if (it.id == id) it.copy(completedAt = null, updatedAt = at) else it },
        currentId = if (undo != null && pinnedId == null) id else current()?.id ?: id,
    )
}

fun DeckState.move(id: String, direction: Int): DeckState {
    if (preferences.sortMode != Sort.manual || pinnedId != null) return this
    val active = ordered().map { it.id }
    val from = active.indexOf(id)
    if (from < 0) return this
    val neighbor = active.getOrNull(from + direction) ?: return this
    return copy(manualOrder = manualOrder.map {
        when (it) {
            id -> neighbor
            neighbor -> id
            else -> it
        }
    })
}

fun DeckState.addCategory(category: Category): DeckState {
    category.validate()
    require(categories.none { it.id == category.id }) { "类别编号重复，请重试。" }
    require(categories.none { !it.deleted && it.name == category.name }) { "已有同名类别。" }
    return copy(categories = categories + category)
}

fun DeckState.deleteCategory(id: String) =
    copy(categories = categories.map { if (it.id == id) it.copy(deleted = true) else it })

fun DeckState.draw(random: Random, today: String? = null): List<String> =
    if (pinnedId == null) (today?.let { cardsFor(it) } ?: ordered()).shuffled(random).take(3).map { it.id } else emptyList()

fun DeckState.saveProgress(progress: FocusProgress): DeckState {
    if (task(progress.taskId)?.let { it.completedAt == null } != true) return this
    return copy(progress = (this.progress.filterNot { it.taskId == progress.taskId } + progress).sortedBy { it.taskId })
}

data class RunningFocus(val base: FocusProgress, val startedAt: Long) {
    fun at(now: Long): FocusProgress = base.advance((now - startedAt).coerceAtLeast(0))
}

fun newProgress(id: String, preferences: Preferences) = FocusProgress(
    id, preferences.focusMode, preferences.focusMinutes * 60_000L,
)
