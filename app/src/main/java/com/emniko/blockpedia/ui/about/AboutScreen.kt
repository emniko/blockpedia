package com.emniko.blockpedia.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emniko.blockpedia.Graph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var cacheBytes by remember { mutableLongStateOf(Graph.pageCache.sizeBytes()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Blockpedia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "A native reader for the Minecraft Wiki. Pages load live from the wiki, " +
                    "so they always match the latest updates, and every page you open is kept for offline reading.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text("Credits and license", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "All article text and images come from the Minecraft Wiki and its contributors. " +
                    "Wiki content is available under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 license. " +
                    "Game images are the property of Mojang.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { runCatching { uriHandler.openUri("https://minecraft.wiki") } }) {
                Text("Visit minecraft.wiki")
            }
            TextButton(onClick = { runCatching { uriHandler.openUri("https://creativecommons.org/licenses/by-nc-sa/3.0/") } }) {
                Text("Read the CC BY-NC-SA 3.0 license")
            }

            Text("Disclaimer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT. " +
                    "This app is also not affiliated with the Minecraft Wiki.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Offline storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Saved pages use %.1f MB.".format(cacheBytes / (1024.0 * 1024.0)),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                Graph.pageCache.clear()
                Graph.repo.clearMemory()
                cacheBytes = Graph.pageCache.sizeBytes()
            }) {
                Text("Clear saved pages")
            }
        }
    }
}
