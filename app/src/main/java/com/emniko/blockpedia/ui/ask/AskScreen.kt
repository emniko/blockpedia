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
import androidx.compose.foundation.lazy.items
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
        busy.value = "Searching the wiki…"
        viewModelScope.launch {
            val reply = try {
                engine.answer(settings.config.value, history, q)
            } catch (e: AskException) {
                ChatMessage(false, "⚠ " + (e.message ?: "Something went wrong."))
            } catch (e: Exception) {
                ChatMessage(false, "⚠ Something went wrong: ${e.message ?: e.javaClass.simpleName}")
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
                            Intro { vm.ask(it) }
                        }
                    }
                }
                items(messages) { m -> Bubble(m, onOpenArticle) }
                if (busy != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(busy ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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
private fun Bubble(m: ChatMessage, onOpenArticle: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .background(
                    if (m.fromUser) colors.primaryContainer else colors.surfaceContainerHigh,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(cleanMarkdown(m.text), style = MaterialTheme.typography.bodyLarge)
            if (m.sources.isNotEmpty()) {
                Text("Sources", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                SourceChips(m.sources, onOpenArticle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(sources: List<String>, onOpenArticle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.forEachIndexed { i, title ->
            AssistChip(onClick = { onOpenArticle(title) }, label = { Text("[${i + 1}] $title") })
        }
    }
}

/** Models often answer in Markdown; show it as plain text with bullets. */
private fun cleanMarkdown(text: String): String = text
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
    .replace(Regex("`([^`]*)`"), "$1")

@Composable
private fun Intro(onAsk: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Ask anything about Minecraft. Answers come from Minecraft Wiki pages, linked under each answer.",
            style = MaterialTheme.typography.bodyLarge,
        )
        listOf(
            "What's the best Y level for diamonds?",
            "How do I cure a zombie villager?",
            "What does Mending do?",
        ).forEach { q -> OutlinedButton(onClick = { onAsk(q) }) { Text(q) } }
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
                    "The key is stored only on this phone. Questions and wiki excerpts are sent to ${config.provider.label}.",
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
