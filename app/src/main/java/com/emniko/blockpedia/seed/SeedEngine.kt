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

    /** Biome ids for a w*h grid; (x, z) is the corner in scaled units; [y] a block height or [SURFACE]. */
    suspend fun biomes(scale: Int, x: Int, z: Int, w: Int, h: Int, y: Int = SURFACE): IntArray? = use { handle ->
        val out = IntArray(w * h)
        if (SeedNative.nativeBiomes(handle, scale, x, z, w, h, y, out)) out else null
    }

    suspend fun biomeAt(x: Int, z: Int, y: Int = SURFACE): Int = use { SeedNative.nativeBiomeAt(it, x, z, y) }

    /** Structures or point layers of [kind] in [x0, x1) x [z0, z1). Null if unsupported here. */
    suspend fun features(kind: StructureKind, x0: Int, z0: Int, x1: Int, z1: Int): List<MapPoint>? =
        if (kind.pointLayer == 0) {
            structures(kind, x0, z0, x1, z1)?.map { MapPoint(it) }
        } else {
            points(kind.pointLayer, x0, z0, x1, z1)?.map { MapPoint(Pos(it[0], it[2]), it[1].takeIf { y -> y != Int.MIN_VALUE }, it[3]) }
        }

    /** Raw (x, y, z, info) groups from a native point layer. */
    suspend fun points(layer: Int, x0: Int, z0: Int, x1: Int, z1: Int): List<IntArray>? = use { handle ->
        var buf = IntArray(4096)
        var n = SeedNative.nativePoints(handle, layer, x0, z0, x1, z1, buf)
        if (n == buf.size / 4) {
            buf = IntArray(65536)
            n = SeedNative.nativePoints(handle, layer, x0, z0, x1, z1, buf)
        }
        if (n < 0) null else List(n) { buf.copyOfRange(4 * it, 4 * it + 4) }
    }

    /** Ore blocks (x, y, z) in one chunk: diamonds in the Overworld, ancient debris in the Nether. */
    suspend fun chunkOres(chunkX: Int, chunkZ: Int): List<IntArray>? {
        val layer = when (dimension) {
            Dimension.Overworld -> LAYER_DIAMOND
            Dimension.Nether -> LAYER_DEBRIS
            Dimension.End -> return null
        }
        return points(layer, chunkX * 16, chunkZ * 16, chunkX * 16 + 16, chunkZ * 16 + 16)
    }

    /** Variant flags (see [variantText]) and Y if known. */
    suspend fun variant(structureId: Int, x: Int, z: Int): Pair<Int, Int?> = use { handle ->
        val y = IntArray(1)
        val flags = SeedNative.nativeVariant(handle, structureId, x, z, y)
        flags to y[0].takeIf { it != Int.MIN_VALUE }
    }

    /** Chest loot as text lines "chest\titem\tcount", or null if the structure has no supported loot. */
    suspend fun loot(structureId: Int, x: Int, z: Int): String? = use { SeedNative.nativeLoot(it, structureId, x, z) }

    suspend fun lootCount(structureId: Int, x: Int, z: Int, item: String): Int =
        use { SeedNative.nativeLootCount(it, structureId, x, z, item) }

    /** Lowest ravine floor Y per block column for 16x16 chunks from (cx0, cz0); [NO_RAVINE] where none. */
    suspend fun ravines(cx0: Int, cz0: Int, chunks: Int): ShortArray? = use { handle ->
        val out = ShortArray(chunks * 16 * chunks * 16)
        if (SeedNative.nativeRavines(handle, cx0, cz0, chunks, chunks, out) < 0) null else out
    }

    /** Approximate surface height per cell; (x, z) is the corner in scaled units. */
    suspend fun heights(scale: Int, x: Int, z: Int, w: Int, h: Int): IntArray? = use { handle ->
        val out = IntArray(w * h)
        if (SeedNative.nativeHeights(handle, scale, x, z, w, h, out)) out else null
    }

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
        const val SURFACE = Int.MIN_VALUE
        const val NO_RAVINE: Short = 32767
        private const val LAYER_DIAMOND = 6
        private const val LAYER_DEBRIS = 7
        const val POOL_SIZE = 3
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(POOL_SIZE)
    }
}
