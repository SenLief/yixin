package cn.yixin.todo

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import cn.yixin.core.DeckState
import cn.yixin.core.Draft
import cn.yixin.core.FocusMode
import cn.yixin.core.HomeView
import cn.yixin.core.add
import cn.yixin.core.decodeBackup
import cn.yixin.core.encodeBackup
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YixinFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun createsPinsFocusesPausesAndArchivesThroughTheNativeUi() {
        val title = "离线验证 ${UUID.randomUUID().toString().take(8)} 🚀"
        openNewTask()
        ui.onNodeWithContentDescription("这一件，想做什么？").performTextInput(title)
        submitNew()
        // The repository can already contain data on an upgrade run.
        ui.onNodeWithText("卡片叠").performScrollTo().performClick()
        await("完成归档")
        click(title)
        click("展开")
        ui.onNodeWithContentDescription("置顶").performClick()
        ui.onNodeWithContentDescription("取消置顶").assertIsDisplayed()
        systemBack()
        ui.onNodeWithContentDescription("抽卡，置顶期间不可用").assertIsNotEnabled()

        ui.onNodeWithText(title).performTouchInput { swipeDown() }
        await("暂停")
        val model = model()
        val timer = checkNotNull(model.running.value)
        ui.onNodeWithText("横屏").assertDoesNotExist()
        click("计时设置")
        await("专注设置")
        ui.onNodeWithText("竖屏").assertIsSelected()
        val otherMode = FocusMode.entries.first { it != timer.base.mode }
        ui.onNodeWithText(timer.base.mode.label).performScrollTo().performClick()
        click(otherMode.label)
        ui.waitUntil(8_000) { model.state.value?.preferences?.focusMode == otherMode }
        assertEquals(timer.base.mode, model.running.value?.base?.mode)
        ui.onNodeWithText(otherMode.label).performScrollTo().performClick()
        click(timer.base.mode.label)
        ui.onNodeWithText("横屏").performScrollTo().performClick()
        ui.waitUntil(8_000) {
            ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        await("专注设置")
        ui.onNodeWithText("横屏").assertIsSelected()
        click("返回专注")
        await("暂停")
        val dial = ui.onNodeWithContentDescription("${timer.base.mode.label}，", substring = true).assertIsDisplayed()
        val taskLabel = ui.onNodeWithText(title).assertIsDisplayed()
        assertTrue(taskLabel.fetchSemanticsNode().boundsInRoot.right <= dial.fetchSemanticsNode().boundsInRoot.left)
        assertEquals(timer.startedAt, model().running.value?.startedAt)

        click("计时设置")
        ui.onNodeWithText("竖屏").performClick()
        ui.waitUntil(8_000) {
            ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        await("专注设置")
        ui.onNodeWithText("竖屏").assertIsSelected()
        click("返回专注")
        await("暂停")
        assertTrue(taskLabel.fetchSemanticsNode().boundsInRoot.bottom <= dial.fetchSemanticsNode().boundsInRoot.top)
        assertEquals(timer.startedAt, model().running.value?.startedAt)
        ui.waitUntil(8_000) {
            model.running.value?.startedAt == timer.startedAt && model.state.value?.progress?.any {
                it.taskId == timer.base.taskId && it.elapsedMs > 0
            } == true
        }
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.waitUntil(8_000) {
            model.running.value == null && model.state.value?.progress?.any {
                it.taskId == timer.base.taskId && it.elapsedMs > 0
            } == true
        }
        val saved = checkNotNull(model.state.value?.progress?.find { it.taskId == timer.base.taskId })
        assertTrue(saved.elapsedMs > 0)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        await("卡片叠")
        assertNull(model.running.value)
        focusAccessibly(title)
        await("暂停")
        assertTrue(checkNotNull(model.running.value).base.elapsedMs >= saved.elapsedMs)
        val beforeLock = checkNotNull(model.running.value)
        shell("input keyevent KEYCODE_SLEEP")
        try {
            ui.waitUntil(8_000) {
                model.running.value == null && model.screens.value.last() == Screen.Home
            }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        await("卡片叠")
        focusAccessibly(title)
        await("暂停")
        assertTrue(checkNotNull(model.running.value).base.elapsedMs >= beforeLock.base.elapsedMs)
        click("完成任务")
        await("撤销")
        assertNull(model.state.value?.pinnedId)
        assertNotNull(model.state.value?.task(timer.base.taskId)?.completedAt)
        click("撤销")
        await(title)
        assertNull(model.state.value?.task(timer.base.taskId)?.completedAt)
        assertNull(model.state.value?.pinnedId)
    }

    @Test fun previewsMergesExportsAndOpensCalendarTaskDetails() {
        val model = model()
        val app = ui.activity.applicationContext
        val id = UUID.randomUUID().toString()
        val title = "备份导入 " + id.take(8)
        val previous = model.state.value?.preferences
        val root = File(app.cacheDir, "shared-backups/test-$id").apply { mkdirs() }
        fun uri(file: File) = FileProvider.getUriForFile(app, "${app.packageName}.backup", file)
        val input = File(root, "input.json").apply {
            writeText(encodeBackup(DeckState("test").add(id, Draft(title, dueDate = LocalDate.now().toString()), 10)))
        }
        try {
            await("卡片叠")
            ui.onNodeWithContentDescription("设置").performClick()
            ui.onNodeWithText("备份与迁移").performScrollTo().performClick()
            await("选择备份文件")
            ui.runOnIdle { model.readBackup(uri(input)) }
            await("确认导入")
            assertNull(model.state.value?.task(id))
            click("确认导入")
            await("备份已导入")
            assertEquals(title, model.state.value?.task(id)?.title)

            val output = File(root, "output.json").apply { createNewFile() }
            ui.runOnIdle { model.exportBackup(uri(output)) }
            await("备份已保存")
            assertEquals(title, decodeBackup(output.readText()).data.task(id)?.title)
            systemBack()
            await("展示方式")
            ui.onNodeWithText("卡片").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            ui.onNodeWithText("日历").performSemanticsAction(SemanticsActions.OnClick)
            ui.waitUntil(8_000) { model.state.value?.preferences?.homeView == HomeView.calendar }
            ui.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick)
            ui.waitUntil(8_000) { model.screens.value.last() == Screen.Home }
            await("回到今天")
            ui.onNodeWithText(title).assertDoesNotExist()
            val today = LocalDate.now()
            val date = ui.onNodeWithContentDescription("${today.monthValue}月${today.dayOfMonth}日", substring = true)
            ui.mainClock.autoAdvance = false
            try {
                date.performScrollTo().performClick()
                ui.mainClock.advanceTimeBy(260)
                ui.onNodeWithText("回到今天").assertDoesNotExist()
                ui.onNodeWithText("返回日历").assertDoesNotExist()
                ui.mainClock.advanceTimeBy(480)
            } finally {
                ui.mainClock.autoAdvance = true
            }
            await("返回日历")
            await(title)
            ui.onNodeWithText(title).performScrollTo().performClick()
            await("眼前这一件")
            systemBack()
            await("返回日历")
            await(title)
            ui.onNodeWithText("返回日历").performScrollTo().performClick()
            await("回到今天")
            ui.onNodeWithText(title).assertDoesNotExist()
            assertEquals(id, model.state.value?.currentId)
        } finally {
            root.deleteRecursively()
            if (previous != null) ui.runOnIdle { model.preferences { previous } }
            else ui.runOnIdle { model.preferences { it.copy(homeView = HomeView.cards) } }
        }
    }

    @Test fun horizontalSwipeKeepsItsAxisAndTheCardEditButtonOpensTheEditor() {
        val model = model()
        val suffix = UUID.randomUUID().toString().take(8)
        val first = "横向滑动测试第一张 $suffix"
        val second = "横向滑动测试第二张 $suffix"
        await("卡片叠")
        for (title in listOf(first, second)) {
            openNewTask()
            ui.onNodeWithContentDescription("这一件，想做什么？").performTextInput(title)
            submitNew()
            await("卡片叠")
        }
        val firstId = checkNotNull(model.state.value?.tasks?.find { it.title == first }?.id)
        val secondId = checkNotNull(model.state.value?.tasks?.find { it.title == second }?.id)
        ui.runOnIdle { model.select(firstId) }
        await(first)
        val card = ui.onNodeWithTag("deck-gesture-area")
        val metrics = ui.activity.resources.displayMetrics
        if (metrics.widthPixels / metrics.density < 600) {
            val heightDp = card.fetchSemanticsNode().boundsInRoot.height / metrics.density
            val fontAllowance = (ui.activity.resources.configuration.fontScale - 1f).coerceAtLeast(0f) * 88f
            assertTrue("Phone card should fit the compact web proportions", heightDp <= 275f + fontAllowance)
        }
        val initialTop = ui.onNodeWithText(first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

        card.performTouchInput {
            down(center)
            moveTo(center + Offset(-width * 0.24f, height * 0.02f), delayMillis = 70)
        }
        val arcTop = ui.onNodeWithText(first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Card should lift along the arc", arcTop < initialTop - 2f)
        card.performTouchInput {
            moveTo(center + Offset(-width * 0.24f, -height * 0.37f), delayMillis = 80)
        }
        val draggedTop = ui.onNodeWithText(first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Finger Y should not steer the arc", arcTop, draggedTop, 1f)
        val previewTitle = ui.onNodeWithText(second, useUnmergedTree = true)
        val previewTop = try {
            ui.mainClock.autoAdvance = false
            card.performTouchInput { up() }
            ui.mainClock.advanceTimeBy(352)
            assertEquals("The preview should still be animating", firstId, model.state.value?.currentId)
            previewTitle.fetchSemanticsNode().boundsInRoot.top
        } finally {
            ui.mainClock.autoAdvance = true
        }
        await(second)
        assertEquals(secondId, model.state.value?.currentId)
        assertEquals("The title should stay in place when the preview becomes current",
            previewTop, previewTitle.fetchSemanticsNode().boundsInRoot.top, 0.8f * metrics.density)
        assertNull(model.state.value?.task(firstId)?.completedAt)

        ui.onNodeWithText("展开").assertIsDisplayed()
        ui.onNodeWithContentDescription("编辑任务").performClick()
        await("编辑这一件")
        assertEquals(listOf(Screen.Home, Screen.Editor(secondId)), model.screens.value)
        ui.onNodeWithContentDescription("关闭").performClick()
        awaitHome()
        await(second)
        assertEquals(secondId, model.state.value?.currentId)
    }

    @Test fun drawDealsAndRevealsBeforeHighlightingTheChosenCardAndStartingFocus() {
        val model = model()
        await("卡片叠")
        repeat(2) { index ->
            openNewTask()
            ui.onNodeWithContentDescription("这一件，想做什么？").performTextInput("发牌验证 ${index + 1} ${UUID.randomUUID().toString().take(8)}")
            submitNew()
            await("卡片叠")
        }
        ui.mainClock.autoAdvance = false
        try {
            ui.onNodeWithContentDescription("抽卡").performClick()
            ui.waitUntil(8_000) { model.screens.value.last() is Screen.Draw }
            ui.mainClock.advanceTimeBy(320)
            ui.onNodeWithText("正在发牌，看看会遇到哪几件小事……").assertIsDisplayed()
            ui.onNodeWithContentDescription("第 1 张，正在翻开").assertIsNotEnabled()
            ui.onAllNodesWithText("遇见这一件", useUnmergedTree = true)
                .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
            ui.mainClock.advanceTimeBy(1_550)
            ui.onNodeWithText("凭第一感觉，选一张开始。").assertIsDisplayed()
            val firstRound = model.screens.value.last() as Screen.Draw
            ui.onNodeWithText("再抽一次").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
            ui.waitUntil(8_000) {
                (model.screens.value.last() as? Screen.Draw)?.deal?.let { it != firstRound.deal } == true
            }
            ui.mainClock.advanceTimeBy(160)
            ui.onNodeWithContentDescription("第 1 张，正在翻开").assertIsNotEnabled()
            ui.mainClock.advanceTimeBy(1_600)
            val round = model.screens.value.last() as Screen.Draw
            val chosenId = round.ids.first()
            val task = checkNotNull(model.state.value?.task(chosenId))
            ui.onNodeWithContentDescription("选择并开始任务：${task.title}").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick)
            ui.mainClock.advanceTimeBy(240)
            ui.onNodeWithText("就做这一件").assertIsDisplayed()
            assertEquals(round, model.screens.value.last())
            ui.mainClock.advanceTimeBy(420)
            ui.waitUntil(8_000) { model.screens.value.last() == Screen.Focus(chosenId) }
            ui.waitUntil(8_000) { model.running.value?.base?.taskId == chosenId }
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun editorAndDetailsFollowTheWebPanelFlow() {
        val model = model()
        val title = "弹层测试 " + UUID.randomUUID().toString().take(8)
        val updated = "$title 更新"
        await("卡片叠")
        val add = ui.onNodeWithText("新安排").performScrollTo().fetchSemanticsNode().boundsInRoot
        val stack = ui.onNodeWithText("卡片叠").fetchSemanticsNode().boundsInRoot
        val archiveCount = checkNotNull(model.state.value).tasks.count { it.completedAt != null }
        val archive = ui.onNodeWithText("收好 $archiveCount 件").fetchSemanticsNode().boundsInRoot
        assertTrue(stack.center.x < add.center.x && add.center.x < archive.center.x)
        assertEquals(stack.center.y, add.center.y, 1f)
        assertEquals(archive.center.y, add.center.y, 1f)
        openNewTask()
        ui.onNode(hasText("新安排") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        await("写一个能开始的动作，就够了。")
        ui.onNodeWithContentDescription("希望哪天完成").assertDoesNotExist()
        ui.onNodeWithContentDescription("这一件，想做什么？").performTextInput(title)
        ui.onNodeWithContentDescription("给自己一句提示").performTextInput("先做第一步")
        ui.onNodeWithText("安排一下").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        ui.onNodeWithContentDescription("预计用时 · 分钟").performScrollTo().performTextInput("25")
        submitNew()
        val id = checkNotNull(model.state.value?.tasks?.find { it.title == title }?.id)
        assertEquals(25, model.state.value?.task(id)?.estimatedMinutes)
        ui.runOnIdle { model.select(id) }
        await(title)
        ui.onNodeWithText("展开").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await("眼前这一件")
        ui.onNodeWithText("投入一点").assertIsDisplayed()
        ui.onNode(hasText("25 分钟") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        val detail = hasAnyAncestor(isDialog())
        val focusBounds = ui.onNode(hasText("开始") and detail).performScrollTo().assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val completeBounds = ui.onNode(hasText("做好了") and detail).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(focusBounds.right <= completeBounds.left)
        assertEquals(focusBounds.center.y, completeBounds.center.y, 1f)
        assertEquals(focusBounds.width, completeBounds.width, 1f)
        assertEquals(focusBounds.height, completeBounds.height, 1f)
        assertTrue(focusBounds.height >= 53 * ui.activity.resources.displayMetrics.density)
        ui.onNodeWithContentDescription("关闭").assertDoesNotExist()
        ui.onNode(hasContentDescription("编辑任务") and hasAnyAncestor(isDialog())).performClick()
        await("编辑这一件")
        assertEquals(listOf(Screen.Home, Screen.Editor(id)), model.screens.value)
        ui.onNodeWithContentDescription("这一件，想做什么？").performTextReplacement(updated)
        ui.onNodeWithText("保存这张卡片").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitHome()
        assertEquals(updated, model.state.value?.task(id)?.title)
        assertEquals(id, model.state.value?.currentId)
        await(updated)

        val viewToggle = ui.onNodeWithContentDescription("切换到日历视图").performScrollTo()
        val toggleBounds = viewToggle.fetchSemanticsNode().boundsInRoot
        val settingsBounds = ui.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        assertEquals(settingsBounds.center.y, toggleBounds.center.y, 1f)
        assertTrue(toggleBounds.center.x < settingsBounds.center.x)
        viewToggle.performClick()
        await("回到今天")
        assertEquals(HomeView.calendar, model.state.value?.preferences?.homeView)
        assertEquals(id, model.state.value?.currentId)
        val today = LocalDate.now()
        val next = today.withDayOfMonth(if (today.dayOfMonth == 1) 2 else 1)
        ui.onNodeWithContentDescription("${next.monthValue}月${next.dayOfMonth}日", substring = true)
            .performScrollTo().performClick()
        await("返回日历")
        await("这一天，留有余地。")
        ui.onAllNodesWithText("新安排")[0].performScrollTo().performClick()
        ui.waitUntil(8_000) { (model.screens.value.last() as? Screen.Editor)?.defaultDueDate == next.toString() }
        ui.onNodeWithText(next.toString(), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("关闭").performClick()
        awaitHome()
        assertEquals(HomeView.calendar, model.state.value?.preferences?.homeView)
        await("返回日历")
        systemBack()
        await("回到今天")
        ui.onNodeWithText("未安排", substring = true).performScrollTo().performClick()
        await("返回日历")
        ui.onNodeWithText(updated).performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("切换到卡片视图").performScrollTo().performClick()
        await(updated)
        assertEquals(HomeView.cards, model.state.value?.preferences?.homeView)
        assertEquals(id, model.state.value?.currentId)
    }

    private fun openNewTask() {
        await("新安排")
        ui.onNodeWithText("新安排").performScrollTo().performClick()
    }

    private fun submitNew() {
        ui.onNodeWithText("放入卡片叠").performScrollTo().performClick()
        awaitHome()
    }

    private fun awaitHome() {
        val model = model()
        ui.waitUntil(10_000) { model.screens.value.last() == Screen.Home }
    }

    private fun focusAccessibly(title: String) {
        val node = ui.onNodeWithText(title).fetchSemanticsNode()
        val focus = node.config[SemanticsActions.CustomActions].single { it.label == "开始或继续专注" }
        ui.runOnIdle { assertTrue(focus.action()) }
    }

    private fun systemBack() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun model(): YixinViewModel {
        lateinit var model: YixinViewModel
        ui.activityRule.scenario.onActivity { model = ViewModelProvider(it)[YixinViewModel::class.java] }
        return model
    }

    private fun click(text: String) {
        await(text)
        ui.onNodeWithText(text).performClick()
    }

    private fun await(text: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
