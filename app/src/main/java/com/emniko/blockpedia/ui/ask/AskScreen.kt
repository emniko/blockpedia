package com.emniko.blockpedia.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.ask.AiProvider
import com.emniko.blockpedia.ask.AskEngine
import com.emniko.blockpedia.ask.AskException
import com.emniko.blockpedia.ask.AskSettings
import com.emniko.blockpedia.ask.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow

class AskViewModel : ViewModel() {
    val settings = Graph.askSettings
    private val engine = AskEngine(Graph.http, Graph.repo)

    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val busy = MutableStateFlow<String?>(null)

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || busy.value != null) return
        val history = messages.value
        messages.value = history + ChatMessage(true, q)
        run(history, q)
    }

    /** Asks the last question again after an error. */
    fun retry() {
        val list = messages.value
        if (busy.value != null || list.size < 2 || !list.last().error) return
        val question = list[list.size - 2]
        messages.value = list.dropLast(1)
        run(list.dropLast(2), question.text)
    }

    private fun run(history: List<ChatMessage>, q: String) {
        busy.value = AskEngine.Stage.Wiki.label
        viewModelScope.launch {
            val reply = try {
                engine.answer(settings.config.value, history, q) { busy.value = it.label }
            } catch (e: AskException) {
                ChatMessage(false, e.message ?: "Something went wrong.", error = true)
            } catch (e: Exception) {
                ChatMessage(false, "Something went wrong: ${e.message ?: e.javaClass.simpleName}", error = true)
            }
            messages.value = messages.value + reply
            busy.value = null
        }
    }

    fun clear() {
        messages.value = emptyList()
    }

    suspend fun models(): List<String> = engine.models(settings.config.value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(initialQuestion: String?, onBack: () -> Unit, onOpenArticle: (String) -> Unit) {
    val vm: AskViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val config by vm.settings.config.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!initialQuestion.isNullOrBlank() && vm.messages.value.isEmpty() && config.ready) vm.ask(initialQuestion)
        else if (!initialQuestion.isNullOrBlank()) input = initialQuestion
    }
    LaunchedEffect(messages.size, busy) {
        val last = messages.size + (if (busy != null) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Ask Blockpedia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = vm::clear) { Icon(Icons.Outlined.Delete, contentDescription = "New chat") }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "AI settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        if (!config.ready) {
                            SetupCard(onOpenSettings = { showSettings = true })
                        } else {
                            EmptyState(web = config.web && config.provider.webSearch)
                        }
                    }
                }
                itemsIndexed(messages) { i, m ->
                    if (m.fromUser) {
                        UserBubble(m.text)
                    } else {
                        AnswerView(
                            m,
                            isLast = i == messages.lastIndex,
                            onOpenArticle = onOpenArticle,
                            onRetry = vm::retry,
                        )
                    }
                }
                if (busy != null) item { Thinking(busy ?: "") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask about Minecraft…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        vm.ask(input)
                        input = ""
                    }),
                )
                IconButton(
                    onClick = {
                        vm.ask(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && busy == null && config.ready,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm, onDismiss = { showSettings = false })
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Text("Blockpedia AI", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerView(m: ChatMessage, isLast: Boolean, onOpenArticle: (String) -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AssistantHeader()
        if (m.error) {
            Column(
                Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(m.text, color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                if (isLast) OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
            return@Column
        }
        MarkdownText(m.text, onCitation = { n -> m.sources.getOrNull(n - 1)?.let(onOpenArticle) })

        if (m.sources.isNotEmpty() || m.web.isNotEmpty()) {
            Text("Sources", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.sources.forEachIndexed { i, title ->
                    SourceRow(
                        badge = "${i + 1}",
                        title = title,
                        subtitle = "Minecraft Wiki",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        onClick = { onOpenArticle(title) },
                    )
                }
                m.web.forEach { w ->
                    SourceRow(
                        badge = null,
                        title = w.title,
                        subtitle = runCatching { java.net.URI(w.url).host.removePrefix("www.") }.getOrDefault(w.url),
                        icon = Icons.Outlined.Language,
                        onClick = { openWeb(context, w.url) },
                    )
                }
            }
        }
        if (m.searches.isNotEmpty()) {
            // Gemini's grounding terms ask apps to show the Google searches it ran.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.searches.forEach { q ->
                    AssistChip(
                        onClick = { openWeb(context, "https://www.google.com/search?q=" + Uri.encode(q)) },
                        label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        Row {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(m.text))
                Toast.makeText(context, "Answer copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
    }
}

@Composable
private fun SourceRow(badge: String?, title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (badge != null) {
            Box(Modifier.size(22.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = colors.onPrimary, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Thinking(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistantHeader()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(web: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("Ask anything about Minecraft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (web) "Answers use the Minecraft Wiki and the web, with sources." else "Answers use the Minecraft Wiki, with sources.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens a web page in a Custom Tab, or any browser. */
private fun openWeb(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set up the AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Blockpedia uses a free AI service with your own API key. Groq is the easiest: sign up, " +
                    "create a key, and paste it in the AI settings. No card needed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenSettings) { Text("Open AI settings") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(vm: AskViewModel, onDismiss: () -> Unit) {
    val config by vm.settings.config.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var key by remember(config.provider) { mutableStateOf(config.apiKey) }
    var model by remember(config.provider) { mutableStateOf(config.model) }
    var models by remember(config.provider) { mutableStateOf<List<String>>(emptyList()) }
    var modelsOpen by remember { mutableStateOf(false) }
    var status by remember(config.provider) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiProvider.entries.forEach { p ->
                        FilterChip(
                            selected = config.provider == p,
                            onClick = {
                                vm.settings.save(key, model)
                                vm.settings.select(p)
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
                Text(config.provider.note, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Search the web", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (config.provider.webSearch) "Use the web as well as the Minecraft Wiki" else "Not free with this service",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = config.web && config.provider.webSearch,
                        enabled = config.provider.webSearch,
                        onCheckedChange = { vm.settings.setWeb(it) },
                    )
                }
                TextButton(onClick = { runCatching { uriHandler.openUri(config.provider.keyPage) } }) {
                    Text("Get a free ${config.provider.label} key")
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(
                        enabled = key.isNotBlank(),
                        onClick = {
                            vm.settings.save(key, model)
                            status = "Loading models…"
                            scope.launch {
                                status = try {
                                    models = vm.models()
                                    modelsOpen = models.isNotEmpty()
                                    if (models.isEmpty()) "No models found." else null
                                } catch (e: Exception) {
                                    e.message ?: "Couldn't load models."
                                }
                            }
                        },
                    ) { Text("Choose from available models") }
                    DropdownMenu(expanded = modelsOpen, onDismissRequest = { modelsOpen = false }) {
                        models.forEach { id ->
                            DropdownMenuItem(text = { Text(id) }, onClick = {
                                model = id
                                modelsOpen = false
                            })
                        }
                    }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text(
                    "The key is stored only on this phone. Questions and wiki excerpts are sent to ${config.provider.label}" +
                        (if (config.provider.webSearch) ", which may search the web with them." else "."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.settings.save(key, model)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
