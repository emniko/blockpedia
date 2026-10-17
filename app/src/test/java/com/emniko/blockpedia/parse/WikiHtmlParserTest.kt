package com.emniko.blockpedia.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiHtmlParserTest {

    private val parser = WikiHtmlParser()

    private fun parse(body: String) = parser.parse("""<div class="mw-parser-output">$body</div>""")

    @Test
    fun paragraphKeepsBoldAndLinksAndDropsReferences() {
        val blocks = parse(
            """<p>A <b>creeper</b> is a <a href="/w/Hostile_mob">hostile mob</a>.<sup class="reference"><a href="#cite_note-1">[1]</a></sup></p>"""
        )
        val p = blocks.single() as ParagraphBlock
        assertEquals("A creeper is a hostile mob.", p.text.text)
        assertEquals(InlineStyle.Bold, p.text.styles.single().style)
        assertEquals(LinkTarget.Article("Hostile mob", null), p.text.links.single().target)
    }

    @Test
    fun headingsInMwHeadingWrappers() {
        val blocks = parse("""<div class="mw-heading mw-heading2"><h2 id="Spawning">Spawning</h2></div>""")
        assertEquals(HeadingBlock(2, "Spawning", "Spawning"), blocks.single())
    }

    @Test
    fun resolvesLinkKinds() {
        assertEquals(LinkTarget.Article("Java Edition 1.7.2+", null), parser.resolveLink("/w/Java_Edition_1.7.2+"))
        assertEquals(LinkTarget.Article("Overworld", "Light_level"), parser.resolveLink("/w/Overworld#Light_level"))
        assertEquals(LinkTarget.Category("Mobs"), parser.resolveLink("/w/Category:Mobs"))
        assertEquals(LinkTarget.Anchor("Drops"), parser.resolveLink("#Drops"))
        assertEquals(null, parser.resolveLink("/index.php?title=Foo&action=edit&redlink=1"))
        assertTrue(parser.resolveLink("https://www.minecraft.net/") is LinkTarget.External)
    }

    @Test
    fun infoboxRowsAndFirstAnimatedImage() {
        val blocks = parse(
            """<div class="notaninfobox"><div class="infobox-title">Creeper</div>
            <div class="infobox-imagearea"><span class="animated"><span class="animated-active"><img src="/images/a.png" width="150" height="240"></span><span><img src="/images/b.png" width="150" height="240"></span></span></div>
            <table class="infobox-rows"><tbody><tr><th>Health</th><td>20</td></tr><tr><th colspan="2">Spawn</th></tr></tbody></table></div>"""
        )
        val box = blocks.single() as InfoboxBlock
        assertEquals("Creeper", box.title)
        assertEquals(listOf("https://minecraft.wiki/images/a.png"), box.images.map { it.url })
        assertEquals(2, box.rows.size)
        assertTrue(box.rows[1].header)
    }

    @Test
    fun craftingGridInsideTableCell() {
        val blocks = parse(
            """<table class="wikitable"><tr><th>A</th><th>B</th></tr><tr><td>x</td><td>
            <span class="mcui mcui-Crafting_Table"><span class="mcui-input"><span class="mcui-row">
            <span class="invslot"><span class="invslot-item"><a href="/w/Gunpowder" title="Gunpowder"><img src="/images/g.png" width="32" height="32"></a></span></span>
            <span class="invslot"></span></span></span><span class="mcui-arrow"></span>
            <span class="mcui-output"><span class="invslot"><span class="invslot-item"><a href="/w/TNT" title="TNT"><img src="/images/t.png" width="32" height="32"></a><span class="invslot-stacksize">4</span></span></span></span></span>
            </td></tr></table>"""
        )
        val table = blocks.single() as TableBlock
        val crafting = table.rows[1].cells[1].blocks.filterIsInstance<CraftingBlock>().single()
        assertEquals(listOf("Gunpowder", null), crafting.grid.single().map { it?.title })
        assertNotNull(crafting.output)
        assertEquals("4", crafting.output!!.count)
    }

    @Test
    fun rowspanAddsPlaceholderCells() {
        val blocks = parse(
            """<table><tr><th>a</th><th>b</th></tr><tr><td rowspan="2">x</td><td>1</td></tr><tr><td>2</td></tr></table>"""
        )
        val table = blocks.single() as TableBlock
        assertTrue(table.rows[2].cells[0].placeholder)
        assertEquals(2, table.rows[2].cells.size)
    }

    @Test
    fun smallSpritesBecomeInlineIcons() {
        val blocks = parse(
            """<table><tr><th colspan="2">Mob</th><th>Qty</th></tr>
            <tr><td><a href="/w/Slime" title="Slime"><img src="/images/EntitySprite_slime.png" width="16" height="16"></a></td>
            <td><a href="/w/Slime">Slime</a></td><td>0–2</td></tr></table>"""
        )
        val table = blocks.single() as TableBlock
        val iconText = (table.rows[1].cells[0].blocks.single() as ParagraphBlock).text
        assertEquals(ICON_CHAR.toString(), iconText.text)
        assertEquals("https://minecraft.wiki/images/EntitySprite_slime.png", iconText.icons.single().image.url)
        assertEquals(LinkTarget.Article("Slime", null), iconText.links.single().target)

        val heart = (parse("""<p>20 <img src="/images/Heart.svg" width="9" height="9"> × 10</p>""").single() as ParagraphBlock).text
        assertEquals("20 $ICON_CHAR × 10", heart.text)
        assertEquals(3, heart.icons.single().index)
    }

    @Test
    fun cssSpritesBecomeCroppedIcons() {
        val p = parse("""<p><span class="sprite env-sprite" style="background-position:-32px -48px"></span> Work</p>""")
            .single() as ParagraphBlock
        val icon = p.text.icons.single().image
        assertEquals("https://minecraft.wiki/w/Special:FilePath/EnvSprite.png", icon.url)
        assertEquals(SpriteCrop(32, 48, 16, 16, 0), icon.crop)

        val scaled = parse(
            """<p><span class="sprite" style="background-image:url(/images/X.png);background-position:-64px -0px;background-size:512px auto;height:32px;width:32px"></span></p>"""
        ).single() as ParagraphBlock
        val s = scaled.text.icons.single().image
        assertEquals("https://minecraft.wiki/images/X.png", s.url)
        assertEquals(SpriteCrop(64, 0, 32, 32, 512), s.crop)
    }

    @Test
    fun soundPlayersBecomeSoundBlocks() {
        val blocks = parse(
            """<table><tr><th>Sound</th><th>Caption</th></tr><tr><td>
            <span class="sound"><span class="sound-title"></span><audio class="sound-audio" controls preload="none"><source src="/images/Villager_idle1.ogg?abc" type="audio/ogg"></audio></span>
            <span class="sound"><span class="sound-title"></span><audio src="/images/Villager_idle2.ogg"></audio></span>
            </td><td>Villager mumbles</td></tr></table>"""
        )
        val cell = (blocks.single() as TableBlock).rows[1].cells[0]
        val sounds = cell.blocks.filterIsInstance<SoundBlock>()
        assertEquals(
            listOf("https://minecraft.wiki/images/Villager_idle1.ogg?abc", "https://minecraft.wiki/images/Villager_idle2.ogg"),
            sounds.flatMap { it.urls },
        )
    }

    @Test
    fun skipsNavboxesAndStyles() {
        val blocks = parse("""<style>.x{}</style><table class="navbox"><tr><td>nav</td></tr></table><p>Text</p>""")
        assertEquals("Text", (blocks.single() as ParagraphBlock).text.text)
    }
}
