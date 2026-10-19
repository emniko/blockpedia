package com.emniko.blockpedia.ui.seed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.seed.Edition
import com.emniko.blockpedia.seed.MapPoint
import com.emniko.blockpedia.seed.Dimension
import com.emniko.blockpedia.seed.Pos
import com.emniko.blockpedia.seed.SeedEngine
import com.emniko.blockpedia.seed.StructureKind
import com.emniko.blockpedia.seed.direction
import com.emniko.blockpedia.seed.STRONGHOLD_ICON_URL
import com.emniko.blockpedia.seed.iconUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What to search for: a structure type, or strongholds (Java only). */
private data class Target(val label: String, val kind: StructureKind?) {
    val dimension: Dimension get() = kind?.dimension ?: Dimension.Overworld
    val iconUrl: String get() = kind?.iconUrl ?: STRONGHOLD_ICON_URL
}

private data class Found(val pos: Pos, val distance: Int, val direction: String, val y: Int? = null)

private sealed interface NearestState {
    data object Idle : NearestState
    data object Searching : NearestState
    data class Done(
        val target: String,
        val iconUrl: String,
        val dimension: Dimension,
        val results: List<Found>,
        val radius: Int,
    ) : NearestState
    data class Failed(val message: String) : NearestState
}

private const val RESULTS = 10

/** Search radii in blocks; dense small structures stop earlier to stay fast. */
private fun radiiFor(kind: StructureKind): List<Int> = when {
    kind.maxScale <= 4 -> listOf(512, 1024, 2048)
    kind.maxScale <= 16 -> listOf(1024, 4096, 8192)
    else -> listOf(2048, 8192, 16384, 32768)
}

private suspend fun search(engine: SeedEngine, target: Target, x: Int, z: Int): NearestState {
    val kind = target.kind
    if (kind == null) {
        val all = engine.strongholds()
        val results = all.map { Found(it, it.distanceTo(x, z).toInt(), direction(x, z, it.x, it.z)) }
            .sortedBy { it.distance }.take(RESULTS)
        return NearestState.Done(target.label, target.iconUrl, target.dimension, results, 0)
    }
    var radius = 0
    var found: List<MapPoint> = emptyList()
    for (r in radiiFor(kind)) {
        radius = r
        found = engine.features(kind, x - r, z - r, x + r, z + r)
            ?: return NearestState.Failed("${kind.label}s don't generate in this version.")
        // Only results inside the circle are guaranteed to be the true nearest ones.
        if (found.count { it.pos.distanceTo(x, z) <= r } >= RESULTS) break
    }
    val results = found.map { Found(it.pos, it.pos.distanceTo(x, z).toInt(), direction(x, z, it.pos.x, it.pos.z), it.y) }
        .sortedBy { it.distance }.take(RESULTS)
    return NearestState.Done(target.label, target.iconUrl, target.dimension, results, radius)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearestScreen(onBack: () -> Unit, onOpenMap: (Pos, Dimension) -> Unit) {
    val store = Graph.seedStore
    val config = remember { store.config().getOrNull() }
    if (config == null) {
        MissingWorld(onBack)
        return
    }
    val targets = remember(config) {
        StructureKind.forEdition(config.edition).map { Target(it.label, it) } +
            if (config.edition == Edition.Java) listOf(Target("Stronghold", null)) else emptyList()
    }

    var xText by rememberSaveable { mutableStateOf("0") }
    var zText by rememberSaveable { mutableStateOf("0") }
    var targetIndex by rememberSaveable { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<NearestState>(NearestState.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val x = xText.trim().toIntOrNull()
    val z = zText.trim().toIntOrNull()
    val target = targets[targetIndex.coerceIn(0, targets.lastIndex)]

    fun startSearch() {
        if (x == null || z == null) return
        job?.cancel()
        state = NearestState.Searching
        job = scope.launch {
            state = try {
                search(store.engine(config, target.dimension), target, x, z)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NearestState.Failed("Search failed.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Nearest structures") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            StructureIcon(target.iconUrl, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Find: ${target.label}")
                        }
                        DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            targets.forEachIndexed { i, t ->
                                DropdownMenuItem(text = { Text(t.label) }, leadingIcon = { StructureIcon(t.iconUrl, 24.dp) }, onClick = {
                                    pickerOpen = false
                                    targetIndex = i
                                })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val where = if (target.dimension == Dimension.Overworld) "Your" else "Your ${target.dimension.label}"
                        CoordField("$where X", xText, { xText = it }, Modifier.weight(1f))
                        CoordField("$where Z", zText, { zText = it }, Modifier.weight(1f))
                    }
                    Button(onClick = ::startSearch, enabled = x != null && z != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Search")
                    }
                    Text(
                        "Tip: press F3 (Java) or turn on Show Coordinates (Bedrock) to see your position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val s = state) {
                NearestState.Idle -> Unit
                NearestState.Searching -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is NearestState.Failed -> item {
                    Text(s.message, modifier = Modifier.padding(16.dp))
                }
                is NearestState.Done -> {
                    if (s.results.isEmpty()) {
                        item {
                            Text(
                                "No ${s.target.lowercase()} within ${s.radius} blocks.",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    itemsIndexed(s.results) { i, f ->
                        ListItem(
                            headlineContent = {
                                val yText = f.y?.let { ", Y $it" } ?: ""
                                Text("${i + 1}.  X ${f.pos.x}$yText, Z ${f.pos.z}", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                val other = when (s.dimension) {
                                    Dimension.Overworld -> " · Nether X ${Math.floorDiv(f.pos.x, 8)}, Z ${Math.floorDiv(f.pos.z, 8)}"
                                    Dimension.Nether -> " · Overworld X ${f.pos.x * 8}, Z ${f.pos.z * 8}"
                                    Dimension.End -> ""
                                }
                                Text("${f.distance} blocks ${f.direction}$other")
                            },
                            leadingContent = { StructureIcon(s.iconUrl, 32.dp) },
                            trailingContent = { Icon(Icons.Outlined.NearMe, contentDescription = "Show on map") },
                            modifier = Modifier.clickable { onOpenMap(f.pos, s.dimension) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CoordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v == "-" || v.toIntOrNull() != null) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
