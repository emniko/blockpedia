package com.emniko.blockpedia.ask

import com.emniko.blockpedia.data.Article
import com.emniko.blockpedia.data.WikiRepository
import com.emniko.blockpedia.parse.ContentBlock
import com.emniko.blockpedia.parse.HeadingBlock
import com.emniko.blockpedia.parse.InfoboxBlock
import com.emniko.blockpedia.parse.TableBlock
import com.emniko.blockpedia.parse.blockText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A web page the answer used. */
data class WebSource(val title: String, val url: String)

/**
 * One chat message. [sources] are wiki page titles cited as [1], [2]...; [web] are pages found by
 * web search; [searches] are the web searches run (shown as Google searches for Gemini).
 */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<String> = emptyList(),
    val web: List<WebSource> = emptyList(),
    val searches: List<String> = emptyList(),
    val error: Boolean = false,
)

class AskException(message: String) : Exception(message)

/**
 * Answers Minecraft questions: the model first turns the question into wiki searches, the app
 * fetches the best Minecraft Wiki pages and keeps the sections that fit the question, then the
 * model answers from those excerpts, citing them as [1], [2]... With web search on, the answer
 * step can also search the web (Groq's compound models, or Google Search grounding on Gemini).
 */
class AskEngine(baseHttp: OkHttpClient, private val repo: WikiRepository) {

    private val http = baseHttp.newBuilder()
        .cache(null)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Progress shown while an answer is being prepared. */
    enum class Stage(val label: String) { Wiki("Reading the Minecraft Wiki…"), Web("Searching the web…"), Writing("Writing the answer…") }

    suspend fun answer(
        config: AskSettings.Config,
        history: List<ChatMessage>,
        question: String,
        onStage: (Stage) -> Unit = {},
    ): ChatMessage {
        if (!config.ready) throw AskException("Add an API key in the AI settings first.")
        onStage(Stage.Wiki)
        val queries = searchQueries(config, history, question)
        val pages = findPages(queries)
        val excerpts = coroutineScope {
            pages.map { title -> async { runCatching { excerpt(repo.article(title).article, question) }.getOrNull() } }.awaitAll()
        }.filterNotNull()
        val web = config.web && config.provider.webSearch
        if (excerpts.isEmpty() && !web) {
            return ChatMessage(false, "I couldn't find Minecraft Wiki pages about that. Try asking another way, or turn on web search in the AI settings.")
        }

        val sourceText = if (excerpts.isEmpty()) {
            "(No Minecraft Wiki pages were found for this question.)"
        } else {
            excerpts.mapIndexed { i, (title, text) -> "[${i + 1}] $title\n$text" }.joinToString("\n\n")
        }
        val system = if (web) ANSWER_PROMPT_WEB else ANSWER_PROMPT
        val turns = history.filterNot { it.error }.takeLast(6)
        val user = "Minecraft Wiki excerpts:\n\n$sourceText\n\nQuestion: $question"
        val wikiTitles = excerpts.map { it.first }

        onStage(if (web) Stage.Web else Stage.Writing)
        return when {
            web && config.provider == AiProvider.Gemini -> geminiGrounded(config, system, turns, user, wikiTitles)
            web && config.provider == AiProvider.Groq -> groqCompound(config, system, turns, user, wikiTitles)
            else -> {
                val messages = JSONArray().put(msg("system", system))
                turns.forEach { messages.put(msg(if (it.fromUser) "user" else "assistant", it.text)) }
                messages.put(msg("user", user))
                ChatMessage(false, chat(config, messages, maxTokens = 3000).trim(), wikiTitles)
            }
        }
    }

    /** Groq's compound systems search the web on their own; sources come back as executed tools. */
    private suspend fun groqCompound(
        config: AskSettings.Config,
        system: String,
        turns: List<ChatMessage>,
        user: String,
        wikiTitles: List<String>,
    ): ChatMessage {
        val messages = JSONArray().put(msg("system", system))
        turns.forEach { messages.put(msg(if (it.fromUser) "user" else "assistant", it.text)) }
        messages.put(msg("user", user))
        var lastError: AskException? = null
        for (model in listOf("groq/compound", "groq/compound-mini")) {
            try {
                val json = complete(config.copy(model = model), messages, maxTokens = 3000)
                val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                val text = message?.optString("content").orEmpty()
                if (text.isBlank()) continue
                val web = LinkedHashMap<String, WebSource>()
                val tools = message?.optJSONArray("executed_tools") ?: JSONArray()
                for (i in 0 until tools.length()) {
                    val results = tools.optJSONObject(i)?.optJSONObject("search_results")?.optJSONArray("results") ?: continue
                    for (j in 0 until results.length()) {
                        val r = results.optJSONObject(j) ?: continue
                        val url = r.optString("url")
                        if (url.startsWith("http")) web.getOrPut(url) { WebSource(r.optString("title").ifBlank { host(url) }, url) }
                    }
                }
                return ChatMessage(false, text.trim(), wikiTitles, web.values.take(6))
            } catch (e: AskException) {
                lastError = e
            }
        }
        throw lastError ?: AskException("Groq returned an empty answer.")
    }

    /** Gemini with Google Search grounding, through Gemini's own API (the OpenAI one has no search). */
    private suspend fun geminiGrounded(
        config: AskSettings.Config,
        system: String,
        turns: List<ChatMessage>,
        user: String,
        wikiTitles: List<String>,
    ): ChatMessage = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        fun part(role: String, text: String) = JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))
        turns.forEach { contents.put(part(if (it.fromUser) "user" else "model", it.text)) }
        contents.put(part("user", user))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 3000))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val model = config.model.removePrefix("models/")
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(body)
            .build()
        val json = send(config, request)
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AskException("Gemini returned no answer.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
        if (text.isBlank()) throw AskException("Gemini returned an empty answer.")
        val meta = candidate.optJSONObject("groundingMetadata")
        val chunks = meta?.optJSONArray("groundingChunks") ?: JSONArray()
        val web = (0 until chunks.length()).mapNotNull { i ->
            val w = chunks.optJSONObject(i)?.optJSONObject("web") ?: return@mapNotNull null
            val url = w.optString("uri")
            if (url.startsWith("http")) WebSource(w.optString("title").ifBlank { host(url) }, url) else null
        }.distinctBy { it.url }
        val queries = meta?.optJSONArray("webSearchQueries")
        val searches = (0 until (queries?.length() ?: 0)).map { queries!!.optString(it) }.filter { it.isNotBlank() }
        ChatMessage(false, text.trim(), wikiTitles, web.take(6), searches)
    }

    private fun host(url: String): String = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrDefault(url)

    /** Up to three wiki searches for the question, using the conversation for follow-ups. */
    private suspend fun searchQueries(config: AskSettings.Config, history: List<ChatMessage>, question: String): List<String> {
        val recent = history.takeLast(4).joinToString("\n") { (if (it.fromUser) "User: " else "Assistant: ") + it.text.take(300) }
        val messages = JSONArray()
            .put(msg("system", QUERY_PROMPT))
            .put(msg("user", (if (recent.isNotBlank()) "Conversation so far:\n$recent\n\n" else "") + "Question: $question"))
        // Reasoning models spend part of the budget thinking, so leave room for the reply itself.
        val reply = runCatching { chat(config, messages, maxTokens = 800) }.getOrElse { e ->
            if (e is AskException && e.message?.contains("empty answer") == true) question else throw e
        }
        val lines = reply.lines()
            .map { it.trim().trimStart('-', '*', '•', ' ').trim().trim('"') }
            .filter { it.isNotBlank() && it.length < 80 }
            .take(3)
        return lines.ifEmpty { listOf(question) }
    }

    private suspend fun findPages(queries: List<String>): List<String> = coroutineScope {
        val results = queries.map { q -> async { runCatching { repo.search(q).map { it.title } }.getOrDefault(emptyList()) } }.awaitAll()
        // Best hit of each search first, then second-best hits, up to four pages.
        val out = LinkedHashSet<String>()
        for (rank in 0 until 3) for (r in results) r.getOrNull(rank)?.let { out += it }
        out.take(4)
    }

    /** The page's intro plus the sections that share the most words with the question. */
    private fun excerpt(article: Article, question: String): Pair<String, String> {
        data class Section(val heading: String, val text: String)
        val sections = ArrayList<Section>()
        var heading = "Introduction"
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotBlank()) sections += Section(heading, sb.toString().trim())
            sb.setLength(0)
        }
        for (block in article.blocks) {
            if (block is HeadingBlock) {
                flush()
                heading = block.text
            } else {
                sb.append(aiText(block)).append('\n')
            }
        }
        flush()

        val words = question.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in STOP_WORDS }.toSet()
        fun score(s: Section): Int = words.count { w -> s.heading.lowercase().contains(w) } * 5 +
            words.sumOf { w -> Regex("\\b" + Regex.escape(w)).findAll(s.text.lowercase()).count().coerceAtMost(5) }

        val chosen = ArrayList<Section>()
        sections.firstOrNull()?.let { chosen += it }
        sections.drop(1).sortedByDescending { score(it) }.filter { score(it) > 0 }.forEach { chosen += it }
        val out = StringBuilder()
        for (s in chosen) {
            val piece = (if (s.heading == "Introduction") "" else "## ${s.heading}\n") + s.text
            if (out.length + piece.length > PAGE_BUDGET) {
                if (out.isEmpty()) out.append(piece.take(PAGE_BUDGET))
                break
            }
            out.append(piece).append("\n\n")
        }
        return article.title to out.toString().trim()
    }

    /** Plain text, keeping table rows and infobox fields on one line each. */
    private fun aiText(block: ContentBlock): String = when (block) {
        is TableBlock -> block.rows.joinToString("\n") { row ->
            row.cells.joinToString(" | ") { cell -> cell.blocks.joinToString(" ") { oneLine(blockText(it)) } }
        }
        is InfoboxBlock -> buildString {
            block.title?.let { append(it).append('\n') }
            block.rows.forEach { row ->
                val label = row.label?.text?.let(::oneLine).orEmpty()
                val value = row.value.joinToString(" ") { oneLine(blockText(it)) }
                append(if (label.isEmpty()) value else "$label: $value").append('\n')
            }
        }
        else -> blockText(block)
    }.replace("￼", "")

    private fun oneLine(s: String) = s.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    private fun msg(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    private suspend fun chat(config: AskSettings.Config, messages: JSONArray, maxTokens: Int): String {
        val json = complete(config, messages, maxTokens)
        return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw AskException("${config.provider.label} returned an empty answer.")
    }

    /** One OpenAI-style chat completion; returns the whole response. */
    private suspend fun complete(config: AskSettings.Config, messages: JSONArray, maxTokens: Int): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("max_tokens", maxTokens)
            .apply {
                // Keep thinking short where the service supports it: answers come faster.
                val thinking = when (config.provider) {
                    AiProvider.Groq -> "gpt-oss" in config.model
                    AiProvider.Gemini -> config.model.startsWith("gemini-2.5") || config.model.startsWith("gemini-3")
                    AiProvider.OpenRouter -> false
                }
                if (thinking) put("reasoning_effort", "low")
            }
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(config.provider.baseUrl + "chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        send(config, request)
    }

    private fun send(config: AskSettings.Config, request: Request): JSONObject {
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw AskException("Couldn't reach ${config.provider.label}. Check your connection.")
        }
        return response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AskException(errorMessage(config, it.code, text))
            runCatching { JSONObject(text) }.getOrNull() ?: throw AskException("Unexpected answer from ${config.provider.label}.")
        }
    }

    /** Model ids the provider offers, for the settings screen. */
    suspend fun models(config: AskSettings.Config): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.provider.baseUrl + "models")
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
        http.newCall(request).execute().use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AskException(errorMessage(config, it.code, text))
            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { i -> data.optJSONObject(i)?.optString("id")?.removePrefix("models/") }
                .filter { id -> config.provider != AiProvider.OpenRouter || id.endsWith(":free") }
                .sorted()
        }
    }

    private fun errorMessage(config: AskSettings.Config, code: Int, body: String): String {
        val detail = runCatching {
            val j = JSONObject(body)
            j.optJSONObject("error")?.optString("message") ?: j.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(200)
        return when (code) {
            401, 403 -> "${config.provider.label} rejected the API key. Check it in the AI settings."
            404 -> "${config.provider.label} doesn't know the model \"${config.model}\". Pick another in the AI settings."
            429 -> "Free limit reached on ${config.provider.label} for now. Try again later or switch provider."
            else -> "${config.provider.label} error $code" + (detail?.let { ": $it" } ?: "")
        }
    }

    private companion object {
        const val PAGE_BUDGET = 3500

        val STOP_WORDS = setOf(
            "the", "and", "for", "how", "what", "where", "when", "which", "who", "why", "can", "does", "you",
            "are", "get", "with", "from", "that", "this", "there", "minecraft", "into", "use", "make", "find",
        )

        const val QUERY_PROMPT = "You turn Minecraft questions into Minecraft Wiki searches. Reply with 1 to 3 " +
            "short search queries, one per line, most useful first, usually the names of the wiki pages that " +
            "would answer it (for example: Diamond Ore, Enchanting, Villager). For a follow-up question, use the " +
            "conversation to fill in what it refers to. No numbering, no explanations."

        private const val STYLE = "Write for a player on a phone: start with the direct answer in one or two " +
            "sentences, then add details only if they help, as a short bulleted or numbered list. Use Markdown: " +
            "**bold** for key items, numbers, coordinates and Y levels; a short ### heading only when the answer " +
            "has distinct parts. No tables. Mention Java and Bedrock differences when they matter."

        const val ANSWER_PROMPT = "You are Blockpedia's Minecraft assistant. Answer the user's question using only " +
            "the numbered Minecraft Wiki excerpts in their message, citing them like [1] or [2] right after the " +
            "fact they support. If the excerpts don't contain the answer, say the wiki pages found don't cover it " +
            "and don't guess. " + STYLE

        const val ANSWER_PROMPT_WEB = "You are Blockpedia's Minecraft assistant. Answer the user's question using the " +
            "numbered Minecraft Wiki excerpts in their message, citing them like [1] or [2] right after the fact " +
            "they support. Search the web when the excerpts don't fully answer it or the question needs current " +
            "information (updates, servers, mods, news); prefer the excerpts for game mechanics. Don't invent facts. " + STYLE
    }
}
