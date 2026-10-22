package com.litetask.app.data.model

import androidx.room.TypeConverter

class TaskTypeConverter {
    @TypeConverter
    fun fromTaskType(taskType: TaskType?): String? {
        return taskType?.name
    }

    @TypeConverter
    fun toTaskType(taskType: String?): TaskType? {
        return if (taskType == null) null else TaskType.valueOf(taskType)
    }

    @TypeConverter
    fun fromAIHistorySource(source: AIHistorySource?): String? {
        return source?.name
    }

    @TypeConverter
    fun toAIHistorySource(source: String?): AIHistorySource? {
        return if (source == null) null else AIHistorySource.valueOf(source)
    }

    @TypeConverter
    fun fromComponentType(type: ComponentType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toComponentType(value: String?): ComponentType? {
        if (value == null) return null
        return ComponentType.entries.firstOrNull { it.name == value } ?: ComponentType.UNKNOWN
    }
}