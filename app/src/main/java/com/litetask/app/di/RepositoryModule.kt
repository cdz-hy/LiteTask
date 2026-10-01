package com.litetask.app.di

import android.content.Context
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.repository.AIRepositoryImpl
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.data.repository.CategoryRepository
import com.litetask.app.data.repository.CategoryRepositoryImpl
import com.litetask.app.reminder.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTaskRepository(
        @ApplicationContext context: Context,
        taskDao: TaskDao,
        categoryDao: CategoryDao,
        taskComponentDao: com.litetask.app.data.local.TaskComponentDao,
        reminderScheduler: ReminderScheduler,
        preferenceManager: PreferenceManager
    ): TaskRepositoryImpl {
        return TaskRepositoryImpl(
            context = context,
            taskDao = taskDao,
            categoryDao = categoryDao,
            taskComponentDao = taskComponentDao,
            reminderScheduler = reminderScheduler,
            preferenceManager = preferenceManager
        )
    }

    @Provides
    @Singleton
    fun provideAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository = aiRepositoryImpl

    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryRepositoryImpl: CategoryRepositoryImpl
    ): CategoryRepository = categoryRepositoryImpl
}