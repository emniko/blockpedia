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
import androidx.compose.runtime.Composable
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
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(address))
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy address")
                    }
                }
            }

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
