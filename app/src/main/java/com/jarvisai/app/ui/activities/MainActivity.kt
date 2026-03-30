package com.jarvisai.app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvisai.app.databinding.ActivityMainBinding
import com.jarvisai.app.utils.SecurePrefs
import com.jarvisai.app.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeState()

        if (SecurePrefs.isFirstLaunch(this)) {
            com.jarvisai.app.ui.onboarding.OnboardingBottomSheet().show(
                supportFragmentManager, "onboarding"
            )
        }
    }

    private fun setupUI() {
        binding.btnMore.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
            popup.menu.add("History")
            popup.menu.add("New Chat")
            popup.menu.add("Settings")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "History" -> binding.drawerLayout.openDrawer(GravityCompat.START)
                    "New Chat" -> startNewChat()
                    "Settings" -> startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            popup.show()
        }

        binding.btnClearHistory.setOnClickListener {
            viewModel.clearAllHistory()
        }

        // Adapter setup
        adapter = ChatAdapter()
        binding.recyclerMessages.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }

        historyAdapter = HistoryAdapter { session ->
            viewModel.loadSession(session.id)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.recyclerHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Input setup
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnMic.setOnClickListener { startVoiceInput() }
        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }
        
        // Vision setup
        binding.btnVision.setOnClickListener {
            captureAndAnalyzeScreen()
        }

        setupMemoryDashboard()
        startPulseAnimation()
    }

    private fun setupMemoryDashboard() {
        val modules = listOf(
            MemoryModuleAdapter.MemoryModule("CORE_IDENTITY", "🧠", "Who you are"),
            MemoryModuleAdapter.MemoryModule("SOCIAL_GRAPH", "👥", "People & history"),
            MemoryModuleAdapter.MemoryModule("BEHAVIORAL_INTELLIGENCE", "🧠", "Patterns & habits"),
            MemoryModuleAdapter.MemoryModule("KNOWLEDGE_BASE", "📚", "What you know"),
            MemoryModuleAdapter.MemoryModule("MEMORY_TIMELINE", "🕒", "Life history"),
            MemoryModuleAdapter.MemoryModule("PREFERENCES_ENGINE", "🎯", "Likes & dislikes"),
            MemoryModuleAdapter.MemoryModule("LIFE_OPERATIONS", "⚙️", "Execution"),
            MemoryModuleAdapter.MemoryModule("COMMUNICATIONS", "💬", "Conversations"),
            MemoryModuleAdapter.MemoryModule("DIGITAL_FOOTPRINT", "🌐", "Online behavior"),
            MemoryModuleAdapter.MemoryModule("DECISION_ENGINE", "🧠", "Thinking model"),
            MemoryModuleAdapter.MemoryModule("HEALTH_PROFILE", "❤️", "Stats & vitals"),
            MemoryModuleAdapter.MemoryModule("FINANCIAL_SYSTEM", "💰", "Budget & logic"),
            MemoryModuleAdapter.MemoryModule("SECURITY_VAULT", "🔐", "Encrypted assets"),
            MemoryModuleAdapter.MemoryModule("LEARNING_ENGINE", "🤖", "Self-improvement"),
            MemoryModuleAdapter.MemoryModule("CONTEXT_ENGINE", "🔄", "Real-time brain"),
            MemoryModuleAdapter.MemoryModule("SYSTEM_LOGS", "📊", "Debug logs")
        )

        val memoryAdapter = MemoryModuleAdapter(modules) { module ->
            android.widget.Toast.makeText(this, "Module ${module.name} active", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.recyclerMemoryModules.apply {
            adapter = memoryAdapter
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 4)
        }

        // Click on title to show/hide dashboard
        binding.layoutCenterTitle.setOnClickListener {
            toggleMemoryDashboard()
        }
    }

    private var isDashboardVisible = false
    private fun toggleMemoryDashboard() {
        isDashboardVisible = !isDashboardVisible
        val targetY = if (isDashboardVisible) 0f else binding.cardMemoryDashboard.height.toFloat()
        binding.cardMemoryDashboard.animate().translationY(-targetY).setDuration(500).start()
    }

    private fun startPulseAnimation() {
        val pulse = binding.statusPulse
        val anim = android.view.animation.AlphaAnimation(0.2f, 1.0f).apply {
            duration = 1500
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        pulse.startAnimation(anim)
    }

    private fun updateStatusPulse(isThinking: Boolean) {
        val statusColor = if (isThinking) {
            getColor(com.jarvisai.app.R.color.status_warning)
        } else {
            getColor(com.jarvisai.app.R.color.status_success)
        }
        binding.statusPulse.backgroundTintList = android.content.res.ColorStateList.valueOf(statusColor)
    }

    private fun captureAndAnalyzeScreen() {
        val bitmap = getScreenBitmap()
        if (bitmap != null) {
            val base64 = bitmapToBase64(bitmap)
            viewModel.analyzeScreen(base64)
            android.widget.Toast.makeText(this, "Screen Captured! Analyzing...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getScreenBitmap(): android.graphics.Bitmap? {
        val v = window.decorView.rootView
        v.isDrawingCacheEnabled = true
        val bitmap = android.graphics.Bitmap.createBitmap(v.drawingCache)
        v.isDrawingCacheEnabled = false
        return bitmap
    }

    private fun bitmapToBase64(bitmap: android.graphics.Bitmap): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }

    private fun sendMessage() {
        val text = binding.editMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        binding.editMessage.text?.clear()
        viewModel.sendMessage(text)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Messages
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                // Smooth scroll only if the list is already near bottom to prevent jitter
                                val layoutManager = binding.recyclerMessages.layoutManager as LinearLayoutManager
                                val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
                                if (lastVisible >= messages.size - 3) {
                                    binding.recyclerMessages.scrollToPosition(messages.size - 1)
                                }
                            }
                        }
                    }
                }
                // Observe Loading/Streaming (Thinking Indicator)
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.layoutThinking.visibility = 
                            if (loading) android.view.View.VISIBLE else android.view.View.GONE
                        updateStatusPulse(loading)
                    }
                }
                // Observe History Sessions in Sidebar
                launch {
                    viewModel.sessions.collect { sessions ->
                        historyAdapter.submitList(sessions)
                    }
                }
            }
        }
    }

    private fun startNewChat() {
        viewModel.startNewChat()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        binding.editMessage.requestFocus()
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis...")
        }
        speechLauncher.launch(intent)
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@registerForActivityResult
        binding.editMessage.setText(spokenText)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val provider = SecurePrefs.getProvider(this)
        if (provider.isNotEmpty() && provider != "UNKNOWN") {
            binding.textTitle.text = "Jarvis"
        } else {
            binding.textTitle.text = "Jarvis (Offline)"
        }
    }
}
