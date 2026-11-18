package com.emniko.blockpedia.enchant

/** Fast XP seed search in C (src/main/cpp/enchant_crack.c), part of the seedmap library. */
object EnchantNative {
    val available: Boolean = runCatching { System.loadLibrary("seedmap") }.isSuccess

    /** [readings] is [shelves, top, middle, bottom] repeated; returns matches in [out] or -1 if it filled up. */
    external fun nativeCrack(readings: IntArray, start: Long, count: Long, out: IntArray): Int
}
