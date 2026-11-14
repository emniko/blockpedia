package com.emniko.blockpedia.ask

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI services with a free API tier, all reached through their OpenAI-compatible chat endpoint.
 * Free tiers change; [keyPage] is where the user creates a key and can see their limits.
 */
enum class AiProvider(val label: String, val baseUrl: String, val defaultModel: String, val keyPage: String, val note: String) {
    Groq(
        "Groq",
        "https://api.groq.com/openai/v1/",
        "openai/gpt-oss-120b",
        "https://console.groq.com/keys",
        "Free plan, no card needed: about 1,000 requests a day per model (each question uses two).",
    ),
    Gemini(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "gemini-2.5-flash",
        "https://aistudio.google.com/apikey",
        "Free tier limits depend on the model and can be small (see AI Studio). Google may use free-tier prompts to improve its products.",
    ),
    OpenRouter(
        "OpenRouter (free models)",
        "https://openrouter.ai/api/v1/",
        "openai/gpt-oss-120b:free",
        "https://openrouter.ai/keys",
        "Only models ending in :free are free, about 50 requests a day (each question uses two).",
    ),
}

/** Saved in the app's private storage on this phone only. */
class AskSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ask", Context.MODE_PRIVATE)

    data class Config(val provider: AiProvider, val apiKey: String, val model: String) {
        val ready: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()
    }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<Config> = _config.asStateFlow()

    private fun load(): Config {
        val provider = runCatching { AiProvider.valueOf(prefs.getString("provider", null) ?: "") }.getOrDefault(AiProvider.Groq)
        return Config(
            provider = provider,
            apiKey = prefs.getString("key_${provider.name}", "").orEmpty(),
            model = prefs.getString("model_${provider.name}", null) ?: provider.defaultModel,
        )
    }

    /** Switches provider, bringing back the key and model saved for it. */
    fun select(provider: AiProvider) {
        prefs.edit().putString("provider", provider.name).apply()
        _config.value = load()
    }

    fun save(apiKey: String, model: String) {
        val p = _config.value.provider
        prefs.edit()
            .putString("key_${p.name}", apiKey.trim())
            .putString("model_${p.name}", model.trim().ifEmpty { p.defaultModel })
            .apply()
        _config.value = load()
    }
}
