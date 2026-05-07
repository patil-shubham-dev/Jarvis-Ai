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
import androidx.lifecycle.lifecycleScope
import com.jarvisai.app.R
import com.jarvisai.app.api.ModelDetector
import com.jarvisai.app.databinding.ActivitySettingsBinding
import com.jarvisai.app.databinding.ItemSettingsToggleBinding
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var okHttpClient: OkHttpClient

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var rowOverlay: ItemSettingsToggleBinding
    private lateinit var rowAccessibility: ItemSettingsToggleBinding
    private lateinit var rowTts: ItemSettingsToggleBinding
    private lateinit var rowBiometric: ItemSettingsToggleBinding

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
        setupConfigActions()
        setupToggles()
        loadSavedValues()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRowLabels() {
        rowOverlay.apply {
            imgIcon.setImageResource(R.drawable.ic_overlay)
            textLabel.text = "Overlay Bubble"
            textSublabel.text = "Show floating Jarvis button"
            textSublabel.visibility = View.VISIBLE
        }
        rowAccessibility.apply {
            imgIcon.setImageResource(R.drawable.ic_accessibility)
            textLabel.text = "Accessibility"
            textSublabel.text = "Required for device control"
            textSublabel.visibility = View.VISIBLE
            textBadge.text = "Active"
            textBadge.setTextColor(getColor(R.color.status_success))
        }
        rowTts.apply {
            imgIcon.setImageResource(R.drawable.ic_notification)
            textLabel.text = "Voice Responses"
            textSublabel.text = "Speak aloud using TTS"
            textSublabel.visibility = View.VISIBLE
        }
        rowBiometric.apply {
            imgIcon.setImageResource(R.drawable.ic_fingerprint)
            textLabel.text = "Biometric Lock"
            textSublabel.text = "Secure Jarvis access"
            textSublabel.visibility = View.VISIBLE
        }
    }

    private fun setupConfigActions() {
        binding.btnFetchModels.setOnClickListener { fetchModels() }

        binding.btnSaveConfig.setOnClickListener {
            saveAllConfig()
            showToast("✓ Sentinel Configuration Applied")
        }

        binding.btnViewLogs.setOnClickListener {
            showToast("Debug Inspector coming soon in Sentinel V2.1")
        }
    }

    private fun fetchModels() {
        val apiKey = binding.editApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            showToast("Please enter an API Key first")
            return
        }

        // Improvement: Automatically detect provider and base URL from the key
        val providerInfo = ModelDetector.detect(apiKey)
        val baseUrl = providerInfo.baseUrl

        lifecycleScope.launch {
            val fetchedModels = withContext(Dispatchers.IO) {
                try {
                    val url = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
                    val request = Request.Builder()
                        .url(url)
                        .header(providerInfo.authHeaderName, providerInfo.authHeaderValue(apiKey))
                        .apply { providerInfo.extraHeaders.forEach { (k, v) -> header(k, v) } }
                        .build()
                    
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) return@withContext emptyList<String>()
                    
                    val json = JSONObject(body)
                    val data = json.getJSONArray("data")
                    val list = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        list.add(data.getJSONObject(i).getString("id"))
                    }
                    list.sorted()
                } catch (e: Exception) {
                    emptyList<String>()
                }
            }

            // Use fetched models if available, otherwise fallback to provider's internal list
            val modelsToDisplay = if (fetchedModels.isNotEmpty()) {
                fetchedModels.filter { ModelDetector.isReasoningModel(it) }
            } else {
                providerInfo.models.filter { ModelDetector.isReasoningModel(it.id) }.map { it.id }
            }

            if (modelsToDisplay.isNotEmpty()) {
                val reasoningAdapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_dropdown_item_1line, modelsToDisplay)
                binding.spinnerModel.setAdapter(reasoningAdapter)
                
                // Smart Preselection
                val bestReasoning = modelsToDisplay.firstOrNull { it == "gpt-4o" || it.contains("claude-3-5-sonnet") || it.contains("deepseek-chat") || it.contains("llama-3.3-70b") }
                                     ?: modelsToDisplay.firstOrNull()
                
                if (bestReasoning != null) {
                    binding.spinnerModel.setText(bestReasoning, false)
                }

                showToast("Detected ${providerInfo.displayName}. ${modelsToDisplay.size} models loaded.")
            } else {
                showToast("No reasoning models found for this key.")
            }
        }
    }

    private fun saveAllConfig() {
        val apiKey = binding.editApiKey.text.toString().trim()
        val providerInfo = ModelDetector.detect(apiKey)
        val baseUrl = providerInfo.baseUrl

        SecurePrefs.saveApiKey(this, apiKey)
        SecurePrefs.saveBaseUrl(this, baseUrl)
        SecurePrefs.saveSelectedModel(this, binding.spinnerModel.text.toString())
        SecurePrefs.saveProvider(this, providerInfo.provider.name)
    }

    private fun loadSavedValues() {
        binding.editApiKey.setText(SecurePrefs.getApiKey(this))
        binding.spinnerModel.setText(SecurePrefs.getSelectedModel(this) ?: "gpt-4o", false)

        isInternalUpdate = true
        rowTts.switchToggle.isChecked = SecurePrefs.isTtsEnabled(this)
        rowBiometric.switchToggle.isChecked = SecurePrefs.isBiometricEnabled(this)
        rowOverlay.switchToggle.isChecked = SecurePrefs.isOverlayEnabled(this) && Settings.canDrawOverlays(this)
        rowAccessibility.switchToggle.isChecked = isAccessibilityEnabled()
        isInternalUpdate = false
    }

    private fun setupToggles() {
        rowOverlay.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (checked && !Settings.canDrawOverlays(this)) {
                rowOverlay.switchToggle.isChecked = false
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                SecurePrefs.saveOverlayEnabled(this, checked)
                val intent = Intent(this, com.jarvisai.app.service.JarvisOverlayService::class.java)
                if (checked) startForegroundService(intent) else stopService(intent)
            }
        }

        rowAccessibility.root.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        rowTts.switchToggle.setOnCheckedChangeListener { _, checked -> SecurePrefs.saveTtsEnabled(this, checked) }
        rowBiometric.switchToggle.setOnCheckedChangeListener { _, checked -> SecurePrefs.saveBiometricEnabled(this, checked) }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/com.jarvisai.app.service.JarvisAccessibilityService"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
