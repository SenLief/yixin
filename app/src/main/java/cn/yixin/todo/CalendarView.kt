package cn.yixin.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yixin.core.DeckState
import cn.yixin.core.Task
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val flipDuration = 640
private val flipEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val calendarShape = RoundedCornerShape(18.dp)
private val dateLabel = DateTimeFormatter.ofPattern("M月d日EEEE", Locale.SIMPLIFIED_CHINESE)

@Composable
internal fun CalendarView(
    tasks: List<Task>, deck: DeckState, selectedDate: String?, selectDate: (String?) -> Unit,
    openEditor: () -> Unit, openTask: (Task) -> Unit,
) {
    val today = LocalDate.now()
    val selected = selectedDate?.let(LocalDate::parse)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: today).toString()) }
    var showAgenda by rememberSaveable { mutableStateOf(false) }
    val tasksByDate = remember(tasks) { tasks.groupBy(Task::dueDate) }
    val reduced = deck.preferences.reduceMotion
    BackHandler(enabled = showAgenda) { showAgenda = false }

    AnimatedContent(
        targetState = showAgenda,
        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(),
        transitionSpec = {
            (EnterTransition.None togetherWith ExitTransition.None).using(
                SizeTransform(clip = true) { _, _ -> if (reduced) snap() else tween(flipDuration, easing = flipEasing) },
            )
        },
        label = "calendar flip",
    ) { agenda ->
        // Each face rotates away from its own resting position. Hide it edge-on so the text is never mirrored.
        val rotation by transition.animateFloat(
            transitionSpec = { if (reduced) snap() else tween(flipDuration, easing = flipEasing) },
            label = "calendar face rotation",
        ) { state ->
            if (state == EnterExitState.Visible) 0f else if (agenda) -180f else 180f
        }
        val interactive = agenda == showAgenda && !transition.isRunning &&
            transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                rotationY = rotation
                cameraDistance = 1400.dp.toPx()
                alpha = if (abs(rotation) < 90f) 1f else 0f
            }.then(if (interactive) Modifier else Modifier.clearAndSetSemantics { }),
        ) {
            if (agenda) {
                CalendarAgenda(
                    selected, tasksByDate[selectedDate].orEmpty(), deck, interactive,
                    onReturn = { showAgenda = false }, openEditor = openEditor, openTask = openTask,
                )
            } else {
                CalendarPicker(
                    month = YearMonth.parse(monthText), today = today, selected = selected,
                    tasksByDate = tasksByDate, interactive = interactive,
                    moveMonth = { monthText = it.toString() },
                    onToday = {
                        monthText = YearMonth.from(today).toString()
                        selectDate(today.toString())
                    },
                    openDate = {
                        selectDate(it?.toString())
                        showAgenda = true
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarPicker(
    month: YearMonth, today: LocalDate, selected: LocalDate?, tasksByDate: Map<String?, List<Task>>,
    interactive: Boolean, moveMonth: (YearMonth) -> Unit, onToday: () -> Unit, openDate: (LocalDate?) -> Unit,
) {
    val c = LocalYixinColors.current
    Surface(shape = calendarShape, border = BorderStroke(1.dp, c.line), color = c.paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f).padding(start = 5.dp),
                    color = c.ink, style = MaterialTheme.typography.titleMedium)
                IconButton({ moveMonth(month.minusMonths(1)) }, enabled = interactive && month != YearMonth.of(0, 1)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", tint = c.accent)
                }
                IconButton({ moveMonth(month.plusMonths(1)) }, enabled = interactive && month != YearMonth.of(9999, 12)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", tint = c.accent)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                "一二三四五六日".forEach { weekday ->
                    Box(Modifier.weight(1f).heightIn(min = 25.dp), contentAlignment = Alignment.Center) {
                        Text(weekday.toString(), style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val monthLength = month.lengthOfMonth()
            repeat((offset + monthLength + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(7) { column ->
                        val number = week * 7 + column - offset + 1
                        if (number in 1..monthLength) {
                            val date = month.atDay(number)
                            CalendarDay(date, date == today, date == selected, tasksByDate[date.toString()].orEmpty().size,
                                interactive, Modifier.weight(1f)) { openDate(date) }
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 10.dp), color = c.line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onToday, enabled = interactive) { Text("回到今天") }
                TextButton(onClick = { openDate(null) }, enabled = interactive,
                    modifier = Modifier.semantics { this.selected = selected == null }) {
                    Text("未安排 ${tasksByDate[null].orEmpty().size}")
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate, today: Boolean, chosen: Boolean, count: Int, interactive: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = LocalYixinColors.current
    Surface(
        onClick = onClick, enabled = interactive,
        modifier = modifier.semantics {
            contentDescription = date.format(dateLabel) + (if (today) "，今天" else "") + "，${count}件待办"
            selected = chosen
        },
        shape = RoundedCornerShape(10.dp),
        border = if (today) BorderStroke(1.dp, c.accent) else null,
        color = if (chosen) c.accent else Color.Transparent,
    ) {
        Column(Modifier.heightIn(min = 46.dp).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(date.dayOfMonth.toString(), color = if (chosen) c.onAccent else c.ink,
                fontSize = 13.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
            Text(if (count > 0) count.toString() else " ", color = if (chosen) c.onAccent else c.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CalendarAgenda(
    date: LocalDate?, tasks: List<Task>, deck: DeckState, interactive: Boolean,
    onReturn: () -> Unit, openEditor: () -> Unit, openTask: (Task) -> Unit,
) {
    val c = LocalYixinColors.current
    val buttonShape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReturn, enabled = interactive, shape = buttonShape, border = BorderStroke(1.dp, c.line),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("返回日历")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(date?.format(dateLabel) ?: "未安排", color = c.ink,
                    textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium)
                Text("${tasks.size} 件待办", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        deck.task(deck.pinnedId)?.let { pinned ->
            Text("当前任务已钉住，完成或取消钉住后再切换。", color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (tasks.none { it.id == pinned.id }) {
                OutlinedButton(onClick = { openTask(pinned) }, enabled = interactive, modifier = Modifier.fillMaxWidth(),
                    shape = buttonShape, border = BorderStroke(1.dp, c.line)) {
                    HomeGlyph(Glyph.Pin, c.accent, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("已钉住：${pinned.title}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
                }
            }
        }
        if (tasks.isEmpty()) CalendarEmpty(date, interactive, openEditor)
        tasks.forEach { task ->
            key(task.id) {
                TaskRow(task, deck, enabled = interactive && deck.canSelect(task), onClick = { openTask(task) })
            }
        }
    }
}

@Composable
private fun CalendarEmpty(date: LocalDate?, interactive: Boolean, openEditor: () -> Unit) {
    val c = LocalYixinColors.current
    Column(Modifier.fillMaxWidth().heightIn(min = 270.dp).border(1.dp, c.line, calendarShape).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        HomeGlyph(Glyph.Calendar, c.accent, Modifier.size(30.dp))
        Text(if (date == null) "每件事，都可以慢慢安排。" else "这一天，留有余地。", color = c.ink,
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(if (date == null) "未填写希望完成日期的卡片会放在这里。" else "还没有待办，记下一件想在这天做的事。",
            color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        OutlinedButton(onClick = openEditor, enabled = interactive, border = BorderStroke(1.dp, c.line)) {
            Icon(Icons.Filled.Add, null, Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("新安排")
        }
    }
}
