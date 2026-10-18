package com.emniko.blockpedia.ui.seed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.seed.Edition
import com.emniko.blockpedia.seed.Pos
import com.emniko.blockpedia.seed.SlimeChunks
import com.emniko.blockpedia.seed.direction
import kotlin.math.floor

private const val RADIUS = 10 // chunks around the centre: a 21 x 21 grid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimeScreen(onBack: () -> Unit) {
    val store = Graph.seedStore
    val input = remember { store.input.value }
    val config = remember { store.config().getOrNull() }
    val edition = config?.edition ?: input.edition
    // Bedrock slime chunks don't depend on the seed, so they work without one.
    if (config == null && edition == Edition.Java) {
        MissingWorld(onBack)
        return
    }
    val seed = config?.seed ?: 0L

    var xText by rememberSaveable { mutableStateOf("0") }
    var zText by rememberSaveable { mutableStateOf("0") }
    val x = xText.trim().toIntOrNull() ?: 0
    val z = zText.trim().toIntOrNull() ?: 0
    val ccx = Math.floorDiv(x, 16)
    val ccz = Math.floorDiv(z, 16)

    val grid = remember(edition, seed, ccx, ccz) {
        Array(2 * RADIUS + 1) { row ->
            BooleanArray(2 * RADIUS + 1) { col -> SlimeChunks.isSlime(edition, seed, ccx - RADIUS + col, ccz - RADIUS + row) }
        }
    }
    val nearest = remember(edition, seed, ccx, ccz, x, z) {
        buildList {
            for (dz in -32..32) for (dx in -32..32) {
                val cx = ccx + dx
                val cz = ccz + dz
                if (SlimeChunks.isSlime(edition, seed, cx, cz)) add(Pos(cx, cz))
            }
        }.sortedBy { Pos(it.x * 16 + 8, it.z * 16 + 8).distanceTo(x, z) }.take(8)
    }
    var selected by remember { mutableStateOf<Pos?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Slime chunks") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoordField("Your X", xText, { xText = it }, Modifier.weight(1f))
                        CoordField("Your Z", zText, { zText = it }, Modifier.weight(1f))
                    }
                    Text(
                        if (edition == Edition.Bedrock) {
                            "Bedrock: slime chunks are the same in every world. They spawn below Y 40 in any light level."
                        } else {
                            "Java: slime chunks depend on your seed. Slimes spawn below Y 40 in any light level."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SlimeGrid(grid, onTap = { col, row ->
                        selected = Pos(ccx - RADIUS + col, ccz - RADIUS + row)
                    }, selected = selected?.let { Pos(it.x - (ccx - RADIUS), it.z - (ccz - RADIUS)) })
                    selected?.let { c ->
                        val slime = SlimeChunks.isSlime(edition, seed, c.x, c.z)
                        Text(
                            "Chunk X ${c.x * 16}…${c.x * 16 + 15}, Z ${c.z * 16}…${c.z * 16 + 15}: " +
                                if (slime) "slime chunk" else "not a slime chunk",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("Nearest slime chunks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(nearest) { c ->
                val cx = c.x * 16 + 8
                val cz = c.z * 16 + 8
                ListItem(
                    headlineContent = { Text("X ${c.x * 16}…${c.x * 16 + 15}, Z ${c.z * 16}…${c.z * 16 + 15}") },
                    supportingContent = {
                        Text("${Pos(cx, cz).distanceTo(x, z).toInt()} blocks ${direction(x, z, cx, cz)}")
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SlimeGrid(grid: Array<BooleanArray>, selected: Pos?, onTap: (Int, Int) -> Unit) {
    val n = grid.size
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val you = MaterialTheme.colorScheme.onSurface
    val latestOnTap by rememberUpdatedState(onTap)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(n) {
                detectTapGestures { o ->
                    val cell = size.width.toFloat() / n
                    val col = floor(o.x / cell).toInt().coerceIn(0, n - 1)
                    val row = floor(o.y / cell).toInt().coerceIn(0, n - 1)
                    latestOnTap(col, row)
                }
            }
    ) {
        val cell = size.width / n
        for (row in 0 until n) for (col in 0 until n) {
            drawRect(
                color = if (grid[row][col]) Color(0xFF34D058) else empty,
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell),
            )
        }
        for (i in 0..n) {
            drawLine(line, Offset(i * cell, 0f), Offset(i * cell, size.height), 1f)
            drawLine(line, Offset(0f, i * cell), Offset(size.width, i * cell), 1f)
        }
        val mid = RADIUS * cell
        drawRect(you, Offset(mid, mid), Size(cell, cell), style = Stroke(3.dp.toPx()))
        if (selected != null) {
            drawRect(accent, Offset(selected.x * cell, selected.z * cell), Size(cell, cell), style = Stroke(3.dp.toPx()))
        }
    }
}
