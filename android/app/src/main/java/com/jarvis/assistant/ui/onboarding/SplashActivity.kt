package com.jarvis.assistant.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.core.prefs.Prefs
import com.jarvis.assistant.ui.chat.ChatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val next = if (prefs.onboardingDone) ChatActivity::class.java else PermissionsActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
