package cn.yixin.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val backupFormat = "yixin-android"
private const val backupVersion = 1
private const val maxSafeJsInteger = 9_007_199_254_740_991L
const val maxBackupBytes = 48 * 1024 * 1024

private val nativeJson = Json { encodeDefaults = true; prettyPrint = true }
private val webJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class NativeBackup(val format: String, val version: Int, val data: DeckState)

@Serializable
private data class WebBackup(
    val schemaVersion: Int,
    val tasks: List<WebTask>,
    val categories: List<Category> = defaultCategories,
    val manualOrder: List<String>,
    val currentId: String?,
    val shuffleSeed: Long,
    val preferences: WebPreferences,
)

@Serializable
private data class WebPreferences(val theme: Mood, val sortMode: Sort, val ritual: Ritual)

@Serializable
private data class WebTask(
    val id: String,
    val title: String,
    val note: String,
    val dueDate: String?,
    val estimatedMinutes: Int?,
    val energy: Energy,
    val category: String? = null,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
) {
    fun toTask(knownCategories: Set<String>) = Task(
        id, title, note, dueDate, estimatedMinutes, energy, category?.takeIf { it in knownCategories },
        createdAt, updatedAt, completedAt,
    )
}

data class Backup(val data: DeckState, val fromWeb: Boolean)

fun encodeBackup(state: DeckState): String =
    nativeJson.encodeToString(NativeBackup(backupFormat, backupVersion, state))

fun decodeBackup(raw: String): Backup {
    try {
        val root = nativeJson.parseToJsonElement(raw.removePrefix("\uFEFF")).jsonObject
        if ("format" in root || "version" in root) {
            require(root["format"]?.jsonPrimitive?.content == backupFormat) { "备份格式不兼容。" }
            require(root.version("version") == backupVersion) { "此版本的一心无法读取这个安卓备份版本。" }
            val data = root["data"]?.jsonObject
            require(data != null && data.keys.containsAll(nativeStateFields) &&
                data["preferences"]?.jsonObject?.keys?.containsAll(nativePreferenceFields) == true) {
                "安卓备份的数据或偏好不完整。"
            }
            data.validatePrimitiveFields(web = false)
            val decoded = nativeJson.decodeFromJsonElement<NativeBackup>(root)
            decoded.data.validateBackup()
            return Backup(decoded.data, fromWeb = false)
        }
        require(root.version("schemaVersion") == 1) { "此版本的一心无法读取这个网页备份版本。" }
        root.validatePrimitiveFields(web = true)
        val web = webJson.decodeFromJsonElement<WebBackup>(root)
        val known = web.categories.map { it.id }.toSet()
        require(web.tasks.all { it.category == null || validCategoryId(it.category) }) { "备份中的类别编号无效。" }
        val tasks = web.tasks.map { it.toTask(known) }
        val pinned = web.tasks.filter { it.pinned && it.completedAt == null }
        require(pinned.size <= 1) { "备份中存在多张置顶卡片。" }
        require(web.currentId == null || tasks.any { it.id == web.currentId }) { "备份的当前卡片无效。" }
        val pinnedId = pinned.singleOrNull()?.id
        val data = DeckState(
            datasetId = "web-v1",
            tasks = tasks,
            categories = web.categories,
            manualOrder = web.manualOrder,
            currentId = pinnedId ?: web.currentId?.takeIf { id -> tasks.any { it.id == id && it.completedAt == null } },
            pinnedId = pinnedId,
            shuffleSeed = web.shuffleSeed,
            preferences = Preferences(
                theme = web.preferences.theme, sortMode = web.preferences.sortMode, ritual = web.preferences.ritual,
            ),
        )
        data.validateBackup()
        return Backup(data, fromWeb = true)
    } catch (error: kotlinx.serialization.SerializationException) {
        throw IllegalArgumentException("备份 JSON 损坏，或字段格式不兼容。", error)
    } catch (error: IllegalStateException) {
        throw IllegalArgumentException("备份中的对象或字段类型无效。", error)
    }
}

private val nativeStateFields = setOf(
    "datasetId", "tasks", "categories", "manualOrder", "currentId", "pinnedId",
    "shuffleSeed", "preferences", "progress",
)

private val nativePreferenceFields = setOf(
    "theme", "sortMode", "ritual", "homeView", "focusMode", "focusMinutes", "focusStyle", "reduceMotion",
)

private fun JsonObject.version(key: String): Int? = get(key)?.jsonPrimitive
    ?.takeUnless { it.isString }?.intOrNull

private fun JsonObject.validatePrimitiveFields(web: Boolean) {
    fun JsonObject.requireUnquoted(vararg fields: String) {
        for (field in fields) {
            val element = this[field] ?: continue
            require(element is JsonPrimitive && !element.isString) { "备份中的 $field 字段类型无效。" }
        }
    }
    fun JsonObject.objects(key: String) = (this[key] as? JsonArray).orEmpty().map { it.jsonObject }

    requireUnquoted("shuffleSeed")
    objects("tasks").forEach { task ->
        task.requireUnquoted("estimatedMinutes", "createdAt", "updatedAt", "completedAt")
        if (web) task.requireUnquoted("pinned")
    }
    objects("categories").forEach { it.requireUnquoted("deleted") }
    if (!web) {
        getValue("preferences").jsonObject.requireUnquoted("focusMinutes", "reduceMotion")
        objects("progress").forEach { it.requireUnquoted("durationMs", "elapsedMs") }
    }
}

private fun DeckState.validateBackup() {
    require(datasetId.isNotBlank() && datasetId.length <= 100 && datasetId.hasPairedSurrogates()) {
        "备份的数据集编号无效。"
    }
    require(shuffleSeed in 0..maxSafeJsInteger && preferences.focusMinutes in 1..1440) {
        "备份中的洗牌状态或专注设置无效。"
    }
    val byId = tasks.associateBy { it.id }
    require(byId.size == tasks.size && manualOrder.size == tasks.size && manualOrder.toSet() == byId.keys) {
        "任务编号重复，或手动顺序与任务不一致。"
    }
    val categoryIds = categories.map { it.id }.toSet()
    require(categoryIds.size == categories.size) { "类别编号重复。" }
    categories.forEach { it.validate() }
    tasks.forEach { task ->
        task.draft().validate()
        require(task.id.length in 1..100 && task.id.hasPairedSurrogates() &&
            task.createdAt >= 0 && task.updatedAt >= 0 && (task.completedAt ?: 0) >= 0) {
            "备份中的任务编号或时间戳无效。"
        }
        require(task.category == null || task.category in categoryIds) { "任务关联的类别不存在。" }
    }
    require(currentId == null || byId[currentId]?.let { it.completedAt == null } == true) {
        "备份的当前卡片无效。"
    }
    require(pinnedId == null || pinnedId == currentId) { "备份的置顶卡片与当前卡片不一致。" }
    require(progress.map { it.taskId }.toSet().size == progress.size) { "备份中的专注记录重复。" }
    progress.forEach {
        require(byId[it.taskId]?.let { task -> task.completedAt == null } == true &&
            it.durationMs in 60_000L..86_400_000L && it.elapsedMs >= 0 &&
            (it.mode == FocusMode.countup || it.elapsedMs <= it.durationMs)) {
            "备份中的专注记录无效。"
        }
    }
}

data class ImportConflict(val id: String, val label: String, val description: String)

data class ImportPreview(
    val total: Int,
    val archived: Int,
    val newTasks: Int,
    val newCategories: Int,
    val conflicts: List<ImportConflict>,
    val pristine: Boolean,
)

private fun DeckState.isPristine() = tasks.isEmpty() && categories == defaultCategories &&
    preferences == Preferences() && shuffleSeed == 1L

fun DeckState.preview(backup: Backup): ImportPreview {
    val incoming = backup.data
    val localTasks = tasks.associateBy { it.id }
    val localCategories = categories.associateBy { it.id }
    val pristine = isPristine()
    val localProgress = progress.associateBy { it.taskId }
    val conflicts = buildList {
        incoming.categories.forEach { other ->
            val local = localCategories[other.id]
            if (!pristine && local != null && local != other) {
                val message = if (local.deleted) "本机已删除，保留删除记录"
                else "本机：${local.name} · ${local.color.label}；备份：${other.name} · ${other.color.label}" +
                    if (other.deleted) "（已删除）" else ""
                add(ImportConflict(other.id, "类别 · ${other.name}", message))
            }
        }
        incoming.tasks.forEach { other ->
            val local = localTasks[other.id]
            if (local != null && local != other) {
                fun status(task: Task) = if (task.completedAt == null) "待办" else "归档"
                val fields = buildList {
                    if (local.title != other.title) add("标题")
                    if (local.note != other.note) add("备注")
                    if (local.dueDate != other.dueDate) add("日期")
                    if (local.estimatedMinutes != other.estimatedMinutes) add("预计用时")
                    if (local.energy != other.energy) add("精力")
                    if (local.category != other.category) add("类别")
                    if (local.completedAt != other.completedAt) add("完成记录")
                    if (local.createdAt != other.createdAt || local.updatedAt != other.updatedAt) add("时间戳")
                }.joinToString("、")
                add(ImportConflict(other.id, "任务 · ${other.title}",
                    "$fields 有差异。\n本机：${local.title}（${status(local)}）；备份：${other.title}（${status(other)}）"))
            }
        }
        incoming.progress.forEach { other ->
            val local = localProgress[other.taskId]
            if (local != null && local != other) {
                add(ImportConflict(other.taskId, "专注 · ${localTasks[other.taskId]?.title.orEmpty()}",
                    "本机 ${local.elapsedMs / 1000} 秒，备份 ${other.elapsedMs / 1000} 秒"))
            }
        }
    }
    return ImportPreview(
        incoming.tasks.size, incoming.tasks.count { it.completedAt != null },
        incoming.tasks.count { it.id !in localTasks },
        incoming.categories.count { it.id !in localCategories }, conflicts, pristine,
    )
}

data class ImportOptions(val useIncomingConflicts: Boolean, val useIncomingOrderAndPreferences: Boolean)

fun DeckState.merge(backup: Backup, options: ImportOptions): DeckState {
    val incoming = backup.data
    val pristine = isPristine()
    val useOrder = pristine || options.useIncomingOrderAndPreferences
    val mergedCategories = categories.associateByTo(linkedMapOf()) { it.id }
    val mergedTasks = tasks.associateByTo(linkedMapOf()) { it.id }
    val mergedProgress = progress.associateByTo(linkedMapOf()) { it.taskId }
    incoming.categories.forEach {
        val local = mergedCategories[it.id]
        if (local == null || (options.useIncomingConflicts && !local.deleted)) mergedCategories[it.id] = it
    }
    incoming.tasks.forEach {
        if (it.id !in mergedTasks || options.useIncomingConflicts) mergedTasks[it.id] = it
    }
    incoming.progress.forEach {
        if (it.taskId !in mergedProgress || options.useIncomingConflicts) mergedProgress[it.taskId] = it
    }
    fun active(id: String?) = id?.takeIf { mergedTasks[it]?.let { task -> task.completedAt == null } == true }
    val pin = active(if (pristine) incoming.pinnedId else pinnedId)
    val current = pin ?: active(if (pristine) incoming.currentId else current()?.id)
    val prefs = when {
        !useOrder -> preferences
        backup.fromWeb -> preferences.copy(
            theme = incoming.preferences.theme, sortMode = incoming.preferences.sortMode, ritual = incoming.preferences.ritual,
        )
        else -> incoming.preferences
    }
    return copy(
        datasetId = if (pristine && !backup.fromWeb) incoming.datasetId else datasetId,
        categories = if (pristine) incoming.categories else mergedCategories.values.toList(),
        tasks = mergedTasks.values.toList(),
        manualOrder = if (useOrder) (incoming.manualOrder + manualOrder).distinct()
            else (manualOrder + incoming.manualOrder).distinct(),
        currentId = current,
        pinnedId = pin,
        shuffleSeed = if (useOrder) incoming.shuffleSeed else shuffleSeed,
        preferences = prefs,
        progress = mergedProgress.values.filter { active(it.taskId) != null }.sortedBy { it.taskId },
    ).let { if (it.currentId == null) it.copy(currentId = it.current()?.id) else it }
        .also { it.validateBackup() }
}
