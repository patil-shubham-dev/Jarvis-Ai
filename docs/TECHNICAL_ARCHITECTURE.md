# JARVIS AI — Technical Architecture

This document provides a deep dive into the architecture, design patterns, and implementation details of Jarvis AI.

---

## Architecture Overview

Jarvis operates on a **four-layer architecture**:

```
┌─────────────────────────────────────────────────────────┐
│  Layer C: User Interface (Activities, Adapters, Layouts)│
├─────────────────────────────────────────────────────────┤
│  Layer B: Intelligence (Agents, LLM Clients, Memory)    │
├─────────────────────────────────────────────────────────┤
│  Layer A: Data (Room DB, Encrypted Prefs, JSON Storage) │
├─────────────────────────────────────────────────────────┤
│  Layer D: Automation (Accessibility Service, Intents)   │
└─────────────────────────────────────────────────────────┘
```

---

## Layer A: Data Management

### Room Database

Stores chat messages and sessions with efficient queries:

```kotlin
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

### Encrypted Shared Preferences

Stores sensitive data using Android's EncryptedSharedPreferences:

```kotlin
object SecurePrefs {
    fun saveApiKey(context: Context, key: String) {
        val encryptedPrefs = EncryptedSharedPreferences.create(
            context, "jarvis_secure", 
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        encryptedPrefs.edit().putString("api_key", key).apply()
    }
}
```

### JSON Memory Modules

Sixteen modules stored as JSON files in app-specific storage:

```
/data/data/com.jarvisai.app/files/jarvis_memory/
├── CORE_IDENTITY.json
├── SOCIAL_GRAPH.json
├── BEHAVIORAL_INTELLIGENCE.json
├── KNOWLEDGE_BASE.json
├── MEMORY_TIMELINE.json
├── PREFERENCES_ENGINE.json
├── LIFE_OPERATIONS.json
├── COMMUNICATIONS.json
├── DIGITAL_FOOTPRINT.json
├── DECISION_ENGINE.json
├── HEALTH_PROFILE.json
├── FINANCIAL_SYSTEM.json
├── SECURITY_VAULT.json
├── LEARNING_ENGINE.json
├── CONTEXT_ENGINE.json
└── SYSTEM_LOGS.json
```

### Vector Memory Store

Embeddings are computed locally or via API and stored for semantic search:

```kotlin
class VectorMemoryStore @Inject constructor(
    private val llmClient: LlmClient,
    private val securePrefs: SecurePrefs
) {
    suspend fun store(text: String, module: String) {
        val embedding = llmClient.getEmbeddings(
            apiKey = securePrefs.getApiKey(),
            text = text,
            model = "text-embedding-3-small"
        )
        // Store embedding + metadata in local database
    }

    suspend fun search(query: String, limit: Int = 5): List<String> {
        val queryEmbedding = llmClient.getEmbeddings(
            apiKey = securePrefs.getApiKey(),
            text = query,
            model = "text-embedding-3-small"
        )
        // Compute cosine similarity and return top-k results
    }
}
```

---

## Layer B: Intelligence System

### Multi-Agent Orchestration

Three specialized agents work in concert:

#### PlannerAgent

Analyzes user intent and selects the appropriate action:

```kotlin
@Singleton
class PlannerAgent @Inject constructor(
    private val llmClient: LlmClient
) {
    fun getToolDefinitions(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "read_screen")
                    put("description", "Read the current screen content")
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "click_element")
                    put("description", "Click a UI element at coordinates")
                })
            })
            // ... more tools
        }
    }

    suspend fun processIntent(response: String): Map<String, Any>? {
        // Parse tool calls from LLM response
        // Execute tools autonomously
        // Return results
    }
}
```

#### MemoryAgent

Manages semantic recall and memory persistence:

```kotlin
@Singleton
class MemoryAgent @Inject constructor(
    private val memoryManager: MemoryManager,
    private val vectorMemoryStore: VectorMemoryStore
) {
    suspend fun recallSemanticContext(query: String): String {
        val snippets = vectorMemoryStore.search(query, limit = 5)
        return buildString {
            append("--- RECALLED MEMORIES ---\n")
            snippets.forEach { append("- $it\n") }
        }
    }

    suspend fun memorize(source: String, text: String, module: String) {
        vectorMemoryStore.store(text, module)
        memoryManager.saveToJson(module, "observation.json", mapOf(
            "source" to source,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
```

#### CommunicationAgent

Refines responses and manages the Jarvis persona:

```kotlin
@Singleton
class CommunicationAgent @Inject constructor() {
    fun buildSystemPrompt(recalledMemory: String, currentContext: String): String {
        return """
            Role: You are JARVIS, a high-intelligence personal OS.
            Objective: Manage a 16-module Persistent Memory System.
            Personality: Professional, sophisticated, minimalist, concise, and proactive.
            
            [MEMORY MODULES]
            CORE_IDENTITY, SOCIAL_GRAPH, BEHAVIORAL_INTELLIGENCE, ...
            
            [CONTEXT]
            Recalled Memory: $recalledMemory
            Device Context: $currentContext
        """.trimIndent()
    }
}
```

### LLM Client (Universal AI Provider Support)

Supports OpenAI, Anthropic, Groq, Mistral, and Nvidia:

```kotlin
@Singleton
class OpenAILlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val plannerAgent: PlannerAgent
) : LlmClient {
    override fun getCompletionStream(
        apiKey: String,
        prompt: String,
        systemContext: String,
        model: String
    ): Flow<String> = callbackFlow {
        val provider = ModelDetector.detect(apiKey)
        val url = "${provider.baseUrl}/chat/completions"
        
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemContext))
                put(JSONObject().put("role", "user").put("content", prompt))
            })
            put("tools", plannerAgent.getToolDefinitions())
            put("tool_choice", "auto")
        }
        
        // Stream response via EventSource
    }
}
```

### Model Detection

Auto-detects provider and available models:

```kotlin
object ModelDetector {
    fun detect(apiKey: String): ProviderInfo {
        return when {
            apiKey.startsWith("sk-") && apiKey.length > 40 -> {
                // OpenAI
                ProviderInfo(
                    provider = Provider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    models = listOf(
                        ModelInfo("gpt-4o", "GPT-4 Omni", 128000, true),
                        ModelInfo("gpt-4o-mini", "GPT-4 Mini", 128000, false)
                    )
                )
            }
            apiKey.startsWith("sk-ant-") -> {
                // Anthropic
                ProviderInfo(
                    provider = Provider.ANTHROPIC,
                    baseUrl = "https://api.anthropic.com",
                    models = listOf(
                        ModelInfo("claude-3-5-sonnet", "Claude 3.5 Sonnet", 200000, true)
                    )
                )
            }
            // ... more providers
        }
    }
}
```

---

## Layer C: User Interface

### MainActivity

Central hub for chat interactions:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeMessages()
        setupInputHandling()
    }

    private fun setupUI() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        startPulseAnimation()
        setupMemoryDashboard()
    }

    private fun setupInputHandling() {
        binding.btnSend.setOnClickListener {
            val message = binding.editMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(message)
                binding.editMessage.text.clear()
            }
        }
    }
}
```

### ChatAdapter

Renders messages with Markdown support:

```kotlin
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {
    private var markwon: Markwon? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AssistantViewHolder -> {
                val message = getItem(position)
                // Hide tool-call JSON from user
                val displayContent = if (message.content.startsWith("{") && message.content.contains("tool_calls")) {
                    "Processing system actions..."
                } else {
                    message.content
                }
                markwon?.setMarkdown(holder.binding.textMessage, displayContent)
            }
        }
    }
}
```

### Theme System

Claude-inspired warm palette:

```xml
<!-- colors.xml -->
<color name="bg_main">#FFFBF7</color>
<color name="bg_surface">#FFF5F0</color>
<color name="jarvis_primary">#6366F1</color>
<color name="jarvis_accent">#EC4899</color>
<color name="text_primary">#1F2937</color>
<color name="text_hint">#9CA3AF</color>
```

---

## Layer D: Device Automation

### Accessibility Service

Reads screen and executes device actions:

```kotlin
class JarvisAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Log screen changes
        // Capture foreground app
        // Detect UI elements
    }

    fun readScreen(): String {
        val rootNode = rootInActiveWindow ?: return ""
        return buildString {
            traverseNode(rootNode, this)
        }
    }

    fun clickElement(x: Int, y: Int) {
        performGlobalAction(GLOBAL_ACTION_CLICK)
    }
}
```

### Action Engine

Executes autonomous actions:

```kotlin
@Singleton
class ActionEngine @Inject constructor(
    private val context: Context,
    private val accessibilityService: JarvisAccessibilityService
) {
    suspend fun executeAction(action: String, params: Map<String, String>): String {
        return when (action) {
            "open_app" -> openApp(params["package_name"] ?: "")
            "read_screen" -> accessibilityService.readScreen()
            "click_element" -> {
                val x = params["x"]?.toIntOrNull() ?: 0
                val y = params["y"]?.toIntOrNull() ?: 0
                accessibilityService.clickElement(x, y)
                "Clicked at ($x, $y)"
            }
            "send_message" -> sendMessage(params["app"] ?: "", params["message"] ?: "")
            "search_web" -> searchWeb(params["query"] ?: "")
            else -> "Unknown action: $action"
        }
    }

    private fun openApp(packageName: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            context.startActivity(intent)
            "Opened $packageName"
        } else {
            "App not found: $packageName"
        }
    }
}
```

---

## Data Flow

### Chat Message Flow

```
User Input
    ↓
ChatViewModel.sendMessage()
    ↓
ChatRepository.listenToResponse()
    ↓
MemoryAgent.recallSemanticContext() ← Semantic Search
    ↓
CommunicationAgent.buildSystemPrompt()
    ↓
OpenAILlmClient.getCompletionStream() ← AI Generation with Tool Support
    ↓
PlannerAgent.processIntent() ← Tool Execution
    ↓
ChatAdapter displays response
    ↓
MemoryAgent.memorize() ← Memory Update
```

---

## Performance Optimizations

1. **Lazy Loading:** Memory modules are loaded on-demand.
2. **Vector Caching:** Embeddings are cached to avoid redundant API calls.
3. **Coroutine Dispatchers:** Heavy operations run on `Dispatchers.IO`.
4. **RecyclerView Optimization:** DiffUtil for efficient list updates.
5. **Streaming:** Token-by-token streaming for responsive UI.

---

## Security Considerations

1. **API Key Encryption:** All keys stored using EncryptedSharedPreferences.
2. **No External Logging:** Logs are only stored locally in SYSTEM_LOGS module.
3. **Biometric Protection:** Optional fingerprint/face unlock.
4. **Minimal Permissions:** Only requests necessary permissions.

---

## Extension Points

### Adding New Tools

Edit `PlannerAgent.kt` and add to `getToolDefinitions()`:

```kotlin
put(JSONObject().apply {
    put("type", "function")
    put("function", JSONObject().apply {
        put("name", "my_new_tool")
        put("description", "What this tool does")
        put("parameters", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("param1", JSONObject().apply {
                    put("type", "string")
                    put("description", "First parameter")
                })
            })
        })
    })
})
```

Then handle in `ActionEngine.kt`.

### Adding New Memory Modules

Add a new JSON file in the memory directory and reference it in `MemoryAgent.kt`.

---

## Testing

```bash
./gradlew test                  # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

---

**This architecture prioritizes privacy, intelligence, and professional-grade device automation.**
