package com.litetask.app.di

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.repository.AIRepositoryImpl
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.reminder.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        reminderScheduler: ReminderScheduler
    ): TaskRepositoryImpl {
        return TaskRepositoryImpl(taskDao, reminderScheduler)
    }

    @Provides
    @Singleton
    fun provideAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository = aiRepositoryImpl
}