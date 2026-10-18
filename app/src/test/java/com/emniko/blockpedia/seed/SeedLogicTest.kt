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
}
