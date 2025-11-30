package com.litetask.app.di

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.repository.AIRepositoryImpl
import com.litetask.app.data.repository.TaskRepositoryImpl
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
    fun provideTaskRepository(taskDao: TaskDao): TaskRepositoryImpl {
        return TaskRepositoryImpl(taskDao)
    }

    @Provides
    @Singleton
    fun provideAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository = aiRepositoryImpl
}