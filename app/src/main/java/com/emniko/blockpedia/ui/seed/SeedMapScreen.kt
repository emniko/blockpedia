package com.emniko.blockpedia.ui.seed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import android.content.Context
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.emniko.blockpedia.seed.STRONGHOLD_ICON_URL
import com.emniko.blockpedia.seed.STRONGHOLD_SPAWNERS
import com.emniko.blockpedia.seed.spawners
import com.emniko.blockpedia.seed.iconUrl
import kotlinx.coroutines.flow.update
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.seed.CELLS
import com.emniko.blockpedia.seed.Edition
import com.emniko.blockpedia.seed.Dimension
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.emniko.blockpedia.seed.APPLE_ICON_URL
import com.emniko.blockpedia.seed.MapOptions
import com.emniko.blockpedia.seed.MapPoint
import com.emniko.blockpedia.seed.RAVINE_SCALE
import com.emniko.blockpedia.seed.STRONGHOLD_ID
import com.emniko.blockpedia.seed.STRONGHOLD_Y_TEXT
import com.emniko.blockpedia.seed.canHaveApples
import com.emniko.blockpedia.seed.featureFacts
import com.emniko.blockpedia.seed.hasLoot
import com.emniko.blockpedia.seed.hasVariants
import com.emniko.blockpedia.seed.variantText
import com.emniko.blockpedia.seed.yLevelText
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.emniko.blockpedia.seed.MapTiles
import com.emniko.blockpedia.seed.Pos
import com.emniko.blockpedia.seed.SCALES
import com.emniko.blockpedia.seed.SeedEngine
import com.emniko.blockpedia.seed.SlimeChunks
import com.emniko.blockpedia.seed.StructureKind
import com.emniko.blockpedia.seed.TileKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MIN_BPP = 0.125   // 8 px per block
private const val MAX_BPP = 96.0
private const val SLIME_MAX_BPP = 1.0
private const val TAP_RADIUS_DP = 24

class SeedMapViewModel(val engine: SeedEngine, appContext: Context) : ViewModel() {
    /** Replaced when the map options change, which starts a fresh tile cache. */
    var tiles by mutableStateOf(
        MapTiles(engine, viewModelScope, MapOptions(heavy = StructureKind.forWorld(engine.edition, engine.dimension).filter { it.heavy }.toSet()))
    )
        private set

    fun setOptions(options: MapOptions) {
        if (options != tiles.options) tiles = MapTiles(engine, viewModelScope, options)
    }

    /** Shows [kinds]; slow layers are only computed while shown. */
    fun setEnabled(kinds: Set<StructureKind>) {
        enabled.value = kinds
        setOptions(tiles.options.copy(heavy = kinds.filter { it.heavy }.toSet()))
    }

    val spawn = MutableStateFlow<Pos?>(null)
    val strongholds = MutableStateFlow<List<Pos>>(emptyList())

    val kinds = StructureKind.forWorld(engine.edition, engine.dimension)
    // Buried treasure and mineshafts are very common, so they start hidden.
    val enabled = MutableStateFlow(
        kinds.filter { it != StructureKind.BuriedTreasure && it != StructureKind.Mineshaft }.toSet()
    )
    val showSlime = MutableStateFlow(false)
    val showStrongholds = MutableStateFlow(true)

    /** Wiki structure icons for the map canvas, by URL. Markers show letters until they load. */
    val icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    init {
        val loader = appContext.imageLoader
        val urls = (kinds.map { it.iconUrl } + STRONGHOLD_ICON_URL + APPLE_ICON_URL).distinct()
        for (url in urls) {
            viewModelScope.launch {
                val request = ImageRequest.Builder(appContext).data(url).allowHardware(false).build()
                val bitmap = safely { (loader.execute(request) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap() }
                if (bitmap != null) icons.update { it + (url to bitmap) }
            }
        }
        if (engine.edition == Edition.Java && engine.dimension == Dimension.Overworld) {
            viewModelScope.launch {
                spawn.value = safely { engine.spawn() }
                strongholds.value = safely { engine.strongholds() } ?: emptyList()
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** Something on the map the user tapped. */
private sealed interface Picked {
    val pos: Pos
    data class Structure(val kind: StructureKind, val point: MapPoint) : Picked {
        override val pos: Pos get() = point.pos
    }
    data class Stronghold(override val pos: Pos) : Picked
    data class Spawn(override val pos: Pos) : Picked
    data class Point(override val pos: Pos) : Picked
}

private data class Marker(val kind: StructureKind, val point: MapPoint, val apple: Boolean) {
    val pos: Pos get() = point.pos
}

/** Extra facts about a tapped spot, loaded after the tap. */
private data class PickDetails(
    val biome: String? = null,
    val lines: List<String> = emptyList(),
    val loot: List<String> = emptyList(),
    val ores: List<String> = emptyList(),
    val oreTitle: String? = null,
    val loading: Boolean = true,
)

/** Coarsest biome cell size that still looks sharp at this zoom. */
private fun scaleFor(bpp: Double): Int = SCALES.firstOrNull { it >= bpp * 3 } ?: SCALES.last()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeedMapScreen(startX: Int?, startZ: Int?, startDimension: Dimension, onBack: () -> Unit) {
    val store = Graph.seedStore
    val config = remember { store.config().getOrNull() }
    if (config == null) {
        MissingWorld(onBack)
        return
    }
    val dimensions = remember(config) { Dimension.forEdition(config.edition) }
    var dimension by rememberSaveable { mutableStateOf(startDimension.takeIf { it in dimensions } ?: Dimension.Overworld) }
    val engine = remember(config, dimension) { store.engine(config, dimension) }
    val appContext = LocalContext.current.applicationContext
    val vm: SeedMapViewModel = viewModel(key = "seedmap:$config:$dimension") { SeedMapViewModel(engine, appContext) }
    val icons by vm.icons.collectAsState()

    val spawn by vm.spawn.collectAsState()
    val strongholds by vm.strongholds.collectAsState()
    val enabled by vm.enabled.collectAsState()
    val showSlime by vm.showSlime.collectAsState()
    val showStrongholds by vm.showStrongholds.collectAsState()
    val tiles = vm.tiles
    val options = tiles.options
    val tileVersion by tiles.version.collectAsState()

    var centerX by rememberSaveable { mutableDoubleStateOf(startX?.toDouble() ?: 0.0) }
    var centerZ by rememberSaveable { mutableDoubleStateOf(startZ?.toDouble() ?: 0.0) }
    var bpp by rememberSaveable { mutableDoubleStateOf(if (startX != null) 0.5 else 2.0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var picked by remember { mutableStateOf<Picked?>(if (startX != null && startZ != null) Picked.Point(Pos(startX, startZ)) else null) }
    var showLayers by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }

    // Center on spawn the first time it is known, if no start position was given.
    var centeredOnSpawn by rememberSaveable { mutableStateOf(startX != null) }
    LaunchedEffect(spawn) {
        val s = spawn
        if (s != null && !centeredOnSpawn) {
            centeredOnSpawn = true
            centerX = s.x.toDouble()
            centerZ = s.z.toDouble()
        }
    }

    val scale = scaleFor(bpp)
    val tileBlocks = CELLS * scale
    val halfW = canvasSize.width / 2.0 * bpp
    val halfH = canvasSize.height / 2.0 * bpp
    val tx0 = floor((centerX - halfW) / tileBlocks).toInt()
    val tx1 = floor((centerX + halfW) / tileBlocks).toInt()
    val tz0 = floor((centerZ - halfH) / tileBlocks).toInt()
    val tz1 = floor((centerZ + halfH) / tileBlocks).toInt()
    val visible = remember(scale, tx0, tx1, tz0, tz1) {
        val ctx = (tx0 + tx1) / 2.0
        val ctz = (tz0 + tz1) / 2.0
        buildList {
            for (tz in tz0..tz1) for (tx in tx0..tx1) add(TileKey(scale, tx, tz))
        }.sortedBy { abs(it.tx - ctx) + abs(it.tz - ctz) }
    }
    // Keyed on the view model too: switching dimension keeps the same tile coordinates
    // but needs the new dimension's tiles.
    LaunchedEffect(vm, tiles, visible, canvasSize) {
        if (canvasSize != IntSize.Zero) tiles.request(visible)
    }

    // Structure markers from loaded tiles.
    val markers = remember(vm, tiles, visible, tileVersion, enabled) {
        buildList {
            for (key in visible) {
                val tile = tiles.get(key) ?: continue
                for ((kind, list) in tile.features) {
                    if (kind !in enabled) continue
                    list.forEach { add(Marker(kind, it, it.pos in tile.apples)) }
                }
            }
        }
    }

    // Slime chunks for the visible area, recomputed when it moves by a few chunks.
    val slimeOn = showSlime && bpp <= SLIME_MAX_BPP && dimension == Dimension.Overworld
    val scx0 = floor((centerX - halfW) / 16).toInt() shr 3
    val scx1 = floor((centerX + halfW) / 16).toInt() shr 3
    val scz0 = floor((centerZ - halfH) / 16).toInt() shr 3
    val scz1 = floor((centerZ + halfH) / 16).toInt() shr 3
    var slimeChunks by remember { mutableStateOf<List<Pos>>(emptyList()) }
    LaunchedEffect(slimeOn, scx0, scx1, scz0, scz1, config) {
        if (!slimeOn) {
            slimeChunks = emptyList()
            return@LaunchedEffect
        }
        slimeChunks = withContext(Dispatchers.Default) {
            buildList {
                for (cz in (scz0 - 1) * 8..(scz1 + 2) * 8) for (cx in (scx0 - 1) * 8..(scx1 + 2) * 8) {
                    if (SlimeChunks.isSlime(config.edition, config.seed, cx, cz)) add(Pos(cx, cz))
                }
            }
        }
    }

    // The tap handler is created once, so it reads the latest markers through this.
    val latestMarkers by rememberUpdatedState(markers)
    val latestSpawn by rememberUpdatedState(spawn)
    val latestStrongholds by rememberUpdatedState(strongholds)
    val latestShowStrongholds by rememberUpdatedState(showStrongholds)

    // Biome, Y levels, variants, chest loot and ores for a tapped spot.
    var details by remember { mutableStateOf(PickDetails()) }
    LaunchedEffect(picked, engine, tiles) {
        val p = picked ?: return@LaunchedEffect
        details = PickDetails()
        details = try {
            loadDetails(engine, p, options, tiles, scaleFor(bpp))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PickDetails(loading = false)
        }
    }

    val density = LocalDensity.current
    val tapRadiusPx = with(density) { TAP_RADIUS_DP.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val markerLabels = remember(textMeasurer) {
        StructureKind.entries.associateWith {
            textMeasurer.measure(it.letter.toString(), TextStyle(color = Color(0xFF1B1B1B), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
    val strongholdLabel = remember(textMeasurer) {
        textMeasurer.measure("E", TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
    val outline = MaterialTheme.colorScheme.onSurface
    val selectionColor = MaterialTheme.colorScheme.primary

    fun worldToScreen(x: Double, z: Double): Offset = Offset(
        ((x - centerX) / bpp + canvasSize.width / 2.0).toFloat(),
        ((z - centerZ) / bpp + canvasSize.height / 2.0).toFloat(),
    )

    fun screenToWorld(o: Offset): Pos = Pos(
        floor(centerX + (o.x - canvasSize.width / 2.0) * bpp).toInt(),
        floor(centerZ + (o.y - canvasSize.height / 2.0) * bpp).toInt(),
    )

    fun zoomBy(factor: Double, focus: Offset? = null) {
        val f = focus ?: Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val wx = centerX + (f.x - canvasSize.width / 2.0) * bpp
        val wz = centerZ + (f.y - canvasSize.height / 2.0) * bpp
        val nb = (bpp / factor).coerceIn(MIN_BPP, MAX_BPP)
        centerX = wx - (f.x - canvasSize.width / 2.0) * nb
        centerZ = wz - (f.y - canvasSize.height / 2.0) * nb
        bpp = nb
    }

    fun pick(o: Offset) {
        var best: Picked? = null
        var bestD = tapRadiusPx
        fun consider(p: Picked) {
            val s = worldToScreen(p.pos.x.toDouble(), p.pos.z.toDouble())
            val d = (s - o).getDistance()
            if (d <= bestD) {
                bestD = d
                best = p
            }
        }
        latestMarkers.forEach { consider(Picked.Structure(it.kind, it.point)) }
        if (latestShowStrongholds) latestStrongholds.forEach { consider(Picked.Stronghold(it)) }
        latestSpawn?.let { consider(Picked.Spawn(it)) }
        picked = best ?: Picked.Point(screenToWorld(o))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Seed map")
                        Text(
                            "${config.edition.label} · ${config.version.label} · ${config.seedText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showGoTo = true }) { Icon(Icons.Outlined.Place, contentDescription = "Go to coordinates") }
                    IconButton(onClick = { showLayers = true }) { Icon(Icons.Outlined.Layers, contentDescription = "Layers") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val background = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            centerX -= pan.x * bpp
                            centerZ -= pan.y * bpp
                            if (zoom != 1f) zoomBy(zoom.toDouble(), centroid)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { zoomBy(2.0, it) },
                            onTap = { pick(it) },
                        )
                    }
            ) {
                drawRect(background)
                tileVersion // redraw when tiles arrive

                // Biome tiles, falling back to a coarser cached tile while loading.
                for (key in visible) {
                    val tl = worldToScreen(key.blockX.toDouble(), key.blockZ.toDouble())
                    val br = worldToScreen((key.blockX + key.blockSize).toDouble(), (key.blockZ + key.blockSize).toDouble())
                    val dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt())
                    val dstSize = IntSize(br.x.roundToInt() - dstOffset.x, br.y.roundToInt() - dstOffset.y)
                    val tile = tiles.get(key)
                    if (tile != null) {
                        drawImage(tile.image, IntOffset.Zero, IntSize(CELLS, CELLS), dstOffset, dstSize, filterQuality = FilterQuality.None)
                        continue
                    }
                    for (parentScale in SCALES) {
                        if (parentScale <= key.scale) continue
                        val parentBlocks = CELLS * parentScale
                        val parent = TileKey(
                            parentScale,
                            Math.floorDiv(key.blockX, parentBlocks),
                            Math.floorDiv(key.blockZ, parentBlocks),
                        )
                        val p = tiles.get(parent) ?: continue
                        val cells = (key.blockSize / parentScale).coerceAtLeast(1)
                        val sx = (key.blockX - parent.blockX) / parentScale
                        val sz = (key.blockZ - parent.blockZ) / parentScale
                        drawImage(p.image, IntOffset(sx, sz), IntSize(cells, cells), dstOffset, dstSize, filterQuality = FilterQuality.None)
                        break
                    }
                }

                // Ravines (block-resolution overlay on the most zoomed-in tiles).
                if (options.ravines && key0Scale(visible) == RAVINE_SCALE) {
                    for (key in visible) {
                        val img = tiles.get(key)?.ravineImage ?: continue
                        val tl = worldToScreen(key.blockX.toDouble(), key.blockZ.toDouble())
                        val br = worldToScreen((key.blockX + key.blockSize).toDouble(), (key.blockZ + key.blockSize).toDouble())
                        val dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt())
                        val dstSize = IntSize(br.x.roundToInt() - dstOffset.x, br.y.roundToInt() - dstOffset.y)
                        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dstOffset, dstSize, filterQuality = FilterQuality.None)
                    }
                }

                // Slime chunks and chunk grid when zoomed in.
                if (slimeOn) {
                    val chunkPx = (16 / bpp).toFloat()
                    for (c in slimeChunks) {
                        val o = worldToScreen(c.x * 16.0, c.z * 16.0)
                        drawRect(Color(0x8834D058), o, Size(chunkPx, chunkPx))
                    }
                }
                if (bpp <= 0.5) {
                    val startX = floor((centerX - halfW) / 16) * 16
                    val startZ = floor((centerZ - halfH) / 16) * 16
                    var gx = startX
                    while (gx <= centerX + halfW) {
                        val x = worldToScreen(gx, 0.0).x
                        drawLine(Color(0x33000000), Offset(x, 0f), Offset(x, size.height), 1f)
                        gx += 16
                    }
                    var gz = startZ
                    while (gz <= centerZ + halfH) {
                        val y = worldToScreen(0.0, gz).y
                        drawLine(Color(0x33000000), Offset(0f, y), Offset(size.width, y), 1f)
                        gz += 16
                    }
                }

                // Structures: wiki icons, or lettered dots until the icons have loaded.
                val r = 9.dp.toPx()
                val iconPx = 20.dp.toPx()
                val backdrop = 3.dp.toPx()
                val appleIcon = icons[APPLE_ICON_URL]
                for (m in markers) {
                    val c = worldToScreen(m.pos.x.toDouble(), m.pos.z.toDouble())
                    val icon = icons[m.kind.iconUrl]
                    if (icon != null) {
                        drawIconMarker(icon, c, iconPx, backdrop, Color(0xB3000000))
                    } else {
                        drawCircle(Color(m.kind.color), r, c)
                        drawCircle(Color(0xFF1B1B1B), r, c, style = Stroke(1.5.dp.toPx()))
                        val label = markerLabels.getValue(m.kind)
                        drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
                    }
                    if (m.apple) {
                        val badge = c + Offset(iconPx * 0.6f, -iconPx * 0.6f)
                        if (appleIcon != null) {
                            drawIconMarker(appleIcon, badge, iconPx * 0.7f, backdrop * 0.6f, Color(0xE6FFF59D))
                        } else {
                            drawCircle(Color(0xFFFFD54F), r * 0.6f, badge)
                        }
                    }
                }
                if (showStrongholds) {
                    val icon = icons[STRONGHOLD_ICON_URL]
                    for (s in strongholds) {
                        val c = worldToScreen(s.x.toDouble(), s.z.toDouble())
                        if (icon != null) {
                            drawIconMarker(icon, c, iconPx * 1.15f, backdrop, Color(0xCC4A148C))
                        } else {
                            drawCircle(Color(0xFF6A1B9A), r * 1.15f, c)
                            drawCircle(Color.White, r * 1.15f, c, style = Stroke(1.5.dp.toPx()))
                            drawText(strongholdLabel, topLeft = Offset(c.x - strongholdLabel.size.width / 2f, c.y - strongholdLabel.size.height / 2f))
                        }
                    }
                }
                spawn?.let { s ->
                    val c = worldToScreen(s.x.toDouble(), s.z.toDouble())
                    drawCircle(Color.White, r * 1.2f, c)
                    drawCircle(Color(0xFFD32F2F), r * 0.7f, c)
                    drawCircle(Color(0xFF1B1B1B), r * 1.2f, c, style = Stroke(1.5.dp.toPx()))
                }

                // Selection ring, and the outline of a selected cave or ore vein.
                picked?.let { p ->
                    val box = (p as? Picked.Structure)?.point?.box
                    if (box != null) {
                        val a = worldToScreen(box[0].toDouble(), box[2].toDouble())
                        val b = worldToScreen(box[3] + 8.0, box[5] + 8.0)
                        drawRect(
                            selectionColor,
                            topLeft = a,
                            size = Size(b.x - a.x, b.y - a.y),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    val c = worldToScreen(p.pos.x.toDouble(), p.pos.z.toDouble())
                    drawCircle(selectionColor, r * 2.1f, c, style = Stroke(3.dp.toPx()))
                }

                // Center crosshair.
                val mid = Offset(size.width / 2f, size.height / 2f)
                val arm = 8.dp.toPx()
                drawLine(outline, mid - Offset(arm, 0f), mid + Offset(arm, 0f), 1.5.dp.toPx())
                drawLine(outline, mid - Offset(0f, arm), mid + Offset(0f, arm), 1.5.dp.toPx())
            }

            // Dimension switcher, center coordinates and notes.
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        dimensions.forEachIndexed { index, d ->
                            SegmentedButton(
                                selected = d == dimension,
                                onClick = {
                                    if (d != dimension) {
                                        // Nether coordinates are an eighth of the Overworld's.
                                        val factor = when {
                                            dimension == Dimension.Overworld && d == Dimension.Nether -> 1.0 / 8
                                            dimension == Dimension.Nether && d == Dimension.Overworld -> 8.0
                                            else -> 1.0
                                        }
                                        centerX *= factor
                                        centerZ *= factor
                                        picked = null
                                        dimension = d
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, dimensions.size),
                            ) { Text(d.label) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapPill("X ${floor(centerX).toInt()}  Z ${floor(centerZ).toInt()}")
                    if (showSlime && !slimeOn && dimension == Dimension.Overworld) MapPill("Zoom in for slime chunks")
                    if (options.ravines && scale != RAVINE_SCALE && dimension == Dimension.Overworld) MapPill("Zoom in for ravines")
                }
                if (!engine.showsBiomes) {
                    MapPill("Bedrock Nether: exact positions, no biomes. Bedrock picks fortress or bastion per region.")
                }
            }

            // Zoom and spawn buttons.
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { zoomBy(2.0) }) { Icon(Icons.Outlined.Add, contentDescription = "Zoom in") }
                SmallFloatingActionButton(onClick = { zoomBy(0.5) }) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom out") }
                if (spawn != null) {
                    SmallFloatingActionButton(onClick = {
                        spawn?.let {
                            centerX = it.x.toDouble()
                            centerZ = it.z.toDouble()
                        }
                    }) { Icon(Icons.Outlined.MyLocation, contentDescription = "Go to spawn") }
                }
            }

            picked?.let { p ->
                PickedCard(
                    picked = p,
                    details = details,
                    dimension = dimension,
                    onClose = { picked = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            }
        }
    }

    if (showLayers) {
        ModalBottomSheet(onDismissRequest = { showLayers = false }) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Structures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.kinds.forEach { kind ->
                        FilterChip(
                            selected = kind in enabled,
                            onClick = {
                                vm.setEnabled(if (kind in enabled) enabled - kind else enabled + kind)
                            },
                            label = { Text(kind.label) },
                            leadingIcon = { StructureIcon(kind.iconUrl, 18.dp) },
                        )
                    }
                }
                if (vm.kinds.any { it.spawners != null }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.setEnabled(vm.kinds.filter { it.spawners != null }.toSet()) }) {
                            Text("Only structures with spawners")
                        }
                        TextButton(onClick = { vm.setEnabled(vm.kinds.toSet()) }) { Text("Show all") }
                    }
                }
                Text(
                    "Small features appear as you zoom in. Tap any spot to see its diamonds (or ancient debris in the Nether).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dimension == Dimension.Overworld) {
                    LayerSwitch("Slime chunks", showSlime) { vm.showSlime.value = it }
                }
                if (dimension == Dimension.Overworld && engine.showsBiomes) {
                    Text("Biome height", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val heights = listOf("Surface" to SeedEngine.SURFACE, "Caves (Y 0)" to 0, "Deep (Y -51)" to -51)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        heights.forEachIndexed { i, (label, y) ->
                            SegmentedButton(
                                selected = options.biomeY == y,
                                onClick = { vm.setOptions(options.copy(biomeY = y)) },
                                shape = SegmentedButtonDefaults.itemShape(i, heights.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    LayerSwitch("Terrain shading (approximate)", options.terrain) { vm.setOptions(options.copy(terrain = it)) }
                }
                if (config.edition == Edition.Java && dimension == Dimension.Overworld) {
                    LayerSwitch("Ravines (zoom in)", options.ravines) { vm.setOptions(options.copy(ravines = it)) }
                }
                if (config.edition == Edition.Java && vm.kinds.any { it.canHaveApples }) {
                    LayerSwitch("Enchanted golden apples in chests", options.apples) { vm.setOptions(options.copy(apples = it)) }
                }
                if (config.edition == Edition.Java && dimension == Dimension.Overworld) {
                    LayerSwitch("Strongholds", showStrongholds) { vm.showStrongholds.value = it }
                }
            }
        }
    }

    if (showGoTo) {
        GoToDialog(
            onDismiss = { showGoTo = false },
            onGo = { x, z ->
                showGoTo = false
                centerX = x.toDouble()
                centerZ = z.toDouble()
                if (bpp > 2.0) bpp = 2.0
                picked = Picked.Point(Pos(x, z))
            },
        )
    }
}

/** A wiki icon drawn on a small dark backdrop, readable on any biome colour. */
private fun DrawScope.drawIconMarker(icon: ImageBitmap, center: Offset, height: Float, pad: Float, backdrop: Color) {
    val width = height * icon.width / icon.height
    val half = Size(width / 2 + pad, height / 2 + pad)
    drawRoundRect(
        color = backdrop,
        topLeft = Offset(center.x - half.width, center.y - half.height),
        size = Size(half.width * 2, half.height * 2),
        cornerRadius = CornerRadius(pad * 2),
    )
    drawImage(
        icon,
        IntOffset.Zero,
        IntSize(icon.width, icon.height),
        IntOffset((center.x - width / 2).roundToInt(), (center.y - height / 2).roundToInt()),
        IntSize(width.roundToInt(), height.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Pixel-art structure icon from the Minecraft Wiki. */
@Composable
fun StructureIcon(url: String, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

@Composable
private fun MapPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LayerSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PickedCard(picked: Picked, details: PickDetails, dimension: Dimension, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val title = when (picked) {
        is Picked.Structure -> picked.kind.label
        is Picked.Stronghold -> "Stronghold"
        is Picked.Spawn -> "World spawn"
        is Picked.Point -> "Location"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            val iconUrl = when (picked) {
                is Picked.Structure -> picked.kind.iconUrl
                is Picked.Stronghold -> STRONGHOLD_ICON_URL
                else -> null
            }
            if (iconUrl != null) {
                StructureIcon(iconUrl, 36.dp)
                Spacer(Modifier.size(12.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "X ${picked.pos.x}, Z ${picked.pos.z}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(details.biome ?: "…")
                        when (dimension) {
                            Dimension.Overworld -> append(" · Nether X ${Math.floorDiv(picked.pos.x, 8)}, Z ${Math.floorDiv(picked.pos.z, 8)}")
                            Dimension.Nether -> append(" · Overworld X ${picked.pos.x * 8}, Z ${picked.pos.z * 8}")
                            Dimension.End -> Unit
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val spawnerText = when (picked) {
                    is Picked.Structure -> picked.kind.spawners
                    is Picked.Stronghold -> STRONGHOLD_SPAWNERS
                    else -> null
                }
                if (spawnerText != null) DetailLine(spawnerText, emphasis = true)
                details.lines.forEach { DetailLine(it) }
                if (details.loot.isNotEmpty()) {
                    DetailHeader("Chest loot")
                    details.loot.forEach { DetailLine(it) }
                }
                if (details.ores.isNotEmpty()) {
                    DetailHeader(details.oreTitle ?: "Ores")
                    details.ores.forEach { DetailLine(it) }
                }
                if (details.loading) DetailLine("Loading details…")
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
        }
    }
}

@Composable
private fun DetailHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun DetailLine(text: String, emphasis: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
        color = if (emphasis) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
    )
}

private fun key0Scale(visible: List<TileKey>): Int = visible.firstOrNull()?.scale ?: 0

/** "minecraft:enchanted_golden_apple" -> "Enchanted golden apple". */
private fun itemName(id: String): String =
    id.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

/** Loot text lines ("chest\titem\tcount") grouped per chest, with stacks of the same item added up. */
private fun lootLines(text: String): List<String> {
    val chests = sortedMapOf<Int, LinkedHashMap<String, Int>>()
    for (line in text.lineSequence()) {
        val parts = line.split('\t')
        if (parts.size != 3) continue
        val chest = parts[0].toIntOrNull() ?: continue
        val count = parts[2].toIntOrNull() ?: continue
        val items = chests.getOrPut(chest) { LinkedHashMap() }
        items[parts[1]] = (items[parts[1]] ?: 0) + count
    }
    return chests.map { (chest, items) ->
        val list = items.entries.joinToString(", ") { (id, n) ->
            val name = itemName(id)
            if (id.endsWith("enchanted_golden_apple")) "⭐ $n × $name" else "$n × $name"
        }
        "Chest ${chest + 1}: $list"
    }
}

private suspend fun loadDetails(
    engine: SeedEngine,
    picked: Picked,
    options: MapOptions,
    tiles: MapTiles,
    scale: Int,
): PickDetails {
    val pos = picked.pos
    val biome = if (!engine.showsBiomes) {
        "Biome not shown for Bedrock"
    } else {
        val y = if (engine.dimension == Dimension.Overworld) options.biomeY else SeedEngine.SURFACE
        engine.biomeName(engine.biomeAt(pos.x, pos.z, y))
    }
    val java = engine.edition == Edition.Java
    val lines = ArrayList<String>()
    var loot: List<String> = emptyList()
    var ores: List<String> = emptyList()
    var oreTitle: String? = null

    when (picked) {
        is Picked.Structure -> {
            val kind = picked.kind
            yLevelText(kind, picked.point)?.let(lines::add)
            if (kind == StructureKind.Geode && picked.point.info and 0x100 != 0) lines += "Cracked: you can see it from a cave"
            lines += featureFacts(kind, picked.point) { engine.biomeName(it) }
            if (java && kind.hasVariants) lines += variantText(engine.variant(kind.id, pos.x, pos.z).first)
            if (java && kind.hasLoot) engine.loot(kind.id, pos.x, pos.z)?.let { loot = lootLines(it) }
        }
        is Picked.Stronghold -> {
            lines += STRONGHOLD_Y_TEXT
            if (java) engine.loot(STRONGHOLD_ID, pos.x, pos.z)?.let { loot = lootLines(it) }
        }
        is Picked.Spawn -> Unit
        is Picked.Point -> {
            // Ravine floor here, if the ravine layer is on and this tile has one.
            if (options.ravines && scale == RAVINE_SCALE) {
                val size = CELLS * RAVINE_SCALE
                val key = TileKey(RAVINE_SCALE, Math.floorDiv(pos.x, size), Math.floorDiv(pos.z, size))
                val floor = tiles.get(key)?.ravineFloor
                if (floor != null) {
                    val y = floor[(pos.z - key.blockZ) * size + (pos.x - key.blockX)]
                    if (y != SeedEngine.NO_RAVINE) lines += "Ravine here: floor at Y $y"
                }
            }
            if (java && engine.dimension != Dimension.End) {
                val found = engine.chunkOres(Math.floorDiv(pos.x, 16), Math.floorDiv(pos.z, 16)).orEmpty()
                val nether = engine.dimension == Dimension.Nether
                oreTitle = if (nether) "Ancient debris in this chunk" else "Diamond ore in this chunk"
                ores = if (found.isEmpty()) {
                    listOf("None")
                } else {
                    val sorted = found.sortedByDescending { it[1] }
                    sorted.take(12).map { "X ${it[0]}, Y ${it[1]}, Z ${it[2]}" } +
                        listOfNotNull(
                            if (sorted.size > 12) "…and ${sorted.size - 12} more" else null,
                            if (nether) null else "Some may be gone where a cave touches the ore.",
                        )
                }
            }
        }
    }
    return PickDetails(biome, lines, loot, ores, oreTitle, loading = false)
}

@Composable
private fun GoToDialog(onDismiss: () -> Unit, onGo: (Int, Int) -> Unit) {
    var x by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("") }
    val xi = x.trim().toIntOrNull()
    val zi = z.trim().toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to coordinates") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = z,
                    onValueChange = { z = it },
                    label = { Text("Z") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = xi != null && zi != null, onClick = { if (xi != null && zi != null) onGo(xi, zi) }) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingWorld(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Seed tools") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Enter your world's seed on the Seeds tab first.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(16.dp))
            TextButton(onClick = onBack) { Text("Go back") }
        }
    }
}
