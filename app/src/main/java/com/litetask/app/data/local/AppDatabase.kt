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
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.TaskTypeConverter
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.model.DailyScheduleAdviceEntity

@Database(
    entities = [
        Task::class, 
        SubTask::class, 
        Reminder::class, 
        AIHistory::class, 
        Category::class, 
        TaskComponentEntity::class,
        UserProfileHistoryEntity::class,
        UserLocationEntity::class,
        DailyScheduleAdviceEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(TaskTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun aiHistoryDao(): AIHistoryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun taskComponentDao(): TaskComponentDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userLocationDao(): UserLocationDao
    abstract fun dailyScheduleAdviceDao(): DailyScheduleAdviceDao
    
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
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 添加过期状态字段
                db.execSQL("ALTER TABLE tasks ADD COLUMN is_expired INTEGER NOT NULL DEFAULT 0")
                
                // 添加过期时间字段
                db.execSQL("ALTER TABLE tasks ADD COLUMN expired_at INTEGER")
                
                // 添加任务创建时间字段（使用start_time作为默认值）
                db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET created_at = start_time WHERE created_at = 0")
                
                // 添加任务完成时间字段
                db.execSQL("ALTER TABLE tasks ADD COLUMN completed_at INTEGER")
                
                // 为新字段添加索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_is_expired ON tasks(is_expired)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_created_at ON tasks(created_at)")
                
                // 将当前已过期但未完成的任务标记为过期状态
                val currentTime = System.currentTimeMillis()
                db.execSQL("""
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
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `source_type` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL,
                        `parsed_count` INTEGER NOT NULL DEFAULT 0,
                        `is_success` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_history_timestamp` ON `ai_history` (`timestamp`)")
            }
        }

        /**
         * 数据库迁移：从版本3到版本4
         * 引入 Category 表，迁移 TaskType
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. 创建 categories 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `color_hex` TEXT NOT NULL, 
                        `icon_name` TEXT NOT NULL DEFAULT 'default',
                        `is_default` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. 插入默认分类
                // WORK: #0B57D0 (蓝色)
                db.execSQL("INSERT INTO categories (id, name, color_hex, is_default) VALUES (1, '工作', '#0B57D0', 1)")
                // LIFE: #146C2E (绿色)
                db.execSQL("INSERT INTO categories (id, name, color_hex, is_default) VALUES (2, '生活', '#146C2E', 1)")
                // STUDY: #65558F (紫色)
                db.execSQL("INSERT INTO categories (id, name, color_hex, is_default) VALUES (3, '学习', '#65558F', 1)")
                // URGENT: #B3261E (红色)
                db.execSQL("INSERT INTO categories (id, name, color_hex, is_default) VALUES (4, '紧急', '#B3261E', 1)")

                // 3. 为 tasks 表添加 category_id 列，默认为 1 (工作)
                // 注意：这里必须指定 DEFAULT 值，否则现有数据会报错
                db.execSQL("ALTER TABLE tasks ADD COLUMN category_id INTEGER NOT NULL DEFAULT 1")
                
                // 4. 根据旧的 type 字段更新 category_id
                db.execSQL("UPDATE tasks SET category_id = 1 WHERE type = 'WORK'")
                db.execSQL("UPDATE tasks SET category_id = 2 WHERE type = 'LIFE'")
                db.execSQL("UPDATE tasks SET category_id = 3 WHERE type = 'STUDY'")
                db.execSQL("UPDATE tasks SET category_id = 4 WHERE type = 'URGENT'")
                
                // 5. 为 category_id 创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_category_id` ON `tasks` (`category_id`)")
            }
        }
        
        /**
         * 数据库迁移：从版本4到版本5
         * 添加任务组件表 (TaskComponent)
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_components` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `task_id` INTEGER NOT NULL, 
                        `component_type` TEXT NOT NULL, 
                        `data_payload` TEXT NOT NULL, 
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_components_task_id` ON `task_components` (`task_id`)")
            }
        }
        
        /**
         * 数据库迁移：从版本5到版本6
         * v6版本重构：剥离日程功能，添加核心画像快照表与物理空间映射表
         * 采用 Append-Only 模式记录每一次生成的画像结果
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. 画像快照历史表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `completion_rate` REAL NOT NULL,
                        `delayed_rate` REAL NOT NULL,
                        `input_preference` TEXT,
                        `category_focus` TEXT,
                        `identity` TEXT,
                        `industry` TEXT,
                        `travel_preference` TEXT,
                        `delay_index` INTEGER NOT NULL,
                        `plan_ability` INTEGER NOT NULL,
                        `stress_level` INTEGER NOT NULL,
                        `execution_rhythm` TEXT,
                        `ai_tone` TEXT,
                        `personality` TEXT
                    )
                """.trimIndent())
                
                // 2. 物理空间映射表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `origin_count` INTEGER NOT NULL DEFAULT 0,
                        `destination_count` INTEGER NOT NULL DEFAULT 0,
                        `last_visited_at` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        /**
         * 数据库迁移：从版本6到版本7
         * 添加每日日程建议表
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_schedule_advice` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `short_term_json` TEXT NOT NULL,
                        `long_term_json` TEXT NOT NULL,
                        `is_read` INTEGER NOT NULL DEFAULT 0,
                        `generation_status` TEXT NOT NULL DEFAULT 'GENERATING'
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_schedule_advice_created_at` ON `daily_schedule_advice` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_schedule_advice_generation_status` ON `daily_schedule_advice` (`generation_status`)")
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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