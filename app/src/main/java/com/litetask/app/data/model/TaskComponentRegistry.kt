package com.litetask.app.data.model

import com.google.gson.JsonObject
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

/**
 * 任务组件（插件）统一注册表与纯逻辑元数据中心。
 *
 * 职责：
 * - 统一提供组件显示名称、胶囊短摘要、可检索文本、日历同步文本与 AI Prompt JSON；
 * - 计算增量 Diff，避免全删全插导致的 createdAt 与足迹重复问题；
 * - 保持纯 Kotlin（无 Android UI 依赖），便于全量 JVM 单测覆盖。
 */
object TaskComponentRegistry {

    data class ComponentDiffResult(
        val toDelete: List<TaskComponentEntity>,
        val toUpdate: List<TaskComponentEntity>,
        val toInsert: List<TaskComponentEntity>
    )

    fun displayTypeName(type: ComponentType): String = when (type) {
        ComponentType.AMAP_ROUTE -> "目的地路线"
        ComponentType.FILE_ATTACHMENT -> "文件附件"
        ComponentType.LINK_BOOKMARK -> "网址链接"
        ComponentType.ACCESS_PASS -> "凭证/号牌"
        ComponentType.RECURRENCE_RULE -> "周期重复"
        ComponentType.VOICE_NOTE -> "语音备忘"
        ComponentType.UNKNOWN -> "扩展组件"
    }

    fun chipSummary(component: TaskComponent, maxLength: Int = 18): String {
        val raw = when (component) {
            is TaskComponent.AMapComponent -> {
                val base = component.data.endName.ifBlank { "目的地" }
                val eta = AMapRouteSupport.formatDurationShort(component.data.estimatedDurationSec)
                if (eta != null) "$base · $eta" else base
            }
            is TaskComponent.FileComponent -> component.data.effectiveDisplayName()
            is TaskComponent.LinkComponent -> {
                val host = LinkAndPassSupport.extractHostDisplay(component.data.url)
                component.data.title?.trim()?.takeIf { it.isNotEmpty() } ?: host.ifBlank { "链接" }
            }
            is TaskComponent.AccessPassComponent -> {
                val code = component.data.primaryCode.trim()
                val title = component.data.title.trim()
                when {
                    code.isNotBlank() && title.isNotBlank() && code.length <= 10 -> "$title $code"
                    code.isNotBlank() -> code
                    else -> title.ifBlank { "凭证" }
                }
            }
            is TaskComponent.RecurrenceComponent -> RecurrenceEngine.formatRuleSummary(component.data)
            is TaskComponent.VoiceNoteComponent -> {
                val clock = VoiceNoteSupport.formatDurationClock(component.data.durationMs)
                component.data.label?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it · $clock" }
                    ?: "语音 $clock"
            }
            is TaskComponent.UnknownComponent -> component.rawType.ifBlank { "未知组件" }
        }
        return truncateText(raw, maxLength)
    }

    fun searchableText(component: TaskComponent): String = when (component) {
        is TaskComponent.AMapComponent -> buildString {
            append(component.data.endName)
            component.data.endAddress?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
            component.data.startName.takeIf { it.isNotBlank() && it != "我的位置" }?.let {
                append(' ').append(it)
            }
        }
        is TaskComponent.FileComponent -> buildString {
            append(component.data.fileName)
            component.data.displayAlias?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
        }
        is TaskComponent.LinkComponent -> buildString {
            append(component.data.title.orEmpty())
            append(' ').append(component.data.url)
            component.data.note?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
        }
        is TaskComponent.AccessPassComponent -> buildString {
            append(component.data.title)
            append(' ').append(component.data.primaryCode)
            component.data.secondarySecret?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
            component.data.providerName?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
        }
        is TaskComponent.RecurrenceComponent -> RecurrenceEngine.formatRuleSummary(component.data)
        is TaskComponent.VoiceNoteComponent -> buildString {
            append(component.data.label.orEmpty())
            component.data.transcript?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(' ')
                append(it)
            }
        }
        is TaskComponent.UnknownComponent -> ""
    }

    fun matchesSearchQuery(composite: TaskDetailComposite, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val task = composite.task
        if (task.title.contains(q, ignoreCase = true)) return true
        if (task.description?.contains(q, ignoreCase = true) == true) return true
        if (composite.category?.name?.contains(q, ignoreCase = true) == true) return true
        if (composite.subTasks.any { it.content.contains(q, ignoreCase = true) }) return true
        return composite.components.any { entity ->
            val comp = entity.toTaskComponent()
            searchableText(comp).contains(q, ignoreCase = true)
        }
    }

    fun extractCalendarLocation(components: List<TaskComponent>): String? {
        val route = components.filterIsInstance<TaskComponent.AMapComponent>().firstOrNull()?.data
            ?: return null
        val name = route.endName.trim()
        val addr = route.endAddress?.trim().orEmpty()
        return when {
            name.isBlank() && addr.isBlank() -> null
            addr.isBlank() || addr == name || addr.contains(name, ignoreCase = true) ->
                name.ifBlank { addr }
            else -> "$name ($addr)"
        }
    }

    fun formatCalendarDescriptionLines(components: List<TaskComponent>): List<String> {
        return components.mapNotNull { component ->
            when (component) {
                is TaskComponent.AMapComponent -> {
                    val data = component.data
                    val modeLabel = with(AMapRouteSupport) { data.resolvedTravelMode().label }
                    val originPart = data.startName.takeIf { it.isNotBlank() && it != "我的位置" }
                        ?.let { "从 $it " } ?: ""
                    val addrPart = data.endAddress?.takeIf { it.isNotBlank() && it != data.endName }
                        ?.let { " ($it)" } ?: ""
                    val etaPart = AMapRouteSupport.formatEtaSummary(
                        data.estimatedDurationSec,
                        data.estimatedDistanceMeters
                    )?.let { " [$modeLabel · $it]" } ?: " [$modeLabel]"
                    "目的地: $originPart${data.endName}$addrPart$etaPart"
                }
                is TaskComponent.FileComponent -> {
                    val size = formatSizeSafe(component.data.fileSize)
                    "附件: ${component.data.effectiveDisplayName()} ($size)"
                }
                is TaskComponent.LinkComponent -> {
                    val title = with(LinkAndPassSupport) { component.data.effectiveTitle() }
                    "链接: $title (${component.data.url})"
                }
                is TaskComponent.AccessPassComponent -> {
                    val label = LinkAndPassSupport.AccessPassType.fromCode(component.data.passType).label
                    val sec = component.data.secondarySecret?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                    "$label: ${component.data.title} ${component.data.primaryCode}$sec"
                }
                is TaskComponent.RecurrenceComponent -> {
                    "重复规则: ${RecurrenceEngine.formatRuleSummary(component.data)}"
                }
                is TaskComponent.VoiceNoteComponent -> {
                    val clock = VoiceNoteSupport.formatDurationClock(component.data.durationMs)
                    val title = component.data.label?.trim()?.takeIf { it.isNotEmpty() } ?: "语音备忘"
                    val transcriptPart = component.data.transcript?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { " - ${truncateText(it, 60)}" } ?: ""
                    "语音: $title ($clock)$transcriptPart"
                }
                is TaskComponent.UnknownComponent -> null
            }
        }
    }

    fun toAiJsonObject(component: TaskComponent): JsonObject? = when (component) {
        is TaskComponent.AMapComponent -> JsonObject().apply {
            addProperty("type", "AMAP_ROUTE")
            addProperty("start_name", component.data.startName)
            addProperty("end_name", component.data.endName)
            if (!component.data.endAddress.isNullOrBlank()) {
                addProperty("end_address", component.data.endAddress)
            }
            addProperty("end_lng", component.data.endLng)
            addProperty("end_lat", component.data.endLat)
            addProperty("travel_mode", with(AMapRouteSupport) { component.data.resolvedTravelMode().code })
            component.data.estimatedDurationSec?.let { addProperty("estimated_duration_sec", it) }
            component.data.estimatedDistanceMeters?.let { addProperty("estimated_distance_meters", it) }
        }
        is TaskComponent.FileComponent -> JsonObject().apply {
            addProperty("type", "FILE_ATTACHMENT")
            addProperty("file_name", component.data.effectiveDisplayName())
            if (!component.data.displayAlias.isNullOrBlank()) {
                addProperty("original_file_name", component.data.fileName)
            }
            if (component.data.mimeType.isNotBlank()) {
                addProperty("mime_type", component.data.mimeType)
            }
        }
        is TaskComponent.LinkComponent -> JsonObject().apply {
            addProperty("type", "LINK_BOOKMARK")
            addProperty("url", component.data.url)
            addProperty("title", with(LinkAndPassSupport) { component.data.effectiveTitle() })
            if (!component.data.linkCategory.isNullOrBlank()) {
                addProperty("category", component.data.linkCategory)
            }
        }
        is TaskComponent.AccessPassComponent -> JsonObject().apply {
            addProperty("type", "ACCESS_PASS")
            addProperty("pass_type", component.data.passType)
            addProperty("title", component.data.title)
            addProperty("primary_code", component.data.primaryCode)
            if (!component.data.secondarySecret.isNullOrBlank()) {
                addProperty("secondary_secret", component.data.secondarySecret)
            }
            if (!component.data.providerName.isNullOrBlank()) {
                addProperty("provider_name", component.data.providerName)
            }
        }
        is TaskComponent.RecurrenceComponent -> JsonObject().apply {
            addProperty("type", "RECURRENCE_RULE")
            addProperty("freq", component.data.freq)
            addProperty("interval", component.data.interval)
            addProperty("summary", RecurrenceEngine.formatRuleSummary(component.data))
            addProperty("occurrence_index", component.data.occurrenceIndex)
            addProperty("is_paused", component.data.isPaused)
        }
        is TaskComponent.VoiceNoteComponent -> JsonObject().apply {
            addProperty("type", "VOICE_NOTE")
            addProperty("duration_ms", component.data.durationMs)
            addProperty("duration_clock", VoiceNoteSupport.formatDurationClock(component.data.durationMs))
            if (!component.data.label.isNullOrBlank()) {
                addProperty("label", component.data.label)
            }
            if (!component.data.transcript.isNullOrBlank()) {
                addProperty("transcript", component.data.transcript)
            }
        }
        is TaskComponent.UnknownComponent -> null
    }

    /**
     * 计算任务组件集合的增量变更，保留未改动记录的原 id 与 createdAt。
     */
    fun computeIncrementalDiff(
        existingEntities: List<TaskComponentEntity>,
        targetComponents: List<TaskComponent>,
        taskId: Long
    ): ComponentDiffResult {
        val existingById = existingEntities.associateBy { it.id }
        val retainedIds = mutableSetOf<Long>()
        val toUpdate = mutableListOf<TaskComponentEntity>()
        val toInsert = mutableListOf<TaskComponentEntity>()

        targetComponents.forEach { target ->
            val matchedExisting = if (target.id > 0L) existingById[target.id] else null
            if (matchedExisting != null && retainedIds.add(matchedExisting.id)) {
                val candidate = target.toEntity(taskId = taskId, createdAt = matchedExisting.createdAt)
                if (candidate.type != matchedExisting.type || candidate.dataJson != matchedExisting.dataJson) {
                    toUpdate.add(candidate)
                }
            } else {
                toInsert.add(target.copyWithTempZeroId().toEntity(taskId = taskId))
            }
        }

        val toDelete = existingEntities.filter { it.id !in retainedIds }
        return ComponentDiffResult(
            toDelete = toDelete,
            toUpdate = toUpdate,
            toInsert = toInsert
        )
    }

    /**
     * 计算新增或目的地变更的高德组件列表（用于去重触发位置足迹记录）。
     */
    fun extractAddedOrChangedRoutes(
        oldComponents: List<TaskComponent>,
        newComponents: List<TaskComponent>
    ): List<TaskComponent.AMapComponent> {
        val oldRouteKeys = oldComponents
            .filterIsInstance<TaskComponent.AMapComponent>()
            .map { Triple(it.data.endName, it.data.endLat, it.data.endLng) }
            .toSet()
        return newComponents
            .filterIsInstance<TaskComponent.AMapComponent>()
            .filter { Triple(it.data.endName, it.data.endLat, it.data.endLng) !in oldRouteKeys }
    }

    fun formatSizeSafe(size: Long): String {
        if (size <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val rawGroup = (log10(size.toDouble()) / log10(1024.0)).toInt()
        val digitGroups = rawGroup.coerceIn(0, units.lastIndex)
        val value = size / 1024.0.pow(digitGroups.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
    }

    private fun truncateText(text: String, maxLength: Int): String {
        val cleaned = text.trim()
        if (cleaned.length <= maxLength || maxLength <= 1) return cleaned
        return cleaned.take(maxLength - 1).trimEnd() + "…"
    }

    private fun TaskComponent.copyWithTempZeroId(): TaskComponent = when (this) {
        is TaskComponent.AMapComponent -> copy(id = 0L)
        is TaskComponent.FileComponent -> copy(id = 0L)
        is TaskComponent.LinkComponent -> copy(id = 0L)
        is TaskComponent.AccessPassComponent -> copy(id = 0L)
        is TaskComponent.RecurrenceComponent -> copy(id = 0L)
        is TaskComponent.VoiceNoteComponent -> copy(id = 0L)
        is TaskComponent.UnknownComponent -> copy(id = 0L)
    }
}
