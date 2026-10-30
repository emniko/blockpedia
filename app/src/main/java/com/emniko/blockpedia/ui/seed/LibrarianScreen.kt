package com.emniko.blockpedia.ui.seed

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.seed.LibrarianData
import com.emniko.blockpedia.seed.LibrarianOffer
import com.emniko.blockpedia.seed.LibrarianSequence
import com.emniko.blockpedia.seed.Reroll
import com.emniko.blockpedia.seed.SeedConfig
import com.emniko.blockpedia.seed.TradeEnchantment
import com.emniko.blockpedia.seed.bookLabel
import com.emniko.blockpedia.seed.itemName
import com.emniko.blockpedia.seed.tradesFollowSeed

private val LEVEL_NAMES = mapOf(1 to "Novice", 2 to "Apprentice", 3 to "Journeyman", 4 to "Expert")
private const val SHOWN = 100

/** Remembers how far each world's librarian sequences have advanced. */
private class Positions(context: Context, private val seed: Long) {
    private val prefs = context.getSharedPreferences("librarian", Context.MODE_PRIVATE)
    fun get(level: Int): Int = prefs.getInt("$seed:$level", 0)
    fun set(level: Int, value: Int) = prefs.edit().putInt("$seed:$level", value).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarianScreen(onBack: () -> Unit) {
    val config = remember { Graph.seedStore.config().getOrNull() }
    if (config == null) {
        MissingWorld(onBack)
        return
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Librarian trades", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (!tradesFollowSeed(config)) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text(
                    "Villager trades only follow the world seed in Java Edition 26.1 and newer. " +
                        "Set your world to Java 26.1, 26.2 or 26.3 on the Seeds tab to predict them.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LibrarianContent(config, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LibrarianContent(config: SeedConfig, modifier: Modifier) {
    val context = LocalContext.current
    val positions = remember(config.seed) { Positions(context, config.seed) }
    var level by remember { mutableIntStateOf(1) }
    val sequence = remember(config.seed, level) { LibrarianSequence(config.seed, level) }
    var current by remember(level) { mutableIntStateOf(positions.get(level)) }
    fun setCurrent(value: Int) {
        current = value
        positions.set(level, value)
    }
    var target by remember { mutableStateOf(LibrarianData.enchantments.first { it.id == "mending" }) }
    var targetLevel by remember { mutableIntStateOf(1) }
    var finding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Reroll?>(null) }

    val start = maxOf(1, current)
    val rows = remember(sequence, start) { sequence.range(start, SHOWN) }
    val found = remember(sequence, target, targetLevel, current) { sequence.next(target.id, targetLevel, current) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LibrarianData.bookLevels.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = level == l,
                        onClick = { level = l },
                        shape = SegmentedButtonDefaults.itemShape(i, LibrarianData.bookLevels.size),
                    ) { Text(LEVEL_NAMES.getValue(l), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (current == 0) "No rerolls yet in this world" else "Your librarian shows reroll #$current",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Every ${LEVEL_NAMES.getValue(level).lowercase()} librarian in the world shares one sequence, " +
                            "so rerolls by anyone on the server move it along.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { finding = true }) { Text("Find my position") }
                        if (current != 0) TextButton(onClick = { setCurrent(0) }) { Text("Reset") }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Looking for", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    EnchantmentPicker(target, targetLevel, onPick = { e, l -> target = e; targetLevel = l })
                    Text(
                        if (found == null) {
                            "Not in the next 20,000 rerolls."
                        } else {
                            val book = found.book!!
                            val away = found.number - current
                            "${bookLabel(book)} for ${book.emeralds} emeralds at reroll #${found.number}: " +
                                (if (away == 1) "the next reroll" else "$away rerolls away")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item {
            Text(
                "Tap a reroll to mark it as the one your librarian is showing now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(rows, key = { it.number }) { r ->
            RerollRow(
                reroll = r,
                isCurrent = r.number == current,
                isTarget = r.number == found?.number,
                onClick = { confirm = r },
            )
        }
        item {
            Text(
                "Java 26.1 and newer, Trade Rebalance experiment off. Worlds changed with /randomsequence " +
                    "or trade-changing mods and datapacks won't match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Reroll #${r.number}") },
            text = { Text("Is your librarian showing these trades now? Predictions will continue from here.") },
            confirmButton = { TextButton(onClick = { setCurrent(r.number); confirm = null }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    if (finding) {
        FindPositionDialog(
            sequence = sequence,
            onDismiss = { finding = false },
            onFound = { setCurrent(it); finding = false },
        )
    }
}

@Composable
private fun RerollRow(reroll: Reroll, isCurrent: Boolean, isTarget: Boolean, onClick: () -> Unit) {
    val bg = when {
        isTarget -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text("#${reroll.number}", fontWeight = FontWeight.Bold)
            if (isCurrent) Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            reroll.offers.forEach { offer ->
                when (offer) {
                    is LibrarianOffer.Book -> Text(
                        "${bookLabel(offer)} · ${offer.emeralds} emeralds",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is LibrarianOffer.Item -> Text(
                        "${offer.wants} ${itemName(offer.wantsItem)} → ${offer.gives} ${itemName(offer.givesItem)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Enchantment and level selector; level chips only for enchantments with more than one level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnchantmentPicker(selected: TradeEnchantment, level: Int, onPick: (TradeEnchantment, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(selected.name)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarianData.enchantments.sortedBy { it.name }.forEach { e ->
                DropdownMenuItem(text = { Text(e.name) }, onClick = {
                    onPick(e, level.coerceAtMost(e.maxLevel))
                    open = false
                })
            }
        }
    }
    if (selected.maxLevel > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (l in 1..selected.maxLevel) {
                FilterChip(
                    selected = level == l,
                    onClick = { onPick(selected, l) },
                    label = { Text(if (l == 1) "Any level" else "Level $l+") },
                )
            }
        }
    }
}

/** Enter the books seen on consecutive rerolls until only one place in the sequence fits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindPositionDialog(sequence: LibrarianSequence, onDismiss: () -> Unit, onFound: (Int) -> Unit) {
    val observed = remember { mutableStateListOf<Pair<String, Int>?>() }
    var pick by remember { mutableStateOf(LibrarianData.enchantments.first()) }
    var pickLevel by remember { mutableIntStateOf(1) }
    val matches = remember(observed.toList()) { sequence.match(observed.toList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find my position") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Reroll your librarian and add the book it offers each time, in order. " +
                        "Add \"No book\" when it offers none.",
                    style = MaterialTheme.typography.bodySmall,
                )
                observed.forEachIndexed { i, o ->
                    Text("${i + 1}. " + (o?.let { (e, l) -> bookLabel(LibrarianOffer.Book(e, l, 0)) } ?: "No book"))
                }
                HorizontalDivider()
                Text("Exact level", style = MaterialTheme.typography.labelMedium)
                Box {
                    var open by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { open = true }) {
                        Text(pick.name)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        LibrarianData.enchantments.sortedBy { it.name }.forEach { e ->
                            DropdownMenuItem(text = { Text(e.name) }, onClick = {
                                pick = e
                                pickLevel = pickLevel.coerceAtMost(e.maxLevel)
                                open = false
                            })
                        }
                    }
                }
                if (pick.maxLevel > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in 1..pick.maxLevel) {
                            FilterChip(selected = pickLevel == l, onClick = { pickLevel = l }, label = { Text("$l") })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { observed += pick.id to pickLevel }) { Text("Add book") }
                    OutlinedButton(onClick = { observed += null }) { Text("No book") }
                }
                if (observed.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { observed.removeAt(observed.size - 1) }) { Text("Undo") }
                        TextButton(onClick = { observed.clear() }) { Text("Clear") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            matches.isEmpty() -> "No match in the first 20,000 rerolls. Check the seed, the version and that the Trade Rebalance experiment is off."
                            matches.size == 1 -> "Found: your librarian is at reroll #${matches[0]}."
                            matches.size > 20 -> "Many places fit. Add another reroll."
                            else -> "${matches.size} places fit. Add another reroll."
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = matches.size == 1, onClick = { onFound(matches[0]) }) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
