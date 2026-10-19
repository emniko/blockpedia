package com.emniko.blockpedia.seed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/**
 * The world for one seed/edition/version. Native generators aren't thread-safe, so the engine
 * keeps a small pool and each call borrows one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeedEngine(val config: SeedConfig, val dimension: Dimension = Dimension.Overworld) {

    val mc: Int = SeedNative.nativeVersionId(config.version.cubiomes)
    val edition: Edition get() = config.edition

    /** Bedrock's Nether biomes aren't modelled, so its Nether map shows structures only. */
    val showsBiomes: Boolean get() = edition == Edition.Java || dimension == Dimension.Overworld

    private val pool = Channel<Long>(POOL_SIZE)
    private val lock = Any()
    private var closed = false

    init {
        repeat(POOL_SIZE) {
            pool.trySend(SeedNative.nativeCreate(config.seed, mc, config.edition.nativeId, dimension.nativeId))
        }
    }

    private suspend fun <T> use(block: (Long) -> T): T = withContext(dispatcher) {
        val handle = pool.receive()
        try {
            block(handle)
        } finally {
            synchronized(lock) {
                if (closed || pool.trySend(handle).isFailure) SeedNative.nativeFree(handle)
            }
        }
    }

    /** Biome ids for a w*h grid; (x, z) is the corner in scaled units. */
    suspend fun biomes(scale: Int, x: Int, z: Int, w: Int, h: Int): IntArray? = use { handle ->
        val out = IntArray(w * h)
        if (SeedNative.nativeBiomes(handle, scale, x, z, w, h, out)) out else null
    }

    suspend fun biomeAt(x: Int, z: Int): Int = use { SeedNative.nativeBiomeAt(it, x, z) }

    /** Structures of [kind] in the block rectangle [x0, x1) x [z0, z1). Null if unsupported. */
    suspend fun structures(kind: StructureKind, x0: Int, z0: Int, x1: Int, z1: Int): List<Pos>? = use { handle ->
        var buf = IntArray(2048)
        var n = SeedNative.nativeStructures(handle, kind.id, x0, z0, x1, z1, buf)
        if (n == buf.size / 2) {
            buf = IntArray(65536)
            n = SeedNative.nativeStructures(handle, kind.id, x0, z0, x1, z1, buf)
        }
        if (n < 0) null else List(n) { Pos(buf[2 * it], buf[2 * it + 1]) }
    }

    suspend fun spawn(): Pos? = use { handle ->
        val out = IntArray(2)
        if (SeedNative.nativeSpawn(handle, out)) Pos(out[0], out[1]) else null
    }

    suspend fun strongholds(): List<Pos> = use { handle ->
        val out = IntArray(256)
        val n = SeedNative.nativeStrongholds(handle, out)
        List(n) { Pos(out[2 * it], out[2 * it + 1]) }
    }

    fun biomeName(id: Int): String = BiomePalette.name(mc, id)

    /** Frees native memory: idle generators now, busy ones when their call returns. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            while (true) {
                val h = pool.tryReceive().getOrNull() ?: break
                SeedNative.nativeFree(h)
            }
            // Callers still waiting for a generator fail instead of hanging.
            pool.close()
        }
    }

    companion object {
        const val POOL_SIZE = 3
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(POOL_SIZE)
    }
}
