package com.litetask.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskTypeConverter

@Database(entities = [Task::class, SubTask::class, Reminder::class], version = 1, exportSchema = false)
@TypeConverters(TaskTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * 获取数据库单例
         * 用于在 BroadcastReceiver 等无法使用 Hilt 注入的地方获取数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "litetask_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 设置数据库实例（由 Hilt 模块调用）
         */
        fun setInstance(database: AppDatabase) {
            INSTANCE = database
        }
    }
}