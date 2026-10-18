package com.emniko.blockpedia.seed

/** Slime chunk rules for both editions. Pure Kotlin, so it runs anywhere and is unit-tested. */
object SlimeChunks {

    fun isSlime(edition: Edition, seed: Long, chunkX: Int, chunkZ: Int): Boolean = when (edition) {
        Edition.Java -> java(seed, chunkX, chunkZ)
        Edition.Bedrock -> bedrock(chunkX, chunkZ)
    }

    /** Java Edition (Minecraft Wiki, "Slime"): seeded java.util.Random, nextInt(10) == 0. */
    fun java(seed: Long, chunkX: Int, chunkZ: Int): Boolean {
        val s = (seed +
            (chunkX * chunkX * 0x4c1906).toLong() +
            (chunkX * 0x5ac0db).toLong() +
            (chunkZ * chunkZ).toLong() * 0x4307a7L +
            (chunkZ * 0x5f24f).toLong()) xor 0x3ad8025fL
        return JavaRandom(s).nextInt(10) == 0
    }

    /** Bedrock Edition: independent of the seed. MT19937 seeded with (x * 0x1f1f1f1f) ^ z. */
    fun bedrock(chunkX: Int, chunkZ: Int): Boolean {
        val seed = (chunkX * 0x1f1f1f1f) xor chunkZ
        return (Mt19937.first(seed).toLong() and 0xffffffffL) % 10L == 0L
    }
}

/** java.util.Random's 48-bit LCG. */
class JavaRandom(seed: Long) {
    private var state = (seed xor MULTIPLIER) and MASK

    private fun next(bits: Int): Int {
        state = (state * MULTIPLIER + 0xBL) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        if (bound and -bound == bound) return ((bound.toLong() * next(31)) shr 31).toInt()
        var bits: Int
        var value: Int
        do {
            bits = next(31)
            value = bits % bound
        } while (bits - value + (bound - 1) < 0)
        return value
    }

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val MASK = (1L shl 48) - 1
    }
}

/** MT19937. Only the first output is needed, which depends on state words 0, 1 and 397. */
object Mt19937 {
    fun first(seed: Int): Int {
        val s = IntArray(398)
        s[0] = seed
        for (i in 1..397) s[i] = 1812433253 * (s[i - 1] xor (s[i - 1] ushr 30)) + i
        val y = (s[0] and 0x80000000.toInt()) or (s[1] and 0x7fffffff)
        var v = s[397] xor (y ushr 1)
        if (y and 1 != 0) v = v xor 0x9908b0df.toInt()
        v = v xor (v ushr 11)
        v = v xor ((v shl 7) and 0x9d2c5680.toInt())
        v = v xor ((v shl 15) and 0xefc60000.toInt())
        v = v xor (v ushr 18)
        return v
    }
}
