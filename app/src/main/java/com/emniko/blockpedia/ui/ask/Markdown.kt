package com.emniko.blockpedia.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The Markdown blocks the assistant writes. */
private sealed interface MdBlock {
    data class Heading(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
}

private fun parse(markdown: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = markdown.replace("\r", "").lines()
    var i = 0
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        para.setLength(0)
    }
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        when {
            line.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) code.appendLine(lines[i++])
                out += MdBlock.Code(code.toString().trimEnd())
            }
            line.isEmpty() -> flush()
            line.matches(Regex("#{1,6}\\s+.*")) -> {
                flush()
                out += MdBlock.Heading(line.trimStart('#').trim())
            }
            line.startsWith("|") && line.endsWith("|") -> {
                flush()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    i++
                }
                out += MdBlock.Table(rows)
                continue
            }
            line.matches(Regex("[-*+•]\\s+.*")) -> {
                flush()
                out += MdBlock.Item("•", line.drop(1).trim(), indentOf(raw))
            }
            line.matches(Regex("\\d+[.)]\\s+.*")) -> {
                flush()
                val n = line.takeWhile { it.isDigit() }
                out += MdBlock.Item("$n.", line.dropWhile { it.isDigit() }.drop(1).trim(), indentOf(raw))
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

private fun indentOf(raw: String): Int = (raw.length - raw.trimStart().length) / 2

/**
 * Inline Markdown: **bold**, *italic*, `code`, [links](url) and wiki citations [1] (tappable,
 * via [onCitation]). Links open through the text's default URI handler.
 */
private fun inline(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    citationColor: Color,
    onCitation: (Int) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("""\*\*(.+?)\*\*|__(.+?)__|\*(?!\s)(.+?)\*|`([^`]+)`|\[([^\]]+)]\((https?://[^)\s]+)\)|\[(\d{1,2})]""")
    var pos = 0
    for (m in pattern.findAll(text)) {
        append(text.substring(pos, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(inline(g[1].ifEmpty { g[2] }, linkColor, codeBackground, citationColor, onCitation))
            }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]) }
            g[4].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(g[4]) }
            g[5].isNotEmpty() -> withLink(LinkAnnotation.Url(g[6], TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)))) {
                append(g[5])
            }
            g[7].isNotEmpty() -> {
                val n = g[7].toInt()
                withLink(
                    LinkAnnotation.Clickable(
                        "cite$n",
                        TextLinkStyles(SpanStyle(color = citationColor, fontWeight = FontWeight.Bold, background = codeBackground)),
                    ) { onCitation(n) },
                ) { append(" $n ") }
            }
        }
        pos = m.range.last + 1
    }
    append(text.substring(pos))
}

/** Renders the assistant's Markdown answer as native text blocks. */
@Composable
fun MarkdownText(markdown: String, onCitation: (Int) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parse(markdown) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    fun fmt(s: String) = inline(s, colors.primary, colors.surfaceVariant, colors.primary, onCitation)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> Text(
                    fmt(b.text),
                    style = type.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Paragraph -> Text(fmt(b.text), style = type.bodyLarge)
                is MdBlock.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(
                        b.marker,
                        style = type.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(fmt(b.text), style = type.bodyLarge, modifier = Modifier.weight(1f))
                }
                is MdBlock.Code -> Text(
                    b.text,
                    fontFamily = FontFamily.Monospace,
                    style = type.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                is MdBlock.Table -> Column(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    b.rows.forEachIndexed { r, row ->
                        Row {
                            row.forEach { cell ->
                                Text(
                                    fmt(cell),
                                    style = type.bodyMedium,
                                    fontWeight = if (r == 0) FontWeight.Bold else null,
                                    modifier = Modifier.width(140.dp).padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
