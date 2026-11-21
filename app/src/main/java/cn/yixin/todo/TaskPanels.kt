package cn.yixin.todo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cn.yixin.core.Category
import cn.yixin.core.DeckState
import cn.yixin.core.Draft
import cn.yixin.core.Energy
import cn.yixin.core.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val controlShape = RoundedCornerShape(8.dp)
private val panelButtonShape = RoundedCornerShape(12.dp)

@Composable
private fun TaskPanel(
    title: String,
    dismiss: () -> Unit,
    tools: @Composable RowScope.() -> Unit,
    alignTop: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalYixinColors.current
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        var visible by remember { mutableStateOf(!alignTop || reduceMotion) }
        LaunchedEffect(Unit) { visible = true }
        val entrance by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (reduceMotion) snap() else tween(220, easing = FastOutSlowInEasing),
            label = "task editor entrance",
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = entrance }
                    .background(rgb(0x101B17).copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null, onClick = dismiss)
                    .clearAndSetSemantics { },
            )
            BoxWithConstraints(
                Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 15.dp).fillMaxSize(),
                // Keyboard insets change the scrollable height, while the editor keeps its top edge.
                contentAlignment = if (alignTop) Alignment.TopCenter else Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight)
                        .graphicsLayer {
                            alpha = entrance
                            translationY = -10.dp.toPx() * (1f - entrance)
                        },
                    color = c.background,
                    shape = RoundedCornerShape(21.dp),
                    border = BorderStroke(1.dp, c.line),
                    shadowElevation = 13.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 17.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title, Modifier.weight(1f).semantics { heading() },
                                color = c.ink, fontFamily = FontFamily.Serif, fontSize = 23.sp,
                                letterSpacing = 1.2.sp, lineHeight = 33.sp,
                            )
                            tools()
                        }
                        Spacer(Modifier.height(12.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelIcon(
    glyph: Glyph, label: String, tint: Color = LocalYixinColors.current.muted, onClick: () -> Unit,
) {
    IconButton(onClick, modifier = Modifier.size(44.dp)) {
        HomeGlyph(glyph, tint, Modifier.size(18.dp), label)
    }
}

@Composable
internal fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    val c = LocalYixinColors.current
    PanelIcon(
        if (pinned) Glyph.PinOff else Glyph.Pin,
        if (pinned) "取消置顶" else "置顶",
        tint = if (pinned) c.accent else c.muted, onClick = onClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskDetails(task: Task, deck: DeckState, model: YixinViewModel) {
    val c = LocalYixinColors.current
    val pinned = deck.pinnedId == task.id
    val selectable = deck.canSelect(task)
    val category = deck.activeCategory(task.category)
    TaskPanel("眼前这一件", model::back, tools = {
        PanelIcon(Glyph.Edit, "编辑任务") { model.open(Screen.Editor(task.id)) }
        if (selectable) PinButton(pinned) { model.pin(task.id) }
    }) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            DetailChip(category?.name ?: "未分类", dot = categoryInk(category, c.muted))
            DetailChip(dueLabel(task.dueDate?.let(LocalDate::parse)))
            task.estimatedMinutes?.let { DetailChip("$it 分钟") }
            DetailChip(task.energy.label)
            if (pinned) DetailChip("已钉住", glyph = Glyph.Pin)
        }
        Spacer(Modifier.height(18.dp))
        Text(task.title, color = c.ink, fontSize = 25.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium)
        if (task.note.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(task.note, color = c.muted, fontSize = 14.sp, lineHeight = 28.sp)
        }
        Spacer(Modifier.height(24.dp))
        val actionPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
        Row(
            Modifier.align(Alignment.End).widthIn(max = if (selectable) 336.dp else 163.dp)
                .fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val actionSize = Modifier.weight(1f).heightIn(min = 54.dp).fillMaxHeight()
            if (selectable) PanelPrimary(
                modifier = actionSize,
                onClick = { model.focus(task.id) },
                contentPadding = actionPadding,
            ) {
                HomeGlyph(Glyph.Play, c.onAccent, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("开始", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { model.complete(task.id) },
                shape = panelButtonShape, border = BorderStroke(1.dp, c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.paper, contentColor = c.ink),
                modifier = actionSize,
                contentPadding = actionPadding,
            ) {
                HomeGlyph(Glyph.Up, c.ink, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("做好了", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DetailChip(text: String, dot: Color? = null, glyph: Glyph? = null) {
    val c = LocalYixinColors.current
    Row(
        Modifier.background(c.tint, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (dot != null) CategoryDot(dot)
        if (glyph != null) HomeGlyph(glyph, c.muted, Modifier.size(12.dp))
        Text(text, color = c.muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditor(screen: Screen.Editor, deck: DeckState, model: YixinViewModel) {
    val c = LocalYixinColors.current
    val task = deck.task(screen.id)
    val initialDate = if (task == null) screen.defaultDueDate else task.dueDate
    var title by rememberSaveable(screen) { mutableStateOf(task?.title.orEmpty()) }
    var note by rememberSaveable(screen) { mutableStateOf(task?.note.orEmpty()) }
    var date by rememberSaveable(screen) { mutableStateOf(initialDate) }
    var minutes by rememberSaveable(screen) { mutableStateOf(task?.estimatedMinutes?.toString().orEmpty()) }
    var energy by rememberSaveable(screen) { mutableStateOf(task?.energy ?: Energy.medium) }
    var category by rememberSaveable(screen) { mutableStateOf(deck.activeCategory(task?.category)?.id) }
    var arrange by rememberSaveable(screen) { mutableStateOf(initialDate != null || task?.estimatedMinutes != null) }
    var datePicker by rememberSaveable(screen) { mutableStateOf(false) }
    var error by rememberSaveable(screen) { mutableStateOf<String?>(null) }

    TaskPanel(
        if (task == null) "新安排" else "编辑这一件",
        model::back,
        tools = { PanelIcon(Glyph.Close, "关闭", onClick = model::back) },
        alignTop = true,
        reduceMotion = deck.preferences.reduceMotion,
    ) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val titleFocus = remember(screen) { FocusRequester() }
        LaunchedEffect(titleFocus) {
            titleFocus.requestFocus()
            withFrameNanos { }
            keyboard?.show()
        }
        Text("写一个能开始的动作，就够了。", color = c.muted, fontSize = 12.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("这一件，想做什么？") {
                FormInput(
                    "这一件，想做什么？", title, { value ->
                        title = limitText(value, 120)
                        error = null
                    },
                    placeholder = "例如：把旅行照片整理好", lines = 2,
                    modifier = Modifier.focusRequester(titleFocus),
                )
            }
            FormField("给自己一句提示", optional = true) {
                FormInput(
                    "给自己一句提示", note, { note = limitText(it, 1000) },
                    placeholder = "把第一步写下来，会更容易开始。", lines = 3,
                )
            }
            val categories = deck.categories.filterNot { it.deleted }
            val chosen = deck.activeCategory(category)
            FormField("类别", optional = true) {
                FormSelect(
                    label = "类别", selected = chosen?.id, choices = listOf(null) + categories.map { it.id },
                    text = { deck.activeCategory(it)?.name ?: "未分类" },
                    dot = { categoryInk(deck.activeCategory(it), c.muted) },
                    onSelect = { category = it },
                )
            }
            ArrangeFields(arrange, deck.preferences.reduceMotion, toggle = { arrange = !arrange }) {
                ScheduleFields(
                    date, minutes, onDate = {
                        focus.clearFocus()
                        datePicker = true
                    },
                    onMinutes = {
                        minutes = it
                        error = null
                    },
                )
                FormField("需要多少精力") {
                    FormSelect("需要多少精力", energy, Energy.entries, Energy::label, onSelect = { energy = it })
                }
                Spacer(Modifier.height(9.dp))
            }
        }
        if (error != null) {
            Spacer(Modifier.height(9.dp))
            Text(checkNotNull(error), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(14.dp))
        PanelPrimary(
            Modifier.align(Alignment.End),
            onClick = {
                val trimmed = title.trim()
                val estimate = minutes.toIntOrNull()
                val draft = Draft(
                    trimmed, note.trim(), date, estimate, energy, deck.activeCategory(category)?.id,
                )
                error = runCatching {
                    require(trimmed.isNotEmpty()) { "写下你想做的一件事。" }
                    require(minutes.isEmpty() || estimate != null && minutes.all { it in '0'..'9' }) {
                        "请输入 1 至 1440 的整数分钟数。"
                    }
                    draft.validate()
                }.exceptionOrNull()?.message
                if (error == null) {
                    focus.clearFocus()
                    model.save(screen, draft)
                }
            },
        ) {
            Text(if (task == null) "放入卡片叠" else "保存这张卡片", fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
        }
    }
    if (datePicker) {
        val initial = date?.let(LocalDate::parse)?.takeIf { it.year in 1900..2100 }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }
                datePicker = false
            }) { Text("选定") }
        }, dismissButton = {
            TextButton(onClick = {
                date = null
                datePicker = false
            }) { Text("清空日期") }
        }) { DatePicker(picker) }
    }
}

@Composable
private fun PanelPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick, modifier.heightIn(min = 46.dp), shape = panelButtonShape,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun FormField(label: String, optional: Boolean = false, input: @Composable () -> Unit) {
    val c = LocalYixinColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.ink, fontSize = 12.sp, lineHeight = 19.sp)
            if (optional) Text("选填", color = c.muted, fontSize = 11.sp, lineHeight = 18.sp)
        }
        input()
    }
}

@Composable
private fun FormInput(
    label: String, value: String, change: (String) -> Unit, placeholder: String,
    lines: Int = 1, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = LocalYixinColors.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = change,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        singleLine = lines == 1,
        minLines = lines,
        maxLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = LocalTextStyle.current.copy(color = c.ink, fontSize = 14.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(c.accent),
        decorationBox = { input ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 42.dp).background(c.paper, controlShape)
                    .border(1.dp, if (focused) c.accent else c.line, controlShape)
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                contentAlignment = if (lines == 1) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 14.sp, lineHeight = 22.sp)
                input()
            }
        },
    )
}

@Composable
private fun <T> FormSelect(
    label: String, selected: T, choices: List<T>, text: (T) -> String,
    dot: ((T) -> Color)? = null, onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val c = LocalYixinColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val controlWidth = maxWidth
        FormControl(label, onClick = { expanded = true }) {
            if (dot != null) CategoryDot(dot(selected))
            Text(text(selected), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp, color = c.ink)
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = c.muted)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(controlWidth)) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(text(choice)) },
                    leadingIcon = dot?.let { tint -> { CategoryDot(tint(choice)) } },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun FormControl(label: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val c = LocalYixinColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).background(c.paper, controlShape)
            .border(1.dp, c.line, controlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun ArrangeFields(
    expanded: Boolean, reduce: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalYixinColors.current
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, if (reduce) snap() else tween(200), label = "schedule arrow")
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = c.line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = toggle)
                .semantics { stateDescription = if (expanded) "已展开" else "已折叠" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("安排一下", color = c.ink, fontSize = 12.sp, lineHeight = 19.sp)
            Spacer(Modifier.width(7.dp))
            Text("选填", color = c.muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(17.dp).rotate(arrow), tint = c.muted)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
        HorizontalDivider(color = c.line)
    }
}

@Composable
private fun ScheduleFields(date: String?, minutes: String, onDate: () -> Unit, onMinutes: (String) -> Unit) {
    val c = LocalYixinColors.current
    val dateField: @Composable () -> Unit = {
        FormField("希望哪天完成") {
            FormControl("希望哪天完成", onDate) {
                Text(date ?: "年-月-日", Modifier.weight(1f), color = if (date == null) c.muted else c.ink,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.DateRange, null, Modifier.size(15.dp), tint = c.muted)
            }
        }
    }
    val minutesField: @Composable () -> Unit = {
        FormField("预计用时 · 分钟") {
            FormInput("预计用时 · 分钟", minutes, onMinutes, "不确定可以留空", keyboardType = KeyboardType.Number)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth.value >= 220f * LocalDensity.current.fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { dateField() }
                Box(Modifier.weight(1f)) { minutesField() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                dateField()
                minutesField()
            }
        }
    }
}

private fun categoryInk(category: Category?, fallback: Color): Color =
    category?.let { rgb(it.color.rgb.toLong()) } ?: fallback

@Composable
private fun CategoryDot(color: Color) {
    Box(Modifier.size(7.dp).background(color, CircleShape))
}

private fun limitText(value: String, maxLength: Int): String {
    if (value.length <= maxLength) return value
    val sliced = value.take(maxLength)
    return if (sliced.last().isHighSurrogate()) sliced.dropLast(1) else sliced
}
