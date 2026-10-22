package com.litetask.app.data.model

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

import androidx.annotation.Keep

/**
 * 任务组件实体类
 *
 * 使用多态存储不同类型的组件数据。
 * 核心设计：component_type + data_payload (JSON)
 */
@Keep
@Entity(
    tableName = "task_components",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["task_id"])]
)
data class TaskComponentEntity(
    @SerializedName("id", alternate = ["a"])
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("task_id", alternate = ["b"])
    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @SerializedName("component_type", alternate = ["c"])
    @ColumnInfo(name = "component_type")
    val type: ComponentType,

    @SerializedName("data_payload", alternate = ["d"])
    @ColumnInfo(name = "data_payload")
    val dataJson: String, // 存储具体组件数据的 JSON 字符串

    @SerializedName("created_at", alternate = ["e"])
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    // 辅助方法：将 JSON 转为具体的组件数据对象（未知类型或损坏 JSON 自动兜底，不抛异常）
    fun toTaskComponent(): TaskComponent {
        if (type == ComponentType.UNKNOWN) {
            return TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
        }
        return runCatching {
            when (type) {
                ComponentType.AMAP_ROUTE -> {
                    val data = componentGson.fromJson(dataJson, AMapRouteData::class.java)
                    if (data != null && !data.endName.isNullOrBlank()) {
                        TaskComponent.AMapComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.FILE_ATTACHMENT -> {
                    val data = componentGson.fromJson(dataJson, FileAttachmentData::class.java)
                    if (data != null && !data.fileName.isNullOrBlank()) {
                        TaskComponent.FileComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.LINK_BOOKMARK -> {
                    val data = componentGson.fromJson(dataJson, LinkBookmarkData::class.java)
                    if (data != null && !data.url.isNullOrBlank()) {
                        TaskComponent.LinkComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.ACCESS_PASS -> {
                    val data = componentGson.fromJson(dataJson, AccessPassData::class.java)
                    if (data != null && !data.primaryCode.isNullOrBlank()) {
                        TaskComponent.AccessPassComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.RECURRENCE_RULE -> {
                    val data = componentGson.fromJson(dataJson, RecurrenceRuleData::class.java)
                    if (data != null && !data.freq.isNullOrBlank()) {
                        TaskComponent.RecurrenceComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.VOICE_NOTE -> {
                    val data = componentGson.fromJson(dataJson, VoiceNoteData::class.java)
                    if (data != null && (!data.filePath.isNullOrBlank() || !data.fileUri.isNullOrBlank())) {
                        TaskComponent.VoiceNoteComponent(id, taskId, data)
                    } else {
                        TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                    }
                }
                ComponentType.UNKNOWN -> {
                    TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
                }
            }
        }.getOrElse {
            TaskComponent.UnknownComponent(id, taskId, type.name, dataJson)
        }
    }
}

private val componentGson: Gson by lazy { Gson() }

/**
 * 为尚未落库的 UI 临时组件分配全局唯一负数 ID，避免同毫秒创建时发生主键/Compose Key 冲突。
 */
object TempComponentIdGenerator {
    private val seq = java.util.concurrent.atomic.AtomicLong(-1L)
    fun nextId(): Long = seq.getAndDecrement()
}

enum class ComponentType {
    AMAP_ROUTE,
    FILE_ATTACHMENT,
    LINK_BOOKMARK,
    ACCESS_PASS,
    RECURRENCE_RULE,
    VOICE_NOTE,
    UNKNOWN
}

// --- 业务逻辑模型 (Sealed Class) ---

sealed class TaskComponent {
    abstract val id: Long
    abstract val taskId: Long
    abstract val type: ComponentType

    data class AMapComponent(
        override val id: Long,
        override val taskId: Long,
        val data: AMapRouteData
    ) : TaskComponent() {
        override val type = ComponentType.AMAP_ROUTE
    }

    data class FileComponent(
        override val id: Long,
        override val taskId: Long,
        val data: FileAttachmentData
    ) : TaskComponent() {
        override val type = ComponentType.FILE_ATTACHMENT
    }

    data class LinkComponent(
        override val id: Long,
        override val taskId: Long,
        val data: LinkBookmarkData
    ) : TaskComponent() {
        override val type = ComponentType.LINK_BOOKMARK
    }

    data class AccessPassComponent(
        override val id: Long,
        override val taskId: Long,
        val data: AccessPassData
    ) : TaskComponent() {
        override val type = ComponentType.ACCESS_PASS
    }

    data class RecurrenceComponent(
        override val id: Long,
        override val taskId: Long,
        val data: RecurrenceRuleData
    ) : TaskComponent() {
        override val type = ComponentType.RECURRENCE_RULE
    }

    data class VoiceNoteComponent(
        override val id: Long,
        override val taskId: Long,
        val data: VoiceNoteData
    ) : TaskComponent() {
        override val type = ComponentType.VOICE_NOTE
    }

    data class UnknownComponent(
        override val id: Long,
        override val taskId: Long,
        val rawType: String = "",
        val rawJson: String = ""
    ) : TaskComponent() {
        override val type = ComponentType.UNKNOWN
    }
}

// --- 组件具体数据结构 (Data Payload) ---

@Keep
data class AMapRouteData(
    @SerializedName("start_name", alternate = ["a"]) val startName: String = "我的位置",
    @SerializedName("end_name", alternate = ["b"]) val endName: String,
    @SerializedName("end_address", alternate = ["c"]) val endAddress: String?,
    @SerializedName("end_lat", alternate = ["d"]) val endLat: Double,
    @SerializedName("end_lng", alternate = ["e"]) val endLng: Double,
    @SerializedName("adcode", alternate = ["f"]) val adcode: String? = null,
    @SerializedName("strategy", alternate = ["g"]) val strategy: Int = 0, // 0: 速度优先, 1: 少收费, 2: 距离优先
    @SerializedName("start_lat", alternate = ["h"]) val startLat: Double? = null,
    @SerializedName("start_lng", alternate = ["i"]) val startLng: Double? = null,
    @SerializedName("travel_mode", alternate = ["j"]) val travelMode: String? = "driving",
    @SerializedName("estimated_duration_sec", alternate = ["k"]) val estimatedDurationSec: Int? = null,
    @SerializedName("estimated_distance_meters", alternate = ["l"]) val estimatedDistanceMeters: Int? = null
)

@Keep
data class FileAttachmentData(
    @SerializedName("file_name", alternate = ["a"]) val fileName: String,
    @SerializedName("file_uri", alternate = ["b"]) val fileUri: String, // 持久化 URI 或 FileProvider URI
    @SerializedName("mime_type", alternate = ["c"]) val mimeType: String,
    @SerializedName("file_size", alternate = ["d"]) val fileSize: Long,
    @SerializedName("managed_file_path", alternate = ["e"]) val managedFilePath: String? = null,
    @SerializedName("display_alias", alternate = ["f"]) val displayAlias: String? = null,
    @SerializedName("is_managed_copy", alternate = ["g"]) val isManagedCopy: Boolean = false
) {
    fun effectiveDisplayName(): String =
        displayAlias?.trim()?.takeIf { it.isNotEmpty() } ?: fileName.ifBlank { "未命名附件" }
}

@Keep
data class LinkBookmarkData(
    @SerializedName("url", alternate = ["a"]) val url: String,
    @SerializedName("title", alternate = ["b"]) val title: String? = null,
    @SerializedName("link_category", alternate = ["c"]) val linkCategory: String? = "WEB",
    @SerializedName("note", alternate = ["d"]) val note: String? = null
)

@Keep
data class AccessPassData(
    @SerializedName("pass_type", alternate = ["a"]) val passType: String = "MEETING",
    @SerializedName("title", alternate = ["b"]) val title: String,
    @SerializedName("primary_code", alternate = ["c"]) val primaryCode: String,
    @SerializedName("secondary_secret", alternate = ["d"]) val secondarySecret: String? = null,
    @SerializedName("provider_name", alternate = ["e"]) val providerName: String? = null,
    @SerializedName("action_url", alternate = ["f"]) val actionUrl: String? = null
)

@Keep
data class RecurrenceRuleData(
    @SerializedName("freq", alternate = ["a"]) val freq: String = "WEEKLY", // DAILY | WEEKLY | MONTHLY | YEARLY | INTERVAL_DAYS
    @SerializedName("interval", alternate = ["b"]) val interval: Int = 1,
    @SerializedName("days_of_week", alternate = ["c"]) val daysOfWeek: List<Int> = emptyList(), // 1=Mon..7=Sun
    @SerializedName("day_of_month", alternate = ["d"]) val dayOfMonth: Int? = null, // 1..31
    @SerializedName("end_type", alternate = ["e"]) val endType: String = "NEVER", // NEVER | AFTER_COUNT | UNTIL_DATE
    @SerializedName("remaining_count", alternate = ["f"]) val remainingCount: Int? = null,
    @SerializedName("until_epoch_ms", alternate = ["g"]) val untilEpochMs: Long? = null,
    @SerializedName("inherit_subtasks", alternate = ["h"]) val inheritSubTasks: Boolean = true,
    @SerializedName("occurrence_index", alternate = ["i"]) val occurrenceIndex: Int = 1,
    @SerializedName("is_paused", alternate = ["j"]) val isPaused: Boolean = false,
    @SerializedName("generated_next_task_id", alternate = ["k"]) val generatedNextTaskId: Long? = null
)

@Keep
data class VoiceNoteData(
    @SerializedName("file_path", alternate = ["a"]) val filePath: String = "",
    @SerializedName("file_uri", alternate = ["b"]) val fileUri: String? = null,
    @SerializedName("duration_ms", alternate = ["c"]) val durationMs: Long = 0L,
    @SerializedName("waveform_peaks", alternate = ["d"]) val waveformPeaks: List<Int> = emptyList(),
    @SerializedName("transcript", alternate = ["e"]) val transcript: String? = null,
    @SerializedName("label", alternate = ["f"]) val label: String? = null,
    @SerializedName("created_at_ms", alternate = ["g"]) val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * 将业务模型转换为数据库实体。
 * 未入库的临时负数 ID 会被统一归一化为 0L，交由 SQLite 自增主键生成。
 */
fun TaskComponent.toEntity(taskId: Long, createdAt: Long = System.currentTimeMillis()): TaskComponentEntity {
    val normalizedId = if (this.id > 0L) this.id else 0L
    return when (this) {
        is TaskComponent.AMapComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.AMAP_ROUTE,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.FileComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.FILE_ATTACHMENT,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.LinkComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.LINK_BOOKMARK,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.AccessPassComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.ACCESS_PASS,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.RecurrenceComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.RECURRENCE_RULE,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.VoiceNoteComponent -> {
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = ComponentType.VOICE_NOTE,
                dataJson = componentGson.toJson(this.data),
                createdAt = createdAt
            )
        }
        is TaskComponent.UnknownComponent -> {
            val resolvedType = ComponentType.entries.firstOrNull { it.name == rawType } ?: ComponentType.UNKNOWN
            TaskComponentEntity(
                id = normalizedId,
                taskId = taskId,
                type = resolvedType,
                dataJson = rawJson,
                createdAt = createdAt
            )
        }
    }
}

fun List<TaskComponentEntity>.toTaskComponentsSafe(): List<TaskComponent> =
    map { it.toTaskComponent() }
