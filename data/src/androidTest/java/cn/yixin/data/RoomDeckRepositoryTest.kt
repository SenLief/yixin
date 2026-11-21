package cn.yixin.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yixin.core.Category
import cn.yixin.core.Draft
import cn.yixin.core.FocusMode
import cn.yixin.core.FocusProgress
import cn.yixin.core.Mood
import cn.yixin.core.Sort
import cn.yixin.core.TagColor
import cn.yixin.core.add
import cn.yixin.core.addCategory
import cn.yixin.core.complete
import cn.yixin.core.deleteCategory
import cn.yixin.core.edit
import cn.yixin.core.move
import cn.yixin.core.saveProgress
import cn.yixin.core.select
import cn.yixin.core.togglePin
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDeckRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun reopensTasksOrderArchiveCategoryTombstoneAndCheckpoint() = runBlocking {
        val name = "${UUID.randomUUID()}.db"
        try {
            RoomDeckRepository(context, name) { "stable-dataset" }.use { repository ->
                repository.open()
                repository.change {
                    it.add("a", Draft("工作🚀", category = "work"), 1)
                        .add("b", Draft("已完成"), 2)
                        .add("c", Draft("专注"), 3)
                        .complete("b", 4).move("c", -1).select("c").togglePin("c")
                        .deleteCategory("work").saveProgress(FocusProgress("c", FocusMode.countup, 1_500_000, 3_440))
                        .copy(shuffleSeed = 77, preferences = it.preferences.copy(theme = Mood.night, sortMode = Sort.today))
                }
            }
            RoomDeckRepository(context, name) { "new-id-must-not-be-used" }.use { reopened ->
                reopened.open()
                val data = checkNotNull(reopened.state.value)
                assertEquals("stable-dataset", data.datasetId)
                assertEquals("工作🚀", data.task("a")?.title)
                assertEquals(4L, data.task("b")?.completedAt)
                assertEquals(listOf("c", "b", "a"), data.manualOrder)
                assertEquals("c", data.pinnedId)
                assertEquals(77L, data.shuffleSeed)
                assertEquals(Mood.night, data.preferences.theme)
                assertEquals(Sort.today, data.preferences.sortMode)
                assertNull(data.activeCategory("work"))
                assertEquals(3_440L, data.progress.single().elapsedMs)
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test fun reusesOpenedStoreAndUpdatesExistingRowsWithOneWrite() = runBlocking {
        val name = "${UUID.randomUUID()}.db"
        val statements = ConcurrentLinkedQueue<String>()
        val tableAccess = Regex(
            """\b(?:FROM|INTO|UPDATE(?: OR \w+)?)\s+[`"]?(?:categories|tasks|manual_order|device_state|focus_progress)\b""",
            RegexOption.IGNORE_CASE,
        )
        val callback = object : RoomDatabase.QueryCallback {
            override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                if (tableAccess.containsMatchIn(sqlQuery)) statements.add(sqlQuery)
            }
        }
        val database = Room.databaseBuilder(context, YixinDatabase::class.java, name)
            .setQueryCallback(callback, Executor { it.run() })
            .build()
        try {
            RoomDeckRepository(database) { "dataset" }.use { repository ->
                repository.open()
                val initial = FocusProgress("a", FocusMode.countup, 1_500_000)
                repository.change { it.add("a", Draft("任务"), 1).add("b", Draft("另一件"), 2).saveProgress(initial) }

                statements.clear()
                repository.open()
                assertTrue(statements.isEmpty())

                repository.change { it.select("b") }
                val selectionWrite = dataWrites(statements).single()
                assertTrue(selectionWrite.startsWith("UPDATE ") && "device_state" in selectionWrite)

                statements.clear()
                repository.change { it.edit("b", Draft("已编辑"), 3) }
                val editWrite = dataWrites(statements).single()
                assertTrue(editWrite.startsWith("UPDATE ") && "tasks" in editWrite)

                statements.clear()
                val checkpoint = initial.copy(elapsedMs = 4_000)
                repository.change { it.saveProgress(checkpoint) }
                val checkpointWrite = dataWrites(statements).single()
                assertTrue(checkpointWrite.startsWith("UPDATE ") && "focus_progress" in checkpointWrite)

                statements.clear()
                repository.change { it.saveProgress(checkpoint) }
                assertTrue(statements.isEmpty())
            }
            RoomDeckRepository(context, name) { "new-id" }.use { reopened ->
                reopened.open()
                assertEquals("b", reopened.state.value?.currentId)
                assertEquals("已编辑", reopened.state.value?.task("b")?.title)
                assertEquals(4_000L, reopened.state.value?.progress?.single()?.elapsedMs)
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test fun rollsBackPartiallyWrittenTransactionOnConstraintFailure() = runBlocking {
        val name = "${UUID.randomUUID()}.db"
        try {
            RoomDeckRepository(context, name) { "dataset" }.use { repository ->
                repository.open()
                val initial = repository.change { it.add("a", Draft("任务"), 1) }
                val failed = runCatching {
                    repository.change { state ->
                        val changed = state.addCategory(Category("category-new", "新标签", TagColor.slate))
                        changed.copy(tasks = changed.tasks.map { it.copy(category = "category-missing") })
                    }
                }
                assertTrue(failed.isFailure)
                assertEquals(initial, repository.state.value)
            }
            RoomDeckRepository(context, name) { "new-id" }.use {
                it.open()
                assertNull(it.state.value?.activeCategory("category-new"))
                assertNull(it.state.value?.task("a")?.category)
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    private fun dataWrites(statements: Iterable<String>) = statements.filter {
        it.startsWith("INSERT ") || it.startsWith("UPDATE ") || it.startsWith("DELETE ")
    }
}
