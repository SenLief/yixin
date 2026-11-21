package cn.yixin.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yixin.core.Category
import cn.yixin.core.DeckState
import cn.yixin.core.HomeView
import cn.yixin.core.Sort
import cn.yixin.core.Task
import cn.yixin.core.cardsFor
import cn.yixin.core.currentCard
import cn.yixin.core.ordered

@Composable
internal fun rememberOrderedTasks(deck: DeckState): List<Task> =
    remember(deck.tasks, deck.manualOrder, deck.pinnedId, deck.preferences.sortMode, deck.shuffleSeed) { deck.ordered() }

@Composable
internal fun Home(deck: DeckState, model: YixinViewModel) {
    val tasks = rememberOrderedTasks(deck)
    val today by model.today.collectAsStateWithLifecycle()
    val todayOnly = deck.preferences.sortMode == Sort.today
    val cards = remember(tasks, today, todayOnly) { deck.cardsFor(today, tasks) }
    val selected = remember(cards, deck.currentId, deck.pinnedId) { deck.currentCard(cards) }
    val archived = deck.tasks.size - tasks.size
    val calendar = deck.preferences.homeView == HomeView.calendar
    var calendarDate by rememberSaveable { mutableStateOf<String?>(today) }
    val openEditor = {
        val defaultDate = if (todayOnly) today else calendarDate.takeIf { calendar }
        model.open(Screen.Editor(null, defaultDueDate = defaultDate))
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val narrow = maxWidth < 360.dp
        val wide = maxWidth >= 600.dp
        val short = maxHeight < 700.dp
        val landscape = maxHeight < 480.dp
        val available = maxHeight
        val pagePadding = if (wide) 42.dp else if (narrow) 18.dp else 24.dp
        val compactCard = !wide || maxHeight < 760.dp
        val (referenceWidth, referenceHeight) = when {
            landscape -> 360.dp to 228.dp
            !wide -> 350.dp to 274.dp
            compactCard -> 360.dp to 280.dp
            maxWidth >= 1000.dp -> 394.dp to 340.dp
            else -> 380.dp to 332.dp
        }
        val cardWidth = (maxWidth - pagePadding * 2).coerceIn(0.dp, referenceWidth)
        val cardHeight = (referenceHeight * (cardWidth / referenceWidth))
            .coerceAtLeast(if (landscape) 228.dp else 256.dp)
        Column(
            Modifier.widthIn(max = 1440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = available)
                .padding(horizontal = pagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(
                showDraw = !calendar && selected != null,
                pinned = deck.pinnedId != null,
                view = deck.preferences.homeView,
                toggleView = {
                    model.preferences {
                        it.copy(homeView = if (it.homeView == HomeView.cards) HomeView.calendar else HomeView.cards)
                    }
                },
                draw = model::draw,
                settings = { model.open(Screen.Settings) },
            )
            if (!calendar) Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(top = if (landscape) 4.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                HomeHeading(calendar, showEyebrow = !short)
                Spacer(Modifier.height(if (short) 16.dp else 21.dp))
                if (calendar) {
                    val addForSelectedDate = { model.open(Screen.Editor(null, defaultDueDate = calendarDate)) }
                    CalendarView(tasks, deck, calendarDate, { calendarDate = it }, addForSelectedDate) { task ->
                        model.select(task.id, details = true)
                    }
                } else if (selected == null) {
                    val unpin = deck.pinnedId?.takeIf { todayOnly }?.let { id -> { model.pin(id) } }
                    EmptyDeck(cardHeight, archived > 0, todayOnly, openEditor, unpin)
                } else {
                    CardDeck(
                        selected, cards, deck, model, cardHeight, compactCard,
                        modifier = Modifier.widthIn(max = cardWidth).fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HomeFooter(
                count = tasks.size,
                archived = archived,
                openDeck = { model.open(Screen.Stack) },
                add = openEditor,
                openArchive = { model.open(Screen.Archive) },
            )
        }
    }
}

@Composable
private fun HomeHeader(
    showDraw: Boolean, pinned: Boolean, view: HomeView,
    toggleView: () -> Unit, draw: () -> Unit, settings: () -> Unit,
) {
    val c = LocalYixinColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("一心", color = c.ink, fontSize = 21.sp, letterSpacing = 3.sp,
                lineHeight = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("YIXIN", color = c.muted, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 2.6.sp,
                maxLines = 1)
        }
        if (showDraw) {
            HomeTool(Glyph.Draw, if (pinned) "抽卡，置顶期间不可用" else "抽卡", draw, enabled = !pinned)
        }
        val showCalendar = view == HomeView.cards
        HomeTool(
            glyph = if (showCalendar) Glyph.Calendar else Glyph.Brand,
            label = if (showCalendar) "切换到日历视图" else "切换到卡片视图",
            onClick = toggleView,
        )
        HomeTool(Glyph.Palette, "设置", settings)
    }
}

@Composable
internal fun HomeTool(glyph: Glyph, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalYixinColors.current
    IconButton(onClick, modifier = Modifier.size(48.dp), enabled = enabled) {
        Box(Modifier.size(40.dp).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
            HomeGlyph(glyph, if (enabled) c.muted else c.muted.copy(alpha = 0.40f), Modifier.size(19.dp), label)
        }
    }
}

@Composable
private fun HomeHeading(calendar: Boolean, showEyebrow: Boolean) {
    val c = LocalYixinColors.current
    if (showEyebrow) {
        Text("ONE THING, WHOLE HEART.", color = c.muted, fontSize = 9.sp, lineHeight = 13.sp,
            letterSpacing = 2.5.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
    }
    Text(
        if (calendar) "给每一件，留一点时间。" else "此刻，只做这一件。",
        color = c.ink, fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 37.sp,
        letterSpacing = 1.5.sp, textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmptyDeck(cardHeight: Dp, hasArchive: Boolean, todayOnly: Boolean, add: () -> Unit, unpin: (() -> Unit)?) {
    val c = LocalYixinColors.current
    Column(
        Modifier.heightIn(min = cardHeight).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(c.tint, CircleShape), contentAlignment = Alignment.Center) {
            HomeGlyph(Glyph.Stack, c.accent, Modifier.size(26.dp))
        }
        Text(
            when {
                unpin != null -> "置顶任务不在今日安排中"
                todayOnly -> "今天没有待办"
                hasArchive -> "此刻，轻一点。"
                else -> "先放下一件小事。"
            },
            style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif,
            color = c.ink, textAlign = TextAlign.Center,
        )
        if (!todayOnly) {
            Text(
                if (hasArchive) "这叠卡片都收好了。歇一下，也很好。" else "不用规划一整天。从眼前的一小步开始。",
                color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        }
        Button(onClick = unpin ?: add) {
            Text(when {
                unpin != null -> "取消置顶"
                todayOnly -> "安排今天的任务"
                hasArchive -> "记下新的一件"
                else -> "放入第一张卡片"
            })
        }
    }
}

@Composable
private fun HomeFooter(
    count: Int, archived: Int,
    openDeck: () -> Unit, add: () -> Unit, openArchive: () -> Unit,
) {
    val c = LocalYixinColors.current
    HorizontalDivider(color = c.line)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TextButton(openDeck, contentPadding = PaddingValues(horizontal = 0.dp)) {
                HomeGlyph(Glyph.Stack, c.muted, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("卡片叠", color = c.muted, fontSize = 12.sp)
                Spacer(Modifier.width(7.dp))
                Text(count.toString(), Modifier.background(c.tint, RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp), color = c.muted, fontSize = 10.sp)
            }
        }
        TextButton(add, contentPadding = PaddingValues(horizontal = 5.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(17.dp), tint = c.accent)
            Spacer(Modifier.width(5.dp))
            Text("新安排", color = c.accent, fontSize = 12.sp, maxLines = 1)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TextButton(openArchive, contentPadding = PaddingValues(horizontal = 0.dp)) {
                HomeGlyph(Glyph.Archive, c.muted, Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("收好 $archived 件", color = c.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun CategoryTag(category: Category?) {
    val c = LocalYixinColors.current
    val dot = category?.color?.let { rgb(it.rgb.toLong()) } ?: c.muted
    Row(
        Modifier.background(c.tint.copy(alpha = 0.55f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(category?.name ?: "未分类", style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}

@Composable
internal fun DeckIcon(color: Color, modifier: Modifier = Modifier.size(24.dp), description: String? = null) {
    Canvas(if (description == null) modifier else modifier.semantics {
        this.contentDescription = description
    }) {
        val w = size.width
        val h = size.height
        val line = w * 0.072f
        drawRoundRect(color, Offset(w * 0.17f, h * 0.13f), Size(w * 0.58f, h * 0.64f),
            CornerRadius(w * 0.12f), style = Stroke(line))
        drawLine(color, Offset(w * 0.35f, h * 0.90f), Offset(w * 0.83f, h * 0.90f), line, StrokeCap.Round)
        drawLine(color, Offset(w * 0.88f, h * 0.38f), Offset(w * 0.88f, h * 0.85f), line, StrokeCap.Round)
        drawLine(color, Offset(w * 0.37f, h * 0.42f), Offset(w * 0.55f, h * 0.42f), line, StrokeCap.Round)
    }
}
