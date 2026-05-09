package com.jarvisai.app.di

import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.service.JarvisAccessibilityService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccessibilityModule {

    @Provides
    fun provideAccessibilityHelper(): AccessibilityHelper? {
        return JarvisAccessibilityService.instance
    }
}
