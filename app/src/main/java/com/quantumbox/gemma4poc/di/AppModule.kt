package com.quantumbox.gemma4poc.di

import android.content.Context
import androidx.room.Room
import com.quantumbox.gemma4poc.data.DownloadRepository
import com.quantumbox.gemma4poc.data.db.AppDatabase
import com.quantumbox.gemma4poc.data.db.ChatDao
import com.quantumbox.gemma4poc.data.db.SessionRepository
import com.quantumbox.gemma4poc.engine.GemmaEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
    ): DownloadRepository = DownloadRepository(context)

    @Provides
    @Singleton
    fun provideGemmaEngine(
        @ApplicationContext context: Context,
    ): GemmaEngine = GemmaEngine(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, "gemma4poc.db"
    ).build()

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideSessionRepository(chatDao: ChatDao): SessionRepository =
        SessionRepository(chatDao)
}
