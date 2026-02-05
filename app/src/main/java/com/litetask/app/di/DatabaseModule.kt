package com.litetask.app.di

import android.content.Context
import androidx.room.Room
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "litetask.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)  // 添加迁移支持
            .fallbackToDestructiveMigration() // 允许在schema变化时重建数据库
            .build()
        
        // 设置单例，供 BroadcastReceiver 等无法使用 Hilt 的地方使用
        AppDatabase.setInstance(database)
        
        return database
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideAIHistoryDao(database: AppDatabase): com.litetask.app.data.local.AIHistoryDao {
        return database.aiHistoryDao()
    }
}
