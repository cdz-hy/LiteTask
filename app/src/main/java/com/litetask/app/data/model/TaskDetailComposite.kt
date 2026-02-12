package com.litetask.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

// 复合数据类 (用于详情页)
data class TaskDetailComposite(
    @Embedded val task: Task,
    
    @Relation(parentColumn = "id", entityColumn = "task_id")
    val subTasks: List<SubTask>,
    
    @Relation(parentColumn = "id", entityColumn = "task_id")
    val reminders: List<Reminder>,

    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category?
)