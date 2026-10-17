package com.emniko.blockpedia.parse

/**
 * Platform-independent model of a wiki article. The parser turns the wiki's HTML into
 * these blocks, and the UI renders each one with native Compose components.
 */
sealed interface ContentBlock

data class HeadingBlock(val level: Int, val text: String, val anchor: String?) : ContentBlock

enum class ParaStyle { Normal, Note, Term }

data class ParagraphBlock(val text: RichText, val style: ParaStyle = ParaStyle.Normal) : ContentBlock

data class ListBlock(val ordered: Boolean, val items: List<List<ContentBlock>>) : ContentBlock

data class IndentBlock(val blocks: List<ContentBlock>) : ContentBlock

data class QuoteBlock(val blocks: List<ContentBlock>) : ContentBlock

data class CalloutBlock(val blocks: List<ContentBlock>) : ContentBlock

data class CodeBlock(val text: String) : ContentBlock

data class ImageBlock(val image: WikiImage, val caption: RichText?) : ContentBlock

data class GalleryItem(val image: WikiImage, val caption: RichText?)

data class GalleryBlock(val items: List<GalleryItem>) : ContentBlock

data class TableCell(
    val blocks: List<ContentBlock>,
    val header: Boolean,
    val colspan: Int,
    val placeholder: Boolean = false,
)

data class TableRow(val cells: List<TableCell>)

data class TableBlock(val caption: RichText?, val rows: List<TableRow>) : ContentBlock

data class InfoRow(val label: RichText?, val value: List<ContentBlock>, val header: Boolean = false)

data class InfoboxBlock(val title: String?, val images: List<WikiImage>, val rows: List<InfoRow>) : ContentBlock

/** One inventory slot in a crafting/smelting grid. A null slot in a grid is an empty cell. */
data class Slot(val image: WikiImage?, val title: String?, val link: LinkTarget?, val count: String?)

data class CraftingBlock(
    val grid: List<List<Slot?>>,
    val fuel: Slot?,
    val output: Slot?,
) : ContentBlock

data object RuleBlock : ContentBlock

data class WikiImage(val url: String, val width: Int, val height: Int, val alt: String?)

sealed interface LinkTarget {
    data class Article(val title: String, val fragment: String?) : LinkTarget
    data class Category(val name: String) : LinkTarget
    data class External(val url: String) : LinkTarget
    data class Anchor(val fragment: String) : LinkTarget
}
