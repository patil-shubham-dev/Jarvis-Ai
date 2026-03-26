package com.jarvis.assistant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.core.prefs.Prefs
import com.jarvis.assistant.databinding.ActivityPermissionsBinding
import com.jarvis.assistant.services.JarvisAccessibilityService
import com.jarvis.assistant.ui.chat.ChatActivity

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.btnMic.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        binding.btnContacts.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 2)
        }
        binding.btnPhone.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 3)
        }
        binding.btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
            }
        }
        binding.btnA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnContinue.setOnClickListener {
            prefs.onboardingDone = true
            startActivity(Intent(this, ChatActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        updateBtn(binding.btnMic, isGranted(Manifest.permission.RECORD_AUDIO))
        updateBtn(binding.btnContacts, isGranted(Manifest.permission.READ_CONTACTS))
        updateBtn(binding.btnPhone, isGranted(Manifest.permission.CALL_PHONE))
        updateBtn(binding.btnNotif, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.POST_NOTIFICATIONS) else true)
        updateBtn(binding.btnA11y, JarvisAccessibilityService.isRunning())
    }

    private fun updateBtn(view: TextView, granted: Boolean) {
        view.text = if (granted) "Done" else if (view == binding.btnA11y) "Enable" else "Grant"
        view.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.status_online else R.color.text_on_accent)
        )
        view.background = ContextCompat.getDrawable(
            this, if (granted) R.drawable.bg_pill_surface else R.drawable.bg_pill_accent
        )
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
