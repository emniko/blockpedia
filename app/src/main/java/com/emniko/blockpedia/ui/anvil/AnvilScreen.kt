package com.emniko.blockpedia.ui.anvil

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emniko.blockpedia.anvil.AnvilPlanner
import com.emniko.blockpedia.anvil.Enchants
import com.emniko.blockpedia.anvil.Gear
import com.emniko.blockpedia.anvil.MAX_STEP_COST
import com.emniko.blockpedia.anvil.Piece
import com.emniko.blockpedia.anvil.PlanNode
import com.emniko.blockpedia.anvil.enchantLabel
import com.emniko.blockpedia.anvil.priorWorkPenalty
import com.emniko.blockpedia.anvil.roman
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_USES = 6

/** Saves "id=level,id=level" so choices survive rotation. */
private val EnchantMapSaver = Saver<SnapshotStateMap<String, Int>, String>(
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

private sealed interface PlanState {
    data object Empty : PlanState
    data object Working : PlanState
    data object TooExpensive : PlanState
    data class Ready(val plan: PlanNode, val pieces: List<Piece>) : PlanState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnvilScreen(onBack: () -> Unit, onOpenArticle: (String) -> Unit) {
    var gearName by rememberSaveable { mutableStateOf(Gear.Sword.name) }
    val gear = Gear.valueOf(gearName)
    val books = rememberSaveable(saver = EnchantMapSaver) { mutableStateMapOf<String, Int>() }
    val onItem = rememberSaveable(saver = EnchantMapSaver) { mutableStateMapOf<String, Int>() }
    var hasEnchants by rememberSaveable { mutableStateOf(false) }
    var uses by rememberSaveable { mutableStateOf(0) }

    val bookSnapshot = books.toMap()
    val itemSnapshot = if (hasEnchants) onItem.toMap() else emptyMap()
    val itemUses = if (hasEnchants) uses else 0

    val state by produceState<PlanState>(PlanState.Empty, gear, bookSnapshot, itemSnapshot, itemUses) {
        if (bookSnapshot.isEmpty()) {
            value = PlanState.Empty
            return@produceState
        }
        value = PlanState.Working
        val pieces = listOf(Piece(itemSnapshot, itemUses, isItem = true)) +
            gear.enchants.filter { it in bookSnapshot }.map { Piece(mapOf(it to bookSnapshot.getValue(it))) }
        val plan = withContext(Dispatchers.Default) { AnvilPlanner.plan(pieces) }
        value = if (plan == null) PlanState.TooExpensive else PlanState.Ready(plan, pieces)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Anvil order", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GearPicker(gear) { picked ->
                    gearName = picked.name
                    books.keys.retainAll(picked.enchants.toSet())
                    onItem.keys.retainAll(picked.enchants.toSet())
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Item is already enchanted", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Or has been through an anvil before",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = hasEnchants, onCheckedChange = { hasEnchants = it })
                        }
                        if (hasEnchants) {
                            Spacer(Modifier.size(8.dp))
                            UsesStepper(uses) { uses = it }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            EnchantList(gear, selected = onItem, other = books)
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Books to add", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "One enchanted book for each ticked enchantment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        EnchantList(gear, selected = books, other = if (hasEnchants) onItem else emptyMap())
                    }
                }
            }

            when (val s = state) {
                PlanState.Empty -> item {
                    Hint("Tick the books you have and the best order to combine them shows up here.")
                }
                PlanState.Working -> item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
                PlanState.TooExpensive -> item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Too Expensive!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(
                                "Every order has a step costing 40 levels or more, which a Survival anvil refuses. " +
                                    "Leave out a book, or reset the item's penalty with a grindstone (it removes enchantments too).",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                is PlanState.Ready -> {
                    val steps = s.plan.steps()
                    val numbers = steps.withIndex().associate { it.value to it.index + 1 }
                    item { Summary(s.plan, steps.size) }
                    itemsIndexed(steps) { i, step -> StepCard(i + 1, step, gear, s.pieces, numbers) }
                }
            }

            item {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Hint(
                        "Java Edition costs. The item always goes in the left slot. Rename only in the last step: " +
                            "renaming adds 1 level but no extra penalty there.",
                    )
                    TextButton(onClick = { onOpenArticle("Anvil mechanics") }) { Text("How anvil costs work") }
                }
            }
        }
    }
}

@Composable
private fun GearPicker(gear: Gear, onPick: (Gear) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Item: ${gear.label}", Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Gear.entries.forEach { g ->
                DropdownMenuItem(text = { Text(g.label) }, onClick = { open = false; onPick(g) })
            }
        }
    }
}

@Composable
private fun UsesStepper(uses: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Earlier anvil uses", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Adds ${priorWorkPenalty(uses)} levels to every step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onChange(uses - 1) }, enabled = uses > 0) { Icon(Icons.Filled.Remove, contentDescription = "Fewer") }
        Text("$uses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(uses + 1) }, enabled = uses < MAX_USES) { Icon(Icons.Filled.Add, contentDescription = "More") }
    }
}

/**
 * Tick list of the gear's enchantments with a level picker for each ticked one.
 * Anything that clashes with a choice here or in [other] is greyed out.
 */
@Composable
private fun EnchantList(gear: Gear, selected: SnapshotStateMap<String, Int>, other: Map<String, Int>) {
    val chosen = selected.keys + other.keys
    gear.enchants.forEach { id ->
        val e = Enchants[id]
        val on = id in selected
        val clash = chosen.firstOrNull { it != id && !Enchants.compatible(it, id) }
        val enabled = on || clash == null
        val toggle: () -> Unit = {
            if (on) {
                selected.remove(id)
            } else {
                selected[id] = e.max
            }
        }
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = toggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = { toggle() }, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(
                        e.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!enabled && clash != null) {
                        Text(
                            "Can't go with ${Enchants[clash].name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (on && e.max > 1) {
                val level = selected.getValue(id)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp)) {
                    (1..e.max).forEach { l ->
                        SegmentedButton(
                            selected = level == l,
                            onClick = { selected[id] = l },
                            shape = SegmentedButtonDefaults.itemShape(l - 1, e.max),
                        ) { Text(roman(l)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(plan: PlanNode, stepCount: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cheapest order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${plan.total} levels in total",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$stepCount ${if (stepCount == 1) "step" else "steps"} · priciest step ${plan.maxStep} levels " +
                    "(limit $MAX_STEP_COST)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Next anvil use will add ${priorWorkPenalty(plan.uses)} levels for the penalty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepCard(number: Int, step: PlanNode, gear: Gear, pieces: List<Piece>, numbers: Map<PlanNode, Int>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Step $number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${step.stepCost} ${if (step.stepCost == 1) "level" else "levels"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slot("Left", step.left!!, gear, pieces, numbers)
            Slot("Right", step.right!!, gear, pieces, numbers)
        }
    }
}

@Composable
private fun Slot(side: String, node: PlanNode, gear: Gear, pieces: List<Piece>, numbers: Map<PlanNode, Int>) {
    val isItem = if (node.isLeaf) pieces[node.piece].isItem else hasItem(node, pieces)
    val title = when {
        node.isLeaf && isItem -> gear.label
        node.isLeaf -> "Book"
        isItem -> "${gear.label} from step ${numbers[node]}"
        else -> "Book from step ${numbers[node]}"
    }
    val enchants = node.enchants.entries.joinToString(", ") { enchantLabel(it.key, it.value) }
    Row {
        Text(
            side,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (enchants.isNotEmpty()) {
                Text(enchants, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun hasItem(node: PlanNode, pieces: List<Piece>): Boolean =
    if (node.isLeaf) pieces[node.piece].isItem else hasItem(node.left!!, pieces)

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
