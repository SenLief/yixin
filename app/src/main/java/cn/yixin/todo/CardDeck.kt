package cn.yixin.todo

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import cn.yixin.core.Category
import cn.yixin.core.DeckState
import cn.yixin.core.Task
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.launch

private enum class CardAxis { Horizontal, Vertical }

private val returnEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val switchEasing = CubicBezierEasing(0.3f, 0f, 0.35f, 1f)
private val cardShape = RoundedCornerShape(20.dp)

private class CardHitAreas {
    var origin = Offset.Zero
    var edit = Rect.Zero
    var expand = Rect.Zero

    fun canDrag(at: Offset): Boolean {
        val rootPosition = at + origin
        return !edit.contains(rootPosition) && !expand.contains(rootPosition)
    }
}

@Composable
internal fun CardDeck(
    task: Task,
    tasks: List<Task>,
    deck: DeckState,
    model: YixinViewModel,
    height: Dp,
    short: Boolean,
    modifier: Modifier,
) {
    // The promoted card has already reached its resting pose when the committed ID changes.
    key(task.id) {
        CardDeckStage(task, tasks, deck, model, height, short, modifier)
    }
}

@Composable
private fun CardDeckStage(
    task: Task,
    tasks: List<Task>,
    deck: DeckState,
    model: YixinViewModel,
    height: Dp,
    short: Boolean,
    modifier: Modifier,
) {
    val c = LocalYixinColors.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hitAreas = remember { CardHitAreas() }
    val canNavigate = deck.pinnedId == null && tasks.size > 1
    val pinned = deck.pinnedId == task.id
    val reduceMotion = deck.preferences.reduceMotion
    val position = remember(tasks, task.id) { tasks.indexOfFirst { it.id == task.id } }
    val cardHeight = height + ((density.fontScale - 1f).coerceAtLeast(0f) * 88f).dp

    var axis by remember { mutableStateOf<CardAxis?>(null) }
    var distance by remember { mutableFloatStateOf(0f) }
    var threshold by remember { mutableFloatStateOf(1f) }
    var exitProgress by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val previewIndex by remember(position, tasks.size, reduceMotion, canNavigate) {
        derivedStateOf(structuralEqualityPolicy()) {
            if (!reduceMotion && canNavigate && axis == CardAxis.Horizontal && distance != 0f) {
                (position + if (distance < 0f) 1 else tasks.lastIndex) % tasks.size
            } else null
        }
    }
    val verticalReady by remember(reduceMotion) {
        derivedStateOf(structuralEqualityPolicy()) { !reduceMotion && axis == CardAxis.Vertical && abs(distance) >= threshold }
    }
    val gestureHint by remember(pinned, canNavigate) {
        derivedStateOf(structuralEqualityPolicy()) {
            val hint = when (axis) {
                CardAxis.Horizontal -> when {
                    settling || distance == 0f -> null
                    pinned -> "已钉住 · 展开后可取消"
                    !canNavigate -> "只有这一件，安心做完"
                    abs(distance) >= threshold -> if (distance < 0f) "松手，换下一件" else "松手，换上一件"
                    else -> if (distance < 0f) "向左滑，换下一件" else "向右滑，换上一件"
                }
                CardAxis.Vertical -> when {
                    settling || distance == 0f -> null
                    abs(distance) >= threshold -> if (distance < 0f) "松手，收好这一件" else "松手，开始这一件"
                    else -> if (distance < 0f) "再向上滑一点，完成这一件" else "再向下滑一点，开始这一件"
                }
                null -> null
            }
            hint ?: when {
                pinned -> "已钉住 · 上滑完成 · 下滑开始"
                canNavigate -> "左右切换 · 上滑完成 · 下滑开始"
                else -> "上滑完成 · 下滑开始"
            }
        }
    }

    suspend fun returnToCenter() {
        if (!reduceMotion) {
            val startDistance = distance
            val startExit = exitProgress
            animate(0f, 1f, animationSpec = tween(270, easing = returnEasing)) { fraction, _ ->
                distance = startDistance * (1f - fraction)
                exitProgress = startExit * (1f - fraction)
            }
        }
        axis = null
        distance = 0f
        exitProgress = 0f
        settling = false
    }

    fun release(gestureAxis: CardAxis, displacement: Float, cancelled: Boolean) {
        settling = true
        val passed = !cancelled && abs(displacement) >= threshold
        if (gestureAxis == CardAxis.Horizontal && canNavigate && passed) {
            val direction = if (displacement < 0f) 1 else -1
            scope.launch {
                if (!reduceMotion) {
                    val releaseDistance = distance
                    val travel = with(density) { 100.dp.toPx() } * sign(displacement)
                    animate(0f, 1f, animationSpec = tween(360, easing = switchEasing)) { fraction, _ ->
                        exitProgress = fraction
                        distance = releaseDistance + fraction * travel
                    }
                }
                model.navigateFrom(task.id, direction) { switched ->
                    if (!switched) scope.launch { returnToCenter() }
                }
            }
        } else {
            if (gestureAxis == CardAxis.Vertical && passed) {
                if (displacement < 0f) model.complete(task.id) else model.focus(task.id)
            }
            scope.launch { returnToCenter() }
        }
    }

    val minHorizontal = with(density) { 50.dp.toPx() }
    val minVertical = with(density) { 70.dp.toPx() }
    val downLimit = with(density) { 110.dp.toPx() }
    val openDetails = { model.showCurrent() }
    val edit = { model.open(Screen.Editor(task.id)) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().testTag("deck-gesture-area")
                .onGloballyPositioned { hitAreas.origin = it.positionInRoot() }
                .pointerInput(canNavigate, reduceMotion, minHorizontal, minVertical, downLimit) {
                    detectCardDrag(
                        canStart = { !settling && hitAreas.canDrag(it) },
                        onDrag = { locked, displacement, viewport ->
                            axis = locked
                            distance = displacement
                            threshold = if (locked == CardAxis.Horizontal) {
                                maxOf(minHorizontal, viewport.width * 0.19f)
                            } else {
                                maxOf(minVertical, viewport.height * 0.26f)
                            }
                        },
                        onEnd = ::release,
                    )
                },
        ) {
            if (tasks.size > 2) UnderCard(Modifier.matchParentSize(), -3.dp, 15.dp, -3.5f, 0.55f)
            if (tasks.size > 1) UnderCard(Modifier.matchParentSize(), 3.dp, 9.dp, 2.6f, 1f)
            previewIndex?.let { index ->
                CardFace(
                    task = tasks[index], category = deck.activeCategory(tasks[index].category),
                    pinned = false, position = index + 1, total = tasks.size, height = cardHeight, short = short,
                    modifier = Modifier.matchParentSize().graphicsLayer {
                        val reveal = (abs(distance) / (size.width * 0.55f)).coerceIn(0f, 1f)
                        val promotion = reveal * (1f - exitProgress) + exitProgress
                        translationY = 9.dp.toPx() * (1f - promotion)
                        scaleX = 0.955f + 0.045f * promotion
                        scaleY = scaleX
                        alpha = (0.60f + 0.40f * promotion).coerceIn(0f, 1f)
                    }.clearAndSetSemantics { },
                )
            }
            CardFace(
                task = task, category = deck.activeCategory(task.category), pinned = pinned,
                position = position + 1, total = tasks.size, height = cardHeight, short = short,
                ready = verticalReady,
                reduceMotion = reduceMotion, onEdit = edit, onExpand = openDetails,
                hitAreas = hitAreas,
                enabled = !settling,
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    if (!reduceMotion) {
                        transformOrigin = TransformOrigin(0.5f, 0.65f)
                        val width = size.width.coerceAtLeast(1f)
                        val horizontal = axis == CardAxis.Horizontal && canNavigate
                        val x = if (horizontal) distance * 0.80f else 0f
                        translationX = x
                        rotationZ = (x / width * 22f).coerceIn(-8f, 8f)
                        translationY = when {
                            horizontal -> {
                                // Derive the arc from horizontal travel so a diagonal finger never steers it.
                                val fraction = (abs(x) / (width * 0.73f)).coerceAtMost(1f)
                                val lift = sin(PI * fraction).toFloat()
                                -minOf(width * 0.087f, 32.dp.toPx()) * lift * lift
                            }
                            axis == CardAxis.Vertical && distance < 0f -> distance * 0.80f
                            axis == CardAxis.Vertical -> (distance * 0.65f).coerceAtMost(downLimit)
                            else -> 0f
                        }
                        scaleX = 1f - exitProgress * 0.03f
                        scaleY = scaleX
                        alpha = 1f - exitProgress * exitProgress
                    }
                }.semantics {
                    customActions = buildList {
                        if (!settling) {
                            if (canNavigate) {
                                add(CustomAccessibilityAction("上一张卡片") { model.navigateFrom(task.id, -1); true })
                                add(CustomAccessibilityAction("下一张卡片") { model.navigateFrom(task.id, 1); true })
                            }
                            add(CustomAccessibilityAction("完成任务") { model.complete(task.id); true })
                            add(CustomAccessibilityAction("开始或继续专注") { model.focus(task.id); true })
                        }
                    }
                }.clickable(enabled = !settling, onClickLabel = "打开任务详情", onClick = openDetails),
            )
        }
        Spacer(Modifier.height(if (short) 23.dp else 28.dp))
        val hasProgress = deck.progress.any { it.taskId == task.id }
        Button(
            onClick = { if (axis == null && !settling) model.focus(task.id) },
            modifier = Modifier.heightIn(min = 46.dp).widthIn(min = 143.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        ) {
            HomeGlyph(Glyph.Play, c.onAccent, Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (hasProgress) "继续这一件" else "开始这一件", fontSize = 13.sp)
        }
        Spacer(Modifier.height(if (short) 8.dp else 12.dp))
        Text(
            gestureHint,
            fontSize = 11.sp, lineHeight = 17.sp, color = c.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** The stationary stage owns the gesture, so moving the front card cannot move the touch origin. */
private suspend fun PointerInputScope.detectCardDrag(
    canStart: (Offset) -> Boolean,
    onDrag: (CardAxis, Float, IntSize) -> Unit,
    onEnd: (CardAxis, Float, Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!canStart(down.position)) return@awaitEachGesture

        var axis: CardAxis? = null
        var distance = 0f
        var released = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                val delta = change.position - down.position
                if (axis == null && change.pressed && delta.getDistance() > viewConfiguration.touchSlop) {
                    axis = if (abs(delta.x) > abs(delta.y) * 1.15f) CardAxis.Horizontal else CardAxis.Vertical
                }
                val locked = axis
                if (locked != null) {
                    distance = if (locked == CardAxis.Horizontal) delta.x else delta.y
                    change.consume()
                    onDrag(locked, distance, size)
                    if (!change.pressed) {
                        released = true
                        onEnd(locked, distance, false)
                        break
                    }
                } else if (!change.pressed) break
            }
        } finally {
            if (!released) axis?.let { onEnd(it, distance, true) }
        }
    }
}

@Composable
private fun UnderCard(modifier: Modifier, x: Dp, y: Dp, rotation: Float, opacity: Float) {
    val c = LocalYixinColors.current
    Box(modifier.graphicsLayer {
        translationX = x.toPx()
        translationY = y.toPx()
        rotationZ = rotation
        alpha = opacity
    }.background(c.tint, cardShape).border(1.dp, c.line, cardShape))
}

@Composable
private fun CardFace(
    task: Task,
    category: Category?,
    pinned: Boolean,
    position: Int,
    total: Int,
    height: Dp,
    short: Boolean,
    modifier: Modifier,
    ready: Boolean = false,
    reduceMotion: Boolean = false,
    enabled: Boolean = true,
    onEdit: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    hitAreas: CardHitAreas? = null,
) {
    val c = LocalYixinColors.current
    val gripWidth by animateDpAsState(
        if (ready) 52.dp else 31.dp,
        if (reduceMotion) snap() else tween(180, easing = returnEasing),
        label = "card grip",
    )
    Surface(
        modifier, shape = cardShape, border = BorderStroke(1.dp, c.line), color = c.paper,
        shadowElevation = if (c.dark) 1.dp else 4.dp,
    ) {
        Column(Modifier.heightIn(min = height).padding(start = 23.dp, end = 23.dp, top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val dot = category?.let { rgb(it.color.rgb.toLong()) } ?: c.muted
                        Box(Modifier.size(7.dp).background(dot, CircleShape))
                        Text(category?.name ?: "未分类", color = c.muted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (pinned) {
                        HomeGlyph(Glyph.Pin, c.accent, Modifier.size(12.dp))
                        Text("已钉住", color = c.accent, fontSize = 11.sp, maxLines = 1)
                    }
                }
                IconButton(
                    onClick = { onEdit?.invoke() }, enabled = enabled && onEdit != null,
                    modifier = Modifier.size(46.dp).onGloballyPositioned { hitAreas?.edit = it.boundsInRoot() },
                ) {
                    HomeGlyph(Glyph.Edit, c.muted, Modifier.size(17.dp), "编辑任务".takeIf { onEdit != null })
                }
            }
            Column(
                Modifier.weight(1f, fill = true).fillMaxWidth().padding(vertical = if (short) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
            ) {
                Text(
                    task.title, color = c.ink, fontSize = if (short) 25.sp else 27.sp,
                    lineHeight = if (short) 35.sp else 39.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Text(task.note, Modifier.weight(1f, fill = false), color = c.muted, fontSize = 12.sp, lineHeight = 21.sp,
                        maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
                CardSchedule(task)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                val ordinal = position.toString().padStart(2, '0')
                val count = total.toString().padStart(2, '0')
                Text("$ordinal   /   $count", Modifier.weight(1f), color = c.muted, fontSize = 11.sp, letterSpacing = 0.7.sp)
                // The preview and the interactive face must reserve the same space below the title.
                TextButton(
                    onClick = { onExpand?.invoke() }, enabled = enabled && onExpand != null,
                    modifier = Modifier.onGloballyPositioned { hitAreas?.expand = it.boundsInRoot() },
                    contentPadding = PaddingValues(start = 8.dp),
                ) { ExpandLabel() }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).size(gripWidth, 3.dp)
                .background(if (ready) c.accent else c.line, CircleShape))
        }
    }
}

@Composable
private fun ExpandLabel() {
    val c = LocalYixinColors.current
    Text("展开", color = c.muted, fontSize = 11.sp)
    Spacer(Modifier.width(7.dp))
    HomeGlyph(Glyph.Expand, c.muted, Modifier.size(13.dp))
}

@Composable
internal fun CardSchedule(task: Task) {
    val c = LocalYixinColors.current
    val today = LocalDate.now()
    val due = task.dueDate?.let(LocalDate::parse)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(dueLabel(due, today), color = if (due != null && due < today) MaterialTheme.colorScheme.error else c.muted, fontSize = 11.sp)
        task.estimatedMinutes?.let {
            Text("·", color = c.muted, fontSize = 11.sp)
            HomeGlyph(Glyph.Clock, c.muted, Modifier.size(12.dp))
            Text("$it 分钟", color = c.muted, fontSize = 11.sp)
        }
    }
}


internal fun dueLabel(due: LocalDate?, today: LocalDate = LocalDate.now()): String = when {
    due == null -> "不赶时间"
    due == today -> "今天"
    due < today -> "已过期 · ${due.monthValue}/${due.dayOfMonth}"
    else -> "${due.monthValue} 月 ${due.dayOfMonth} 日"
}
