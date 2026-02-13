package com.litetask.app.di

import android.content.Context
import androidx.room.Room
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.CategoryDao
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
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideAIHistoryDao(database: AppDatabase): com.litetask.app.data.local.AIHistoryDao {
        return database.aiHistoryDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideTaskComponentDao(database: AppDatabase): com.litetask.app.data.local.TaskComponentDao {
        return database.taskComponentDao()
    }
}
