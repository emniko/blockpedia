package com.emniko.blockpedia.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.emniko.blockpedia.parse.CalloutBlock
import com.emniko.blockpedia.parse.CodeBlock
import com.emniko.blockpedia.parse.ContentBlock
import com.emniko.blockpedia.parse.CraftingBlock
import com.emniko.blockpedia.parse.GalleryBlock
import com.emniko.blockpedia.parse.HeadingBlock
import com.emniko.blockpedia.parse.ImageBlock
import com.emniko.blockpedia.parse.IndentBlock
import com.emniko.blockpedia.parse.InfoboxBlock
import com.emniko.blockpedia.parse.InlineStyle
import com.emniko.blockpedia.parse.LinkTarget
import com.emniko.blockpedia.parse.ListBlock
import com.emniko.blockpedia.parse.ParaStyle
import com.emniko.blockpedia.parse.ParagraphBlock
import com.emniko.blockpedia.parse.QuoteBlock
import com.emniko.blockpedia.parse.RichText
import com.emniko.blockpedia.parse.RuleBlock
import com.emniko.blockpedia.parse.Slot
import com.emniko.blockpedia.parse.TableBlock
import com.emniko.blockpedia.parse.TableCell
import com.emniko.blockpedia.parse.WikiImage

typealias OnLink = (LinkTarget) -> Unit

/** Body text style for the current context; tables and infoboxes use a smaller one. */
private val LocalBodyStyle = compositionLocalOf<TextStyle?> { null }

@Composable
private fun bodyStyle(): TextStyle = LocalBodyStyle.current ?: MaterialTheme.typography.bodyLarge

@Composable
fun BlockList(blocks: List<ContentBlock>, onLink: OnLink, nested: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { BlockView(it, onLink, nested) }
    }
}

/**
 * Renders one block natively. [nested] is true inside horizontally scrolling containers
 * (table cells), where another horizontal scroller isn't allowed.
 */
@Composable
fun BlockView(block: ContentBlock, onLink: OnLink, nested: Boolean = false) {
    when (block) {
        is HeadingBlock -> HeadingView(block)
        is ParagraphBlock -> ParagraphView(block, onLink)
        is ListBlock -> ListView(block, onLink, nested)
        is IndentBlock -> Box(Modifier.padding(start = 16.dp)) { BlockList(block.blocks, onLink, nested) }
        is QuoteBlock -> QuoteView(block, onLink, nested)
        is CalloutBlock -> CalloutView(block, onLink, nested)
        is CodeBlock -> CodeView(block, nested)
        is ImageBlock -> ImageBlockView(block, onLink)
        is GalleryBlock -> GalleryView(block, onLink, nested)
        is TableBlock -> TableView(block, onLink, nested)
        is InfoboxBlock -> InfoboxView(block, onLink)
        is CraftingBlock -> CraftingView(block, onLink)
        RuleBlock -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
    }
}

// ------------------------------------------------------------------ text

@Composable
fun RichTextView(
    text: RichText,
    onLink: OnLink,
    modifier: Modifier = Modifier,
    style: TextStyle = bodyStyle(),
    color: Color = Color.Unspecified,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val currentOnLink by rememberUpdatedState(onLink)
    val annotated = remember(text, linkColor, codeBackground) {
        buildAnnotatedString {
            append(text.text)
            for (span in text.styles) {
                val spanStyle = when (span.style) {
                    InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                    InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    InlineStyle.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    InlineStyle.Small -> SpanStyle(fontSize = 0.8.em)
                }
                addStyle(spanStyle, span.start, span.end)
            }
            for (link in text.links) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "wiki-link",
                        styles = TextLinkStyles(style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)),
                        linkInteractionListener = { currentOnLink(link.target) },
                    ),
                    link.start,
                    link.end,
                )
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

@Composable
private fun HeadingView(block: HeadingBlock) {
    val typography = MaterialTheme.typography
    val style = when (block.level) {
        1, 2 -> typography.headlineSmall
        3 -> typography.titleLarge
        4 -> typography.titleMedium
        else -> typography.titleSmall
    }
    Column(Modifier.padding(top = if (block.level <= 2) 20.dp else 10.dp)) {
        Text(
            text = block.text,
            style = style,
            fontWeight = FontWeight.Bold,
            color = if (block.level <= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (block.level <= 2) {
            HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ParagraphView(block: ParagraphBlock, onLink: OnLink) {
    when (block.style) {
        ParaStyle.Normal -> RichTextView(block.text, onLink)
        ParaStyle.Term -> RichTextView(block.text, onLink, style = bodyStyle().copy(fontWeight = FontWeight.Bold))
        ParaStyle.Note -> RichTextView(
            block.text,
            onLink,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ListView(block: ListBlock, onLink: OnLink, nested: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(if (block.ordered) 28.dp else 18.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.forEach { BlockView(it, onLink, nested) }
                }
            }
        }
    }
}

@Composable
private fun QuoteView(block: QuoteBlock, onLink: OnLink, nested: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
        )
        Box(Modifier.padding(start = 12.dp)) { BlockList(block.blocks, onLink, nested) }
    }
}

@Composable
private fun CalloutView(block: CalloutBlock, onLink: OnLink, nested: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(12.dp)) {
            CompositionLocalProvider(LocalBodyStyle provides MaterialTheme.typography.bodyMedium) {
                BlockList(block.blocks, onLink, nested)
            }
        }
    }
}

@Composable
private fun CodeView(block: CodeBlock, nested: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        val scroll = if (nested) Modifier else Modifier.horizontalScroll(rememberScrollState())
        Text(
            text = block.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = scroll.padding(12.dp),
        )
    }
}

// ------------------------------------------------------------------ images

private fun isPixelArt(image: WikiImage) = image.width in 1..300

@Composable
fun WikiImageView(image: WikiImage, modifier: Modifier = Modifier, maxHeight: Dp = 320.dp) {
    val sized = if (image.width > 0 && image.height > 0) {
        // Small pixel art is shown at 2x so it stays legible.
        val displayWidth = if (image.width < 64) image.width * 2 else image.width
        modifier
            .widthIn(max = displayWidth.dp)
            .heightIn(max = maxHeight)
            .aspectRatio(image.width.toFloat() / image.height.toFloat())
    } else {
        modifier.fillMaxWidth().heightIn(max = maxHeight)
    }
    AsyncImage(
        model = image.url,
        contentDescription = image.alt,
        modifier = sized,
        contentScale = ContentScale.Fit,
        filterQuality = if (isPixelArt(image)) FilterQuality.None else FilterQuality.Low,
    )
}

@Composable
private fun ImageBlockView(block: ImageBlock, onLink: OnLink) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        WikiImageView(block.image)
        block.caption?.let {
            RichTextView(
                it,
                onLink,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GalleryView(block: GalleryBlock, onLink: OnLink, nested: Boolean) {
    val itemContent: @Composable (Int) -> Unit = { index ->
        val item = block.items[index]
        Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(140.dp, 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.image.url,
                    contentDescription = item.image.alt,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = if (isPixelArt(item.image)) FilterQuality.None else FilterQuality.Low,
                )
            }
            item.caption?.let {
                RichTextView(
                    it,
                    onLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    if (nested) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.indices.forEach { itemContent(it) }
        }
    } else {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            block.items.indices.forEach { itemContent(it) }
        }
    }
}

// ------------------------------------------------------------------ tables

private const val ROW_LIMIT = 40

private fun desiredWidth(cell: TableCell): Float {
    if (cell.blocks.any { it is TableBlock || it is GalleryBlock }) return 300f
    if (cell.blocks.any { it is CraftingBlock }) return 250f
    val textLength = cell.blocks.sumOf { (it as? ParagraphBlock)?.text?.text?.length ?: 0 }
    return when {
        textLength > 240 -> 260f
        textLength > 120 -> 210f
        else -> 0f
    }
}

private fun columnWidths(table: TableBlock): FloatArray {
    val columns = table.rows.maxOf { row -> row.cells.sumOf { it.colspan } }.coerceAtLeast(1)
    val base = when {
        columns <= 2 -> 165f
        columns <= 4 -> 140f
        else -> 120f
    }
    val widths = FloatArray(columns) { base }
    for (row in table.rows) {
        var col = 0
        for (cell in row.cells) {
            if (cell.colspan == 1 && col < columns) {
                val want = desiredWidth(cell)
                if (want > widths[col]) widths[col] = want
            }
            col += cell.colspan
        }
    }
    return widths
}

@Composable
private fun TableView(table: TableBlock, onLink: OnLink, nested: Boolean) {
    val widths = remember(table) { columnWidths(table) }
    var expanded by remember(table) { mutableStateOf(false) }
    val rows = if (expanded || table.rows.size <= ROW_LIMIT) table.rows else table.rows.take(ROW_LIMIT)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(10.dp)

    Column {
        table.caption?.let {
            RichTextView(
                it,
                onLink,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(
            Modifier
                .clip(shape)
                .border(1.dp, borderColor, shape)
        ) {
            val scroll = if (nested) Modifier else Modifier.horizontalScroll(rememberScrollState())
            Column(scroll) {
                CompositionLocalProvider(LocalBodyStyle provides MaterialTheme.typography.bodyMedium) {
                    rows.forEachIndexed { rowIndex, row ->
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            var col = 0
                            row.cells.forEach { cell ->
                                var width = 0f
                                for (c in col until col + cell.colspan) width += widths.getOrElse(c) { 120f }
                                TableCellView(cell, width.dp, onLink)
                                col += cell.colspan
                            }
                        }
                        if (rowIndex < rows.lastIndex) HorizontalDivider(color = borderColor)
                    }
                }
            }
        }
        if (!expanded && table.rows.size > ROW_LIMIT) {
            TextButton(onClick = { expanded = true }) { Text("Show all ${table.rows.size} rows") }
        }
    }
}

@Composable
private fun TableCellView(cell: TableCell, width: Dp, onLink: OnLink) {
    val background = if (cell.header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (cell.blocks.isNotEmpty()) {
            val style = if (cell.header) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            }
            CompositionLocalProvider(LocalBodyStyle provides style) {
                BlockList(cell.blocks, onLink, nested = true)
            }
        }
    }
}

// ------------------------------------------------------------------ infobox

@Composable
private fun InfoboxView(block: InfoboxBlock, onLink: OnLink) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape)
    ) {
        block.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (block.images.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    block.images.forEach { image ->
                        val ratio = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f
                        AsyncImage(
                            model = image.url,
                            contentDescription = image.alt,
                            modifier = Modifier
                                .height(128.dp)
                                .aspectRatio(ratio.coerceIn(0.3f, 3f)),
                            contentScale = ContentScale.Fit,
                            filterQuality = if (isPixelArt(image)) FilterQuality.None else FilterQuality.Low,
                        )
                    }
                }
            }
        }
        CompositionLocalProvider(LocalBodyStyle provides MaterialTheme.typography.bodyMedium) {
            block.rows.forEachIndexed { index, row ->
                if (index > 0 || block.images.isNotEmpty() || block.title != null) {
                    HorizontalDivider(color = colors.outlineVariant)
                }
                if (row.header) {
                    row.label?.let {
                        RichTextView(
                            it,
                            onLink,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.secondaryContainer)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val label = row.label
                        if (label != null) {
                            Box(Modifier.weight(0.42f).padding(end = 8.dp)) {
                                RichTextView(
                                    label,
                                    onLink,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        Box(Modifier.weight(if (label != null) 0.58f else 1f)) {
                            BlockList(row.value, onLink, nested = true)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ crafting

private val GuiPanel = Color(0xFFC6C6C6)
private val GuiSlot = Color(0xFF8B8B8B)
private val GuiDark = Color(0xFF373737)

@Composable
private fun CraftingView(block: CraftingBlock, onLink: OnLink) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .clip(shape)
            .background(GuiPanel)
            .border(2.dp, GuiDark, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            block.grid.forEach { row ->
                Row { row.forEach { SlotView(it, 38.dp, onLink) } }
            }
            if (block.fuel != null) {
                Text("🔥", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                SlotView(block.fuel, 38.dp, onLink)
            }
        }
        Text(
            text = "➜",
            fontSize = 24.sp,
            color = GuiDark,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        SlotView(block.output, 52.dp, onLink)
    }
}

@Composable
private fun SlotView(slot: Slot?, size: Dp, onLink: OnLink) {
    val link = slot?.link
    Box(
        Modifier
            .size(size)
            .padding(1.dp)
            .background(GuiSlot)
            .border(1.dp, GuiDark)
            .then(if (link != null) Modifier.clickable { onLink(link) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val image = slot?.image
        val title = slot?.title
        if (image != null) {
            AsyncImage(
                model = image.url,
                contentDescription = title,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else if (title != null) {
            Text(title.take(3), fontSize = 10.sp, color = Color.White)
        }
        val count = slot?.count
        if (count != null) {
            Text(
                text = count,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 0f)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp),
            )
        }
    }
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
