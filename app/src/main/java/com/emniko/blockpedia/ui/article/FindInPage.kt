package com.emniko.blockpedia.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.emniko.blockpedia.parse.CalloutBlock
import com.emniko.blockpedia.parse.CodeBlock
import com.emniko.blockpedia.parse.ContentBlock
import com.emniko.blockpedia.parse.GalleryBlock
import com.emniko.blockpedia.parse.HeadingBlock
import com.emniko.blockpedia.parse.ImageBlock
import com.emniko.blockpedia.parse.IndentBlock
import com.emniko.blockpedia.parse.InfoboxBlock
import com.emniko.blockpedia.parse.ListBlock
import com.emniko.blockpedia.parse.ParagraphBlock
import com.emniko.blockpedia.parse.QuoteBlock
import com.emniko.blockpedia.parse.SoundBlock
import com.emniko.blockpedia.parse.TableBlock

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

/** All the text a block shows, for counting matches. */
fun blockText(block: ContentBlock): String = buildString { appendText(block) }

private fun StringBuilder.appendText(block: ContentBlock) {
    when (block) {
        is HeadingBlock -> line(block.text)
        is ParagraphBlock -> line(block.text.text)
        is ListBlock -> block.items.forEach { item -> item.forEach { appendText(it) } }
        is IndentBlock -> block.blocks.forEach { appendText(it) }
        is QuoteBlock -> block.blocks.forEach { appendText(it) }
        is CalloutBlock -> block.blocks.forEach { appendText(it) }
        is CodeBlock -> line(block.text)
        is ImageBlock -> block.caption?.let { line(it.text) }
        is GalleryBlock -> block.items.forEach { item -> item.caption?.let { line(it.text) } }
        is TableBlock -> {
            block.caption?.let { line(it.text) }
            block.rows.forEach { row -> row.cells.forEach { cell -> cell.blocks.forEach { appendText(it) } } }
        }
        is InfoboxBlock -> {
            block.title?.let { line(it) }
            block.rows.forEach { row ->
                row.label?.let { line(it.text) }
                row.value.forEach { appendText(it) }
            }
        }
        is SoundBlock -> block.title?.let { line(it.text) }
        else -> {}
    }
}

/** Separate pieces so a match can't run across two of them. */
private fun StringBuilder.line(text: String) {
    append(text)
    append('\n')
}

/** One entry per match, in page order: the index of the block holding it. */
fun findMatches(blocks: List<ContentBlock>, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    val out = ArrayList<Int>()
    blocks.forEachIndexed { i, block -> repeat(matchOffsets(blockText(block), query).size) { out += i } }
    return out
}
