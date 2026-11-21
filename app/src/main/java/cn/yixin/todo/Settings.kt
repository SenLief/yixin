package cn.yixin.todo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yixin.core.Category
import cn.yixin.core.DeckState
import cn.yixin.core.FocusMode
import cn.yixin.core.FocusStyle
import cn.yixin.core.HomeView
import cn.yixin.core.ImportOptions
import cn.yixin.core.Mood
import cn.yixin.core.Preferences
import cn.yixin.core.Ritual
import cn.yixin.core.Sort
import cn.yixin.core.TagColor
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Settings(deck: DeckState, model: YixinViewModel) {
    val p = deck.preferences
    val c = LocalYixinColors.current
    ScrollPage("设置", model::back) {
        SettingsGroup("选一种心情") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mood.entries.forEach { mood ->
                    val preview = moodColors(mood)
                    FilterChip(
                        selected = mood == p.theme, onClick = { model.preferences { it.copy(theme = mood) } },
                        label = { Text(mood.label) },
                        leadingIcon = {
                            Box(Modifier.size(13.dp).background(preview.accent, CircleShape))
                        },
                    )
                }
            }
        }
        SettingsGroup("卡片与日历") {
            ChoiceField("展示方式", p.homeView, HomeView.entries, HomeView::label) { view ->
                model.preferences { it.copy(homeView = view) }
            }
            ChoiceField("任务排序", p.sortMode, Sort.entries, Sort::label) { sort ->
                model.preferences { it.copy(sortMode = sort) }
            }
            if (p.sortMode == Sort.random) OutlinedButton(onClick = model::shuffle) { Text("洗一次牌") }
            SettingsLink("管理类别") { model.open(Screen.Categories) }
        }
        SettingsGroup("动效") {
            ChoiceField("完成仪式", p.ritual, Ritual.entries, Ritual::label) { ritual ->
                model.preferences { it.copy(ritual = ritual) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("减少动态效果")
                    Text("关闭应用内的发牌、粒子和氛围动画。", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(p.reduceMotion, onCheckedChange = { checked -> model.preferences { it.copy(reduceMotion = checked) } })
            }
        }
        SettingsGroup("本地数据") {
            SettingsLink("备份与迁移") { model.open(Screen.Backups) }
        }
    }
}

@Composable
internal fun FocusPreferencesFields(preferences: Preferences, model: YixinViewModel) {
    ChoiceField("计时方式", preferences.focusMode, FocusMode.entries, FocusMode::label) { mode ->
        model.preferences { it.copy(focusMode = mode) }
    }
    if (preferences.focusMode == FocusMode.countdown) FocusDuration(preferences.focusMinutes, model)
    ChoiceField("专注风格", preferences.focusStyle, FocusStyle.entries, FocusStyle::label) { style ->
        model.preferences { it.copy(focusStyle = style) }
    }
}

@Composable
private fun FocusDuration(minutes: Int, model: YixinViewModel) {
    var input by rememberSaveable(minutes) { mutableStateOf(minutes.toString()) }
    val value = input.toIntOrNull()?.takeIf { it in 1..1440 && input.all { ch -> ch in '0'..'9' } }
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            input, { input = it }, Modifier.weight(1f), label = { Text("倒计时分钟数") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = value == null, supportingText = { Text("1–1440 分钟") },
        )
        TextButton(enabled = value != null && value != minutes, onClick = {
            value?.let { chosen ->
                focus.clearFocus()
                model.preferences { it.copy(focusMinutes = chosen) }
            }
        }) { Text("应用") }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalYixinColors.current
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, c.line), color = c.paper,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            content()
        }
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
internal fun Categories(deck: DeckState, model: YixinViewModel) {
    val c = LocalYixinColors.current
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(TagColor.blue) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    ScrollPage("管理类别", model::back) {
        SettingsGroup("新增类别") {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(), label = { Text("类别名称") }, singleLine = true,
                supportingText = { Text("${name.trim().length} / 20") }, isError = name.trim().length > 20,
            )
            ChoiceField("色签", color, TagColor.entries, TagColor::label) { color = it }
            Button(enabled = name.trim().length in 1..20, onClick = {
                model.addCategory(name, color) {
                    name = ""
                    focus.clearFocus()
                }
            }) { Text("添加类别") }
        }
        deck.categories.filterNot { it.deleted }.forEach { category ->
            CategoryItem(category) { pendingDelete = category.id }
        }
        if (deck.categories.none { !it.deleted }) Text("还没有可用类别。", color = c.muted)
    }
    deck.activeCategory(pendingDelete)?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${category.name}」？") },
            text = { Text("原有任务和完成记录会保留，相关任务会显示为未分类。") },
            confirmButton = {
                TextButton(onClick = { model.deleteCategory(category.id); pendingDelete = null }) { Text("删除类别") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryItem(category: Category, onDelete: () -> Unit) {
    val c = LocalYixinColors.current
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, c.line), color = c.paper,
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CategoryTag(category) }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
internal fun Backups(model: YixinViewModel) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.readBackup(uri)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportBackup(uri)
    }
    ScrollPage("备份与迁移", model::back) {
        SettingsGroup("导出备份") {
            Text("将任务、类别、顺序、偏好和专注进度保存为 JSON。备份文件包含任务内容。")
            Button(onClick = { save.launch("一心-${LocalDate.now()}.json") }, enabled = !busy) { Text("保存到文件") }
            OutlinedButton(onClick = { model.exportBackup() }, enabled = !busy) { Text("分享备份文件") }
        }
        SettingsGroup("导入备份") {
            Text("选择一心安卓备份或网页端导出的 JSON，查看预览后再导入。")
            Button(onClick = { load.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !busy) { Text("选择备份文件") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun BackupReview(review: Screen.Review, model: YixinViewModel) {
    val c = LocalYixinColors.current
    val preview = review.preview
    var useIncoming by rememberSaveable(review.baseline.hashCode()) { mutableStateOf(false) }
    var usePreferences by rememberSaveable(review.baseline.hashCode()) { mutableStateOf(preview.pristine) }
    Frame("导入预览", model::back) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            item {
                SettingsGroup(if (review.backup.fromWeb) "网页备份" else "安卓备份") {
                    Text("${preview.total} 件任务，含 ${preview.archived} 件归档")
                    Text("将新增 ${preview.newTasks} 件任务、${preview.newCategories} 个类别")
                    if (preview.conflicts.isNotEmpty()) Text("${preview.conflicts.size} 处同编号差异")
                    else Text("没有同编号差异", color = c.muted)
                    if (review.backup.fromWeb) Text("只导入网页备份中已有的任务与偏好。", color = c.muted)
                }
            }
            if (!preview.pristine) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("采用备份的排序、洗牌和偏好", Modifier.weight(1f))
                    Switch(usePreferences, onCheckedChange = { usePreferences = it })
                }
            }
            if (preview.conflicts.isNotEmpty()) item {
                ChoiceField("同编号记录如何处理", useIncoming, listOf(false, true),
                    { if (it) "采用备份记录" else "保留本机记录" }) { useIncoming = it }
            }
            items(preview.conflicts) { conflict ->
                Surface(color = c.paper, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, c.line)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(conflict.label, style = MaterialTheme.typography.titleSmall)
                        Text(conflict.description, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = { model.applyBackup(review, ImportOptions(useIncoming, usePreferences)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("确认导入") }
    }
}
