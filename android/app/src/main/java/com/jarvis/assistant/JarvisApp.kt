package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.data.db.JarvisDatabase

class JarvisApp : Application() {
    val db by lazy { JarvisDatabase.get(this) }
}
