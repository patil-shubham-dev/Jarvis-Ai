package com.jarvis.assistant.ui.chat

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.assistant.databinding.ActivityChatBinding
import com.jarvis.assistant.services.JarvisListenerService
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == JarvisListenerService.ACTION_WAKE) startVoiceInput()
        }
    }

    private val waveBars by lazy {
        listOf(
            binding.wave1, binding.wave2, binding.wave3,
            binding.wave4, binding.wave5, binding.wave6, binding.wave7
        )
    }
    private val waveBaseHeightsDp = listOf(16, 28, 44, 56, 44, 28, 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        startListenerService()
        registerWakeReceiver()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter { text -> viewModel.speak(text) }
        binding.rvChat.apply {
            this.adapter = this@ChatActivity.adapter
            layoutManager = LinearLayoutManager(this@ChatActivity).also { it.stackFromEnd = true }
            itemAnimator = null
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.etInput.setText("")
                viewModel.send(text, fromVoice = false)
            }
        }

        binding.btnMic.setOnClickListener {
            if (viewModel.prefs.apiKey.isBlank()) {
                Toast.makeText(this, "Add your API key in Settings first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isListening) stopVoiceInput() else startVoiceInput()
        }

        binding.btnSpeaker.setOnClickListener {
            viewModel.ttsSessionEnabled = !viewModel.ttsSessionEnabled
            binding.btnSpeaker.setImageResource(
                if (viewModel.ttsSessionEnabled) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode
            )
            if (!viewModel.ttsSessionEnabled) viewModel.stopSpeaking()
        }

        binding.btnMemory.setOnClickListener {
            startActivity(Intent(this, MemoryActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnConfirmYes.setOnClickListener {
            viewModel.confirmPending()
            binding.confirmCard.visibility = View.GONE
        }

        binding.btnConfirmNo.setOnClickListener {
            viewModel.dismissPending()
            binding.confirmCard.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                adapter.submitList(messages.toList())
                if (messages.isNotEmpty()) {
                    binding.rvChat.post { binding.rvChat.scrollToPosition(messages.size - 1) }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                when (state) {
                    is ChatState.Idle -> {
                        binding.tvTyping.visibility = View.GONE
                        binding.tvStatus.text = "Ready"
                    }
                    is ChatState.Thinking -> {
                        binding.tvTyping.visibility = View.VISIBLE
                        binding.tvTyping.text = "Jarvis is thinking…"
                        binding.tvStatus.text = "Thinking…"
                    }
                    is ChatState.Streaming -> {
                        binding.tvTyping.visibility = View.GONE
                        binding.tvStatus.text = "Responding…"
                    }
                    is ChatState.Error -> {
                        binding.tvTyping.visibility = View.GONE
                        binding.tvStatus.text = "Error"
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pendingCommand.collectLatest { cmd ->
                if (cmd != null) {
                    binding.confirmCard.visibility = View.VISIBLE
                    binding.tvConfirmMsg.text = cmd.speech
                } else {
                    binding.confirmCard.visibility = View.GONE
                }
            }
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        isListening = true
        binding.waveContainer.visibility = View.VISIBLE

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rms: Float) = animateWave(rms)
            override fun onPartialResults(bundle: Bundle?) {
                val partial = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                binding.etInput.setText(partial?.firstOrNull() ?: "")
            }
            override fun onResults(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: ""
                stopVoiceInput()
                if (text.isNotEmpty()) {
                    binding.etInput.setText("")
                    viewModel.send(text, fromVoice = true)
                }
            }
            override fun onError(error: Int) {
                stopVoiceInput()
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(this@ChatActivity, "Speech error ($error)", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onReadyForSpeech(p: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onEndOfSpeech() = Unit
            override fun onBufferReceived(b: ByteArray?) = Unit
            override fun onEvent(t: Int, p: Bundle?) = Unit
        })
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceInput() {
        isListening = false
        binding.waveContainer.visibility = View.GONE
        binding.etInput.setText("")
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        waveBars.forEach { it.clearAnimation() }
    }

    private fun animateWave(rms: Float) {
        val density = resources.displayMetrics.density
        val scale = ((rms + 2f) / 10f).coerceIn(0.3f, 2.2f)
        waveBars.forEachIndexed { i, bar ->
            val targetPx = (waveBaseHeightsDp[i] * scale * density).toInt().coerceIn(
                8, (80 * density).toInt()
            )
            bar.layoutParams = bar.layoutParams.also { it.height = targetPx }
            bar.requestLayout()
        }
    }

    private fun startListenerService() {
        if (!JarvisListenerService.isRunning) {
            ContextCompat.startForegroundService(
                this, Intent(this, JarvisListenerService::class.java)
            )
        }
    }

    private fun registerWakeReceiver() {
        val filter = IntentFilter(JarvisListenerService.ACTION_WAKE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(wakeReceiver, filter)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        try { unregisterReceiver(wakeReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
