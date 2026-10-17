package com.emniko.blockpedia.data

import android.util.LruCache
import com.emniko.blockpedia.parse.HeadingBlock
import com.emniko.blockpedia.parse.WikiHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

class WikiRepository(
    private val api: WikiApi,
    private val cache: PageCache,
    private val parser: WikiHtmlParser = WikiHtmlParser(),
) {
    private val memory = LruCache<String, Article>(24)

    /**
     * Cache-first: a saved copy younger than [FRESH_MS] is shown straight away. Otherwise the
     * page is fetched; if the network fails, an older saved copy is used and flagged offline.
     */
    suspend fun article(title: String, forceRefresh: Boolean = false): ArticleResult {
        val key = normalizeTitle(title)
        if (!forceRefresh) memory.get(key)?.let { return ArticleResult(it, offline = false) }

        val cached = withContext(Dispatchers.IO) { cache.read(key) }
        if (!forceRefresh && cached != null && cached.ageMs < FRESH_MS) {
            val article = runCatching { build(cached.body) }.getOrNull()
            if (article != null) {
                remember(key, article)
                return ArticleResult(article, offline = false)
            }
        }

        return try {
            val raw = api.parsePageRaw(title)
            val article = build(raw)
            withContext(Dispatchers.IO) {
                cache.write(key, raw)
                val resolved = normalizeTitle(article.title)
                if (resolved != key) cache.write(resolved, raw)
            }
            remember(key, article)
            ArticleResult(article, offline = false)
        } catch (e: IOException) {
            if (cached == null) throw e
            val article = build(cached.body)
            remember(key, article)
            ArticleResult(article, offline = true)
        }
    }

    suspend fun search(query: String): List<SearchHit> = api.search(query)

    suspend fun subcategories(category: String): List<String> = api.subcategories(category)

    suspend fun categoryPages(category: String, cont: String?): CategoryPage = api.categoryPages(category, cont)

    suspend fun randomTitle(): String = api.randomTitle()

    fun clearMemory() = memory.evictAll()

    private fun remember(key: String, article: Article) {
        memory.put(key, article)
        memory.put(normalizeTitle(article.title), article)
    }

    private suspend fun build(raw: String): Article = withContext(Dispatchers.Default) {
        val parse = JSONObject(raw).getJSONObject("parse")
        val title = parse.getString("title")
        val displayTitle = parse.optString("displaytitle")
            .let { if (it.isBlank()) title else Jsoup.parse(it).text().ifBlank { title } }
        val blocks = parser.parse(parse.optString("text"))
        val categories = parse.optJSONArray("categories")?.let { arr ->
            (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("hidden", false) }
                .map { it.optString("category").replace('_', ' ') }
                .filter { it.isNotBlank() }
        } ?: emptyList()
        val toc = blocks.mapIndexedNotNull { index, block ->
            (block as? HeadingBlock)?.takeIf { it.level <= 3 }?.let { TocEntry(index, it.level, it.text, it.anchor) }
        }
        Article(title, displayTitle, blocks, categories, toc)
    }

    private companion object {
        const val FRESH_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
    }
}
