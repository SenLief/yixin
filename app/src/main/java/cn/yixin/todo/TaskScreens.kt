package cn.yixin.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.yixin.core.CategoryFilter
import cn.yixin.core.DeckState
import cn.yixin.core.Sort
import cn.yixin.core.Task
import cn.yixin.core.matches
import java.time.Instant

@Composable
internal fun Loading(error: String?, retry: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (error == null) CircularProgressIndicator()
        else {
            Text(error, color = LocalYixinColors.current.ink)
            TextButton(onClick = retry) { Text("重试") }
        }
    }
}

@Composable
internal fun Frame(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalYixinColors.current
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = c.ink) }
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink,
                    fontFamily = FontFamily.Serif, modifier = Modifier.padding(start = 4.dp))
            }
            content()
        }
    }
}

@Composable
internal fun ScrollPage(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Frame(title, back) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }
}

@Composable
internal fun <T> ChoiceField(
    title: String,
    selected: T,
    choices: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) Text(title, color = LocalYixinColors.current.muted, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(label(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(label(choice)) }, onClick = {
                        expanded = false
                        onSelect(choice)
                    })
                }
            }
        }
    }
}

@Composable
internal fun TaskList(deck: DeckState, model: YixinViewModel, archive: Boolean) {
    val c = LocalYixinColors.current
    val categories = remember(deck.categories) { deck.categories.filterNot { it.deleted } }
    var storedFilter by rememberSaveable { mutableStateOf("") }
    val filterId = storedFilter.takeIf { it == "-" || categories.any { category -> category.id == it } }.orEmpty()
    val filter = when (filterId) {
        "" -> CategoryFilter.All
        "-" -> CategoryFilter.Unclassified
        else -> CategoryFilter.Tagged(filterId)
    }
    val tasks = if (archive) remember(deck.tasks) {
        deck.tasks.filter { it.completedAt != null }.sortedByDescending { it.completedAt }
    } else rememberOrderedTasks(deck)
    val currentId = if (archive) null else deck.current(tasks)?.id
    val visible = if (filter == CategoryFilter.All) tasks
        else remember(tasks, deck.categories, filter) { tasks.filter { deck.matches(it, filter) } }
    Frame(if (archive) "完成归档" else "卡片叠", model::back) {
        if (!archive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { model.open(Screen.Archive) }) { Text("完成归档") }
            }
        }
        ChoiceField("按类别查看", filterId, listOf("", "-") + categories.map { it.id }, {
            when (it) {
                "" -> "全部类别"
                "-" -> "未分类"
                else -> deck.activeCategory(it)?.name.orEmpty()
            }
        }) { storedFilter = it }
        Spacer(Modifier.height(10.dp))
        if (!archive && deck.pinnedId != null) {
            Text("置顶期间只可打开置顶任务。", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        if (visible.isEmpty()) {
            Text(if (archive) "这里还没有完成记录。" else "这里没有待办。", Modifier.padding(12.dp), color = c.muted)
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 22.dp),
        ) {
            itemsIndexed(visible, key = { _, task -> task.id }) { index, task ->
                val selectable = !archive && deck.canSelect(task)
                TaskRow(task, deck, enabled = selectable,
                    onClick = { model.select(task.id) }) {
                    when {
                        archive -> TextButton(onClick = { model.restore(task.id) }) { Text("恢复") }
                        selectable -> {
                            if (filter == CategoryFilter.All && deck.preferences.sortMode == Sort.manual && deck.pinnedId == null) {
                                IconButton(onClick = { model.move(task.id, -1) }, enabled = index > 0,
                                    modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "向前移动") }
                                IconButton(onClick = { model.move(task.id, 1) }, enabled = index < tasks.lastIndex,
                                    modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "向后移动") }
                            }
                            if (currentId == task.id) {
                                PinButton(deck.pinnedId == task.id, onClick = { model.pin(task.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskRow(
    task: Task,
    deck: DeckState,
    enabled: Boolean,
    onClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalYixinColors.current
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CategoryTag(deck.activeCategory(task.category))
                task.completedAt?.let { timestamp ->
                    val date = Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Text("$date 完成", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            actions()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, c.line)
    if (task.completedAt == null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = c.paper, border = border, content = body)
    } else Surface(shape = shape, color = c.paper, border = border, content = body)
}
