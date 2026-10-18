package com.emniko.blockpedia.ui.seed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.seed.Edition
import com.emniko.blockpedia.seed.JavaVersions
import com.emniko.blockpedia.seed.Pos
import com.emniko.blockpedia.seed.SeedConfig
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedsScreen(
    onOpenMap: (Pos?) -> Unit,
    onOpenNearest: () -> Unit,
    onOpenSlime: () -> Unit,
) {
    val store = Graph.seedStore
    val input by store.input.collectAsStateWithLifecycle()
    val config = remember(input) { store.config(input) }
    val ready = config.getOrNull()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Seed tools", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorldCard(
                input = input,
                error = config.exceptionOrNull()?.message,
                onChange = store::update,
            )

            if (ready != null) SpawnCard(ready, onShow = { onOpenMap(it) })

            ToolCard(
                icon = Icons.Outlined.Map,
                title = "Seed map",
                subtitle = "Biomes and structures, zoom and pan anywhere",
                enabled = ready != null,
                onClick = { onOpenMap(null) },
            )
            ToolCard(
                icon = Icons.Outlined.TravelExplore,
                title = "Nearest structures",
                subtitle = "Closest villages, temples, cities and more from any spot",
                enabled = ready != null,
                onClick = onOpenNearest,
            )
            ToolCard(
                icon = Icons.Outlined.GridOn,
                title = "Slime chunks",
                subtitle = "Find chunks where slimes spawn underground",
                enabled = ready != null || input.edition == Edition.Bedrock,
                onClick = onOpenSlime,
            )

            Text(
                text = "Worlds are calculated on your phone with cubiomes (MIT licence). Java results match the game. " +
                    "Bedrock biomes match the game; Bedrock structure positions follow community-documented rules and " +
                    "a few may differ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldCard(input: com.emniko.blockpedia.seed.SeedStore.Input, error: String?, onChange: (com.emniko.blockpedia.seed.SeedStore.Input) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your world", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Edition.entries.forEachIndexed { index, edition ->
                    SegmentedButton(
                        selected = input.edition == edition,
                        onClick = { onChange(input.copy(edition = edition)) },
                        shape = SegmentedButtonDefaults.itemShape(index, Edition.entries.size),
                    ) { Text(edition.label) }
                }
            }

            OutlinedTextField(
                value = input.seedText,
                onValueChange = { onChange(input.copy(seedText = it)) },
                label = { Text("Seed") },
                placeholder = { Text(if (input.edition == Edition.Java) "Number or text" else "Number") },
                singleLine = true,
                isError = error != null && input.seedText.isNotBlank(),
                supportingText = {
                    when {
                        error != null && input.seedText.isNotBlank() -> Text(error)
                        input.edition == Edition.Java -> Text("In game: type /seed")
                        else -> Text("In game: Settings ▸ Game ▸ Seed")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            if (input.edition == Edition.Java) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Version: ${input.javaVersion.label}")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        JavaVersions.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.label) },
                                onClick = {
                                    open = false
                                    onChange(input.copy(javaVersion = v))
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Uses the latest Bedrock world generation (1.18 and newer worlds).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpawnCard(config: SeedConfig, onShow: (Pos) -> Unit) {
    val spawn by produceState<SpawnState>(SpawnState.Loading, config) {
        value = if (config.edition != Edition.Java) {
            SpawnState.Unavailable
        } else {
            try {
                Graph.seedStore.engine(config).spawn()?.let { SpawnState.Ready(it) } ?: SpawnState.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SpawnState.Unavailable
            }
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("World spawn", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                when (val s = spawn) {
                    SpawnState.Loading -> Text("Calculating…", style = MaterialTheme.typography.bodyMedium)
                    is SpawnState.Ready -> Text(
                        "X ${s.pos.x}, Z ${s.pos.z}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SpawnState.Unavailable -> Text(
                        if (config.edition == Edition.Bedrock) "Bedrock's spawn point can't be worked out from the seed."
                        else "Couldn't calculate the spawn point.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (val s = spawn) {
                SpawnState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                is SpawnState.Ready -> TextButton(onClick = { onShow(s.pos) }) { Text("Show") }
                SpawnState.Unavailable -> Unit
            }
        }
    }
}

private sealed interface SpawnState {
    data object Loading : SpawnState
    data object Unavailable : SpawnState
    data class Ready(val pos: Pos) : SpawnState
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
