package com.emniko.blockpedia.seed

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Map tiles: 64x64 biome cells at one of [SCALES] blocks per cell, plus their structures. */
data class TileKey(val scale: Int, val tx: Int, val tz: Int) {
    val blockSize: Int get() = CELLS * scale
    val blockX: Int get() = tx * blockSize
    val blockZ: Int get() = tz * blockSize
}

class Tile(val image: ImageBitmap, val structures: Map<StructureKind, List<Pos>>)

const val CELLS = 64

/** Background where biomes aren't shown (Bedrock Nether). */
private const val PLAIN_NETHER = 0xFF4A1E1E.toInt()
val SCALES = intArrayOf(4, 16, 64, 256)

class MapTiles(private val engine: SeedEngine, private val scope: CoroutineScope) {

    private val cache = object : LruCache<TileKey, Tile>(240) {}
    private val inFlight = HashSet<TileKey>()
    private var wanted: Set<TileKey> = emptySet()

    private val _version = MutableStateFlow(0)
    /** Bumped whenever a tile finishes, so the map redraws. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val kinds = StructureKind.forWorld(engine.edition, engine.dimension)

    fun get(key: TileKey): Tile? = cache.get(key)

    /** Loads the given tiles, most important first; tiles no longer wanted are skipped. */
    fun request(keys: List<TileKey>) {
        synchronized(inFlight) { wanted = keys.toHashSet() }
        for (key in keys) {
            if (cache.get(key) != null) continue
            val added = synchronized(inFlight) { inFlight.add(key) }
            if (!added) continue
            scope.launch {
                try {
                    val stillWanted = synchronized(inFlight) { key in wanted }
                    if (stillWanted) {
                        build(key)?.let {
                            cache.put(key, it)
                            _version.value++
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Leave the tile empty; it is retried the next time it is requested.
                } finally {
                    synchronized(inFlight) { inFlight.remove(key) }
                }
            }
        }
    }

    private suspend fun build(key: TileKey): Tile? {
        val pixels = if (engine.showsBiomes) {
            val ids = engine.biomes(key.scale, key.tx * CELLS, key.tz * CELLS, CELLS, CELLS) ?: return null
            IntArray(ids.size) { BiomePalette.color(ids[it]) }
        } else {
            IntArray(CELLS * CELLS) { PLAIN_NETHER }
        }
        val bitmap = Bitmap.createBitmap(pixels, CELLS, CELLS, Bitmap.Config.ARGB_8888)

        val x0 = key.blockX
        val z0 = key.blockZ
        val structures = HashMap<StructureKind, List<Pos>>()
        for (kind in kinds) {
            if (key.scale > kind.maxScale) continue
            val found = engine.structures(kind, x0, z0, x0 + key.blockSize, z0 + key.blockSize) ?: continue
            if (found.isNotEmpty()) structures[kind] = found
        }
        return Tile(bitmap.asImageBitmap(), structures)
    }
}
