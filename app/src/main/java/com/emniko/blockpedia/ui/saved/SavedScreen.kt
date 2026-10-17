package com.emniko.blockpedia.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emniko.blockpedia.Graph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onOpenArticle: (String) -> Unit) {
    val favorites by Graph.userStore.favorites.collectAsStateWithLifecycle()
    val history by Graph.userStore.history.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Saved", fontWeight = FontWeight.Bold) },
                actions = {
                    if (tab == 1 && history.isNotEmpty()) {
                        TextButton(onClick = { Graph.userStore.clearHistory() }) { Text("Clear") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            if (tab == 0) {
                TitleList(
                    titles = favorites,
                    icon = Icons.Outlined.Bookmark,
                    emptyText = "Tap the bookmark icon on any page to keep it here.",
                    onOpen = onOpenArticle,
                    onRemove = { Graph.userStore.removeFavorite(it) },
                )
            } else {
                TitleList(
                    titles = history,
                    icon = Icons.Outlined.History,
                    emptyText = "Pages you read will show up here.",
                    onOpen = onOpenArticle,
                    onRemove = { Graph.userStore.removeHistory(it) },
                )
            }
        }
    }
}

@Composable
private fun TitleList(
    titles: List<String>,
    icon: ImageVector,
    emptyText: String,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (titles.isEmpty()) {
        Text(
            emptyText,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(titles, key = { it }) { title ->
            ListItem(
                headlineContent = { Text(title) },
                leadingContent = { Icon(icon, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onRemove(title) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove")
                    }
                },
                modifier = Modifier.clickable { onOpen(title) },
            )
        }
    }
}
