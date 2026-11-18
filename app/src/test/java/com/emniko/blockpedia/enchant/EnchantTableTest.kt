package com.emniko.blockpedia.enchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reference files were produced by running Earthcomputer's EnchantmentCracker (Java, "1.21.11 -
 * latest") on the same inputs, so these tests check the port gives identical answers.
 */
class EnchantTableTest {

    private fun resource(name: String): List<String> =
        javaClass.classLoader!!.getResource("enchant/$name")!!.readText().lines().filter { it.isNotBlank() }

    private fun item(id: String): TableItem {
        val kinds = mapOf(
            "helmet" to ItemKind.Helmet, "chestplate" to ItemKind.Chestplate, "leggings" to ItemKind.Leggings,
            "boots" to ItemKind.Boots, "sword" to ItemKind.Sword, "spear" to ItemKind.Spear, "axe" to ItemKind.Axe,
            "pickaxe" to ItemKind.Pickaxe, "shovel" to ItemKind.Shovel, "hoe" to ItemKind.Hoe,
        )
        val single = mapOf(
            "bow" to ItemKind.Bow, "crossbow" to ItemKind.Crossbow, "trident" to ItemKind.Trident,
            "fishing_rod" to ItemKind.FishingRod, "mace" to ItemKind.Mace, "book" to ItemKind.Book,
        )
        single[id]?.let { return TableItem(it, null) }
        val material = id.substringBefore('_')
        val kind = kinds.getValue(id.substringAfter('_'))
        val m = when (material) {
            "wooden" -> Material.Wood
            "golden" -> Material.Gold
            "turtle" -> Material.Turtle
            else -> Material.valueOf(material.replaceFirstChar { it.uppercaseChar() })
        }
        return TableItem(kind, m)
    }

    private fun ench(s: String): EnchantLevel {
        val (id, level) = s.split(':')
        return EnchantLevel(if (id == "sweeping") "sweeping_edge" else id, level.toInt())
    }

    @Test
    fun offersMatchEnchantmentCracker() {
        for (line in resource("table_vectors.txt")) {
            val parts = line.split('|')
            val head = parts[0].trim().split(' ')
            val seed = head[0].toInt()
            val it = item(head[1])
            val shelves = head[2].toInt()
            val costs = head.subList(3, 6).map { c -> c.toInt() }
            val offers = EnchantTable.offers(seed, it, shelves)
            assertEquals(line, costs, offers.map { o -> o.cost })
            for (slot in 0..2) {
                val words = parts[slot + 1].trim().split(' ').filter { w -> w.isNotEmpty() }
                val expected = words.filterNot { w -> w.startsWith("clue=") }.map(::ench)
                val clue = words.firstOrNull { w -> w.startsWith("clue=") }?.removePrefix("clue=")?.let(::ench)
                assertEquals(line, expected, offers[slot].enchants)
                assertEquals(line, clue, offers[slot].clue)
            }
        }
    }

    @Test
    fun manipulationMatchesEnchantmentCracker() {
        for (line in resource("manipulation_vectors.txt")) {
            val (input, output) = line.split(" -> ")
            val p = input.split(' ')
            val wanted = p[2].split(',').associate { w -> ench(w).let { e -> e.id to e.level } }
            val found = Manipulator.find(p[0].toLong(), item(p[1]), wanted, p[3].toInt(), p[4].toInt())
            if (output == "none") {
                assertNull(line, found)
            } else {
                val (throws, shelves, slot, cost) = output.split(' ').map { s -> s.toInt() }
                assertEquals(line, listOf(throws, shelves, slot, cost), listOf(found!!.throws, found.shelves, found.slot, found.offer.cost))
            }
        }
    }

    @Test
    fun playerSeedFromTwoXpSeeds() {
        val start = 0x1234_5678_9ABCL
        val first = PlayerSeed.step(start)
        val second = PlayerSeed.step(first)
        val state = PlayerSeed.fromXpSeeds(PlayerSeed.xpSeed(first), PlayerSeed.xpSeed(second))
        assertEquals(second, state)
        assertEquals(PlayerSeed.xpSeed(second), PlayerSeed.xpSeed(state!!))
    }

    @Test
    fun readingsNarrowToTheRealSeed() {
        // Readings the table would show for a known seed; the true seed must survive every filter.
        val seed = -287790814
        val readings = listOf(15, 14, 12, 9).map { shelves ->
            val c = EnchantTable.costs(seed, shelves)
            Reading(shelves, c[0], c[1], c[2])
        }
        assertEquals(Reading(14, 4, 17, 28), readings[1])
        assertTrue(XpSeeds.matches(seed, readings))
        // Search a window around the seed with the Kotlin fallback (the full 2^32 runs natively on the phone).
        val out = IntArray(1000)
        val n = XpSeeds.searchRange(readings, seed.toLong() - 5_000_000, 10_000_000, out)
        assertTrue(n >= 1)
        assertTrue(out.take(n).contains(seed))
        assertEquals(1, XpSeeds.filter(out.copyOf(n), readings[3]).count { it == seed })
    }
}
