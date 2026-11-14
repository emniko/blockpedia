package com.emniko.blockpedia.parse

/** All the text a block shows, as plain text (for searching and for the AI assistant). */
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
