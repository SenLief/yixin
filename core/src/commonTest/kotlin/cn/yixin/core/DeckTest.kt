package cn.yixin.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckTest {
    @Test fun draftsUseUtf16AndValidateDatesAndEstimates() {
        Draft("中文" + "🚀".repeat(59), "夜".repeat(1000), "2028-02-29", 1440).validate()
        assertFailsWith<IllegalArgumentException> { Draft("🚀".repeat(61)).validate() }
        assertFailsWith<IllegalArgumentException> { Draft("\uD83D").validate() }
        assertFailsWith<IllegalArgumentException> { Draft("  ").validate() }
        assertFailsWith<IllegalArgumentException> { Draft("事", "🌙".repeat(501)).validate() }
        assertFailsWith<IllegalArgumentException> { Draft("事", dueDate = "2026-02-29").validate() }
        assertFailsWith<IllegalArgumentException> { Draft("事", estimatedMinutes = 0).validate() }
        assertFailsWith<IllegalArgumentException> { Draft("事", estimatedMinutes = 1441).validate() }
    }

    @Test fun pinConstrainsSelectionAndCompletionUndoIsScoped() {
        val start = triple()
        assertEquals(start, start.togglePin("b"))
        assertEquals("a", start.togglePin("a").pinnedId)
        val pinned = start.select("b").togglePin("b")
        assertEquals("b", pinned.pinnedId)
        assertEquals(listOf("b", "a", "c"), pinned.ordered().ids())
        assertEquals(pinned, pinned.navigate(1))
        assertEquals(pinned, pinned.select("c"))
        assertFalse(pinned.canSelect("a"))
        assertEquals(emptyList(), pinned.draw(Random(4)))
        val completed = pinned.complete("b", 40)
        assertNull(completed.pinnedId)
        assertEquals("a", completed.currentId)
        val later = completed.add("d", Draft("新任务"), 41).copy(preferences = Preferences(theme = Mood.rose))
        val undone = later.restore("b", 42, Completion("b", 40))
        assertEquals(listOf("a", "b", "c", "d"), undone.manualOrder)
        assertEquals("b", undone.currentId)
        assertEquals(Mood.rose, undone.preferences.theme)
        assertNull(undone.pinnedId)
        assertNull(undone.task("b")?.completedAt)
        val newer = undone.complete("b", 43).select("c").togglePin("c")
        assertEquals(newer, newer.restore("b", 44, Completion("b", 40)))
        assertEquals("c", newer.restore("b", 44).currentId)
        assertEquals("c", newer.restore("b", 44, Completion("b", 43)).pinnedId)
    }

    @Test fun separatesManualOrderFromSortAndArchive() {
        val start = triple().complete("b", 5).move("c", -1)
        assertEquals(listOf("c", "b", "a"), start.manualOrder)
        val restored = start.restore("b", 6)
        assertEquals(listOf("c", "b", "a"), restored.ordered().ids())
        val shortest = restored.using(Sort.shortest)
        assertEquals(listOf("b", "a", "c"), shortest.ordered().ids())
        assertEquals(listOf("c", "b", "a"), shortest.using(Sort.manual).ordered().ids())
        assertEquals(listOf("c", "a", "b"), restored.using(Sort.due).ordered().ids())
        assertEquals(listOf("a", "b", "c"), restored.using(Sort.created).ordered().ids())
        assertEquals(restored.currentId, shortest.currentId)
        assertEquals("b", restored.copy(currentId = null).navigate(1).currentId)
        assertEquals("a", start.copy(currentId = "b").navigate(-1).currentId)
        val fallback = restored.copy(currentId = null)
        assertEquals(fallback, fallback.navigate(1, fromId = "a"))
        assertEquals("b", fallback.navigate(1, fromId = "c").currentId)
    }

    @Test fun randomRetainsRelativeOrderAndCurrent() {
        val start = triple().using(Sort.random)
        val before = start.ordered().ids()
        val edited = start.add("d", Draft("新任务"), 9).complete("b", 10)
        assertEquals(before - "b", edited.ordered().ids() - "d")
        assertEquals(before, edited.restore("b", 11).ordered().ids() - "d")
        val shuffled = start.copy(shuffleSeed = 18)
        assertEquals(start.currentId, shuffled.currentId)
        assertEquals(shuffled.ordered().ids(), decodeBackup(encodeBackup(shuffled)).data.ordered().ids())
        assertEquals(3, shuffled.draw(Random(6)).distinct().size)
    }

    @Test fun todayCardsIncludeOnlyPendingTasksDueOnTheLocalDate() {
        val today = "2026-12-31"
        val tomorrow = "2027-01-01"
        val state = triple()
            .add("overdue", Draft("昨天", dueDate = "2026-12-30"), 4)
            .add("d", Draft("今天的第二件", dueDate = today), 5)
            .add("archived", Draft("今天已完成", dueDate = today), 6)
            .complete("archived", 7)
            .using(Sort.today)
        val cards = state.cardsFor(today)
        assertEquals(listOf("c", "d"), cards.ids())
        assertEquals(listOf("a", "b", "c", "overdue", "d"), state.ordered().ids())
        assertEquals("c", state.currentCard(cards)?.id)
        assertEquals("d", state.navigate(1, fromId = "c", today = today).currentId)
        assertEquals("d", state.navigate(-1, fromId = "c", today = today).currentId)
        assertEquals(state, state.navigate(1, fromId = "a", today = today))
        assertEquals(setOf("c", "d"), state.draw(Random(6), today).toSet())
        assertEquals("d", state.complete("c", 8, today).currentId)
        assertEquals(listOf("a"), state.cardsFor(tomorrow).ids())
        assertTrue(state.cardsFor("2027-01-02").isEmpty())
        assertEquals(state.ordered(), state.using(Sort.manual).cardsFor(today))
        assertEquals(Sort.today, decodeBackup(encodeBackup(state)).data.preferences.sortMode)

        val pinned = state.togglePin("a")
        assertNull(pinned.currentCard(pinned.cardsFor(today)))
        assertEquals(pinned, pinned.navigate(1, fromId = "c", today = today))
        assertEquals("c", pinned.togglePin("a").currentCard(cards)?.id)
    }

    @Test fun categoryTombstonesKeepTasksAndDoNotChangeOrderOrPin() {
        val state = triple().select("c").togglePin("c")
        val filtered = state.ordered().filter { state.matches(it, CategoryFilter.Tagged("work")) }
        assertEquals(listOf("a"), filtered.ids())
        val deleted = state.deleteCategory("work")
        assertEquals(state.currentId, deleted.currentId)
        assertEquals(state.pinnedId, deleted.pinnedId)
        assertEquals(state.manualOrder, deleted.manualOrder)
        assertEquals("work", deleted.task("a")?.category)
        assertTrue(deleted.matches(deleted.task("a")!!, CategoryFilter.Unclassified))
    }

    @Test fun clockUsesMonotonicAnchorsAndCapsCountdown() {
        val progress = FocusProgress("a", FocusMode.countdown, 60_000, 8_200)
        val timer = RunningFocus(progress, 5_000)
        assertEquals(8_200, timer.at(4_500).elapsedMs)
        val paused = timer.at(6_850)
        assertEquals(10_050, paused.elapsedMs)
        val resumed = RunningFocus(paused, 99_999)
        assertEquals(11_050, resumed.at(100_999).elapsedMs)
        assertEquals(60_000, resumed.at(170_000).elapsedMs)
        assertTrue(resumed.at(170_000).finished)
        val countUp = progress.copy(mode = FocusMode.countup)
        assertEquals(68_200, countUp.advance(60_000).elapsedMs)
        assertFalse(countUp.advance(60_000).finished)
    }
}

private fun DeckState.using(sort: Sort) = copy(preferences = preferences.copy(sortMode = sort))
internal fun List<Task>.ids() = map { it.id }
internal fun triple() = DeckState("local")
    .add("a", Draft("写中文", dueDate = "2027-01-01", estimatedMinutes = 45, category = "work"), 1)
    .add("b", Draft("带水🚀", estimatedMinutes = 2), 2)
    .add("c", Draft("回一封信", dueDate = "2026-12-31"), 3)
