package com.emniko.blockpedia.seed

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
)

data class Tile(
    val image: ImageBitmap,
    val features: Map<StructureKind, List<MapPoint>> = emptyMap(),
    /** Ravine floors at block resolution (only on [RAVINE_SCALE] tiles with ravines on). */
    val ravineFloor: ShortArray? = null,
    val ravineImage: ImageBitmap? = null,
    /** Structure positions whose chests hold an enchanted golden apple. */
    val apples: Set<Pos> = emptySet(),
)

/** What is still loading for the tiles on screen. [fraction] is 0..1 over all of their work. */
data class TileProgress(
    val mapLeft: Int = 0,
    val slowLeft: Int = 0,
    val slowLabel: String? = null,
    val fraction: Float = 1f,
) {
    val busy: Boolean get() = mapLeft > 0 || slowLeft > 0
}

const val CELLS = 64

/** Ravines are drawn at block resolution, on the most zoomed-in tiles only. */
const val RAVINE_SCALE = 4

/** Background where biomes aren't shown (Bedrock Nether). */
private const val PLAIN_NETHER = 0xFF4A1E1E.toInt()
val SCALES = intArrayOf(4, 16, 64, 256)

/**
 * Loads tiles in steps so the map fills in quickly: first the biome picture of every tile on
 * screen (nearest the middle first), then the shown structures and quick features, then the slow
 * simulated layers (dungeons, caves, ravines) a quarter tile at a time. Tiles just outside the
 * screen get their biomes and structures afterwards, so panning shows something at once.
 * Only the kinds passed to [setKinds] are computed; turning one on later just adds its work.
 */
class MapTiles(
    private val engine: SeedEngine,
    parentScope: CoroutineScope,
    val options: MapOptions,
    shown: Set<StructureKind> = emptySet(),
) {
    // Off the main thread: workers also turn biome ids into pictures.
    private val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Default)

    private enum class Step(val rank: Int) { Base(0), Features(1), Ravines(2), Slow(2) }

    private data class Work(val key: TileKey, val step: Step, val kind: StructureKind? = null, val part: Int = 0)

    private class Entry {
        @Volatile var tile: Tile? = null
        val done = HashSet<Work>()
        val running = HashSet<Work>()
    }

    private val lock = Any()
    private val entries = object : LruCache<TileKey, Entry>(320) {}
    private var visible: List<TileKey> = emptyList()
    private var nearby: List<TileKey> = emptyList()
    private var workers = 0
    @Volatile private var shown: Set<StructureKind> = shown

    private val _version = MutableStateFlow(0)
    /** Bumped whenever a tile changes, so the map redraws. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _progress = MutableStateFlow(TileProgress())
    val progress: StateFlow<TileProgress> = _progress.asStateFlow()

    private val kinds = StructureKind.forWorld(engine.edition, engine.dimension)
    private val java = engine.edition == Edition.Java

    fun get(key: TileKey): Tile? = entries.get(key)?.tile

    /**
     * Loads [visible] tiles (ordered most important first) completely and [nearby] ones' biomes
     * and structures; work for tiles no longer listed is dropped.
     */
    fun request(visible: List<TileKey>, nearby: List<TileKey> = emptyList()) {
        synchronized(lock) {
            this.visible = visible
            this.nearby = nearby
        }
        kick()
    }

    /** The kinds to compute and show. */
    fun setKinds(kinds: Set<StructureKind>) {
        shown = kinds
        kick()
    }

    private fun kick() {
        val start = synchronized(lock) {
            updateProgress()
            (WORKERS - workers).also { workers = WORKERS }
        }
        repeat(start) { scope.launch { work() } }
    }

    /** Stops all loading; used when the options change and a new [MapTiles] takes over. */
    fun close() {
        scope.cancel()
    }

    private fun stepsFor(key: TileKey): List<Work> = buildList {
        add(Work(key, Step.Base))
        val shown = shown
        for (kind in kinds) {
            if (kind !in shown || key.scale > kind.maxScale) continue
            if (kind.heavy) {
                for (part in 0 until 4) add(Work(key, Step.Slow, kind, part))
            } else {
                add(Work(key, Step.Features, kind))
            }
        }
        if (options.ravines && java && engine.dimension == Dimension.Overworld && key.scale == RAVINE_SCALE) {
            add(Work(key, Step.Ravines))
        }
    }

    private fun entry(key: TileKey): Entry = entries.get(key) ?: Entry().also { entries.put(key, it) }

    /** The most important step not yet done or running. Called with [lock] held. */
    private fun next(): Work? {
        val passes = listOf(visible to 0, nearby to 0, visible to 1, visible to 2, nearby to 1)
        for ((keys, rank) in passes) {
            for (key in keys) {
                val e = entry(key)
                if (rank > 0 && e.tile == null) continue
                for (w in stepsFor(key)) {
                    if (w.step.rank != rank || w in e.done || w in e.running) continue
                    e.running += w
                    return w
                }
            }
        }
        return null
    }

    private suspend fun work() {
        while (true) {
            val w = synchronized(lock) {
                next().also { if (it == null) workers-- }
            } ?: return
            val e = synchronized(lock) { entry(w.key) }
            try {
                run(w, e)
            } catch (c: CancellationException) {
                synchronized(lock) {
                    e.running -= w
                    workers--
                }
                throw c
            } catch (_: Exception) {
                // Leave this step out; the tile keeps whatever it already has.
            }
            synchronized(lock) {
                e.running -= w
                e.done += w
                updateProgress()
            }
            _version.update { it + 1 }
        }
    }

    private suspend fun run(w: Work, e: Entry) {
        val key = w.key
        when (w.step) {
            Step.Base -> {
                e.tile = Tile(baseImage(key) ?: return)
            }
            Step.Features -> {
                val kind = w.kind ?: return
                val x0 = key.blockX
                val z0 = key.blockZ
                val list = engine.features(kind, x0, z0, x0 + key.blockSize, z0 + key.blockSize) ?: return
                if (list.isEmpty()) return
                val apples = HashSet<Pos>()
                if (options.apples && java && kind.canHaveApples) {
                    for (p in list) {
                        if (engine.lootCount(kind.id, p.pos.x, p.pos.z, "enchanted_golden_apple") > 0) apples += p.pos
                    }
                }
                update(e) { it.copy(features = it.features + (kind to list), apples = it.apples + apples) }
            }
            Step.Ravines -> {
                val floor = engine.ravines(key.blockX / 16, key.blockZ / 16, key.blockSize / 16) ?: return
                if (floor.any { it != SeedEngine.NO_RAVINE }) {
                    val image = ravineBitmap(floor, key.blockSize).asImageBitmap()
                    update(e) { it.copy(ravineFloor = floor, ravineImage = image) }
                }
            }
            Step.Slow -> {
                val kind = w.kind ?: return
                val half = key.blockSize / 2
                val x0 = key.blockX + (w.part % 2) * half
                val z0 = key.blockZ + (w.part / 2) * half
                val list = engine.features(kind, x0, z0, x0 + half, z0 + half) ?: return
                if (list.isNotEmpty()) update(e) { it.copy(features = it.features + (kind to (it.features[kind].orEmpty() + list))) }
            }
        }
    }

    private inline fun update(e: Entry, change: (Tile) -> Tile) {
        synchronized(lock) { e.tile?.let { e.tile = change(it) } }
    }

    /** Called with [lock] held. */
    private fun updateProgress() {
        var total = 0
        var done = 0
        var mapLeft = 0
        var slowLeft = 0
        var slowKind: StructureKind? = null
        for (key in visible) {
            val e = entries.get(key)
            for (w in stepsFor(key)) {
                total++
                if (e != null && w in e.done) {
                    done++
                    continue
                }
                if (w.step.rank < 2) {
                    mapLeft++
                } else {
                    slowLeft++
                    if (slowKind == null) slowKind = w.kind
                }
            }
        }
        val label = when {
            slowLeft == 0 -> null
            slowKind == StructureKind.Dungeon -> "Finding dungeons"
            slowKind == StructureKind.Cave -> "Finding caves"
            else -> "Tracing ravines"
        }
        _progress.value = TileProgress(mapLeft, slowLeft, label, if (total == 0) 1f else done.toFloat() / total)
    }

    private suspend fun baseImage(key: TileKey): ImageBitmap? {
        val pixels = if (engine.showsBiomes) {
            val ids = engine.biomes(key.scale, key.tx * CELLS, key.tz * CELLS, CELLS, CELLS, options.biomeY) ?: return null
            IntArray(ids.size) { BiomePalette.color(ids[it]) }
        } else {
            IntArray(CELLS * CELLS) { PLAIN_NETHER }
        }
        if (options.terrain && engine.dimension == Dimension.Overworld) shade(key, pixels)
        return Bitmap.createBitmap(pixels, CELLS, CELLS, Bitmap.Config.ARGB_8888).asImageBitmap()
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

    private companion object {
        /** Matches the engine's generator pool, so a slow step never blocks all others. */
        const val WORKERS = SeedEngine.POOL_SIZE
    }
}
