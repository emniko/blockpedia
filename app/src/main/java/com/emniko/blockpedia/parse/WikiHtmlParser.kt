package com.emniko.blockpedia.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URLDecoder

/**
 * Converts the HTML that MediaWiki's `action=parse` returns into [ContentBlock]s.
 *
 * It walks the DOM once. Inline content accumulates in a [RichTextBuilder]; any block-level
 * element flushes the current paragraph and emits its own block. This "flow" model copes
 * with wiki markup that mixes blocks inside paragraphs and table cells (crafting grids, etc.).
 */
class WikiHtmlParser(private val baseUrl: String = BASE_URL) {

    fun parse(html: String): List<ContentBlock> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        val root = doc.selectFirst(".mw-parser-output") ?: doc.body()
        return blocksOf(root)
    }

    // ---------------------------------------------------------------- flow

    private class Flow {
        val out = mutableListOf<ContentBlock>()
        var rb = RichTextBuilder()
        var style = ParaStyle.Normal

        fun flush() {
            val text = rb.build()
            if (!text.isBlank) out += ParagraphBlock(text, style)
            rb = RichTextBuilder()
        }

        fun emit(block: ContentBlock) {
            flush()
            out += block
        }

        fun emitAll(blocks: List<ContentBlock>) {
            flush()
            out += blocks
        }
    }

    private fun blocksOf(el: Element, style: ParaStyle = ParaStyle.Normal): List<ContentBlock> {
        val flow = Flow()
        flow.style = style
        flowChildren(el, flow)
        flow.flush()
        return flow.out
    }

    private fun inline(el: Element): RichText {
        val parts = blocksOf(el).mapNotNull { (it as? ParagraphBlock)?.text }
        return RichText.join(parts, " ")
    }

    private fun flowChildren(el: Element, flow: Flow) {
        for (child in el.childNodes()) flowNode(child, flow)
    }

    private fun flowNode(node: Node, flow: Flow) {
        when (node) {
            is TextNode -> flow.rb.appendText(node.wholeText)
            is Element -> flowElement(node, flow)
            else -> Unit
        }
    }

    private fun flowElement(el: Element, flow: Flow) {
        if (shouldSkip(el)) return
        val tag = el.normalName()
        when {
            el.hasClass("mw-heading") -> {
                val h = el.children().firstOrNull { it.normalName() in HEADING_TAGS }
                if (h != null) heading(h)?.let(flow::emit) else blockContainer(el, flow)
            }
            tag in HEADING_TAGS -> heading(el)?.let(flow::emit)
            isInfobox(el) -> flow.emit(parseInfobox(el))
            el.hasClass("mcui") -> parseCrafting(el)?.let(flow::emit)
            el.hasClass("animated") -> {
                val active = el.children().firstOrNull { it.hasClass("animated-active") }
                    ?: el.children().firstOrNull()
                if (active != null) flowElement(active, flow)
            }
            el.hasClass("gallery") -> parseGallery(el)?.let(flow::emit)
            isNote(el) -> {
                val blocks = blocksOf(el, ParaStyle.Note)
                if (blocks.isNotEmpty()) flow.emitAll(blocks)
            }
            isCallout(el) -> {
                val blocks = blocksOf(el)
                if (blocks.isNotEmpty()) flow.emit(CalloutBlock(blocks))
            }
            tag == "p" -> {
                flow.flush()
                flowChildren(el, flow)
                flow.flush()
            }
            tag == "br" -> flow.rb.newline()
            tag == "ul" || tag == "ol" -> parseList(el, ordered = tag == "ol")?.let(flow::emit)
            tag == "dl" -> parseDefinitionList(el, flow)
            tag == "table" -> parseTable(el, flow)
            tag == "figure" || (tag == "div" && el.hasClass("thumb")) -> parseFigure(el, flow)
            tag == "img" -> {
                val img = toImage(el) ?: return
                if (img.width == 0 || img.width >= MIN_BLOCK_IMAGE) {
                    flow.emit(ImageBlock(img, null))
                } else {
                    // Sprites and item icons sit inline with the text, as on the wiki.
                    flow.rb.appendIcon(img)
                }
            }
            tag == "blockquote" -> {
                val blocks = blocksOf(el)
                if (blocks.isNotEmpty()) flow.emit(QuoteBlock(blocks))
            }
            tag == "pre" -> {
                val text = el.wholeText().trimEnd()
                if (text.isNotBlank()) flow.emit(CodeBlock(text))
            }
            tag == "hr" -> flow.emit(RuleBlock)
            tag == "a" -> link(el, flow)
            tag in BOLD_TAGS -> styled(el, flow, InlineStyle.Bold)
            tag in ITALIC_TAGS -> styled(el, flow, InlineStyle.Italic)
            tag in CODE_TAGS -> styled(el, flow, InlineStyle.Code)
            tag in SMALL_TAGS -> styled(el, flow, InlineStyle.Small)
            tag in BLOCK_CONTAINERS -> blockContainer(el, flow)
            else -> flowChildren(el, flow) // span, abbr, u, s, font, … pass through
        }
    }

    private fun blockContainer(el: Element, flow: Flow) {
        flow.flush()
        flowChildren(el, flow)
        flow.flush()
    }

    private fun styled(el: Element, flow: Flow, style: InlineStyle) {
        val builder = flow.rb
        val start = builder.length
        flowChildren(el, flow)
        // If a block was emitted inside, the builder was replaced; drop the style in that case.
        if (flow.rb === builder) builder.addStyle(start, builder.length, style)
    }

    private fun link(el: Element, flow: Flow) {
        val target = resolveLink(el.attr("href"))
        val builder = flow.rb
        val start = builder.length
        flowChildren(el, flow)
        if (target != null && flow.rb === builder) builder.addLink(start, builder.length, target)
    }

    // ---------------------------------------------------------------- blocks

    private fun heading(h: Element): HeadingBlock? {
        val level = h.normalName().getOrNull(1)?.digitToIntOrNull() ?: 2
        val anchor = h.id().ifBlank { h.selectFirst(".mw-headline")?.id().orEmpty() }.ifBlank { null }
        val copy = h.clone()
        copy.select(".mw-editsection, .mw-headline-number").remove()
        val text = copy.text().trim()
        if (text.isEmpty()) return null
        return HeadingBlock(level, text, anchor)
    }

    private fun parseList(el: Element, ordered: Boolean): ListBlock? {
        val items = el.children()
            .filter { it.normalName() == "li" && !shouldSkip(it) }
            .map { blocksOf(it) }
            .filter { it.isNotEmpty() }
        return if (items.isEmpty()) null else ListBlock(ordered, items)
    }

    private fun parseDefinitionList(el: Element, flow: Flow) {
        flow.flush()
        for (child in el.children()) {
            if (shouldSkip(child)) continue
            when (child.normalName()) {
                "dt" -> {
                    val text = inline(child)
                    if (!text.isBlank) flow.emit(ParagraphBlock(text, ParaStyle.Term))
                }
                "dd" -> {
                    val blocks = blocksOf(child)
                    if (blocks.isNotEmpty()) flow.emit(IndentBlock(blocks))
                }
            }
        }
    }

    private fun parseFigure(el: Element, flow: Flow) {
        val images = mutableListOf<WikiImage>()
        collectImages(el, images)
        val image = images.firstOrNull()
        if (image == null) {
            blockContainer(el, flow)
            return
        }
        val captionEl = el.selectFirst("figcaption, .thumbcaption")
        captionEl?.select(".magnify")?.remove()
        val caption = captionEl?.let { inline(it) }?.takeUnless { it.isBlank }
        flow.emit(ImageBlock(image, caption))
    }

    private fun parseGallery(el: Element): GalleryBlock? {
        val boxes = el.select("li.gallerybox").ifEmpty { el.children() }
        val items = boxes.mapNotNull { box ->
            val images = mutableListOf<WikiImage>()
            collectImages(box, images)
            val image = images.firstOrNull() ?: return@mapNotNull null
            val caption = box.selectFirst(".gallerytext")?.let { inline(it) }?.takeUnless { it.isBlank }
            GalleryItem(image, caption)
        }
        return if (items.isEmpty()) null else GalleryBlock(items)
    }

    private fun parseInfobox(el: Element): InfoboxBlock {
        val title = el.selectFirst(".infobox-title")?.text()?.trim()?.ifBlank { null }
        val images = mutableListOf<WikiImage>()
        el.selectFirst(".infobox-imagearea")?.let { collectImages(it, images) }

        val rowsTable = el.selectFirst("table.infobox-rows")
            ?: if (el.normalName() == "table") el else el.selectFirst("table")
        val rows = mutableListOf<InfoRow>()
        if (rowsTable != null) {
            for (tr in directRows(rowsTable)) {
                val cells = tr.children().filter { it.normalName() == "th" || it.normalName() == "td" }
                val th = cells.firstOrNull { it.normalName() == "th" }
                val td = cells.firstOrNull { it.normalName() == "td" }
                when {
                    th != null && td != null -> rows += InfoRow(inline(th), blocksOf(td))
                    th != null -> inline(th).takeUnless { it.isBlank }?.let { rows += InfoRow(it, emptyList(), header = true) }
                    td != null -> {
                        val value = blocksOf(td)
                        if (value.isNotEmpty()) rows += InfoRow(null, value)
                    }
                }
            }
        }
        return InfoboxBlock(title, images.take(MAX_INFOBOX_IMAGES), rows)
    }

    private fun parseCrafting(el: Element): CraftingBlock? {
        val input = el.selectFirst(".mcui-input")
        val grid: List<List<Slot?>> = when {
            input == null -> emptyList()
            else -> {
                val rows = input.children().filter { it.hasClass("mcui-row") }
                if (rows.isNotEmpty()) {
                    rows.map { row -> row.children().filter { it.hasClass("invslot") }.map(::slotOf) }
                } else {
                    listOf(input.select(".invslot").map(::slotOf))
                }
            }
        }.filter { it.isNotEmpty() }
        val fuel = el.selectFirst(".mcui-fuel")?.selectFirst(".invslot")?.let(::slotOf)
        val output = el.selectFirst(".mcui-output")?.selectFirst(".invslot")?.let(::slotOf)
        if (grid.isEmpty() && output == null) return null
        return CraftingBlock(grid, fuel, output)
    }

    private fun slotOf(slot: Element): Slot? {
        val items = slot.select(".invslot-item")
        val item = items.firstOrNull { it.hasClass("animated-active") } ?: items.firstOrNull() ?: return null
        val a = item.selectFirst("a")
        val img = item.selectFirst("img")
        val title = a?.attr("title")?.ifBlank { null } ?: img?.attr("alt")?.ifBlank { null }
        val count = item.selectFirst(".invslot-stacksize")?.text()?.trim()?.ifBlank { null }
        val image = img?.let(::toImage)
        if (image == null && title == null) return null
        return Slot(image, title, a?.let { resolveLink(it.attr("href")) }, count)
    }

    private fun parseTable(table: Element, flow: Flow) {
        if (table.hasClass("infobox")) {
            flow.emit(parseInfobox(table))
            return
        }
        val trs = directRows(table)
        if (trs.isEmpty()) return

        val rows = mutableListOf<TableRow>()
        val carry = HashMap<Int, IntArray>() // column -> [remaining rows, colspan]
        for (tr in trs) {
            if (shouldSkip(tr)) continue
            val cells = mutableListOf<TableCell>()
            var col = 0
            fun drainCarry() {
                while (true) {
                    val c = carry[col] ?: return
                    cells += TableCell(emptyList(), header = false, colspan = c[1], placeholder = true)
                    c[0] -= 1
                    if (c[0] <= 0) carry.remove(col)
                    col += c[1]
                }
            }
            for (cell in tr.children()) {
                val tag = cell.normalName()
                if (tag != "td" && tag != "th") continue
                drainCarry()
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 12) ?: 1
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 200) ?: 1
                cells += TableCell(blocksOf(cell), header = tag == "th", colspan = colspan)
                if (rowspan > 1) carry[col] = intArrayOf(rowspan - 1, colspan)
                col += colspan
            }
            drainCarry()
            if (cells.isNotEmpty()) rows += TableRow(cells)
        }
        if (rows.isEmpty() || rows.all { r -> r.cells.all { it.blocks.isEmpty() } }) return

        // Single-column tables are usually layout wrappers: just flow their content.
        if (rows.all { it.cells.size == 1 && !it.cells[0].header }) {
            rows.forEach { flow.emitAll(it.cells[0].blocks) }
            return
        }

        val caption = table.children().firstOrNull { it.normalName() == "caption" }
            ?.let { inline(it) }?.takeUnless { it.isBlank }
        flow.emit(TableBlock(caption, rows))
    }

    private fun directRows(table: Element): List<Element> {
        val rows = mutableListOf<Element>()
        for (child in table.children()) {
            when (child.normalName()) {
                "tr" -> rows += child
                "thead", "tbody", "tfoot" -> child.children().filterTo(rows) { it.normalName() == "tr" }
            }
        }
        return rows
    }

    // ---------------------------------------------------------------- images

    /** Collects images in document order, showing only the first frame of animated sets. */
    private fun collectImages(el: Element, out: MutableList<WikiImage>) {
        if (shouldSkip(el)) return
        when {
            el.normalName() == "img" -> toImage(el)?.takeIf { it.width == 0 || it.width >= 16 }?.let(out::add)
            el.hasClass("animated") -> {
                val active = el.children().firstOrNull { it.hasClass("animated-active") } ?: el.children().firstOrNull()
                active?.let { collectImages(it, out) }
            }
            else -> el.children().forEach { collectImages(it, out) }
        }
    }

    private fun toImage(img: Element): WikiImage? {
        val src = img.attr("data-src").ifBlank { img.attr("src") }
        if (src.isBlank() || src.startsWith("data:")) return null
        return WikiImage(
            url = absolutize(src),
            width = img.attr("width").toIntOrNull() ?: 0,
            height = img.attr("height").toIntOrNull() ?: 0,
            alt = img.attr("alt").ifBlank { null },
        )
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> baseUrl + src
        else -> src
    }

    // ---------------------------------------------------------------- links

    fun resolveLink(href: String): LinkTarget? {
        if (href.isBlank()) return null
        if (href.startsWith("#")) return LinkTarget.Anchor(decode(href.substring(1)))

        val url = absolutize(href)
        val hash = url.indexOf('#')
        val fragment = if (hash >= 0) decode(url.substring(hash + 1)).ifBlank { null } else null
        val noFragment = if (hash >= 0) url.substring(0, hash) else url

        if (!noFragment.startsWith("$baseUrl/")) {
            return if (url.startsWith("http://") || url.startsWith("https://")) LinkTarget.External(url) else null
        }
        val path = noFragment.removePrefix(baseUrl)

        val rawTitle: String = when {
            path.startsWith("/w/") -> path.substring(3).substringBefore('?')
            path.startsWith("/index.php") -> {
                if (path.contains("redlink=1") || path.contains("action=")) return null
                path.substringAfter("title=", "").substringBefore('&').ifBlank { return null }
            }
            else -> return LinkTarget.External(url)
        }
        val title = decode(rawTitle).replace('_', ' ').trim()
        if (title.isEmpty()) return null

        return when {
            title.startsWith("Category:") -> LinkTarget.Category(title.removePrefix("Category:"))
            NON_ARTICLE_PREFIXES.any { title.startsWith(it) } -> LinkTarget.External(url)
            else -> LinkTarget.Article(title, fragment)
        }
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    // ---------------------------------------------------------------- classification

    private fun shouldSkip(el: Element): Boolean {
        if (el.normalName() in SKIP_TAGS) return true
        if (el.id() == "toc") return true
        for (c in el.classNames()) if (c in SKIP_CLASSES) return true
        val style = el.attr("style")
        if (style.isNotEmpty() && style.replace(" ", "").contains("display:none")) return true
        return false
    }

    private fun isInfobox(el: Element): Boolean =
        el.hasClass("notaninfobox") || (el.hasClass("infobox") && el.normalName() != "table")

    private fun isNote(el: Element): Boolean =
        el.hasClass("hatnote") || el.hasClass("dablink") || el.attr("role") == "note"

    private fun isCallout(el: Element): Boolean =
        el.hasClass("msgbox") || el.hasClass("notice") || el.hasClass("ambox")

    companion object {
        const val BASE_URL = "https://minecraft.wiki"
        private const val MIN_BLOCK_IMAGE = 64
        private const val MAX_INFOBOX_IMAGES = 8

        private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val BOLD_TAGS = setOf("b", "strong")
        private val ITALIC_TAGS = setOf("i", "em", "cite", "var", "dfn")
        private val CODE_TAGS = setOf("code", "kbd", "samp", "tt")
        private val SMALL_TAGS = setOf("small", "sub", "sup")
        private val BLOCK_CONTAINERS = setOf(
            "div", "section", "article", "center", "header", "footer", "main", "aside",
            "nav", "details", "summary", "figcaption", "caption", "li", "dd", "dt", "tbody",
        )
        private val SKIP_TAGS = setOf(
            "style", "script", "link", "meta", "noscript", "input", "button", "template",
            "map", "audio", "video", "source", "track",
        )
        private val SKIP_CLASSES = setOf(
            "mw-editsection", "navbox", "toc", "printfooter", "catlinks", "mw-empty-elt",
            "reference", "mw-cite-backlink", "load-page-button", "mw-collapsible-toggle",
            "sitenotice", "mw-jump-link",
        )
        private val NON_ARTICLE_PREFIXES = listOf("File:", "Special:", "Template:", "User:", "MediaWiki:", "Module:")
    }
}
