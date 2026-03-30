package com.jarvisai.app.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvisai.app.R
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.databinding.ActivitySettingsBinding
import com.jarvisai.app.databinding.ItemSettingsToggleBinding
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var rowOverlay: ItemSettingsToggleBinding
    private lateinit var rowAccessibility: ItemSettingsToggleBinding
    private lateinit var rowTts: ItemSettingsToggleBinding
    private lateinit var rowBiometric: ItemSettingsToggleBinding

    private var detectedModels: List<ModelDetector.ModelInfo> = emptyList()
    private var isInternalUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rowOverlay      = binding.rowOverlay
        rowAccessibility = binding.rowAccessibility
        rowTts          = binding.rowTts
        rowBiometric    = binding.rowBiometric

        setupToolbar()
        setupRowLabels()
        setupApiKey()
        setupToggles()
        setupAbout()
        loadSavedValues()
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh accessibility state when user returns from system settings
        val isEnabled = isAccessibilityEnabled()
        isInternalUpdate = true
        rowAccessibility.switchToggle.isChecked = isEnabled
        isInternalUpdate = false
        rowAccessibility.textBadge.visibility = if (isEnabled) View.VISIBLE else View.GONE
        rowAccessibility.textSublabel.text =
            if (isEnabled) getString(R.string.accessibility_active)
            else getString(R.string.accessibility_inactive)
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // ── Row Labels & Icons ────────────────────────────────────────────────────
    private fun setupRowLabels() {
        rowOverlay.imgIcon.setImageResource(R.drawable.ic_overlay)
        rowOverlay.textLabel.text = getString(R.string.pref_overlay)
        rowOverlay.textSublabel.text = "Show floating Jarvis bubble on screen"
        rowOverlay.textSublabel.visibility = View.VISIBLE

        rowAccessibility.imgIcon.setImageResource(R.drawable.ic_accessibility)
        rowAccessibility.textLabel.text = getString(R.string.pref_accessibility)
        rowAccessibility.textSublabel.text = getString(R.string.accessibility_inactive)
        rowAccessibility.textSublabel.visibility = View.VISIBLE
        rowAccessibility.textBadge.text = "Active"
        rowAccessibility.textBadge.setTextColor(getColor(R.color.status_success))

        rowTts.imgIcon.setImageResource(R.drawable.ic_notification)
        rowTts.textLabel.text = getString(R.string.pref_tts)
        rowTts.textSublabel.text = "Jarvis speaks responses aloud"
        rowTts.textSublabel.visibility = View.VISIBLE

        rowBiometric.imgIcon.setImageResource(R.drawable.ic_fingerprint)
        rowBiometric.textLabel.text = getString(R.string.pref_biometric)
        rowBiometric.textSublabel.text = "Required on every app launch"
        rowBiometric.textSublabel.visibility = View.VISIBLE
    }

    // ── API Key ───────────────────────────────────────────────────────────────
    private fun setupApiKey() {
        binding.editApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val key = s?.toString()?.trim() ?: return
                if (key.length >= 20) detectProvider(key)
                else resetProviderUI()
            }
        })

        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.editApiKey.text?.toString()?.trim() ?: ""
            if (key.length < 20) {
                showToast("Please enter a valid API key")
                return@setOnClickListener
            }
            val provider = ModelDetector.detect(key)
            if (provider.provider == ModelDetector.Provider.UNKNOWN) {
                showToast("Could not detect provider — key saved anyway")
            }
            SecurePrefs.saveApiKey(context = this, key = key)
            SecurePrefs.saveProvider(context = this, provider = provider.provider.name)

            // Save selected model if visible
            if (binding.layoutModelSelector.visibility == View.VISIBLE) {
                val selectedModel = binding.spinnerModel.text.toString()
                getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
                    .edit().putString("selected_model", selectedModel).apply()
            }

            showToast("✓ Configuration saved")
        }
    }

    private fun detectProvider(key: String) {
        val info = ModelDetector.detect(key)
        if (info.provider == ModelDetector.Provider.UNKNOWN) {
            resetProviderUI()
            return
        }

        detectedModels = info.models
        binding.textProviderBadge.text = info.displayName
        binding.textProviderBadge.setTextColor(getColor(R.color.jarvis_primary))

        val recommended = info.models.firstOrNull { it.isRecommended }
        binding.textDetectedModel.text =
            "Detected: ${info.displayName} · ${info.models.size} models available"
        binding.rowModelDetected.visibility = View.VISIBLE

        // Populate model spinner
        val modelNames = info.models.map { "${it.displayName} (${it.contextWindow} ctx)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modelNames)
        binding.spinnerModel.setAdapter(adapter)
        val defaultIdx = info.models.indexOfFirst { it.isRecommended }.takeIf { it >= 0 } ?: 0
        binding.spinnerModel.setText(modelNames.getOrNull(defaultIdx) ?: "", false)
        binding.layoutModelSelector.visibility = View.VISIBLE
    }

    private fun resetProviderUI() {
        detectedModels = emptyList()
        binding.textProviderBadge.text = "Not detected"
        binding.textProviderBadge.setTextColor(getColor(R.color.text_hint))
        binding.rowModelDetected.visibility = View.GONE
        binding.layoutModelSelector.visibility = View.GONE
    }

    // ── Toggles ───────────────────────────────────────────────────────────────
    private fun setupToggles() {
        // Overlay
        rowOverlay.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (checked && !Settings.canDrawOverlays(this)) {
                rowOverlay.switchToggle.isChecked = false
                openOverlayPermission()
            } else {
                SecurePrefs.saveOverlayEnabled(this, checked)
            }
        }

        // Accessibility
        rowAccessibility.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (isInternalUpdate) return@setOnCheckedChangeListener
            if (checked && !isAccessibilityEnabled()) {
                isInternalUpdate = true
                rowAccessibility.switchToggle.isChecked = false
                isInternalUpdate = false
                openAccessibilitySettings()
            }
        }
        // Tapping the whole row also opens settings (more discoverable)
        rowAccessibility.root.setOnClickListener {
            openAccessibilitySettings()
        }

        // TTS
        rowTts.switchToggle.setOnCheckedChangeListener { _, checked ->
            SecurePrefs.saveTtsEnabled(this, checked)
        }

        // Biometric
        rowBiometric.switchToggle.setOnCheckedChangeListener { _, checked ->
            SecurePrefs.saveBiometricEnabled(this, checked)
            if (checked) showToast("Biometric lock enabled — active on next launch")
        }
    }

    // ── About ─────────────────────────────────────────────────────────────────
    private fun setupAbout() {
        binding.textVersion.text = getString(R.string.version)
        binding.btnGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.github_url))))
        }
        binding.textCredits.text = getString(R.string.credits)
    }

    // ── Load saved values ─────────────────────────────────────────────────────
    private fun loadSavedValues() {
        val savedKey = SecurePrefs.getApiKey(this)
        if (savedKey.isNotEmpty()) {
            binding.editApiKey.setText(savedKey)
            detectProvider(savedKey)
        }
        isInternalUpdate = true
        rowTts.switchToggle.isChecked      = SecurePrefs.isTtsEnabled(this)
        rowBiometric.switchToggle.isChecked = SecurePrefs.isBiometricEnabled(this)
        rowOverlay.switchToggle.isChecked   = SecurePrefs.isOverlayEnabled(this) &&
                Settings.canDrawOverlays(this)
        val accEnabled = isAccessibilityEnabled()
        rowAccessibility.switchToggle.isChecked = accEnabled
        rowAccessibility.textBadge.visibility = if (accEnabled) View.VISIBLE else View.GONE
        isInternalUpdate = false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        showToast("Enable 'Appear on top' for Jarvis overlay")
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        showToast("Enable 'Jarvis AI' under Installed Services")
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/com.jarvisai.app.service.JarvisAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
