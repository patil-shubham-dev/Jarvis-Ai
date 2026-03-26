package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.core.prefs.Prefs
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import com.jarvis.assistant.services.JarvisAccessibilityService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val models = listOf(
        "claude-opus-4-5"           to "Claude Opus 4.5  —  Smartest",
        "claude-sonnet-4-5"         to "Claude Sonnet 4.5  —  Balanced",
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5  —  Fastest"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        setupModelSpinner()
        populateFields()
        setupClickListeners()
    }

    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models.map { it.second }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerModel.adapter = adapter
        val savedIndex = models.indexOfFirst { it.first == prefs.model }.coerceAtLeast(0)
        binding.spinnerModel.setSelection(savedIndex)
    }

    private fun populateFields() {
        binding.etApiKey.setText(prefs.apiKey)
        binding.switchTts.isChecked = prefs.ttsEnabled
        binding.seekSpeed.progress = speedToProgress(prefs.ttsSpeed)
        binding.seekPitch.progress = pitchToProgress(prefs.ttsPitch)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.apiKey    = key
            prefs.model     = models[binding.spinnerModel.selectedItemPosition].first
            prefs.ttsEnabled = binding.switchTts.isChecked
            prefs.ttsSpeed  = progressToSpeed(binding.seekSpeed.progress)
            prefs.ttsPitch  = progressToPitch(binding.seekPitch.progress)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnOpenA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'Jarvis Phone Control' and enable it", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val active = JarvisAccessibilityService.isRunning()
        binding.tvA11yStatus.text = if (active) "Active" else "Not enabled"
        binding.tvA11yStatus.setTextColor(
            getColor(if (active) com.jarvis.assistant.R.color.status_online
                     else com.jarvis.assistant.R.color.status_error)
        )
    }

    // progress 0–100 ↔ speed 0.5–2.0
    private fun speedToProgress(speed: Float) = ((speed - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
    private fun progressToSpeed(p: Int) = 0.5f + p / 100f * 1.5f

    // progress 0–100 ↔ pitch 0.5–2.0
    private fun pitchToProgress(pitch: Float) = ((pitch - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
    private fun progressToPitch(p: Int) = 0.5f + p / 100f * 1.5f
}
