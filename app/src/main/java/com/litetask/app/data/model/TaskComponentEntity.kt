package com.litetask.app.data.model

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 任务组件实体类
 *
 * 使用多态存储不同类型的组件数据。
 * 核心设计：component_type + data_payload (JSON)
 */
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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @ColumnInfo(name = "component_type")
    val type: ComponentType,

    @ColumnInfo(name = "data_payload")
    val dataJson: String, // 存储具体组件数据的 JSON 字符串

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

data class AMapRouteData(
    @SerializedName("start_name") val startName: String = "我的位置",
    @SerializedName("end_name") val endName: String,
    @SerializedName("end_address") val endAddress: String?,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("strategy") val strategy: Int = 0 // 0: 速度优先, 2: 距离优先
)

data class FileAttachmentData(
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_uri") val fileUri: String, // 持久化 URI
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("file_size") val fileSize: Long
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
