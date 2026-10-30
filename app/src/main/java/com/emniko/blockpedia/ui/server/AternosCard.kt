package com.emniko.blockpedia.ui.server

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emniko.blockpedia.Graph
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Aternos's panel for the server last selected on the account. */
private const val ATERNOS_SERVER_URL = "https://aternos.org/server/"

private const val PREFS = "aternos"
private const val KEY_ADDRESS = "address"

/** Keeps "name.aternos.me" form: lowercase, no scheme, spaces or trailing port separator. */
private fun cleanAddress(text: String): String =
    text.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/', ':')

/**
 * The user's Aternos server: its address to copy into Minecraft, and a button that opens the
 * Aternos panel in a Custom Tab, where the user presses Start. Aternos's terms forbid automating
 * its website, so the app never starts the server itself.
 */
@Composable
fun AternosCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var address by remember { mutableStateOf(prefs.getString(KEY_ADDRESS, "").orEmpty()) }
    var editing by remember { mutableStateOf(address.isEmpty()) }
    var draft by remember { mutableStateOf(address) }

    // Server status: checked when the address changes, on refresh, and when coming back to the app
    // (for example after pressing Start on Aternos).
    var status by remember { mutableStateOf<ServerStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(address, refresh) {
        if (address.isEmpty()) {
            status = null
            return@LaunchedEffect
        }
        checking = true
        status = fetchServerStatus(Graph.http, address)
        checking = false
    }

    fun save() {
        address = cleanAddress(draft)
        prefs.edit().putString(KEY_ADDRESS, address).apply()
        editing = false
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("My Aternos server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!editing) {
                    IconButton(onClick = { draft = address; editing = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Change address")
                    }
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Server address (optional)") },
                    placeholder = { Text("yourname.aternos.me") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { save() }) { Text("Save") }
                    if (address.isNotEmpty()) TextButton(onClick = { editing = false }) { Text("Cancel") }
                }
            } else if (address.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        address,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { refresh++ }, enabled = !checking) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Check status")
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(address))
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy address")
                    }
                }
            }

            if (!editing && address.isNotEmpty()) StatusLine(status, checking)

            Button(onClick = { openAternos(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Aternos to start")
            }
            Text(
                "Press Start on the Aternos page. Aternos doesn't allow apps to start servers for you, " +
                    "so Blockpedia only opens the page. Log in there once and it stays logged in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusLine(status: ServerStatus?, checking: Boolean) {
    val (color, text) = when (status) {
        is ServerStatus.Online -> Color(0xFF43A047) to buildString {
            append("Online · ${status.players}/${status.max} players")
            status.version?.let { append(" · $it") }
        }
        is ServerStatus.Waking -> Color(0xFFFFB300) to status.state
        is ServerStatus.Offline -> Color(0xFF9E9E9E) to "Offline"
        ServerStatus.Unknown -> Color(0xFF9E9E9E) to "Status unavailable"
        null -> Color(0xFF9E9E9E) to "Checking…"
    }
    val motd = when (status) {
        is ServerStatus.Online -> status.motd
        is ServerStatus.Waking -> status.motd
        is ServerStatus.Offline -> status.motd
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (checking && status == null) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
        } else {
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (checking && status != null) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
    }
    if (motd != null) {
        Text(
            motd,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens the Aternos panel in a Custom Tab, falling back to any browser. */
private fun openAternos(context: Context) {
    val uri = Uri.parse(ATERNOS_SERVER_URL)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
}
