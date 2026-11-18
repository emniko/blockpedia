package com.emniko.blockpedia.enchant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/*
 * XP seed and player seed cracking, following Earthcomputer's EnchantmentCracker (MIT licence).
 */

/** What the table showed: bookshelf count and the three costs (0 for an empty button). */
data class Reading(val shelves: Int, val top: Int, val middle: Int, val bottom: Int) {
    fun toIntArray() = intArrayOf(shelves, top, middle, bottom)
}

private const val MULT = 0x5DEECE66DL
private const val MASK = (1L shl 48) - 1

/** Java's Random, cut down to what the cost check needs (all calls are next(31)). */
private class CostRandom {
    private var state = 0L
    fun seed(seed: Int) {
        state = (seed.toLong() xor MULT) and MASK
    }

    private fun next(): Int {
        state = (state * MULT + 0xBL) and MASK
        return (state ushr 17).toInt()
    }

    fun nextInt(bound: Int): Int {
        var r = next()
        val m = bound - 1
        if (bound and m == 0) return ((bound * r.toLong()) shr 31).toInt()
        var u = r
        while (true) {
            r = u % bound
            if (u - r + m >= 0) return r
            u = next()
        }
    }
}

object XpSeeds {
    /** Above this many candidates we ask for another reading instead of keeping them all. */
    const val MAX_CANDIDATES = 4_000_000

    private const val CHUNKS = 256
    private const val CHUNK = (1L shl 32) / CHUNKS

    /** Room for one chunk's matches; a chunk with more than this means far too many overall. */
    private const val BUFFER = 1 shl 18

    fun matches(seed: Int, readings: List<Reading>): Boolean {
        val r = CostRandom()
        return readings.all { matches(r, seed, it) }
    }

    private fun matches(r: CostRandom, seed: Int, reading: Reading): Boolean {
        val shelves = reading.shelves
        r.seed(seed)
        val a = r.nextInt(8) + 1 + (shelves shr 1) + r.nextInt(shelves + 1)
        if (maxOf(a / 3, 1) != reading.top) return false
        val b = r.nextInt(8) + 1 + (shelves shr 1) + r.nextInt(shelves + 1)
        val middle = b * 2 / 3 + 1
        if ((if (middle < 2) 0 else middle) != reading.middle) return false
        val c = r.nextInt(8) + 1 + (shelves shr 1) + r.nextInt(shelves + 1)
        val bottom = maxOf(c, shelves * 2)
        return (if (bottom < 3) 0 else bottom) == reading.bottom
    }

    /** Pure Kotlin search of [count] seeds from [start]; used when the native library is missing. */
    fun searchRange(readings: List<Reading>, start: Long, count: Long, out: IntArray): Int {
        val r = CostRandom()
        var found = 0
        var s = start
        val end = start + count
        while (s < end) {
            val seed = s.toInt()
            if (readings.all { matches(r, seed, it) }) {
                if (found == out.size) return -1
                out[found++] = seed
            }
            s++
        }
        return found
    }

    /**
     * Every XP seed (all 2^32) that fits all [readings]. Runs on every core, in chunks, so it can be
     * cancelled. Returns null if more than [MAX_CANDIDATES] fit (take another reading first).
     */
    suspend fun crack(readings: List<Reading>, onProgress: (Float) -> Unit): IntArray? {
        val flat = readings.flatMap { it.toIntArray().toList() }.toIntArray()
        val native = EnchantNative.available
        val next = AtomicInteger(0)
        val done = AtomicInteger(0)
        val total = AtomicInteger(0)
        val overflow = AtomicBoolean(false)
        val parts = arrayOfNulls<IntArray>(CHUNKS)
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.Default) {
                    val buffer = IntArray(BUFFER)
                    while (!overflow.get()) {
                        val i = next.getAndIncrement()
                        if (i >= CHUNKS) break
                        ensureActive()
                        val start = Int.MIN_VALUE.toLong() + i * CHUNK
                        val n = if (native) {
                            EnchantNative.nativeCrack(flat, start, CHUNK, buffer)
                        } else {
                            searchRange(readings, start, CHUNK, buffer)
                        }
                        if (n < 0 || total.addAndGet(n) > MAX_CANDIDATES) {
                            overflow.set(true)
                            break
                        }
                        parts[i] = buffer.copyOf(n)
                        onProgress(done.incrementAndGet() / CHUNKS.toFloat())
                    }
                }
            }
        }
        if (overflow.get()) return null
        val out = IntArray(total.get())
        var at = 0
        for (p in parts) {
            if (p == null) continue
            p.copyInto(out, at)
            at += p.size
        }
        return out
    }

    /** Keeps the candidates that also fit [reading]. */
    fun filter(candidates: IntArray, reading: Reading): IntArray {
        val r = CostRandom()
        val out = IntArray(candidates.size)
        var n = 0
        for (seed in candidates) if (matches(r, seed, reading)) out[n++] = seed
        return out.copyOf(n)
    }
}
