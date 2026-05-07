package com.jarvisai.app.api

import javax.inject.Singleton

/**
 * Detects the AI provider and available models based on the API key prefix or Base URL.
 * Supports OpenAI, Anthropic, Google Gemini, Groq, Mistral, OpenRouter, DeepSeek, Together AI, Nvidia, etc.
 * Each provider sets the correct base URL and required auth headers.
 */
object ModelDetector {

    data class ProviderInfo(
        val provider: Provider,
        val displayName: String,
        val models: List<ModelInfo>,
        val baseUrl: String,
        val authHeaderName: String = "Authorization",   // Default=Bearer
        val authHeaderValue: (String) -> String = { key -> "Bearer $key" },
        val extraHeaders: Map<String, String> = emptyMap(), // e.g. HTTP-Referer for OpenRouter
        var customBaseUrl: String? = null // Support for custom overrides
    ) {
        val effectiveBaseUrl: String
            get() = customBaseUrl?.takeIf { it.isNotBlank() } ?: baseUrl
    }

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val contextWindow: String,
        val isRecommended: Boolean = false,
        val isVision: Boolean = false
    )

    enum class Provider {
        OPENAI, ANTHROPIC, GOOGLE, GROQ, MISTRAL, OPENROUTER, DEEPSEEK, TOGETHER, SAMBANOVA, NVIDIA, PERPLEXITY, OLLAMA, DEEPINFRA, LM_STUDIO, UNKNOWN
    }

    fun getDisplayLabel(model: ModelInfo): String = "${model.displayName} (${model.contextWindow} ctx)"

    fun detect(apiKey: String, baseUrl: String? = null): ProviderInfo {
        val key = apiKey.trim()
        val url = baseUrl?.trim()?.lowercase().orEmpty()

        // 1. Detect by URL (useful for local models or custom endpoints)
        if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("ollama")) {
            return ollamaProvider().apply { customBaseUrl = baseUrl }
        }
        if (url.contains("deepinfra")) return deepInfraProvider().apply { customBaseUrl = baseUrl }

        // 2. Detect by API Key
        return when {
            key.startsWith("sk-or-v1-") || key.startsWith("sk-or-") -> openRouterProvider()
            key.startsWith("sk-proj-") || (key.startsWith("sk-") && !key.startsWith("sk-ant-") && !key.startsWith("sk-deepseek-") && !key.startsWith("sk-no-")) -> openAiProvider()
            key.startsWith("sk-ant-") -> anthropicProvider()
            key.startsWith("AIza") -> googleProvider()
            key.startsWith("gsk_") -> groqProvider()
            key.startsWith("sk-deepseek-") || key.startsWith("deepseek-") -> deepSeekProvider()
            key.startsWith("mi-") -> mistralProvider()
            key.startsWith("tog_") || key.startsWith("together_") -> togetherProvider()
            key.startsWith("nvapi-") -> nvidiaProvider()
            key.startsWith("pplx-") -> perplexityProvider()
            key.length == 36 && key.contains("-") -> sambaNovaProvider()
            else -> unknownProvider().apply { customBaseUrl = baseUrl }
        }
    }

    fun resolveModel(apiKey: String, savedValue: String?, isVision: Boolean = false): ModelInfo {
        val provider = detect(apiKey)
        val normalized = savedValue?.trim().orEmpty()

        val models = if (isVision) provider.models.filter { isVisionModel(it.id) }
                     else provider.models.filter { isReasoningModel(it.id) }

        return models.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
            ?: models.firstOrNull { getDisplayLabel(it).equals(normalized, ignoreCase = true) }
            ?: models.firstOrNull { it.displayName.equals(normalized, ignoreCase = true) }
            ?: models.firstOrNull { it.isRecommended }
            ?: models.firstOrNull()
            ?: provider.models.first()
    }

    fun isVisionModel(modelId: String): Boolean {
        val id = modelId.lowercase()
        // Must contain vision keywords or be a known high-end multimodal model
        return (id.contains("vision") || id.contains("4o") || id.contains("gemini-1.5") || 
               id.contains("claude-3") || id.contains("llava") || id.contains("pixtral")) && 
               !id.contains("embedding") && !id.contains("whisper") && !id.contains("tts")
    }

    fun isReasoningModel(modelId: String): Boolean {
        val id = modelId.lowercase()
        // Exclude vision-only models and media models. 
        // Most reasoning models can do text, but we want to prioritize "smart" chat/reasoning models.
        val isMedia = id.contains("vision") || id.contains("embedding") || id.contains("whisper") || id.contains("tts") || id.contains("dall-e")
        return !isMedia || id.contains("gpt-4o") || id.contains("claude-3") || id.contains("gemini-1.5")
    }

    // ── PROVIDERS ────────────────────────────────────────────────────────────

    private fun openRouterProvider() = ProviderInfo(
        provider = Provider.OPENROUTER,
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        extraHeaders = mapOf("HTTP-Referer" to "https://jarvis-ai.app", "X-Title" to "Jarvis Sentinel"),
        models = listOf(
            ModelInfo("openai/gpt-4o", "GPT-4o", "128K", isRecommended = true, isVision = true),
            ModelInfo("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "200K", isVision = true),
            ModelInfo("google/gemini-pro-1.5", "Gemini 1.5 Pro", "1M", isVision = true),
            ModelInfo("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B", "128K"),
            ModelInfo("deepseek/deepseek-chat", "DeepSeek V3", "64K")
        )
    )

    private fun openAiProvider() = ProviderInfo(
        provider = Provider.OPENAI,
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        models = listOf(
            ModelInfo("gpt-4o", "GPT-4o", "128K", isRecommended = true, isVision = true),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", "128K", isVision = true),
            ModelInfo("o1-preview", "o1 Preview", "128K"),
        )
    )

    private fun anthropicProvider() = ProviderInfo(
        provider = Provider.ANTHROPIC,
        displayName = "Anthropic",
        baseUrl = "https://api.anthropic.com/v1/",
        authHeaderName = "x-api-key",
        authHeaderValue = { key -> key },
        extraHeaders = mapOf("anthropic-version" to "2023-06-01"),
        models = listOf(
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "200K", isRecommended = true, isVision = true),
            ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "200K"),
        )
    )

    private fun googleProvider() = ProviderInfo(
        provider = Provider.GOOGLE,
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        models = listOf(
            ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "1M", isRecommended = true, isVision = true),
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "1M", isVision = true),
        )
    )

    private fun groqProvider() = ProviderInfo(
        provider = Provider.GROQ,
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        models = listOf(
            ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", "128K", isRecommended = true),
            ModelInfo("llama-3.2-90b-vision-preview", "Llama 3.2 90B Vision", "128K", isVision = true),
        )
    )

    private fun ollamaProvider() = ProviderInfo(
        provider = Provider.OLLAMA,
        displayName = "Ollama (Local)",
        baseUrl = "http://localhost:11434/v1/",
        models = listOf(
            ModelInfo("llama3", "Llama 3", "8K", isRecommended = true),
            ModelInfo("llava", "LLaVA (Vision)", "4K", isVision = true),
        )
    )

    private fun deepInfraProvider() = ProviderInfo(
        provider = Provider.DEEPINFRA,
        displayName = "DeepInfra",
        baseUrl = "https://api.deepinfra.com/v1/openai/",
        models = listOf(
            ModelInfo("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B", "128K", isRecommended = true),
        )
    )

    private fun deepSeekProvider() = ProviderInfo(
        provider = Provider.DEEPSEEK,
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/",
        models = listOf(
            ModelInfo("deepseek-chat", "DeepSeek V3", "64K", isRecommended = true),
            ModelInfo("deepseek-reasoner", "DeepSeek R1", "64K"),
        )
    )

    private fun mistralProvider() = ProviderInfo(
        provider = Provider.MISTRAL,
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1/",
        models = listOf(ModelInfo("mistral-large-latest", "Mistral Large", "128K", isRecommended = true))
    )

    private fun nvidiaProvider() = ProviderInfo(
        provider = Provider.NVIDIA,
        displayName = "Nvidia NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1/",
        models = listOf(ModelInfo("meta/llama-3.3-70b-instruct", "Llama 3.3 70B", "128K", isRecommended = true))
    )

    private fun togetherProvider() = ProviderInfo(
        provider = Provider.TOGETHER,
        displayName = "Together AI",
        baseUrl = "https://api.together.xyz/v1/",
        models = listOf(ModelInfo("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B", "128K", isRecommended = true))
    )

    private fun sambaNovaProvider() = ProviderInfo(
        provider = Provider.SAMBANOVA,
        displayName = "SambaNova",
        baseUrl = "https://api.sambanova.ai/v1/",
        models = listOf(ModelInfo("Meta-Llama-3.1-405B-Instruct", "Llama 3.1 405B", "128K", isRecommended = true))
    )

    private fun perplexityProvider() = ProviderInfo(
        provider = Provider.PERPLEXITY,
        displayName = "Perplexity",
        baseUrl = "https://api.perplexity.ai/",
        models = listOf(ModelInfo("llama-3.1-sonar-large-128k-online", "Sonar Large", "128K", isRecommended = true))
    )

    private fun unknownProvider() = ProviderInfo(
        provider = Provider.UNKNOWN,
        displayName = "Custom / OpenAI API",
        baseUrl = "https://api.openai.com/v1/",
        models = listOf(ModelInfo("gpt-4o-mini", "Default Model", "Unknown", isRecommended = true))
    )
}
