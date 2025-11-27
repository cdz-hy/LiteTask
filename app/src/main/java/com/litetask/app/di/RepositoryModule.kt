package com.litetask.app.di

import com.litetask.app.data.remote.OpenAIService
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.repository.AIRepositoryImpl
import com.litetask.app.data.repository.TaskRepository
import com.litetask.app.data.repository.TaskRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideOpenAIService(retrofit: Retrofit): OpenAIService {
        return retrofit.create(OpenAIService::class.java)
    }
}
