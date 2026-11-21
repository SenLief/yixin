package cn.yixin.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cn.yixin.core.DeckState
import cn.yixin.core.Task

private val drawShape = RoundedCornerShape(16.dp)
private val dealEasing = CubicBezierEasing(0.2f, 0.7f, 0.25f, 1f)
private val flipEasing = CubicBezierEasing(0.3f, 0.1f, 0.2f, 1f)
private val chooseEasing = CubicBezierEasing(0.22f, 0.7f, 0.22f, 1f)
private const val dealDuration = 650f
private const val dealStagger = 140f
private const val flipDuration = 700f
private const val flipStagger = 150f
private const val firstFlip = 500f
private const val choiceDuration = 520

private fun flipStart(index: Int) = firstFlip + index * flipStagger
private fun phase(elapsed: Float, start: Float, duration: Float) =
    ((elapsed - start) / duration).coerceIn(0f, 1f)

@Composable
internal fun DrawScreen(screen: Screen.Draw, deck: DeckState, model: YixinViewModel) {
    val c = LocalYixinColors.current
    val reduce = deck.preferences.reduceMotion
    val tasks = screen.ids.mapNotNull(deck::task).filter { it.completedAt == null }
    val revealEnd = flipStart((tasks.size - 1).coerceAtLeast(0)) + flipDuration
    val finishAt = revealEnd + 350f
    var roundReady by rememberSaveable(screen.deal) { mutableStateOf(reduce || tasks.isEmpty()) }
    var selectedId by rememberSaveable(screen.deal) { mutableStateOf<String?>(null) }
    var selectionRejected by rememberSaveable(screen.deal) { mutableStateOf(false) }
    val roundMotion = remember(screen.deal, reduce) { Animatable(if (roundReady || reduce) finishAt else 0f) }
    val choiceMotion = remember(screen.deal) { Animatable(0f) }

    LaunchedEffect(roundMotion, finishAt) {
        if (roundMotion.value < finishAt) {
            roundMotion.animateTo(finishAt, tween(finishAt.toInt(), easing = LinearEasing)) {
                if (value >= revealEnd) roundReady = true
            }
        }
        roundReady = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { selectedId = null }
    LaunchedEffect(screen, selectedId, reduce) {
        val id = selectedId
        if (id == null) {
            if (reduce) choiceMotion.snapTo(0f)
            else choiceMotion.animateTo(0f, tween(180, easing = chooseEasing))
        } else {
            if (!reduce) choiceMotion.animateTo(1f, tween(choiceDuration, easing = chooseEasing))
            model.chooseDraw(screen, id) {
                if (selectedId == id) {
                    selectedId = null
                    selectionRejected = true
                }
            }
        }
    }

    val ready = reduce || roundReady
    val choosing = selectedId != null
    val unlocked = deck.pinnedId == null
    ScrollPage("抽卡，遇见小事", model::back) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val wide = maxWidth >= 560.dp
            val elapsed = roundMotion.asState()
            val choice = choiceMotion.asState()
            if (!reduce && tasks.isNotEmpty()) {
                DrawHalo(elapsed, Modifier.matchParentSize())
            }
            val option: @Composable (Task, Int) -> Unit = { task, index ->
                val selected = selectedId == task.id
                DrawOption(
                    task = task,
                    deck = deck,
                    index = index,
                    count = tasks.size,
                    wide = wide,
                    elapsed = elapsed,
                    choice = choice,
                    reduced = reduce,
                    ready = ready,
                    selected = selected,
                    choosing = choosing,
                    enabled = ready && !choosing && unlocked,
                    onClick = {
                        if (selectedId == null) {
                            selectionRejected = false
                            selectedId = task.id
                        }
                    },
                )
            }
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                    tasks.forEachIndexed { index, task ->
                        key(screen.deal, task.id) {
                            Box(Modifier.widthIn(max = 230.dp).weight(1f, fill = false)) { option(task, index) }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tasks.forEachIndexed { index, task ->
                        key(screen.deal, task.id) { option(task, index) }
                    }
                }
            }
        }

        Text(
            when {
                selectionRejected -> "这张暂时无法开始，请重选或再抽一次。"
                !unlocked -> "当前卡片已钉住，取消钉住后可以抽卡。"
                choosing -> "就从这一件开始。"
                !ready -> "正在发牌，看看会遇到哪几件小事……"
                tasks.isEmpty() -> "这几件已经收好，再抽一次吧。"
                else -> "凭第一感觉，选一张开始。"
            },
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center, color = c.accent, fontSize = 12.sp, lineHeight = 21.sp,
        )
        OutlinedButton(
            enabled = unlocked && ready && !choosing && deck.tasks.any { it.completedAt == null },
            onClick = model::draw,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("再抽一次")
        }
    }
}

@Composable
private fun DrawOption(
    task: Task,
    deck: DeckState,
    index: Int,
    count: Int,
    wide: Boolean,
    elapsed: State<Float>,
    choice: State<Float>,
    reduced: Boolean,
    ready: Boolean,
    selected: Boolean,
    choosing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalYixinColors.current
    val faceUp by remember(index, elapsed, reduced) {
        derivedStateOf { reduced || flipEasing.transform(phase(elapsed.value, flipStart(index), flipDuration)) >= 0.5f }
    }
    val position = index - (count - 1) / 2f
    val height = (if (wide) 294 else 142).dp +
        ((LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * 80).dp

    Box(
        Modifier.fillMaxWidth().heightIn(min = height).graphicsLayer {
            if (!reduced) {
                val dealt = dealEasing.transform(phase(elapsed.value, index * dealStagger, dealDuration))
                val pick = choice.value
                val selectionScale = if (selected) 1f + pick * 0.025f else 1f - pick * 0.06f
                translationX = -position * size.width * (if (wide) 0.63f else 0.12f) * (1f - dealt)
                translationY = (if (wide) 43.dp else 30.dp).toPx() * (1f - dealt) -
                    if (selected) 12.dp.toPx() * pick else 0f
                rotationZ = position * 8f * (1f - dealt)
                scaleX = (0.84f + 0.16f * dealt) * selectionScale
                scaleY = scaleX
                alpha = dealt * if (selected) 1f else 1f - pick * 0.70f
            }
        }.clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = if (ready) "选择并开始任务：${task.title}" else "第 ${index + 1} 张，正在翻开"
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = height).graphicsLayer {
                val angle = flipEasing.transform(phase(elapsed.value, flipStart(index), flipDuration)) * 180f
                rotationY = if (reduced) 0f else if (faceUp) angle - 180f else angle
                cameraDistance = 850.dp.toPx()
            },
            shape = drawShape,
            color = c.paper,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line),
            shadowElevation = if (selected) 8.dp else 3.dp,
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = height).then(
                if (ready) Modifier else Modifier.clearAndSetSemantics { },
            )) {
                if (faceUp) {
                    DrawFront(task, deck, index, wide, selected, Modifier.fillMaxWidth().heightIn(min = height))
                    if (!reduced && !choosing) DrawShimmer(index, elapsed, Modifier.matchParentSize().clip(drawShape))
                } else {
                    DrawBack(wide, Modifier.matchParentSize())
                }
            }
        }
    }
}

@Composable
private fun DrawBack(wide: Boolean, modifier: Modifier) {
    val c = LocalYixinColors.current
    Box(modifier.background(c.paper).padding(9.dp).border(1.dp, c.line, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp))) {
            val spacing = 13.dp.toPx()
            val ink = c.accent.copy(alpha = 0.055f)
            for (step in 0..((size.width + size.height) / spacing).toInt()) {
                val from = step * spacing - size.height
                drawLine(ink, Offset(from, size.height), Offset(from + size.height, 0f), 1.dp.toPx())
            }
        }
        val mark: @Composable () -> Unit = {
            HomeGlyph(Glyph.Draw, c.accent, Modifier.size(23.dp))
            Text("一心", color = c.accent, fontFamily = FontFamily.Serif, fontSize = 25.sp, letterSpacing = 5.sp)
            Text("遇见这一件", color = c.accent, fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
        if (wide) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { mark() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { mark() }
    }
}

@Composable
private fun DrawFront(task: Task, deck: DeckState, index: Int, wide: Boolean, selected: Boolean, modifier: Modifier) {
    val c = LocalYixinColors.current
    Column(modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text((index + 1).toString().padStart(2, '0'), Modifier.weight(1f),
                color = c.muted, fontSize = 10.sp, letterSpacing = 1.sp)
            CategoryTag(deck.activeCategory(task.category))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                task.title, color = c.ink, fontSize = if (wide) 19.sp else 16.sp,
                lineHeight = if (wide) 27.sp else 23.sp, fontWeight = FontWeight.Medium,
                maxLines = if (wide) 4 else 2, overflow = TextOverflow.Ellipsis,
            )
            if (wide && task.note.isNotBlank()) {
                Text(task.note, color = c.muted, fontSize = 11.sp, lineHeight = 19.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (wide) {
            CardSchedule(task)
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 9.dp), color = c.line)
            DrawPick(selected)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardSchedule(task) }
                DrawPick(selected)
            }
        }
    }
}

@Composable
private fun DrawPick(selected: Boolean) {
    val c = LocalYixinColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (selected) "就做这一件" else "选这一件", color = c.accent, fontSize = 11.sp)
        Icon(
            if (selected) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null, modifier = Modifier.size(13.dp), tint = c.accent,
        )
    }
}

@Composable
private fun DrawHalo(elapsed: State<Float>, modifier: Modifier) {
    val c = LocalYixinColors.current
    Canvas(modifier) {
        val progress = phase(elapsed.value, 0f, 1400f)
        val glow = (1f - progress) * progress * 1.6f
        if (glow <= 0f) return@Canvas
        val radius = size.minDimension * (0.20f + progress * 0.26f)
        drawCircle(
            Brush.radialGradient(listOf(c.accent.copy(alpha = glow), Color.Transparent), center, radius),
            radius = radius,
        )
        drawCircle(c.accent.copy(alpha = glow * 0.46f), radius, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun DrawShimmer(index: Int, elapsed: State<Float>, modifier: Modifier) {
    val c = LocalYixinColors.current
    Canvas(modifier) {
        val shine = phase(elapsed.value, flipStart(index) + 455f, 550f)
        if (shine <= 0f || shine >= 1f) return@Canvas
        val x = size.width * (-0.52f + 1.80f * shine)
        val streak = Brush.linearGradient(
            listOf(Color.Transparent, c.accent.copy(alpha = 0.14f), Color.Transparent),
            start = Offset(x - size.width * 0.17f, size.height * 0.31f),
            end = Offset(x + size.width * 0.17f, size.height * 0.69f),
        )
        drawRect(streak)
    }
}
