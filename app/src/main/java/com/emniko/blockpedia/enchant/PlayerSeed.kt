package com.emniko.blockpedia.enchant

/*
 * The player's random number generator, which picks each new XP seed. Method from
 * Earthcomputer's EnchantmentCracker (MIT licence).
 */
object PlayerSeed {
    private const val MULT = 0x5DEECE66DL
    private const val MASK = (1L shl 48) - 1
    private const val HIGH = 0xFFFF_FFFF_0000L

    /** Random calls used by throwing one item out (Q). */
    const val CALLS_PER_THROW = 4

    /** Longest manipulation we look for: 32 stacks of throws. */
    const val MAX_THROWS = 64 * 32

    fun step(state: Long, times: Int = 1): Long {
        var s = state
        repeat(times) { s = (s * MULT + 0xBL) and MASK }
        return s
    }

    /** The XP seed the player has while the generator is at [state] (it was the last nextInt()). */
    fun xpSeed(state: Long): Int = (state ushr 16).toInt()

    /**
     * The generator state right after it produced [second], given two XP seeds in a row
     * (nothing else used the player's random numbers between the two enchantments).
     */
    fun fromXpSeeds(first: Int, second: Int): Long? {
        val high1 = (first.toLong() shl 16) and HIGH
        val high2 = (second.toLong() shl 16) and HIGH
        for (low in 0 until 65536) {
            val next = step(high1 or low.toLong())
            if (next and HIGH == high2) return next
        }
        return null
    }
}

/**
 * How to get the wanted enchantments: throw [throws] items one by one, enchant any dummy item in
 * the top slot, then enchant the real item with [shelves] bookshelves in [slot] (0-based).
 * [throws] = -1 means the current XP seed already works: no throws and no dummy.
 */
data class Manipulation(val throws: Int, val shelves: Int, val slot: Int, val offer: Offer) {
    val needsDummy get() = throws >= 0

    /** The generator state after doing all of it. */
    fun after(state: Long): Long {
        var s = state
        if (needsDummy) s = PlayerSeed.step(s, throws * PlayerSeed.CALLS_PER_THROW + 1)
        return PlayerSeed.step(s)
    }
}

object Manipulator {
    /** Whether [offer] has every wanted enchantment at least at the wanted level. */
    fun satisfies(offer: Offer, wanted: Map<String, Int>): Boolean = offer.cost > 0 &&
        wanted.all { (id, level) -> offer.enchants.any { it.id == id && it.level >= level } }

    /**
     * The fewest throws (then fewest bookshelves, then the cheapest slot) that give [wanted].
     * [level] is the player's XP level: the dummy enchantment costs one of them.
     */
    fun find(state: Long, item: TableItem, wanted: Map<String, Int>, maxShelves: Int, level: Int): Manipulation? {
        var s = state
        for (throws in -1..PlayerSeed.MAX_THROWS) {
            val xp = if (throws == -1) PlayerSeed.xpSeed(s) else PlayerSeed.xpSeed(PlayerSeed.step(s))
            val budget = if (throws == -1) level else level - 1
            for (shelves in 0..maxShelves) {
                val offers = EnchantTable.offers(xp, item, shelves)
                for (o in offers) {
                    if (o.cost == 0 || o.cost > budget) continue
                    if (satisfies(o, wanted)) return Manipulation(throws, shelves, o.slot, o)
                }
            }
            if (throws >= 0) s = PlayerSeed.step(s, PlayerSeed.CALLS_PER_THROW)
        }
        return null
    }
}
