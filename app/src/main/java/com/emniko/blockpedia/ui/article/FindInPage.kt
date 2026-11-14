package com.emniko.blockpedia.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.emniko.blockpedia.parse.ContentBlock
import com.emniko.blockpedia.parse.blockText

/** What "Find in page" is looking for, and whether the block being drawn holds the current match. */
data class FindState(val query: String = "", val current: Boolean = false)

val LocalFind = compositionLocalOf { FindState() }

private val MATCH = Color(0x66FFD54F)
private val CURRENT_MATCH = Color(0xCCFF9800)

/** Start offsets of [query] in [text], ignoring case. */
fun matchOffsets(text: String, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    val out = ArrayList<Int>()
    var i = text.indexOf(query, ignoreCase = true)
    while (i >= 0) {
        out += i
        i = text.indexOf(query, i + query.length, ignoreCase = true)
    }
    return out
}

/** Background for every match of the current search in [text] (offsets into the text). */
fun AnnotatedString.Builder.highlightMatches(text: String, find: FindState) {
    val style = SpanStyle(background = if (find.current) CURRENT_MATCH else MATCH)
    for (start in matchOffsets(text, find.query)) addStyle(style, start, start + find.query.length)
}

/** Plain text with the current search highlighted, for headings and other unstyled text. */
@Composable
fun highlighted(text: String): AnnotatedString {
    val find = LocalFind.current
    return remember(text, find) {
        buildAnnotatedString {
            append(text)
            highlightMatches(text, find)
        }
    }
}

/** One entry per match, in page order: the index of the block holding it. */
fun findMatches(blocks: List<ContentBlock>, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    val out = ArrayList<Int>()
    blocks.forEachIndexed { i, block -> repeat(matchOffsets(blockText(block), query).size) { out += i } }
    return out
}
