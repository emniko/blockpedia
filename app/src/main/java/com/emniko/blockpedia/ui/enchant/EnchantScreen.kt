package com.emniko.blockpedia.ui.enchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.enchant.EnchantSession
import com.emniko.blockpedia.enchant.EnchantTable
import com.emniko.blockpedia.enchant.ItemKind
import com.emniko.blockpedia.enchant.Manipulation
import com.emniko.blockpedia.enchant.Manipulator
import com.emniko.blockpedia.enchant.Material
import com.emniko.blockpedia.enchant.Offer
import com.emniko.blockpedia.enchant.PlayerSeed
import com.emniko.blockpedia.enchant.Reading
import com.emniko.blockpedia.enchant.TableEnchants
import com.emniko.blockpedia.enchant.TableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SLOT_NAMES = listOf("Top", "Middle", "Bottom")

private fun hex(seed: Int) = "%08X".format(seed)

private val WantedSaver = Saver<SnapshotStateMap<String, Int>, String>(
    save = { m -> m.entries.joinToString(",") { "${it.key}=${it.value}" } },
    restore = { s ->
        mutableStateMapOf<String, Int>().apply {
            s.split(',').filter { it.isNotBlank() }.forEach { part ->
                val (id, level) = part.split('=')
                put(id, level.toInt())
            }
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnchantScreen(onBack: () -> Unit) {
    val session = Graph.enchanting
    val state by session.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { session.resume() }

    var kindName by rememberSaveable { mutableStateOf(ItemKind.Sword.name) }
    var materialName by rememberSaveable { mutableStateOf(Material.Diamond.name) }
    val kind = ItemKind.valueOf(kindName)
    val material = Material.valueOf(materialName).takeIf { it in kind.materials } ?: kind.materials.lastOrNull()
    val item = TableItem(kind, material)
    val wanted = rememberSaveable(saver = WantedSaver) { mutableStateMapOf<String, Int>() }
    var levelText by rememberSaveable { mutableStateOf("30") }
    var maxShelves by rememberSaveable { mutableStateOf(EnchantTable.MAX_SHELVES) }
    var previewShelves by rememberSaveable { mutableStateOf(EnchantTable.MAX_SHELVES) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Enchanting table", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Start over") },
                                onClick = { menu = false; session.startOver() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { msg -> item { Note(msg) } }

            val xp = state.xpSeed
            if (xp == null) {
                item { CrackCard(state, session) }
            } else {
                item { SeedCard(xp, state.playerSeed, onEnchanted = session::enchanted) }
                item {
                    ItemPicker(item) { k, m ->
                        kindName = k.name
                        if (m != null) materialName = m.name
                        wanted.keys.retainAll(TableItem(k, m).possible.map { it.id }.toSet())
                    }
                }
                item { PreviewCard(xp, item, previewShelves) { previewShelves = it } }
                item {
                    WantedCard(
                        item = item,
                        wanted = wanted,
                        levelText = levelText,
                        onLevel = { levelText = it.filter(Char::isDigit).take(4) },
                        maxShelves = maxShelves,
                        onMaxShelves = { maxShelves = it },
                    )
                }
                if (wanted.isNotEmpty()) {
                    item {
                        PlanCard(
                            xp = xp,
                            playerSeed = state.playerSeed,
                            item = item,
                            wanted = wanted.toMap(),
                            level = levelText.toIntOrNull() ?: 0,
                            maxShelves = maxShelves,
                            onDone = session::applyManipulation,
                        )
                    }
                }
            }
            item { RulesCard() }
            item {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Hint(
                        "Java Edition 1.16 and newer. Works in single player and on servers. Based on Enchantment Cracker " +
                            "by Earthcomputer (MIT licence).",
                    )
                }
            }
        }
    }
}

@Composable
private fun CrackCard(state: EnchantSession.State, session: EnchantSession) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.previousXpSeed != null) "Find your next XP seed" else "Find your XP seed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.readings.isEmpty()) {
                Text(
                    "Your XP seed decides every offer the table makes. To find it:\n" +
                        "1. Put any enchantable item (not a book) in the table.\n" +
                        "2. Count the bookshelves the table uses (15 works best) and enter the three level costs.\n" +
                        "3. Hide one bookshelf behind a torch or carpet and enter the new costs. Repeat until the seed shows up, " +
                        "usually after 3 to 5 readings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.readings.forEachIndexed { i, r ->
                Text(
                    "${i + 1}. ${r.shelves} bookshelves: ${r.top} · ${r.middle} · ${r.bottom}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            val searching = state.searching
            when {
                searching != null -> {
                    LinearProgressIndicator(progress = { searching }, modifier = Modifier.fillMaxWidth())
                    Text("Checking all 4.3 billion seeds… ${(searching * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                state.readings.size == 1 -> Status("Add one more reading with a different bookshelf count to start the search.")
                state.tooMany -> Status("Still too many possible seeds. Add another reading.")
                state.remaining == 0 -> Status(
                    "No seed fits these readings. Check the numbers (a mistyped cost or bookshelf count is the usual cause), " +
                        "remove the last reading, or start over.",
                    error = true,
                )
                state.remaining > 1 -> Status("${"%,d".format(state.remaining)} seeds still fit. Add another reading.")
            }

            if (searching == null) ReadingInput(state.readings, onAdd = session::addReading)

            if (state.readings.isNotEmpty() && searching == null) {
                TextButton(onClick = session::removeLastReading) { Text("Remove last reading") }
            }
        }
    }
}

@Composable
private fun ReadingInput(readings: List<Reading>, onAdd: (Reading) -> Unit) {
    var shelves by remember(readings.size) {
        mutableStateOf(readings.lastOrNull()?.let { (it.shelves - 1).coerceAtLeast(0) } ?: EnchantTable.MAX_SHELVES)
    }
    var top by remember(readings.size) { mutableStateOf("") }
    var middle by remember(readings.size) { mutableStateOf("") }
    var bottom by remember(readings.size) { mutableStateOf("") }

    Stepper("Bookshelves", shelves, 0..EnchantTable.MAX_SHELVES) { shelves = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CostField("Top", top, Modifier.weight(1f)) { top = it }
        CostField("Middle", middle, Modifier.weight(1f)) { middle = it }
        CostField("Bottom", bottom, Modifier.weight(1f)) { bottom = it }
    }
    Hint("Leave a cost empty if that button is blank.")
    val reading = Reading(shelves, top.toIntOrNull() ?: 0, middle.toIntOrNull() ?: 0, bottom.toIntOrNull() ?: 0)
    Button(
        onClick = { onAdd(reading) },
        enabled = reading.top > 0 && readings.none { it.shelves == shelves },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (readings.any { it.shelves == shelves }) "Already have $shelves bookshelves" else "Add reading") }
}

@Composable
private fun CostField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SeedCard(xp: Int, playerSeed: Long?, onEnchanted: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("XP seed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                hex(xp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (playerSeed != null) {
                    "Player seed known: the app can plan any reachable enchantment and follows along when you enchant."
                } else {
                    "The offers below are fixed until you enchant something. To steer what comes after, the app also needs " +
                        "your player seed: enchant something, press the button, and find the next XP seed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onEnchanted, modifier = Modifier.fillMaxWidth()) { Text("I enchanted something") }
            Hint("Press it after every enchantment at the table, so the app keeps up.")
        }
    }
}

@Composable
private fun ItemPicker(item: TableItem, onPick: (ItemKind, Material?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            var open by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(item.kind.label, Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ItemKind.entries.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k.label) },
                        onClick = {
                            open = false
                            onPick(k, item.material?.takeIf { it in k.materials } ?: k.materials.lastOrNull())
                        },
                    )
                }
            }
        }
        if (item.kind.materials.isNotEmpty()) {
            Box(Modifier.weight(1f)) {
                var open by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.material?.label ?: "", Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    item.kind.materials.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { open = false; onPick(item.kind, m) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(xp: Int, item: TableItem, shelves: Int, onShelves: (Int) -> Unit) {
    val offers = remember(xp, item, shelves) { EnchantTable.offers(xp, item, shelves) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offers for ${item.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Stepper("Bookshelves", shelves, 0..EnchantTable.MAX_SHELVES, onShelves)
            offers.forEach { OfferRow(it) }
            Hint("Check that the costs and the hint match your table before you spend levels.")
        }
    }
}

@Composable
private fun OfferRow(offer: Offer) {
    Column {
        HorizontalDivider()
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SLOT_NAMES[offer.slot], style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (offer.cost == 0) "Not available" else "${offer.cost} levels · ${offer.slot + 1} lapis",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (offer.cost > 0) {
            Text(
                offer.enchants.joinToString(", ") { TableEnchants.label(it) }.ifEmpty { "Nothing" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            offer.clue?.let {
                Text(
                    "Hint shown in the table: ${TableEnchants.label(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WantedCard(
    item: TableItem,
    wanted: SnapshotStateMap<String, Int>,
    levelText: String,
    onLevel: (String) -> Unit,
    maxShelves: Int,
    onMaxShelves: (Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Get specific enchantments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tick what you want, with the lowest level you'd accept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            item.possible.forEach { e ->
                val on = e.id in wanted
                val clash = wanted.keys.firstOrNull { it != e.id && !TableEnchants.compatible(it, e.id) }
                val enabled = on || clash == null
                val toggle: () -> Unit = {
                    if (on) {
                        wanted.remove(e.id)
                    } else {
                        wanted[e.id] = e.max
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = toggle),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = { toggle() }, enabled = enabled)
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge)
                        if (!enabled && clash != null) {
                            Text(
                                "Can't go with ${TableEnchants[clash].name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (on && e.max > 1) {
                    val level = wanted.getValue(e.id)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp)) {
                        (1..e.max).forEach { l ->
                            SegmentedButton(
                                selected = level == l,
                                onClick = { wanted[e.id] = l },
                                shape = SegmentedButtonDefaults.itemShape(l - 1, e.max),
                            ) { Text(listOf("I", "II", "III", "IV", "V")[l - 1]) }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = levelText,
                onValueChange = onLevel,
                label = { Text("Your XP level") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            Stepper("Most bookshelves you have", maxShelves, 0..EnchantTable.MAX_SHELVES, onMaxShelves)
        }
    }
}

private sealed interface PlanResult {
    data object Working : PlanResult
    data class Now(val options: List<Pair<Int, Offer>>) : PlanResult
    data class Steered(val plan: Manipulation) : PlanResult
    data object NeedPlayerSeed : PlanResult
    data object Unreachable : PlanResult
}

@Composable
private fun PlanCard(
    xp: Int,
    playerSeed: Long?,
    item: TableItem,
    wanted: Map<String, Int>,
    level: Int,
    maxShelves: Int,
    onDone: (Manipulation) -> Unit,
) {
    val result by produceState<PlanResult>(PlanResult.Working, xp, playerSeed, item, wanted, level, maxShelves) {
        value = PlanResult.Working
        value = withContext(Dispatchers.Default) {
            val now = (0..maxShelves).flatMap { shelves ->
                EnchantTable.offers(xp, item, shelves)
                    .filter { it.cost in 1..level && Manipulator.satisfies(it, wanted) }
                    .map { shelves to it }
            }.sortedBy { it.second.cost }
            when {
                now.isNotEmpty() -> PlanResult.Now(now.take(4))
                playerSeed == null -> PlanResult.NeedPlayerSeed
                else -> Manipulator.find(playerSeed, item, wanted, maxShelves, level)
                    ?.let { PlanResult.Steered(it) } ?: PlanResult.Unreachable
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val r = result) {
                PlanResult.Working -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                is PlanResult.Now -> {
                    Text("On offer right now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    r.options.forEach { (shelves, offer) ->
                        Text(
                            "$shelves bookshelves, ${SLOT_NAMES[offer.slot].lowercase()} slot (${offer.cost} levels): " +
                                offer.enchants.joinToString(", ") { TableEnchants.label(it) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Hint("Then press \"I enchanted something\".")
                }
                PlanResult.NeedPlayerSeed -> {
                    Text("Not on offer with this XP seed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "To steer the next XP seed, the app needs your player seed: enchant something cheap (top slot, " +
                            "1 level), press \"I enchanted something\" and find the new XP seed. Then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                PlanResult.Unreachable -> {
                    Text("Can't be reached", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "No XP seed within ${PlayerSeed.MAX_THROWS} throws gives this at your level. Ask for lower levels, " +
                            "fewer enchantments, or more XP levels (some, like Sharpness V on a book, never come from a table).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is PlanResult.Steered -> SteeredPlan(r.plan, onDone)
            }
        }
    }
}

@Composable
private fun SteeredPlan(plan: Manipulation, onDone: (Manipulation) -> Unit) {
    Text("How to get it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val offer = plan.offer
    val steps = buildList {
        if (plan.needsDummy) {
            if (plan.throws > 0) {
                val stacks = plan.throws / 64
                val rest = plan.throws % 64
                val count = if (stacks > 0) "${plan.throws} items ($stacks × 64 + $rest)" else "${plan.throws} items"
                add("Throw out $count one at a time with Q. Any cheap item works.")
            }
            add("Enchant a dummy item (anything enchantable) with the top slot.")
        }
        add(
            "Put your item in with ${plan.shelves} bookshelves and take the ${SLOT_NAMES[offer.slot].lowercase()} slot " +
                "(${offer.cost} levels, ${offer.slot + 1} lapis).",
        )
    }
    steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
    Text(
        "You get: " + offer.enchants.joinToString(", ") { TableEnchants.label(it) },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    offer.clue?.let { Hint("The table's hint should read ${TableEnchants.label(it)}. If it doesn't, stop and start over.") }
    Button(onClick = { onDone(plan) }, modifier = Modifier.fillMaxWidth()) { Text("Done, I did all of this") }
    Hint("Press it instead of \"I enchanted something\" so the app counts the throws and the dummy enchantment.")
}

@Composable
private fun RulesCard() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Keep the prediction working", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "The game uses the same random numbers for other things. Between steps, avoid: sprinting, falling, taking " +
                    "damage, eating or drinking, Mending repairs (picking up XP while holding or wearing Mending), using " +
                    "Unbreaking gear, dropping items unless told to, potion or beacon effects, anvils, enchanting other " +
                    "things, relogging, changing dimension and dying. If one slips through, start over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "More")
        }
    }
}

@Composable
private fun Status(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Note(text: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
