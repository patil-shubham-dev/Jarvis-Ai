package com.jarvisai.app.core.ai

/**
 * Detects the AI provider and available models based on the API key prefix.
 * Supports OpenAI, Anthropic, Google Gemini, Groq, Mistral, OpenRouter, DeepSeek, Together AI.
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
        val extraHeaders: Map<String, String> = emptyMap() // e.g. HTTP-Referer for OpenRouter
    )

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val contextWindow: String,
        val isRecommended: Boolean = false
    )

    enum class Provider {
        OPENAI, ANTHROPIC, GOOGLE, GROQ, MISTRAL, OPENROUTER, DEEPSEEK, TOGETHER, SAMBANOVA, UNKNOWN
    }

    fun detect(apiKey: String): ProviderInfo {
        val key = apiKey.trim()
        return when {
            key.startsWith("sk-or-v1-") || key.startsWith("sk-or-") -> openRouterProvider()
            key.startsWith("sk-proj-") || (key.startsWith("sk-") && !key.startsWith("sk-ant-")) -> openAiProvider()
            key.startsWith("sk-ant-") -> anthropicProvider()
            key.startsWith("AIza") -> googleProvider()
            key.startsWith("gsk_") -> groqProvider()
            key.startsWith("sk-deepseek-") || key.startsWith("deepseek-") -> deepSeekProvider()
            key.startsWith("mi-") -> mistralProvider()
            key.startsWith("tog_") || key.startsWith("together_") -> togetherProvider()
            key.length == 36 && key.contains("-") -> sambaNovaProvider() // Standard UUID key format
            else -> unknownProvider()
        }
    }

    // ── PROVIDERS ────────────────────────────────────────────────────────────

    private fun openRouterProvider() = ProviderInfo(
        provider = Provider.OPENROUTER,
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        authHeaderValue = { key -> "Bearer $key" },
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/patil-shubham-dev",
            "X-Title" to "Jarvis AI"
        ),
        models = listOf(
            ModelInfo("openai/gpt-4o",                                 "GPT-4o (OpenRouter)",         "128K", isRecommended = true),
            ModelInfo("openai/gpt-4o-mini",                            "GPT-4o Mini",                 "128K"),
            ModelInfo("anthropic/claude-3.5-sonnet",                   "Claude 3.5 Sonnet",           "200K"),
            ModelInfo("anthropic/claude-3.5-haiku",                    "Claude 3.5 Haiku",            "200K"),
            ModelInfo("google/gemini-pro-1.5",                         "Gemini 1.5 Pro",              "1M"),
            ModelInfo("meta-llama/llama-3.3-70b-instruct",             "Llama 3.3 70B",              "128K"),
            ModelInfo("mistralai/mixtral-8x7b-instruct",               "Mixtral 8x7B",               "32K"),
            ModelInfo("deepseek/deepseek-chat",                        "DeepSeek V3",                 "64K"),
        )
    )

    private fun openAiProvider() = ProviderInfo(
        provider = Provider.OPENAI,
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        models = listOf(
            ModelInfo("gpt-4o",           "GPT-4o",        "128K", isRecommended = true),
            ModelInfo("gpt-4o-mini",      "GPT-4o Mini",   "128K"),
            ModelInfo("gpt-4-turbo",      "GPT-4 Turbo",   "128K"),
            ModelInfo("gpt-3.5-turbo",    "GPT-3.5 Turbo", "16K"),
        )
    )

    private fun anthropicProvider() = ProviderInfo(
        provider = Provider.ANTHROPIC,
        displayName = "Anthropic",
        baseUrl = "https://api.anthropic.com/v1/",
        authHeaderName = "x-api-key",
        authHeaderValue = { key -> key },   // Anthropic sends raw key (no "Bearer")
        extraHeaders = mapOf("anthropic-version" to "2023-06-01"),
        models = listOf(
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "200K", isRecommended = true),
            ModelInfo("claude-3-5-haiku-20241022",  "Claude 3.5 Haiku",  "200K"),
            ModelInfo("claude-3-opus-20240229",      "Claude 3 Opus",     "200K"),
        )
    )

    private fun googleProvider() = ProviderInfo(
        provider = Provider.GOOGLE,
        displayName = "Google AI",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/",
        models = listOf(
            ModelInfo("gemini-1.5-pro",   "Gemini 1.5 Pro",   "1M",  isRecommended = true),
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "1M"),
            ModelInfo("gemini-pro",       "Gemini Pro",        "32K"),
        )
    )

    private fun groqProvider() = ProviderInfo(
        provider = Provider.GROQ,
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        models = listOf(
            ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B",  "128K", isRecommended = true),
            ModelInfo("llama-3.1-8b-instant",    "Llama 3.1 8B",   "128K"),
            ModelInfo("mixtral-8x7b-32768",      "Mixtral 8x7B",   "32K"),
        )
    )

    private fun deepSeekProvider() = ProviderInfo(
        provider = Provider.DEEPSEEK,
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/",
        models = listOf(
            ModelInfo("deepseek-chat",    "DeepSeek V3",       "64K", isRecommended = true),
            ModelInfo("deepseek-coder",   "DeepSeek Coder",    "64K"),
        )
    )

    private fun mistralProvider() = ProviderInfo(
        provider = Provider.MISTRAL,
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1/",
        models = listOf(
            ModelInfo("mistral-large-latest", "Mistral Large", "128K", isRecommended = true),
            ModelInfo("mistral-small-latest", "Mistral Small", "128K"),
        )
    )

    private fun togetherProvider() = ProviderInfo(
        provider = Provider.TOGETHER,
        displayName = "Together AI",
        baseUrl = "https://api.together.xyz/v1/",
        models = listOf(
            ModelInfo("meta-llama/Llama-3-70b-chat-hf", "Llama 3 70B", "8K", isRecommended = true),
            ModelInfo("mistralai/Mixtral-8x7B-Instruct-v0.1", "Mixtral 8x7B", "32K"),
        )
    )

    private fun sambaNovaProvider() = ProviderInfo(
        provider = Provider.SAMBANOVA,
        displayName = "SambaNova",
        baseUrl = "https://api.sambanova.ai/v1/",
        models = listOf(
            ModelInfo("Meta-Llama-3.1-405B-Instruct", "Llama 3.1 405B", "128K", isRecommended = true),
            ModelInfo("Meta-Llama-3.1-70B-Instruct",  "Llama 3.1 70B",  "128K"),
            ModelInfo("Meta-Llama-3.1-8B-Instruct",   "Llama 3.1 8B",   "128K")
        )
    )

    private fun unknownProvider() = ProviderInfo(
        provider = Provider.UNKNOWN,
        displayName = "Unknown / Custom",
        baseUrl = "https://api.openai.com/v1/",  // Fallback to OpenAI format for self-hosted
        models = listOf(
            ModelInfo("gpt-4o-mini", "Default Model", "Unknown", isRecommended = true),
        )
    )
}
