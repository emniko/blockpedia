package com.emniko.blockpedia.ui.article

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.data.Article
import com.emniko.blockpedia.data.normalizeTitle
import com.emniko.blockpedia.data.pageUrl
import com.emniko.blockpedia.parse.HeadingBlock
import com.emniko.blockpedia.parse.LinkTarget
import kotlinx.coroutines.launch

private fun anchorsMatch(a: String, b: String) = a.replace(' ', '_').equals(b.replace(' ', '_'), ignoreCase = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    title: String,
    fragment: String?,
    onBack: () -> Unit,
    onOpenArticle: (String, String?) -> Unit,
    onOpenCategory: (String) -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
) {
    val vm: ArticleViewModel = viewModel(key = "article:$title") {
        ArticleViewModel(title, Graph.repo, Graph.userStore)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by Graph.userStore.favorites.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var showToc by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { SoundPlayer.stop() } }

    val success = state as? ArticleUiState.Success
    val article: Article? = success?.result?.article
    val pageTitle = article?.title ?: title

    fun scrollToAnchor(anchor: String) {
        val a = article ?: return
        val index = a.blocks.indexOfFirst { block ->
            val id = (block as? HeadingBlock)?.anchor
            id != null && anchorsMatch(id, anchor)
        }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
    }

    fun handleLink(target: LinkTarget) {
        when (target) {
            is LinkTarget.Anchor -> scrollToAnchor(target.fragment)
            is LinkTarget.Article -> {
                val frag = target.fragment
                val sameArticle = article != null && normalizeTitle(target.title) == normalizeTitle(article.title)
                if (!sameArticle) {
                    onOpenArticle(target.title, frag)
                } else if (frag != null) {
                    scrollToAnchor(frag)
                }
            }
            is LinkTarget.Category -> onOpenCategory(target.name)
            is LinkTarget.External -> {
                runCatching { uriHandler.openUri(target.url) }
            }
        }
    }
    val onLink: (LinkTarget) -> Unit = { handleLink(it) }

    // Jump to the requested section once the page has loaded.
    var fragmentHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(article != null) {
        if (article != null && fragment != null && !fragmentHandled) {
            fragmentHandled = true
            scrollToAnchor(fragment)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(article?.displayTitle ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Outlined.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    if (article != null) {
                        if (article.toc.isNotEmpty()) {
                            IconButton(onClick = { showToc = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents")
                            }
                        }
                        val isFavorite = article.title in favorites
                        IconButton(onClick = { Graph.userStore.toggleFavorite(article.title) }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isFavorite) "Remove bookmark" else "Bookmark",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Reload") }, onClick = {
                                menuOpen = false
                                vm.load(force = true)
                            })
                            DropdownMenuItem(text = { Text("Share link") }, onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, pageUrl(pageTitle))
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            })
                            DropdownMenuItem(text = { Text("Open on minecraft.wiki") }, onClick = {
                                menuOpen = false
                                runCatching { uriHandler.openUri(pageUrl(pageTitle)) }
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val s = state) {
                ArticleUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ArticleUiState.Error -> ErrorView(
                    message = s.message,
                    showRetry = !s.notFound,
                    onRetry = { vm.load(force = true) },
                    onBack = onBack,
                )
                is ArticleUiState.Success -> {
                    ArticleContent(
                        article = s.result.article,
                        offline = s.result.offline,
                        listState = listState,
                        onLink = onLink,
                        onOpenCategory = onOpenCategory,
                        onOpenSource = { runCatching { uriHandler.openUri(pageUrl(s.result.article.title)) } },
                    )
                    if (s.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }

    if (showToc && article != null) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            Text(
                "Contents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(article.toc) { entry ->
                    Text(
                        text = entry.text,
                        style = if (entry.level <= 2) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (entry.level <= 2) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showToc = false
                                scope.launch { listState.animateScrollToItem(entry.blockIndex + 1) }
                            }
                            .padding(start = if (entry.level <= 2) 24.dp else 44.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleContent(
    article: Article,
    offline: Boolean,
    listState: LazyListState,
    onLink: (LinkTarget) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenSource: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    article.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (offline) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Offline copy") },
                        leadingIcon = { Icon(Icons.Outlined.CloudOff, contentDescription = null) },
                    )
                }
            }
        }
        itemsIndexed(article.blocks) { _, block ->
            BlockView(block, onLink)
        }
        if (article.categories.isNotEmpty()) {
            item(key = "categories") {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        article.categories.forEach { category ->
                            SuggestionChip(onClick = { onOpenCategory(category) }, label = { Text(category) })
                        }
                    }
                }
            }
        }
        item(key = "attribution") {
            Text(
                text = "From the Minecraft Wiki, licensed CC BY-NC-SA 3.0. Tap to view the original page and its contributors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(onClick = onOpenSource),
            )
        }
    }
}

@Composable
fun ErrorView(message: String, showRetry: Boolean, onRetry: () -> Unit, onBack: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (showRetry) Button(onClick = onRetry) { Text("Try again") }
        if (onBack != null) OutlinedButton(onClick = onBack) { Text("Go back") }
    }
}
