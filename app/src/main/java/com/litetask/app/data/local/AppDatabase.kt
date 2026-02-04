package com.litetask.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.TaskTypeConverter

@Database(entities = [Task::class, SubTask::class, Reminder::class, AIHistory::class], version = 3, exportSchema = false)
@TypeConverters(TaskTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun aiHistoryDao(): AIHistoryDao
    
    companion object {
        // 数据库名称必须与 DatabaseModule 中的一致
        private const val DATABASE_NAME = "litetask.db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * 数据库迁移：从版本1到版本2
         * 添加新的任务状态字段
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 添加过期状态字段
                database.execSQL("ALTER TABLE tasks ADD COLUMN is_expired INTEGER NOT NULL DEFAULT 0")
                
                // 添加过期时间字段
                database.execSQL("ALTER TABLE tasks ADD COLUMN expired_at INTEGER")
                
                // 添加任务创建时间字段（使用start_time作为默认值）
                database.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE tasks SET created_at = start_time WHERE created_at = 0")
                
                // 添加任务完成时间字段
                database.execSQL("ALTER TABLE tasks ADD COLUMN completed_at INTEGER")
                
                // 为新字段添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_is_expired ON tasks(is_expired)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_created_at ON tasks(created_at)")
                
                // 将当前已过期但未完成的任务标记为过期状态
                val currentTime = System.currentTimeMillis()
                database.execSQL("""
                    UPDATE tasks SET 
                        is_expired = 1,
                        expired_at = $currentTime
                    WHERE deadline < $currentTime 
                    AND is_done = 0 
                    AND is_expired = 0
                """)
            }
        }

        /**
         * 数据库迁移：从版本2到版本3
         * 添加 AI 分析历史表
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `source_type` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL,
                        `parsed_count` INTEGER NOT NULL DEFAULT 0,
                        `is_success` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_history_timestamp` ON `ai_history` (`timestamp`)")
            }
        }
        
        /**
         * 获取数据库单例
         * 用于在 BroadcastReceiver 等无法使用 Hilt 注入的地方获取数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // 添加迁移
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
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