package com.jarvisai.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jarvisai.app.core.ai.LlmClient
import com.jarvisai.app.core.ai.OpenAILlmClient
import com.jarvisai.app.data.local.AppDatabase
import com.jarvisai.app.data.local.dao.ChatDao
import com.jarvisai.app.data.local.dao.ChatSessionDao
import com.jarvisai.app.data.local.dao.MemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /** LlmClient now uses raw OkHttp to support dynamic base URLs per provider */
    @Provides
    @Singleton
    fun provideLlmClient(okHttpClient: OkHttpClient): LlmClient {
        return OpenAILlmClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jarvis_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()
}
