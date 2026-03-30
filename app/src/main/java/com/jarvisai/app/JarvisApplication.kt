package com.jarvisai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
