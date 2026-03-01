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
    // 辅助方法：将 JSON 转为具体的组件数据对象
    fun toTaskComponent(): TaskComponent {
        val gson = Gson()
        return when (type) {
            ComponentType.AMAP_ROUTE -> {
                val data = gson.fromJson(dataJson, AMapRouteData::class.java)
                TaskComponent.AMapComponent(id, taskId, data)
            }
            ComponentType.FILE_ATTACHMENT -> {
                val data = gson.fromJson(dataJson, FileAttachmentData::class.java)
                TaskComponent.FileComponent(id, taskId, data)
            }
        }
    }
}

enum class ComponentType {
    AMAP_ROUTE,
    FILE_ATTACHMENT
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
    @SerializedName("strategy", alternate = ["g"]) val strategy: Int = 0 // 0: 速度优先, 2: 距离优先
)

@Keep
data class FileAttachmentData(
    @SerializedName("file_name", alternate = ["a"]) val fileName: String,
    @SerializedName("file_uri", alternate = ["b"]) val fileUri: String, // 持久化 URI
    @SerializedName("mime_type", alternate = ["c"]) val mimeType: String,
    @SerializedName("file_size", alternate = ["d"]) val fileSize: Long
)

/**
 * 将业务模型转换为数据库实体
 */
fun TaskComponent.toEntity(taskId: Long): TaskComponentEntity {
    val gson = Gson()
    return when (this) {
        is TaskComponent.AMapComponent -> {
            TaskComponentEntity(
                id = this.id,
                taskId = taskId,
                type = ComponentType.AMAP_ROUTE,
                dataJson = gson.toJson(this.data)
            )
        }
        is TaskComponent.FileComponent -> {
            TaskComponentEntity(
                id = this.id,
                taskId = taskId,
                type = ComponentType.FILE_ATTACHMENT,
                dataJson = gson.toJson(this.data)
            )
        }
    }
}
