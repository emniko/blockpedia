package com.emniko.blockpedia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emniko.blockpedia.Graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A curated entry point: the overview article, plus the wiki category for browsing everything. */
data class GuideSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val article: String,
    val category: String? = null,
)

val GuideSections = listOf(
    GuideSection("Blocks", "Every placeable block", Icons.Outlined.ViewInAr, "Block", "Blocks"),
    GuideSection("Items", "Tools, food and materials", Icons.Outlined.Diamond, "Item", "Items"),
    GuideSection("Mobs", "Creatures and monsters", Icons.Outlined.Pets, "Mob", "Mobs"),
    GuideSection("Biomes", "Terrain and climates", Icons.Outlined.Landscape, "Biome", "Biomes"),
    GuideSection("Structures", "Villages, temples, cities", Icons.Outlined.AccountBalance, "Structure", "Structures"),
    GuideSection("Crafting", "Recipes and the grid", Icons.Outlined.Construction, "Crafting"),
    GuideSection("Smelting", "Furnaces and fuel", Icons.Outlined.LocalFireDepartment, "Smelting"),
    GuideSection("Brewing", "Potions and ingredients", Icons.Outlined.Science, "Brewing"),
    GuideSection("Enchanting", "Enchantments and tables", Icons.Outlined.AutoAwesome, "Enchanting", "Enchantments"),
    GuideSection("Effects", "Status effects", Icons.Outlined.LocalDrink, "Effect", "Effects"),
    GuideSection("Trading", "Villagers and trades", Icons.Outlined.Storefront, "Trading"),
    GuideSection("Smithing", "Upgrades and armor trims", Icons.Outlined.Handyman, "Smithing"),
    GuideSection("Redstone", "Circuits and mechanisms", Icons.Outlined.Memory, "Redstone circuits"),
    GuideSection("Commands", "Cheats and command syntax", Icons.Outlined.Terminal, "Commands"),
    GuideSection("Advancements", "Goals and progress", Icons.Outlined.EmojiEvents, "Advancement"),
    GuideSection("Dimensions", "Overworld, Nether, End", Icons.Outlined.Public, "Dimension"),
    GuideSection("Versions", "Every update so far", Icons.Outlined.History, "Version history"),
    GuideSection("Tutorials", "Guides and strategies", Icons.Outlined.School, "Tutorials"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenArticle: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var randomLoading by remember { mutableStateOf(false) }
    val history by Graph.userStore.history.collectAsStateWithLifecycle()

    fun openRandom() {
        if (randomLoading) return
        scope.launch {
            randomLoading = true
            try {
                onOpenArticle(Graph.repo.randomTitle())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't reach the wiki. Check your connection.")
            } finally {
                randomLoading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Blockpedia", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchPill(onSearch)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { openRandom() }, modifier = Modifier.weight(1f)) {
                        if (randomLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Random page")
                    }
                    FilledTonalButton(onClick = onOpenSaved, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Saved")
                    }
                }
            }
            if (history.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("Continue reading")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(history.take(12)) { title ->
                                AssistChip(
                                    onClick = { onOpenArticle(title) },
                                    label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("Guide")
            }
            items(GuideSections) { section ->
                SectionCard(
                    section = section,
                    onOpen = { onOpenArticle(section.article) },
                    onBrowse = section.category?.let { category -> { onOpenCategory(category) } },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Unofficial guide. Content comes live from the Minecraft Wiki (minecraft.wiki) under CC BY-NC-SA 3.0. " +
                        "Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Search blocks, mobs, items…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SectionCard(section: GuideSection, onOpen: () -> Unit, onBrowse: (() -> Unit)?) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.weight(1f))
                if (onBrowse != null) {
                    IconButton(onClick = onBrowse) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = "Browse all ${section.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
