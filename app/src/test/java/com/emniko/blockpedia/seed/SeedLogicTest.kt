package com.emniko.blockpedia.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedLogicTest {

    private fun slimeSet(edition: Edition, seed: Long): Set<String> = buildSet {
        for (z in -6..6) for (x in -6..6) if (SlimeChunks.isSlime(edition, seed, x, z)) add("$x,$z")
    }

    private fun parse(list: String) = list.split(' ').toSet()

    @Test
    fun mt19937MatchesReference() {
        // Reference first output for the default seed 5489.
        assertEquals(3499211612L, Mt19937.first(5489).toLong() and 0xffffffffL)
    }

    @Test
    fun javaSlimeChunksMatchCubiomes() {
        assertEquals(
            parse("0,-6 4,-6 4,-5 -2,-4 4,-3 5,-3 0,-2 6,-2 -4,0 3,0 -2,1 4,1 -5,2 -1,2 -2,4 3,5"),
            slimeSet(Edition.Java, 12345L),
        )
        assertEquals(
            parse("5,-6 6,-6 -6,-5 -2,-5 -1,-5 2,-5 5,-5 6,-5 -4,-4 -3,-4 4,-4 4,-2 1,0 3,0 1,1 -5,2 -5,3 -2,3 4,3"),
            slimeSet(Edition.Java, -4172144997902289642L),
        )
    }

    @Test
    fun bedrockSlimeChunksMatchReference() {
        val expected = parse("4,-6 0,-5 6,-4 -3,-2 0,-2 1,-2 -4,-1 -3,-1 -6,0 -1,0 3,0 3,1 -4,2 0,4 6,5 0,6")
        assertEquals(expected, slimeSet(Edition.Bedrock, 0L))
        assertEquals(expected, slimeSet(Edition.Bedrock, 987654321L)) // seed doesn't matter
        assertTrue(SlimeChunks.bedrock(1009, -2000))
        assertTrue(SlimeChunks.bedrock(-31249, 31250))
        assertTrue(SlimeChunks.bedrock(123460, -654321))
    }

    @Test
    fun parsesSeeds() {
        assertEquals(SeedParse.Ok(-4172144997902289642L), parseSeed(" -4172144997902289642 ", Edition.Java))
        assertEquals(SeedParse.Ok("glacier".hashCode().toLong()), parseSeed("glacier", Edition.Java))
        assertEquals(SeedParse.Ok(-1L), parseSeed("18446744073709551615", Edition.Bedrock))
        assertTrue(parseSeed("glacier", Edition.Bedrock) is SeedParse.Error)
        assertTrue(parseSeed("", Edition.Java) is SeedParse.Error)
        assertTrue(parseSeed("99999999999999999999999", Edition.Java) is SeedParse.Error)
    }

    @Test
    fun compassDirections() {
        assertEquals("N", direction(0, 0, 0, -100))
        assertEquals("E", direction(0, 0, 100, 0))
        assertEquals("SW", direction(0, 0, -100, 100))
    }

    /** Villageroll's published check (Java 26.1.2/26.2 gameplay): novice librarian, 26 rerolls. */
    @Test
    fun librarianSequenceMatchesGame() {
        val expected = listOf(
            "power:3:34;bookshelf", "paper;efficiency:1:11", "feather_falling:1:18;paper", "bookshelf;paper",
            "piercing:3:13;bookshelf", "punch:1:19;bookshelf", "paper;loyalty:1:5", "paper;piercing:3:13",
            "bookshelf;silk_touch:1:14", "paper;bookshelf", "bookshelf;blast_protection:2:11",
            "blast_protection:3:25;paper", "bookshelf;paper", "bookshelf;infinity:1:8", "mending:1:20;bookshelf",
            "paper;bookshelf", "bookshelf;loyalty:2:26", "lunge:1:15;paper", "aqua_affinity:1:18;bookshelf",
            "smite:4:43;bookshelf", "bookshelf;power:1:9", "bookshelf;fortune:3:23", "paper;frost_walker:1:32",
            "paper;bookshelf", "infinity:1:6;bookshelf", "bookshelf;paper",
        )
        val seq = LibrarianSequence(java.lang.Long.parseUnsignedLong("8848607748424184544"), 1)
        val actual = seq.range(1, expected.size).map { r ->
            r.offers.joinToString(";") {
                when (it) {
                    is LibrarianOffer.Book -> "${it.enchantment}:${it.level}:${it.emeralds}"
                    is LibrarianOffer.Item -> if (it.givesItem == "emerald") it.wantsItem else it.givesItem
                }
            }
        }
        assertEquals(expected, actual)
        assertEquals(listOf(7), seq.match(listOf("piercing" to 3, "punch" to 1, "loyalty" to 1), limit = 2000))
        assertEquals(15, seq.next("mending", 1, 0)?.number)
    }
}
