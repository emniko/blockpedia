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
import kotlin.math.roundToInt

/** Map tiles: 64x64 biome cells at one of [SCALES] blocks per cell, plus their features. */
data class TileKey(val scale: Int, val tx: Int, val tz: Int) {
    val blockSize: Int get() = CELLS * scale
    val blockX: Int get() = tx * blockSize
    val blockZ: Int get() = tz * blockSize
}

/** Map settings that change what a tile contains. A new [MapTiles] is made when they change. */
data class MapOptions(
    /** Block height to sample biomes at, or [SeedEngine.SURFACE]. */
    val biomeY: Int = SeedEngine.SURFACE,
    val terrain: Boolean = false,
    val ravines: Boolean = false,
    val apples: Boolean = false,
    /** Slow layers ([StructureKind.heavy]) to compute; the others are always computed. */
    val heavy: Set<StructureKind> = emptySet(),
)

class Tile(
    val image: ImageBitmap,
    val features: Map<StructureKind, List<MapPoint>>,
    /** Ravine floors at block resolution (only on [RAVINE_SCALE] tiles with ravines on). */
    val ravineFloor: ShortArray?,
    val ravineImage: ImageBitmap?,
    /** Structure positions whose chests hold an enchanted golden apple. */
    val apples: Set<Pos>,
)

const val CELLS = 64

/** Ravines are drawn at block resolution, on the most zoomed-in tiles only. */
const val RAVINE_SCALE = 4

/** Background where biomes aren't shown (Bedrock Nether). */
private const val PLAIN_NETHER = 0xFF4A1E1E.toInt()
val SCALES = intArrayOf(4, 16, 64, 256)

class MapTiles(
    private val engine: SeedEngine,
    private val scope: CoroutineScope,
    val options: MapOptions,
) {

    private val cache = object : LruCache<TileKey, Tile>(240) {}
    private val inFlight = HashSet<TileKey>()
    private var wanted: Set<TileKey> = emptySet()

    private val _version = MutableStateFlow(0)
    /** Bumped whenever a tile finishes, so the map redraws. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val kinds = StructureKind.forWorld(engine.edition, engine.dimension)
    private val java = engine.edition == Edition.Java

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
            val ids = engine.biomes(key.scale, key.tx * CELLS, key.tz * CELLS, CELLS, CELLS, options.biomeY) ?: return null
            IntArray(ids.size) { BiomePalette.color(ids[it]) }
        } else {
            IntArray(CELLS * CELLS) { PLAIN_NETHER }
        }
        if (options.terrain && engine.dimension == Dimension.Overworld) shade(key, pixels)
        val bitmap = Bitmap.createBitmap(pixels, CELLS, CELLS, Bitmap.Config.ARGB_8888)

        val x0 = key.blockX
        val z0 = key.blockZ
        val features = HashMap<StructureKind, List<MapPoint>>()
        val apples = HashSet<Pos>()
        for (kind in kinds) {
            if (key.scale > kind.maxScale) continue
            if (kind.heavy && kind !in options.heavy) continue
            val found = engine.features(kind, x0, z0, x0 + key.blockSize, z0 + key.blockSize) ?: continue
            if (found.isEmpty()) continue
            features[kind] = found
            if (options.apples && java && kind.canHaveApples) {
                for (p in found) {
                    if (engine.lootCount(kind.id, p.pos.x, p.pos.z, "enchanted_golden_apple") > 0) apples += p.pos
                }
            }
        }

        var ravineFloor: ShortArray? = null
        var ravineImage: ImageBitmap? = null
        if (options.ravines && java && engine.dimension == Dimension.Overworld && key.scale == RAVINE_SCALE) {
            val chunks = key.blockSize / 16
            val floor = engine.ravines(x0 / 16, z0 / 16, chunks)
            if (floor != null && floor.any { it != SeedEngine.NO_RAVINE }) {
                ravineFloor = floor
                ravineImage = ravineBitmap(floor, key.blockSize).asImageBitmap()
            }
        }
        return Tile(bitmap.asImageBitmap(), features, ravineFloor, ravineImage, apples)
    }

    /** Hill shading from approximate terrain height: lit slopes facing north-west, shaded otherwise. */
    private suspend fun shade(key: TileKey, pixels: IntArray) {
        val n = CELLS + 1
        val h = engine.heights(key.scale, key.tx * CELLS - 1, key.tz * CELLS - 1, n, n) ?: return
        for (j in 0 until CELLS) for (i in 0 until CELLS) {
            val here = h[(j + 1) * n + (i + 1)]
            val slope = ((here - h[(j + 1) * n + i]) + (here - h[j * n + (i + 1)])).toFloat() / key.scale
            val light = (1f + slope * 0.6f).coerceIn(0.55f, 1.4f)
            val c = pixels[j * CELLS + i]
            val r = ((c shr 16 and 0xFF) * light).roundToInt().coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) * light).roundToInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * light).roundToInt().coerceIn(0, 255)
            pixels[j * CELLS + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** Ravines as a translucent overlay: light orange where shallow, dark red where deep. */
    private fun ravineBitmap(floor: ShortArray, size: Int): Bitmap {
        val px = IntArray(size * size) { i ->
            val y = floor[i].toInt()
            if (y == SeedEngine.NO_RAVINE.toInt()) {
                0
            } else {
                val t = ((y + 64) / 136f).coerceIn(0f, 1f) // Y -64 (deep) .. 72 (shallow)
                val r = (120 + 135 * t).roundToInt()
                val g = (20 + 130 * t).roundToInt()
                (0xD0 shl 24) or (r shl 16) or (g shl 8) or 30
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}
