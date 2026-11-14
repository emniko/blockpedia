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

data class ChatMessage(val fromUser: Boolean, val text: String, val sources: List<String> = emptyList())

class AskException(message: String) : Exception(message)

/**
 * Answers Minecraft questions from the Minecraft Wiki: the model first turns the question into
 * wiki searches, the app fetches the best pages and keeps the sections that fit the question,
 * then the model answers from those excerpts only, citing them as [1], [2]...
 */
class AskEngine(baseHttp: OkHttpClient, private val repo: WikiRepository) {

    private val http = baseHttp.newBuilder()
        .cache(null)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun answer(config: AskSettings.Config, history: List<ChatMessage>, question: String): ChatMessage {
        if (!config.ready) throw AskException("Add an API key in the AI settings first.")
        val queries = searchQueries(config, history, question)
        val pages = findPages(queries)
        if (pages.isEmpty()) {
            return ChatMessage(false, "I couldn't find any Minecraft Wiki pages about that. Try asking in a different way.")
        }
        val excerpts = coroutineScope {
            pages.map { title -> async { runCatching { excerpt(repo.article(title).article, question) }.getOrNull() } }.awaitAll()
        }.filterNotNull()
        if (excerpts.isEmpty()) throw AskException("Couldn't load the wiki pages. Check your connection.")

        val sourceText = excerpts.mapIndexed { i, (title, text) -> "[${i + 1}] $title\n$text" }.joinToString("\n\n")
        val messages = JSONArray()
        messages.put(msg("system", ANSWER_PROMPT))
        history.takeLast(6).forEach { messages.put(msg(if (it.fromUser) "user" else "assistant", it.text)) }
        messages.put(msg("user", "Wiki excerpts:\n\n$sourceText\n\nQuestion: $question"))
        val text = chat(config, messages, maxTokens = 3000)
        return ChatMessage(false, text.trim(), excerpts.map { it.first })
    }

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

    private suspend fun chat(config: AskSettings.Config, messages: JSONArray, maxTokens: Int): String = withContext(Dispatchers.IO) {
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
                if (thinking) {
                    put("reasoning_effort", "low")
                }
            }
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(config.provider.baseUrl + "chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw AskException("Couldn't reach ${config.provider.label}. Check your connection.")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AskException(errorMessage(config, it.code, text))
            val json = runCatching { JSONObject(text) }.getOrNull() ?: throw AskException("Unexpected answer from ${config.provider.label}.")
            json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { c -> c.isNotBlank() }
                ?: throw AskException("${config.provider.label} returned an empty answer.")
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

        const val ANSWER_PROMPT = "You are Blockpedia's Minecraft assistant. Answer the user's question using only " +
            "the numbered Minecraft Wiki excerpts in their message. Cite the excerpts you use like [1] or [2]. " +
            "If the excerpts don't contain the answer, say that the wiki pages found don't cover it, and don't guess. " +
            "Mention Java and Bedrock differences when the excerpts give them. Be concise and practical: short " +
            "paragraphs or a short list, plain text without tables."
    }
}
