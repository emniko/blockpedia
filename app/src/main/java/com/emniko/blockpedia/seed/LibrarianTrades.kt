package com.emniko.blockpedia.seed

import java.security.MessageDigest

/*
 * Librarian trade prediction for Java 26.1 and later.
 *
 * Since 26.1 a villager's trades for one profession level come from a random sequence keyed by
 * the world seed and the trade set id (e.g. minecraft:trade_set/librarian/level_1). Every
 * librarian of that level in the world draws from the same sequence, so knowing the seed and how
 * far it has advanced predicts every reroll. Trade data below is from the game's data files
 * (identical in 26.1, 26.2 and 26.3).
 */

/** One offer in a reroll: an enchanted book, or a plain item trade. */
sealed interface LibrarianOffer {
    data class Book(val enchantment: String, val level: Int, val emeralds: Int) : LibrarianOffer
    data class Item(val wants: Int, val wantsItem: String, val gives: Int, val givesItem: String) : LibrarianOffer
}

/** The offers a librarian shows after one reroll; [number] counts from 1. */
data class Reroll(val number: Int, val offers: List<LibrarianOffer>) {
    val book: LibrarianOffer.Book? get() = offers.firstOrNull { it is LibrarianOffer.Book } as LibrarianOffer.Book?
}

/** An enchantment a librarian can sell, with its highest level. */
data class TradeEnchantment(val id: String, val maxLevel: Int) {
    val name: String get() = id.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
}

object LibrarianData {
    /** #minecraft:tradeable, in tag order (the game picks by index into this list). */
    val enchantments = listOf(
        TradeEnchantment("protection", 4), TradeEnchantment("fire_protection", 4),
        TradeEnchantment("feather_falling", 4), TradeEnchantment("blast_protection", 4),
        TradeEnchantment("projectile_protection", 4), TradeEnchantment("respiration", 3),
        TradeEnchantment("aqua_affinity", 1), TradeEnchantment("thorns", 3),
        TradeEnchantment("depth_strider", 3), TradeEnchantment("sharpness", 5),
        TradeEnchantment("smite", 5), TradeEnchantment("bane_of_arthropods", 5),
        TradeEnchantment("knockback", 2), TradeEnchantment("fire_aspect", 2),
        TradeEnchantment("looting", 3), TradeEnchantment("sweeping_edge", 3),
        TradeEnchantment("efficiency", 5), TradeEnchantment("silk_touch", 1),
        TradeEnchantment("unbreaking", 3), TradeEnchantment("fortune", 3),
        TradeEnchantment("power", 5), TradeEnchantment("punch", 2),
        TradeEnchantment("flame", 1), TradeEnchantment("infinity", 1),
        TradeEnchantment("luck_of_the_sea", 3), TradeEnchantment("lure", 3),
        TradeEnchantment("loyalty", 3), TradeEnchantment("impaling", 5),
        TradeEnchantment("riptide", 3), TradeEnchantment("channeling", 1),
        TradeEnchantment("multishot", 1), TradeEnchantment("quick_charge", 3),
        TradeEnchantment("piercing", 4), TradeEnchantment("density", 5),
        TradeEnchantment("breach", 4), TradeEnchantment("lunge", 3),
        TradeEnchantment("binding_curse", 1), TradeEnchantment("vanishing_curse", 1),
        TradeEnchantment("frost_walker", 2), TradeEnchantment("mending", 1),
    )

    /** #minecraft:double_trade_price (treasure enchantments cost twice as much). */
    val doublePrice = setOf("binding_curse", "vanishing_curse", "swift_sneak", "soul_speed", "frost_walker", "mending", "wind_burst")

    /** A trade in a level's list; null [item] means the enchanted book trade. */
    class Entry(val wants: Int = 0, val wantsItem: String = "", val gives: Int = 1, val givesItem: String = "", val book: Boolean = false)

    private val book = Entry(book = true)

    /** Offers per reroll and the trade list (tag order) for librarian levels 1-5. */
    val levels: Map<Int, Pair<Int, List<Entry>>> = mapOf(
        1 to (2 to listOf(Entry(24, "paper", 1, "emerald"), book, Entry(9, "emerald", 1, "bookshelf"))),
        2 to (2 to listOf(Entry(4, "book", 1, "emerald"), book, Entry(1, "emerald", 1, "lantern"))),
        3 to (2 to listOf(Entry(5, "ink_sac", 1, "emerald"), book, Entry(1, "emerald", 4, "glass"))),
        4 to (2 to listOf(Entry(2, "writable_book", 1, "emerald"), book, Entry(5, "emerald", 1, "clock"), Entry(4, "emerald", 1, "compass"))),
        5 to (3 to listOf(Entry(3, "emerald", 1, "yellow_candle"), Entry(3, "emerald", 1, "red_candle"))),
    )

    /** Levels whose trades include enchanted books. */
    val bookLevels = listOf(1, 2, 3, 4)
}

/** The random sequence a world uses for one trade set (Xoroshiro128++ as in the game). */
class TradeRandom(worldSeed: Long, sequenceId: String) {
    private var lo: Long
    private var hi: Long

    init {
        val md5 = MessageDigest.getInstance("MD5").digest(sequenceId.toByteArray(Charsets.UTF_8))
        var hashLo = 0L
        var hashHi = 0L
        for (i in 0 until 8) hashLo = (hashLo shl 8) or (md5[i].toLong() and 0xFF)
        for (i in 8 until 16) hashHi = (hashHi shl 8) or (md5[i].toLong() and 0xFF)
        val a = worldSeed xor 0x6A09E667F3BCC909L
        val b = a + -7046029254386353131L
        lo = mix(a xor hashLo)
        hi = mix(b xor hashHi)
        if (lo or hi == 0L) {
            lo = -7046029254386353131L
            hi = 7640891576956012809L
        }
    }

    private fun mix(v: Long): Long {
        var s = (v xor (v ushr 30)) * -4658895280553007687L
        s = (s xor (s ushr 27)) * -7723592293110705685L
        return s xor (s ushr 31)
    }

    private fun nextLong(): Long {
        val l = lo
        var m = hi
        val n = java.lang.Long.rotateLeft(l + m, 17) + l
        m = m xor l
        lo = java.lang.Long.rotateLeft(l, 49) xor m xor (m shl 21)
        hi = java.lang.Long.rotateLeft(m, 28)
        return n
    }

    /** Uniform int in [0, bound), the game's method for this generator. */
    fun nextInt(bound: Int): Int {
        var l = nextLong().toInt().toLong() and 0xFFFFFFFFL
        var m = l * bound
        var n = m and 0xFFFFFFFFL
        if (n < bound) {
            val j = Integer.remainderUnsigned(-bound, bound)
            while (n < j) {
                l = nextLong().toInt().toLong() and 0xFFFFFFFFL
                m = l * bound
                n = m and 0xFFFFFFFFL
            }
        }
        return (m shr 32).toInt()
    }
}

/** Rerolls of librarians at one level in one world, from the start of the world. */
class LibrarianSequence(worldSeed: Long, val level: Int) {
    private val random = TradeRandom(worldSeed, "minecraft:trade_set/librarian/level_$level")
    private val amount = LibrarianData.levels.getValue(level).first
    private val entries = LibrarianData.levels.getValue(level).second
    private val rolled = ArrayList<Reroll>()

    /** Reroll [number] (1-based), generating the sequence up to it. */
    fun get(number: Int): Reroll {
        while (rolled.size < number) rolled += roll(rolled.size + 1)
        return rolled[number - 1]
    }

    fun range(from: Int, count: Int): List<Reroll> = (from until from + count).map { get(it) }

    private fun roll(number: Int): Reroll {
        val left = entries.toMutableList()
        val offers = ArrayList<LibrarianOffer>()
        while (offers.size < amount && left.isNotEmpty()) {
            val entry = left.removeAt(random.nextInt(left.size))
            offers += if (entry.book) book() else LibrarianOffer.Item(entry.wants, entry.wantsItem, entry.gives, entry.givesItem)
        }
        return Reroll(number, offers)
    }

    private fun book(): LibrarianOffer.Book {
        val all = LibrarianData.enchantments
        val e = all[random.nextInt(all.size)]
        val level = if (e.maxLevel <= 1) 1 else random.nextInt(e.maxLevel) + 1
        var cost = 2 + random.nextInt(5 + level * 10) + 3 * level
        if (e.id in LibrarianData.doublePrice) cost *= 2
        return LibrarianOffer.Book(e.id, level, cost.coerceAtMost(64))
    }

    /**
     * Reroll numbers where the observed books (null for a reroll without a book) appear in a row,
     * searching the first [limit] rerolls. The result is the reroll matching the last observation.
     */
    fun match(observed: List<Pair<String, Int>?>, limit: Int = 20000): List<Int> {
        if (observed.isEmpty()) return emptyList()
        val found = ArrayList<Int>()
        for (start in 1..limit) {
            var ok = true
            for (i in observed.indices) {
                val b = get(start + i).book
                val o = observed[i]
                if (if (o == null) b != null else b == null || b.enchantment != o.first || b.level != o.second) {
                    ok = false
                    break
                }
            }
            if (ok) {
                found += start + observed.size - 1
                if (found.size > 20) break
            }
        }
        return found
    }

    /** The first reroll after [after] selling [enchantment] at [minLevel] or more, within [limit]. */
    fun next(enchantment: String, minLevel: Int, after: Int, limit: Int = 20000): Reroll? {
        for (n in after + 1..after + limit) {
            val b = get(n).book ?: continue
            if (b.enchantment == enchantment && b.level >= minLevel) return get(n)
        }
        return null
    }
}

/** True for versions whose villager trades follow the world seed. */
fun tradesFollowSeed(config: SeedConfig): Boolean =
    config.edition == Edition.Java && config.version.cubiomes.startsWith("26.")

private val ROMAN = arrayOf("I", "II", "III", "IV", "V")

fun bookLabel(book: LibrarianOffer.Book): String {
    val e = LibrarianData.enchantments.firstOrNull { it.id == book.enchantment }
    val name = e?.name ?: book.enchantment
    return if (e != null && e.maxLevel == 1) name else "$name ${ROMAN.getOrElse(book.level - 1) { book.level.toString() }}"
}

fun itemName(id: String): String = id.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
