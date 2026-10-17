package com.emniko.blockpedia.parse

enum class InlineStyle { Bold, Italic, Code, Small }

data class StyleSpan(val start: Int, val end: Int, val style: InlineStyle)

data class LinkSpan(val start: Int, val end: Int, val target: LinkTarget)

/** A small inline image (sprite/icon). It occupies one [ICON_CHAR] at [index] in the text. */
data class IconSpan(val index: Int, val image: WikiImage)

const val ICON_CHAR = '￼'

/** Styled text with link ranges, kept free of Compose types so parsing can run off the UI thread. */
data class RichText(
    val text: String,
    val styles: List<StyleSpan> = emptyList(),
    val links: List<LinkSpan> = emptyList(),
    val icons: List<IconSpan> = emptyList(),
) {
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        val Empty = RichText("")

        fun join(parts: List<RichText>, separator: String = "\n"): RichText {
            val nonBlank = parts.filterNot { it.isBlank }
            if (nonBlank.isEmpty()) return Empty
            if (nonBlank.size == 1) return nonBlank[0]
            val sb = StringBuilder()
            val styles = mutableListOf<StyleSpan>()
            val links = mutableListOf<LinkSpan>()
            val icons = mutableListOf<IconSpan>()
            nonBlank.forEachIndexed { i, part ->
                if (i > 0) sb.append(separator)
                val offset = sb.length
                sb.append(part.text)
                part.styles.mapTo(styles) { it.copy(start = it.start + offset, end = it.end + offset) }
                part.links.mapTo(links) { it.copy(start = it.start + offset, end = it.end + offset) }
                part.icons.mapTo(icons) { it.copy(index = it.index + offset) }
            }
            return RichText(sb.toString(), styles, links, icons)
        }
    }
}

class RichTextBuilder {
    private val sb = StringBuilder()
    private val styles = mutableListOf<StyleSpan>()
    private val links = mutableListOf<LinkSpan>()
    private val icons = mutableListOf<IconSpan>()

    val length: Int get() = sb.length

    /** Appends text, collapsing HTML whitespace the way a browser would. */
    fun appendText(raw: String) {
        var s = WHITESPACE.replace(raw, " ")
        if (s.isEmpty()) return
        if (s[0] == ' ' && (sb.isEmpty() || sb[sb.length - 1] == ' ' || sb[sb.length - 1] == '\n')) {
            s = s.trimStart(' ')
        }
        sb.append(s)
    }

    fun appendIcon(image: WikiImage) {
        icons += IconSpan(sb.length, image)
        sb.append(ICON_CHAR)
    }

    fun newline() {
        if (sb.isEmpty()) return
        if (sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        if (sb.endsWith("\n\n")) return
        sb.append('\n')
    }

    fun addStyle(start: Int, end: Int, style: InlineStyle) {
        if (end > start) styles += StyleSpan(start, end, style)
    }

    fun addLink(start: Int, end: Int, target: LinkTarget) {
        if (end > start) links += LinkSpan(start, end, target)
    }

    fun build(): RichText {
        val raw = sb.toString()
        val start = raw.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return RichText.Empty
        val end = raw.indexOfLast { !it.isWhitespace() } + 1
        val text = raw.substring(start, end)
        val len = text.length

        fun clamp(s: Int, e: Int): Pair<Int, Int>? {
            val ns = (s - start).coerceIn(0, len)
            val ne = (e - start).coerceIn(0, len)
            return if (ne > ns) ns to ne else null
        }

        val outStyles = styles.mapNotNull { sp -> clamp(sp.start, sp.end)?.let { StyleSpan(it.first, it.second, sp.style) } }
        val outLinks = links.mapNotNull { ln -> clamp(ln.start, ln.end)?.let { LinkSpan(it.first, it.second, ln.target) } }
        val outIcons = icons.mapNotNull { ic ->
            val i = ic.index - start
            if (i in 0 until len && text[i] == ICON_CHAR) IconSpan(i, ic.image) else null
        }
        return RichText(text, outStyles, outLinks, outIcons)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
