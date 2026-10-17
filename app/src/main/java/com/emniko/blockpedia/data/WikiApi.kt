package com.emniko.blockpedia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

/** Thin client for the Minecraft Wiki's MediaWiki Action API. */
class WikiApi(private val client: OkHttpClient) {

    /** Returns the raw JSON of `action=parse`, so it can be cached as-is. */
    suspend fun parsePageRaw(title: String): String = getRaw(
        mapOf(
            "action" to "parse",
            "page" to title,
            "prop" to "text|displaytitle|categories",
            "redirects" to "1",
            "disableeditsection" to "1",
            "disabletoc" to "1",
            "disablelimitreport" to "1",
        )
    )

    suspend fun search(query: String): List<SearchHit> {
        val json = get(
            mapOf(
                "action" to "query",
                "list" to "prefixsearch|search",
                "pssearch" to query,
                "pslimit" to "8",
                "psnamespace" to "0",
                "srsearch" to query,
                "srlimit" to "30",
                "srnamespace" to "0",
                "srprop" to "snippet",
            )
        )
        val q = json.optJSONObject("query") ?: return emptyList()
        val hits = LinkedHashMap<String, SearchHit>()
        q.optJSONArray("prefixsearch")?.let { arr ->
            for (i in 0 until arr.length()) {
                val title = arr.getJSONObject(i).optString("title")
                if (title.isNotBlank() && title !in hits) hits[title] = SearchHit(title, null)
            }
        }
        q.optJSONArray("search")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                if (title.isBlank()) continue
                val snippet = Jsoup.parse(o.optString("snippet")).text().ifBlank { null }
                val existing = hits[title]
                if (existing == null || existing.snippet == null) hits[title] = SearchHit(title, snippet)
            }
        }
        return hits.values.toList()
    }

    suspend fun subcategories(category: String): List<String> {
        val all = mutableListOf<String>()
        var cont: String? = null
        do {
            val page = categoryMembers(category, "subcat", cont)
            all += page.titles.map { it.removePrefix("Category:") }
            cont = page.next
        } while (cont != null && all.size < 1000)
        return all
    }

    suspend fun categoryPages(category: String, cont: String?): CategoryPage =
        categoryMembers(category, "page", cont)

    private suspend fun categoryMembers(category: String, type: String, cont: String?): CategoryPage {
        val params = mutableMapOf(
            "action" to "query",
            "list" to "categorymembers",
            "cmtitle" to "Category:$category",
            "cmtype" to type,
            "cmlimit" to if (type == "subcat") "500" else "100",
            "cmprop" to "title",
        )
        if (cont != null) params["cmcontinue"] = cont
        val json = get(params)
        val arr = json.optJSONObject("query")?.optJSONArray("categorymembers")
        val titles = buildList {
            if (arr != null) for (i in 0 until arr.length()) add(arr.getJSONObject(i).optString("title"))
        }.filter { it.isNotBlank() }
        val next = json.optJSONObject("continue")?.optString("cmcontinue")?.ifBlank { null }
        return CategoryPage(titles, next)
    }

    suspend fun randomTitle(): String {
        val json = get(mapOf("action" to "query", "list" to "random", "rnnamespace" to "0", "rnlimit" to "1"))
        return json.getJSONObject("query").getJSONArray("random").getJSONObject(0).getString("title")
    }

    private suspend fun get(params: Map<String, String>): JSONObject = JSONObject(getRaw(params))

    private suspend fun getRaw(params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("format", "json")
            addQueryParameter("formatversion", "2")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty response")
            if (body.take(200).contains("\"error\":{")) {
                val err = JSONObject(body).optJSONObject("error")
                if (err != null) throw WikiException(err.optString("code"), err.optString("info", "Wiki error"))
            }
            body
        }
    }

    companion object {
        const val API_URL = "https://minecraft.wiki/api.php"
    }
}
