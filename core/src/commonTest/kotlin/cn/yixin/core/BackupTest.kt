package cn.yixin.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    @Test fun roundTripPreservesIdsArchiveOrderPreferencesAndPausedFocus() {
        val state = triple().addCategory(Category("category-draw", "色卡", TagColor.rose))
            .edit("c", Draft("画山", category = "category-draw"), 10)
            .complete("b", 13)
            .select("c").togglePin("c")
            .saveProgress(FocusProgress("c", FocusMode.countdown, 60_000, 9_424))
            .copy(shuffleSeed = 77, preferences = Preferences(
                theme = Mood.woodland, sortMode = Sort.random, homeView = HomeView.calendar,
                focusStyle = FocusStyle.orbit, reduceMotion = true,
            ))
        val raw = encodeBackup(state)
        val imported = decodeBackup(raw)
        assertFalse(imported.fromWeb)
        assertFalse(raw.contains("startedAt"))
        assertEquals(state, imported.data)
        val restored = DeckState("new").merge(imported, ImportOptions(false, false))
        assertEquals(state, restored)
        assertEquals(restored, restored.merge(imported, ImportOptions(false, false)))
    }

    @Test fun validatesBeforeWriteAndRejectsUnknownVersionsAndForeignFields() {
        val raw = encodeBackup(triple())
        assertFailsWith<IllegalArgumentException> { decodeBackup("{oops") }
        assertFailsWith<IllegalArgumentException> { decodeBackup(raw.replace("\"version\": 1", "\"version\": 2")) }
        assertFailsWith<IllegalArgumentException> { decodeBackup(raw.replace("\"version\": 1", "\"version\": \"1\"")) }
        assertFailsWith<IllegalArgumentException> { decodeBackup(raw.replace("\"category\": \"work\"", "\"category\": \"category-x\"")) }
        assertFailsWith<IllegalArgumentException> { decodeBackup(raw.replace("\"estimatedMinutes\": 45", "\"estimatedMinutes\": \"45\"")) }
        assertFailsWith<IllegalArgumentException> { decodeBackup(raw.replace("\"manualOrder\"", "\"unrecognized\"")) }
        assertFailsWith<IllegalArgumentException> { decodeBackup(encodeBackup(triple().copy(manualOrder = listOf("a", "b", "b")))) }
        assertFailsWith<IllegalArgumentException> {
            decodeBackup(encodeBackup(triple().copy(progress = listOf(FocusProgress("missing", FocusMode.countdown, 60_000)))))
        }
    }

    @Test fun mergeRequiresExplicitConflictChoiceAndPreservesCategoryDeletion() {
        val local = triple().deleteCategory("work").saveProgress(FocusProgress("a", FocusMode.countup, 1_500_000, 900))
        val remote = triple().edit("a", Draft("有改动", category = "work"), 400)
            .add("d", Draft("只在备份"), 401).move("d", -1)
            .saveProgress(FocusProgress("a", FocusMode.countup, 1_500_000, 950))
            .copy(preferences = Preferences(theme = Mood.night), shuffleSeed = 99)
        val backup = decodeBackup(encodeBackup(remote))
        val preview = local.preview(backup)
        assertEquals(1, preview.newTasks)
        assertEquals(setOf("a", "work"), preview.conflicts.map { it.id }.toSet())
        assertEquals(3, preview.conflicts.size)
        val kept = local.merge(backup, ImportOptions(false, false))
        assertEquals(listOf("a", "b", "c", "d"), kept.manualOrder)
        assertEquals(local.task("a"), kept.task("a"))
        assertEquals(local.progress, kept.progress)
        val accepted = local.merge(backup, ImportOptions(true, true))
        assertEquals(remote.manualOrder, accepted.manualOrder)
        assertEquals(remote.task("a"), accepted.task("a"))
        assertEquals(remote.progress, accepted.progress)
        assertEquals(remote.preferences, accepted.preferences)
        assertEquals(99L, accepted.shuffleSeed)
        assertNull(accepted.activeCategory("work"))
        assertTrue(accepted.categories.first { it.id == "work" }.deleted)
    }

    @Test fun importsRealWebShapeWithSameShuffleOrderAndEmojiIds() {
        val backup = decodeBackup(webFixture)
        assertTrue(backup.fromWeb)
        val data = backup.data
        assertEquals(listOf("a", "card-z", "任务🧭A", "card-x", "🔵", "🚀", "key"), data.manualOrder)
        assertEquals(listOf("key", "card-x", "a", "🔵", "🚀", "任务🧭A"), data.ordered().ids())
        assertEquals(17L, data.task("card-z")?.completedAt)
        assertNull(data.task("a")?.category)
        assertEquals(defaultCategories, data.categories)
        val imported = DeckState("phone").merge(backup, ImportOptions(false, false))
        assertEquals(data.ordered().ids(), imported.ordered().ids())
        assertEquals("phone", imported.datasetId)
        assertFailsWith<IllegalArgumentException> { decodeBackup(webFixture.replace("schemaVersion\": 1", "schemaVersion\": 2")) }
    }
}

// Exported by the web model using the same JSON shape as the download action.
private val webFixture = """{
      "schemaVersion": 1,
      "tasks": [
        {
          "id": "a",
          "title": "任务 a",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 0,
          "updatedAt": 0,
          "completedAt": null
        },
        {
          "id": "card-z",
          "title": "任务 card-z",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 1,
          "updatedAt": 17,
          "completedAt": 17
        },
        {
          "id": "任务🧭A",
          "title": "任务 任务🧭A",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 2,
          "updatedAt": 2,
          "completedAt": null
        },
        {
          "id": "card-x",
          "title": "任务 card-x",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 3,
          "updatedAt": 3,
          "completedAt": null
        },
        {
          "id": "🔵",
          "title": "任务 🔵",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 4,
          "updatedAt": 4,
          "completedAt": null
        },
        {
          "id": "🚀",
          "title": "任务 🚀",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 5,
          "updatedAt": 5,
          "completedAt": null
        },
        {
          "id": "key",
          "title": "任务 key",
          "note": "",
          "dueDate": null,
          "estimatedMinutes": null,
          "energy": "medium",
          "pinned": false,
          "createdAt": 6,
          "updatedAt": 6,
          "completedAt": null
        }
      ],
      "manualOrder": [
        "a",
        "card-z",
        "任务🧭A",
        "card-x",
        "🔵",
        "🚀",
        "key"
      ],
      "currentId": "a",
      "shuffleSeed": 4294967295,
      "preferences": {
        "theme": "sage",
        "sortMode": "random",
        "ritual": "stamp"
      },
      "activities": [
        {
          "kind": "created",
          "at": 0,
          "taskId": "a"
        },
        {
          "kind": "created",
          "at": 1,
          "taskId": "card-z"
        },
        {
          "kind": "created",
          "at": 2,
          "taskId": "任务🧭A"
        },
        {
          "kind": "created",
          "at": 3,
          "taskId": "card-x"
        },
        {
          "kind": "created",
          "at": 4,
          "taskId": "🔵"
        },
        {
          "kind": "created",
          "at": 5,
          "taskId": "🚀"
        },
        {
          "kind": "created",
          "at": 6,
          "taskId": "key"
        },
        {
          "kind": "completed",
          "at": 17,
          "taskId": "card-z"
        },
        {
          "kind": "sort-changed",
          "at": 18
        }
      ]
    }
""".trimIndent()
