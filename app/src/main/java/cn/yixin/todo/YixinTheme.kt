package cn.yixin.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import cn.yixin.core.Mood
import kotlin.random.Random

data class YixinColors(
    val background: Color,
    val horizon: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    val tint: Color,
    val dark: Boolean,
)

private fun palette(
    background: Long, horizon: Long, paper: Long, ink: Long, muted: Long,
    accent: Long, onAccent: Long, line: Long, tint: Long, dark: Boolean = false,
) = YixinColors(
    rgb(background), rgb(horizon), rgb(paper), rgb(ink), rgb(muted),
    rgb(accent), rgb(onAccent), rgb(line), rgb(tint), dark,
)

internal fun rgb(value: Long): Color = Color(0xFF000000 or value)
internal fun moodColors(mood: Mood): YixinColors = when (mood) {
    Mood.sage -> palette(0xF1F3ED, 0xF1F3ED, 0xFFFFFC, 0x293F35, 0x5D6C61, 0x466953, 0xFFFEF8, 0xD8DFD2, 0xE4EADD)
    Mood.paper -> palette(0xF7F0E5, 0xEFE3D2, 0xFFFBF4, 0x3D3026, 0x6E594A, 0x80553B, 0xFFFFFF, 0xDDCBB7, 0xF0E0CC)
    Mood.night -> palette(0x181F2A, 0x202B3C, 0x222E40, 0xF1F2F7, 0xBBC8DB, 0xB9CDEB, 0x202D41, 0x46536B, 0x344158, true)
    Mood.rose -> palette(0xF7EEF0, 0xF3E2E8, 0xFFF9FA, 0x462E39, 0x765461, 0x8B4E68, 0xFFFFFF, 0xE4CAD5, 0xF1DFE8)
    Mood.galaxy -> palette(0x0B1229, 0x191837, 0x222641, 0xF6F2FF, 0xCBC7E6, 0xD0C2FF, 0x2A1D46, 0x555273, 0x383456, true)
    Mood.aurora -> palette(0x0C2431, 0x113744, 0x18343F, 0xEDF9F7, 0xBBDBD6, 0xACE6D4, 0x153831, 0x436668, 0x284C50, true)
    Mood.woodland -> palette(0x10292D, 0x183A34, 0x203B38, 0xF1F6E8, 0xBFDAAA, 0xC0DB9E, 0x283B23, 0x50685A, 0x314D40, true)
}

internal val LocalYixinColors = staticCompositionLocalOf { moodColors(Mood.sage) }

@Composable
internal fun YixinTheme(mood: Mood, content: @Composable () -> Unit) {
    val c = moodColors(mood)
    val base = if (c.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.tint, onPrimaryContainer = c.ink,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.tint, onSecondaryContainer = c.ink,
        background = c.background, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.tint, onSurfaceVariant = c.muted, surfaceContainer = c.paper,
        surfaceContainerHigh = c.paper, outline = c.muted, outlineVariant = c.line,
    )
    CompositionLocalProvider(LocalYixinColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
internal fun Scene(mood: Mood, modifier: Modifier = Modifier, subdued: Boolean = false) {
    val c = moodColors(mood)
    Box(modifier.background(Brush.verticalGradient(listOf(c.background, c.horizon)))) {
        when (mood) {
            Mood.galaxy, Mood.aurora -> {
                Image(
                    painterResource(if (mood == Mood.galaxy) R.drawable.galaxy_light else R.drawable.aurora_light),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(c.background.copy(alpha = if (subdued) 0.85f else 0.50f),
                        c.background.copy(alpha = if (subdued) 0.72f else 0.28f),
                        c.background.copy(alpha = 0.92f)),
                )))
            }
            Mood.woodland -> Woodland(Modifier.fillMaxSize())
            else -> Unit
        }
    }
}

@Composable
private fun Woodland(modifier: Modifier) {
    Canvas(modifier) {
        val random = Random(15)
        val moon = Offset(size.width * 0.74f, size.height * 0.24f)
        drawCircle(
            brush = Brush.radialGradient(listOf(rgb(0xCEDCA7).copy(alpha = 0.24f), Color.Transparent), moon, size.minDimension * 0.30f),
            radius = size.minDimension * 0.30f, center = moon,
        )
        drawCircle(rgb(0xDAE6BE).copy(alpha = 0.53f), size.minDimension * 0.042f, moon)
        repeat(40) { i ->
            val x = size.width * (i / 39f + (random.nextFloat() - 0.5f) * 0.045f)
            val base = size.height * (0.87f + random.nextFloat() * 0.16f)
            val treeHeight = size.height * (0.10f + random.nextFloat() * 0.26f)
            val treeWidth = treeHeight * 0.19f
            val tree = Path().apply {
                moveTo(x, base - treeHeight)
                lineTo(x - treeWidth * 0.63f, base - treeHeight * 0.38f)
                lineTo(x - treeWidth * 0.34f, base - treeHeight * 0.42f)
                lineTo(x - treeWidth, base)
                lineTo(x + treeWidth, base)
                lineTo(x + treeWidth * 0.34f, base - treeHeight * 0.42f)
                lineTo(x + treeWidth * 0.63f, base - treeHeight * 0.38f)
                close()
            }
            drawPath(tree, rgb(if (i % 3 == 0) 0x122D2B else 0x102824))
        }
    }
}
