package cn.yixin.todo

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yixin.core.DeckState
import cn.yixin.core.FocusMode
import cn.yixin.core.FocusProgress
import cn.yixin.core.FocusStyle
import cn.yixin.core.Mood
import cn.yixin.core.Preferences
import cn.yixin.core.Ritual
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusScreen(id: String, deck: DeckState, model: YixinViewModel, rotate: (Boolean) -> Unit) {
    val task = deck.task(id) ?: return
    val saved = deck.progress.firstOrNull { it.taskId == id } ?: return
    val session by model.running.collectAsStateWithLifecycle()
    val running = session?.takeIf { it.base.taskId == id }
    var settingsOpen by rememberSaveable(id) { mutableStateOf(false) }
    val deviceLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var now by remember(running) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running != null) {
            now = SystemClock.elapsedRealtime()
            if (running.at(now).finished) {
                model.requestCheckpoint(running)
                break
            }
            delay(200)
        }
    }
    val progress = remember(running, saved) { derivedStateOf { running?.at(now) ?: saved } }
    val finished by remember(progress) { derivedStateOf(structuralEqualityPolicy()) { progress.value.finished } }
    val style = deck.preferences.focusStyle
    val mood = when (style) {
        FocusStyle.quiet -> Mood.paper
        FocusStyle.orbit, FocusStyle.galaxy -> Mood.galaxy
        FocusStyle.tide, FocusStyle.aurora -> Mood.aurora
        FocusStyle.glow -> Mood.rose
        FocusStyle.woodland -> Mood.woodland
    }
    YixinTheme(mood) {
        val c = LocalYixinColors.current
        Box(Modifier.fillMaxSize().background(c.background)) {
            when (style) {
                FocusStyle.galaxy, FocusStyle.aurora, FocusStyle.woodland -> Scene(mood, Modifier.fillMaxSize())
                FocusStyle.orbit, FocusStyle.tide, FocusStyle.glow ->
                    Ambient(style, c, deck.preferences.reduceMotion, Modifier.fillMaxSize())
                FocusStyle.quiet -> Unit
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.TopCenter,
            ) {
                val landscape = maxWidth > maxHeight
                val compact = maxHeight < 490.dp
                val narrow = maxWidth < 600.dp
                val available = maxHeight
                val action: () -> Unit = {
                    if (finished) model.restartTimer(id) else model.toggleTimer(id)
                }
                val complete = { model.complete(id) }
                val timerLabel = if (finished) "重新计时" else if (running != null) "暂停" else "继续"
                Column(
                    Modifier.widthIn(max = if (landscape) 960.dp else 545.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).heightIn(min = available)
                        .padding(horizontal = if (landscape && !narrow) 32.dp else 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FocusHeader(model::back, { settingsOpen = true }, Modifier.padding(top = if (landscape) 0.dp else 6.dp))
                    if (landscape) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = (available - 60.dp).coerceAtLeast(0.dp))
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (narrow) 14.dp else 36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FocusTaskTitle(task.title, compact = compact, align = TextAlign.Start)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Button(onClick = action) { Text(timerLabel) }
                                    TextButton(onClick = complete) { Text("完成任务") }
                                }
                            }
                            val dialSize = (available - 82.dp).coerceIn(120.dp, 330.dp)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                TimerRing(
                                    progress, running != null,
                                    Modifier.widthIn(max = dialSize).fillMaxWidth().aspectRatio(1f),
                                    maxFontSize = 49f,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(0.5f))
                        FocusTaskTitle(task.title, compact, TextAlign.Center, Modifier.padding(8.dp))
                        Spacer(Modifier.height(if (compact) 5.dp else 20.dp))
                        TimerRing(
                            progress, running != null,
                            Modifier.widthIn(max = if (compact) 156.dp else 235.dp).fillMaxWidth().aspectRatio(1f),
                        )
                        Spacer(Modifier.height(if (compact) 7.dp else 20.dp))
                        Button(onClick = action) { Text(timerLabel) }
                        Spacer(Modifier.weight(0.5f))
                        TextButton(onClick = complete, modifier = Modifier.padding(vertical = 9.dp)) {
                            Text("完成任务")
                        }
                    }
                }
            }
        }
        if (settingsOpen) {
            FocusSettingsDialog(deck.preferences, model, deviceLandscape, rotate) { settingsOpen = false }
        }
    }
}

@Composable
private fun FocusHeader(back: () -> Unit, openSettings: () -> Unit, modifier: Modifier) {
    val c = LocalYixinColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出专注", tint = c.ink)
        }
        Text("专注", color = c.muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = openSettings,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            HomeGlyph(Glyph.Sliders, c.muted, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("计时设置", color = c.muted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSettingsDialog(
    preferences: Preferences, model: YixinViewModel, landscape: Boolean,
    rotate: (Boolean) -> Unit, dismiss: () -> Unit,
) {
    val c = LocalYixinColors.current
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("专注设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("屏幕方向", color = c.muted, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !landscape, onClick = { rotate(false) }, label = { Text("竖屏") })
                        FilterChip(selected = landscape, onClick = { rotate(true) }, label = { Text("横屏") })
                    }
                }
                FocusPreferencesFields(preferences, model)
                Text("风格即时生效，计时方式和时长用于新的计时。",
                    color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = dismiss) { Text("返回专注") } },
        containerColor = c.paper,
    )
}

@Composable
private fun FocusTaskTitle(title: String, compact: Boolean, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        title, modifier = modifier,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        color = LocalYixinColors.current.ink, fontFamily = FontFamily.Serif, textAlign = align,
        maxLines = 3, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimerRing(progressState: State<FocusProgress>, running: Boolean, modifier: Modifier, maxFontSize: Float = 32f) {
    val progress by progressState
    val c = LocalYixinColors.current
    val countDown = progress.mode == FocusMode.countdown
    val seconds = if (countDown) (progress.durationMs - progress.elapsedMs + 999) / 1000 else progress.elapsedMs / 1000
    val time = remember(seconds) {
        if (seconds >= 3600) String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
    val state = if (progress.finished) "时间到" else if (running) progress.mode.label else "已暂停"
    val fraction = if (countDown) 1f - progress.elapsedMs.toFloat() / progress.durationMs
        else (progress.elapsedMs % 60_000) / 60_000f
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = "$state，$time" },
        contentAlignment = Alignment.Center) {
        val fontScale = LocalDensity.current.fontScale
        val fontSize = ((maxWidth.value - 22f) / (time.length * 0.65f * fontScale)).coerceIn(12f, maxFontSize).sp
        Canvas(Modifier.fillMaxSize()) {
            val width = 2.5.dp.toPx()
            val radius = size.minDimension / 2 - width
            drawCircle(c.line, radius, style = Stroke(width))
            if (fraction > 0) {
                drawArc(c.accent, -90f, 360f * fraction.coerceIn(0f, 1f), false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = c.ink, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontSize = fontSize,
                maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp))
            Text(state, color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Ambient(style: FocusStyle, colors: YixinColors, reduced: Boolean, modifier: Modifier) {
    val wave = if (reduced) 0.40f else {
        val loop = rememberInfiniteTransition(label = "ambient")
        val phase by loop.animateFloat(
            0f, 1f, infiniteRepeatable(tween(5400), RepeatMode.Reverse), label = "drift",
        )
        phase
    }
    Canvas(modifier.background(Brush.verticalGradient(listOf(colors.horizon, colors.background)))) {
        val middle = Offset(size.width * 0.5f, size.height * 0.46f)
        when (style) {
            FocusStyle.orbit -> {
                val radius = size.minDimension * 0.44f
                drawCircle(colors.accent.copy(alpha = 0.17f), radius, middle, style = Stroke(1.dp.toPx()))
                val angle = wave * PI * 2 - PI / 2
                drawCircle(colors.accent, 3.dp.toPx(),
                    middle + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius))
                val random = Random(18)
                repeat(58) {
                    val x = random.nextFloat() * size.width
                    val y = random.nextFloat() * size.height
                    drawCircle(colors.accent.copy(alpha = 0.13f + wave * 0.16f), 1.5.dp.toPx(), Offset(x, y))
                }
            }
            FocusStyle.tide -> {
                repeat(7) { index ->
                    val y = size.height * (0.64f + index * 0.045f)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, y)
                        quadraticTo(size.width * 0.48f, y - size.height * (0.16f + wave * 0.09f), size.width, y)
                    }
                    drawPath(path, colors.accent.copy(alpha = 0.14f + index * 0.013f), style = Stroke(1.5.dp.toPx()))
                }
            }
            FocusStyle.glow -> {
                val halo = middle + Offset(size.width * (wave - 0.5f) * 0.30f, 0f)
                val radius = size.minDimension * 0.52f
                drawCircle(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.22f), Color.Transparent), halo, radius),
                    radius, halo)
            }
            else -> Unit
        }
    }
}

@Composable
internal fun BoxScope.CompletionEffect(event: Feedback.Completed, reduced: Boolean, finished: () -> Unit) {
    val c = LocalYixinColors.current
    val phase = remember(event) { Animatable(0f) }
    val onFinished by rememberUpdatedState(finished)
    LaunchedEffect(event, reduced) {
        if (!reduced && event.ritual != Ritual.quiet) phase.animateTo(1f, tween(680))
        onFinished()
    }
    if (reduced) return
    when (event.ritual) {
        Ritual.quiet -> Unit
        Ritual.stamp -> Box(
            Modifier.align(Alignment.Center).size(88.dp).graphicsLayer {
                alpha = ((1f - phase.value) * 1.8f).coerceIn(0f, 1f)
                scaleX = (0.68f + phase.value * 1.4f).coerceAtMost(1f)
                scaleY = scaleX
                rotationZ = -13f
            }.background(c.tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, modifier = Modifier.size(43.dp), tint = c.accent) }
        Ritual.dissolve -> Canvas(Modifier.fillMaxSize()) {
            val random = Random(event.token.id.hashCode())
            repeat(34) {
                val angle = random.nextDouble() * PI * 2
                val distance = (0.10f + random.nextFloat() * 0.31f) * size.minDimension * phase.value
                val radius = (2f + random.nextFloat() * 3).dp.toPx()
                val position = center + Offset(cos(angle).toFloat() * distance, sin(angle).toFloat() * distance)
                drawCircle(c.accent.copy(alpha = (1f - phase.value) * 0.64f), radius, position)
            }
        }
    }
}
