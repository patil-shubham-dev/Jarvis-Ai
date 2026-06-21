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
import androidx.core.content.ContextCompat
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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
    private var detectedProvider: ModelDetector.ProviderInfo? = null

    private val testClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rowOverlay = binding.rowOverlay
        rowAccessibility = binding.rowAccessibility
        rowTts = binding.rowTts
        rowBiometric = binding.rowBiometric

        setupToolbar()
        setupRowLabels()
        setupApiKeyAutoDetect()
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
            textBadge.text = if (isAccessibilityEnabled()) "Active" else "Off"
            textBadge.setTextColor(getColor(
                if (isAccessibilityEnabled()) R.color.status_success else R.color.text_hint
            ))
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

    private fun setupApiKeyAutoDetect() {
        binding.editApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val key = s?.toString()?.trim().orEmpty()
                if (key.isNotBlank()) {
                    detectedProvider = ModelDetector.detect(key)
                    updateProviderBadge()
                } else {
                    detectedProvider = null
                    binding.providerBadge.visibility = View.GONE
                    binding.testStatus.visibility = View.GONE
                }
            }
        })
    }

    private fun updateProviderBadge() {
        val provider = detectedProvider ?: return
        binding.providerBadge.visibility = View.VISIBLE

        val initials = provider.displayName.take(2).uppercase()
        val bgColorRes = when (provider.provider) {
            ModelDetector.Provider.OPENAI -> R.color.provider_openai
            ModelDetector.Provider.ANTHROPIC -> R.color.provider_anthropic
            ModelDetector.Provider.GOOGLE -> R.color.provider_google
            ModelDetector.Provider.GROQ -> R.color.provider_groq
            ModelDetector.Provider.OLLAMA -> R.color.provider_ollama
            ModelDetector.Provider.OPENROUTER -> R.color.provider_openrouter
            ModelDetector.Provider.DEEPSEEK -> R.color.provider_deepseek
            else -> R.color.provider_unknown
        }

        val bgColor = ContextCompat.getColor(this, bgColorRes)
        binding.providerBadge.setCardBackgroundColor(bgColor)
        binding.providerIcon.visibility = View.GONE
        binding.providerInitials.visibility = View.VISIBLE
        binding.providerInitials.text = initials
        binding.providerInitials.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        binding.providerName.text = provider.displayName
    }

    private fun setupConfigActions() {
        binding.btnFetchModels.setOnClickListener { fetchModels() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSaveConfig.setOnClickListener {
            saveAllConfig()
            showToast("Configuration saved")
        }
        binding.btnViewLogs.setOnClickListener {
            showToast("Debug Inspector coming soon")
        }
    }

    private fun fetchModels() {
        val apiKey = binding.editApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            binding.editApiKey.error = "Enter your API key first"
            return
        }

        val providerInfo = ModelDetector.detect(apiKey)
        val baseUrl = providerInfo.effectiveBaseUrl

        binding.btnFetchModels.isEnabled = false
        binding.btnFetchModels.text = "Loading models..."

        lifecycleScope.launch {
            val fetchedModels = withContext(Dispatchers.IO) {
                try {
                    val url = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
                    val request = Request.Builder()
                        .url(url)
                        .header(providerInfo.authHeaderName, providerInfo.authHeaderValue(apiKey))
                        .apply { providerInfo.extraHeaders.forEach { (k, v) -> header(k, v) } }
                        .build()

                    val response = testClient.newCall(request).execute()
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

            binding.btnFetchModels.isEnabled = true
            binding.btnFetchModels.text = "Fetch Models"

            val modelsToDisplay = if (fetchedModels.isNotEmpty()) {
                fetchedModels.filter { ModelDetector.isReasoningModel(it) }
            } else {
                providerInfo.models.filter { ModelDetector.isReasoningModel(it.id) }.map { it.id }
            }

            if (modelsToDisplay.isNotEmpty()) {
                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_dropdown_item_1line, modelsToDisplay)
                binding.spinnerModel.setAdapter(adapter)

                val bestModel = modelsToDisplay.firstOrNull {
                    it == "gpt-4o" || it.contains("claude-3-5-sonnet") ||
                    it.contains("deepseek-chat") || it.contains("llama-3.3-70b")
                } ?: modelsToDisplay.firstOrNull()

                if (bestModel != null) {
                    binding.spinnerModel.setText(bestModel, false)
                }

                showToast("${providerInfo.displayName}: ${modelsToDisplay.size} models loaded")
            } else {
                showToast("No models found for this provider")
            }
        }
    }

    private fun testConnection() {
        val apiKey = binding.editApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            binding.editApiKey.error = "Enter your API key first"
            return
        }

        val providerInfo = ModelDetector.detect(apiKey)
        val baseUrl = providerInfo.effectiveBaseUrl
        val model = binding.spinnerModel.text.toString().trim().ifEmpty { providerInfo.models.firstOrNull()?.id ?: "gpt-4o-mini" }

        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "Testing..."
        binding.testStatus.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = if (providerInfo.provider == ModelDetector.Provider.ANTHROPIC) {
                        "$baseUrl/messages"
                    } else {
                        "$baseUrl/chat/completions"
                    }

                    val bodyJson = if (providerInfo.provider == ModelDetector.Provider.ANTHROPIC) {
                        JSONObject().apply {
                            put("model", model)
                            put("max_tokens", 10)
                            put("messages", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", "Say 'ok'")
                                })
                            })
                        }
                    } else {
                        JSONObject().apply {
                            put("model", model)
                            put("max_tokens", 10)
                            put("messages", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", "Say 'ok'")
                                })
                            })
                        }
                    }

                    val request = Request.Builder()
                        .url(url)
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .header(providerInfo.authHeaderName, providerInfo.authHeaderValue(apiKey))
                        .apply { providerInfo.extraHeaders.forEach { (k, v) -> header(k, v) } }
                        .build()

                    val response = testClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    Pair(response.isSuccessful, body.take(100))
                } catch (e: Exception) {
                    Pair(false, e.message ?: "Connection failed")
                }
            }

            binding.btnTestConnection.isEnabled = true
            binding.btnTestConnection.text = "Test Connection"
            binding.testStatus.visibility = View.VISIBLE

            if (result.first) {
                binding.testStatus.text = "Connected: ${providerInfo.displayName} ($model)"
                binding.testStatus.setTextColor(getColor(R.color.status_success))
                SecurePrefs.saveLastVerified(this@SettingsActivity, System.currentTimeMillis())
            } else {
                binding.testStatus.text = "Failed: ${result.second}"
                binding.testStatus.setTextColor(getColor(R.color.status_error))
            }
        }
    }

    private fun saveAllConfig() {
        val apiKey = binding.editApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            binding.editApiKey.error = "API key is required"
            showToast("Please enter your API key first")
            return
        }

        val providerInfo = ModelDetector.detect(apiKey)
        val baseUrl = providerInfo.effectiveBaseUrl
        val selectedModel = binding.spinnerModel.text.toString().trim()

        SecurePrefs.saveApiKey(this, apiKey)
        SecurePrefs.saveBaseUrl(this, baseUrl)
        SecurePrefs.saveProvider(this, providerInfo.provider.name)
        SecurePrefs.saveSelectedModel(this, selectedModel.ifBlank { providerInfo.models.first().id })

        showToast("Ready: ${providerInfo.displayName} $selectedModel")
    }

    private fun loadSavedValues() {
        val savedKey = SecurePrefs.getApiKey(this)
        binding.editApiKey.setText(savedKey)

        if (savedKey.isNotBlank()) {
            detectedProvider = ModelDetector.detect(savedKey)
            updateProviderBadge()
        }

        val savedModel = SecurePrefs.getSelectedModel(this)
        binding.spinnerModel.setText(savedModel ?: "gpt-4o-mini", false)

        val lastVerified = SecurePrefs.getLastVerified(this)
        if (lastVerified > 0) {
            val minutes = (System.currentTimeMillis() - lastVerified) / 60000
            if (minutes < 60) {
                binding.testStatus.visibility = View.VISIBLE
                binding.testStatus.text = "Last verified ${minutes}m ago"
                binding.testStatus.setTextColor(getColor(R.color.status_success))
            }
        }

        isInternalUpdate = true
        rowTts.switchToggle.isChecked = SecurePrefs.isTtsEnabled(this)
        rowBiometric.switchToggle.isChecked = SecurePrefs.isBiometricEnabled(this)
        rowOverlay.switchToggle.isChecked = SecurePrefs.isOverlayEnabled(this) && Settings.canDrawOverlays(this)
        rowAccessibility.switchToggle.isChecked = isAccessibilityEnabled()
        isInternalUpdate = false
    }

    private fun setupToggles() {
        rowOverlay.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (isInternalUpdate) return@setOnCheckedChangeListener
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

        rowTts.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (!isInternalUpdate) SecurePrefs.saveTtsEnabled(this, checked)
        }
        rowBiometric.switchToggle.setOnCheckedChangeListener { _, checked ->
            if (!isInternalUpdate) SecurePrefs.saveBiometricEnabled(this, checked)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/com.jarvisai.app.service.JarvisAccessibilityService"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
