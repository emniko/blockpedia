package com.emniko.blockpedia.data

import com.emniko.blockpedia.parse.ContentBlock

data class Article(
    val title: String,
    val displayTitle: String,
    val blocks: List<ContentBlock>,
    val categories: List<String>,
    val toc: List<TocEntry>,
)

data class TocEntry(val blockIndex: Int, val level: Int, val text: String, val anchor: String?)

/** [offline] is true when the network failed and an older saved copy is shown instead. */
data class ArticleResult(val article: Article, val offline: Boolean)

data class SearchHit(val title: String, val snippet: String?)

data class CategoryPage(val titles: List<String>, val next: String?)

class WikiException(val code: String, message: String) : Exception(message)

fun normalizeTitle(title: String): String {
    val t = title.replace('_', ' ').trim()
    return if (t.isEmpty()) t else t.substring(0, 1).uppercase() + t.substring(1)
}

fun pageUrl(title: String): String =
    "https://minecraft.wiki/w/" + android.net.Uri.encode(title.replace(' ', '_'), "/:()_,")
