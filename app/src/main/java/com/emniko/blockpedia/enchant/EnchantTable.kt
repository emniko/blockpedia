package com.emniko.blockpedia.enchant

import java.util.Random

/*
 * Enchanting table maths for Java Edition 1.16 and newer (tested against 1.21.11 – 26.x data).
 * Ported from Earthcomputer's EnchantmentCracker (MIT licence):
 * https://github.com/Earthcomputer/EnchantmentCracker
 * Copyright (c) 2019 Joseph Burton (Earthcomputer). See the About screen for the full notice.
 */

/**
 * An enchantment the table can give. Its enchanting power window for level `l` is
 * `minBase + (l-1)*minPer .. maxBase + (l-1)*maxPer`.
 */
class TableEnchant(
    val id: String,
    val name: String,
    val max: Int,
    val weight: Int,
    private val minBase: Int,
    private val minPer: Int,
    private val maxBase: Int,
    private val maxPer: Int,
) {
    fun minPower(level: Int) = minBase + (level - 1) * minPer
    fun maxPower(level: Int) = maxBase + (level - 1) * maxPer
}

data class EnchantLevel(val id: String, val level: Int)

object TableEnchants {
    /** In the game's registry order, which the weighted pick depends on. */
    val all: List<TableEnchant> = listOf(
        TableEnchant("protection", "Protection", 4, 10, 1, 11, 12, 11),
        TableEnchant("fire_protection", "Fire Protection", 4, 5, 10, 8, 18, 8),
        TableEnchant("feather_falling", "Feather Falling", 4, 5, 5, 6, 11, 6),
        TableEnchant("blast_protection", "Blast Protection", 4, 2, 5, 8, 13, 8),
        TableEnchant("projectile_protection", "Projectile Protection", 4, 5, 3, 6, 9, 6),
        TableEnchant("respiration", "Respiration", 3, 2, 10, 10, 40, 10),
        TableEnchant("aqua_affinity", "Aqua Affinity", 1, 2, 1, 0, 41, 0),
        TableEnchant("thorns", "Thorns", 3, 1, 10, 20, 60, 20),
        TableEnchant("depth_strider", "Depth Strider", 3, 2, 10, 10, 25, 10),
        TableEnchant("sharpness", "Sharpness", 5, 10, 1, 11, 21, 11),
        TableEnchant("smite", "Smite", 5, 5, 5, 8, 25, 8),
        TableEnchant("bane_of_arthropods", "Bane of Arthropods", 5, 5, 5, 8, 25, 8),
        TableEnchant("knockback", "Knockback", 2, 5, 5, 20, 55, 20),
        TableEnchant("fire_aspect", "Fire Aspect", 2, 2, 10, 20, 60, 20),
        TableEnchant("looting", "Looting", 3, 2, 15, 9, 65, 9),
        TableEnchant("sweeping_edge", "Sweeping Edge", 3, 2, 5, 9, 20, 9),
        TableEnchant("efficiency", "Efficiency", 5, 10, 1, 10, 60, 10),
        TableEnchant("silk_touch", "Silk Touch", 1, 1, 15, 0, 65, 0),
        TableEnchant("unbreaking", "Unbreaking", 3, 5, 5, 8, 55, 8),
        TableEnchant("fortune", "Fortune", 3, 2, 15, 9, 65, 9),
        TableEnchant("power", "Power", 5, 10, 1, 10, 16, 10),
        TableEnchant("punch", "Punch", 2, 2, 12, 20, 37, 20),
        TableEnchant("flame", "Flame", 1, 2, 20, 0, 50, 0),
        TableEnchant("infinity", "Infinity", 1, 1, 20, 0, 50, 0),
        TableEnchant("luck_of_the_sea", "Luck of the Sea", 3, 2, 15, 9, 65, 9),
        TableEnchant("lure", "Lure", 3, 2, 15, 9, 65, 9),
        TableEnchant("loyalty", "Loyalty", 3, 5, 12, 7, 50, 0),
        TableEnchant("impaling", "Impaling", 5, 2, 1, 8, 21, 8),
        TableEnchant("riptide", "Riptide", 3, 2, 17, 7, 50, 0),
        TableEnchant("channeling", "Channeling", 1, 1, 25, 0, 50, 0),
        TableEnchant("multishot", "Multishot", 1, 2, 20, 0, 50, 0),
        TableEnchant("quick_charge", "Quick Charge", 3, 5, 12, 20, 50, 0),
        TableEnchant("piercing", "Piercing", 4, 10, 1, 10, 50, 0),
        TableEnchant("density", "Density", 5, 5, 5, 8, 25, 8),
        TableEnchant("breach", "Breach", 4, 2, 15, 9, 65, 9),
        TableEnchant("lunge", "Lunge", 3, 5, 5, 8, 25, 8),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): TableEnchant = byId.getValue(id)

    private val damage = setOf("sharpness", "smite", "bane_of_arthropods", "density", "breach")
    private val protection = setOf("protection", "fire_protection", "blast_protection", "projectile_protection")
    private val pairs = listOf(
        "depth_strider" to "frost_walker",
        "silk_touch" to "looting",
        "silk_touch" to "fortune",
        "silk_touch" to "luck_of_the_sea",
        "riptide" to "loyalty",
        "riptide" to "channeling",
        "multishot" to "piercing",
    )

    fun compatible(a: String, b: String): Boolean {
        if (a == b) return false
        if (a in damage && b in damage) return false
        if (a in protection && b in protection) return false
        return pairs.none { (x, y) -> (a == x && b == y) || (a == y && b == x) }
    }

    fun label(e: EnchantLevel): String {
        val t = byId[e.id] ?: return e.id
        return if (t.max == 1) t.name else "${t.name} ${roman(e.level)}"
    }

    private fun roman(level: Int) = listOf("I", "II", "III", "IV", "V").getOrElse(level - 1) { level.toString() }
}

enum class ItemKind(val label: String) {
    Helmet("Helmet"), Chestplate("Chestplate"), Leggings("Leggings"), Boots("Boots"),
    Sword("Sword"), Spear("Spear"), Axe("Axe"), Pickaxe("Pickaxe"), Shovel("Shovel"), Hoe("Hoe"),
    Bow("Bow"), Crossbow("Crossbow"), Trident("Trident"), FishingRod("Fishing Rod"), Mace("Mace"), Book("Book");

    val isArmor get() = this == Helmet || this == Chestplate || this == Leggings || this == Boots
    val isTool get() = this == Axe || this == Pickaxe || this == Shovel || this == Hoe
    val isWeapon get() = this == Sword || this == Spear

    /** Materials it comes in, with their enchantability. Empty means a single fixed item. */
    val materials: List<Material>
        get() = when {
            this == Helmet -> ARMOR_MATERIALS + Material.Turtle
            isArmor -> ARMOR_MATERIALS
            isTool || isWeapon -> TOOL_MATERIALS
            else -> emptyList()
        }
}

enum class Material(val label: String, val armor: Int, val tool: Int) {
    Leather("Leather", 15, 0),
    Chainmail("Chainmail", 12, 0),
    Wood("Wooden", 0, 15),
    Stone("Stone", 0, 5),
    Copper("Copper", 8, 13),
    Iron("Iron", 9, 14),
    Gold("Golden", 25, 22),
    Diamond("Diamond", 10, 10),
    Netherite("Netherite", 15, 15),
    Turtle("Turtle", 9, 0),
}

private val ARMOR_MATERIALS = listOf(
    Material.Leather, Material.Chainmail, Material.Copper, Material.Iron, Material.Gold, Material.Diamond, Material.Netherite,
)
private val TOOL_MATERIALS = listOf(
    Material.Wood, Material.Stone, Material.Copper, Material.Iron, Material.Gold, Material.Diamond, Material.Netherite,
)

data class TableItem(val kind: ItemKind, val material: Material? = kind.materials.lastOrNull()) {
    val enchantability: Int
        get() = when {
            kind.isArmor -> material?.armor ?: 0
            kind.isTool || kind.isWeapon -> material?.tool ?: 0
            kind == ItemKind.Mace -> 15
            else -> 1
        }

    val label: String
        get() = when {
            material == null -> kind.label
            material == Material.Turtle -> "Turtle Shell"
            else -> "${material.label} ${kind.label}"
        }

    /** Whether the table can roll [e] for this item (its "primary" items; books take everything). */
    fun accepts(e: String): Boolean = when (e) {
        "protection", "fire_protection", "blast_protection", "projectile_protection" -> kind.isArmor
        "thorns" -> kind == ItemKind.Chestplate
        "feather_falling", "depth_strider" -> kind == ItemKind.Boots
        "respiration", "aqua_affinity" -> kind == ItemKind.Helmet
        "sharpness", "smite", "bane_of_arthropods", "fire_aspect", "knockback", "looting" -> kind.isWeapon
        "sweeping_edge" -> kind == ItemKind.Sword
        "lunge" -> kind == ItemKind.Spear
        "efficiency", "silk_touch", "fortune" -> kind.isTool
        "power", "punch", "flame", "infinity" -> kind == ItemKind.Bow
        "luck_of_the_sea", "lure" -> kind == ItemKind.FishingRod
        "unbreaking" -> kind != ItemKind.Book
        "loyalty", "impaling", "riptide", "channeling" -> kind == ItemKind.Trident
        "multishot", "quick_charge", "piercing" -> kind == ItemKind.Crossbow
        "density", "breach" -> kind == ItemKind.Mace
        else -> false
    } || kind == ItemKind.Book

    /** Enchantments this item can get from the table, for pickers. */
    val possible: List<TableEnchant> get() = TableEnchants.all.filter { accepts(it.id) }
}

/** One of the three buttons in the table. [cost] 0 means the button is greyed out. */
data class Offer(val slot: Int, val cost: Int, val enchants: List<EnchantLevel>, val clue: EnchantLevel?)

object EnchantTable {
    const val MAX_SHELVES = 15

    /** The three level costs shown for [xpSeed] with [shelves] bookshelves (0 = button empty). */
    fun costs(xpSeed: Int, shelves: Int, random: Random = Random()): IntArray {
        random.setSeed(xpSeed.toLong())
        return IntArray(3) { slot ->
            val power = random.nextInt(8) + 1 + (shelves shr 1) + random.nextInt(shelves + 1)
            val cost = when (slot) {
                0 -> maxOf(power / 3, 1)
                1 -> power * 2 / 3 + 1
                else -> maxOf(power, shelves * 2)
            }
            if (cost < slot + 1) 0 else cost
        }
    }

    /** What the table offers for [item] with [shelves] bookshelves, including the hint the game shows. */
    fun offers(xpSeed: Int, item: TableItem, shelves: Int): List<Offer> {
        val random = Random()
        val costs = costs(xpSeed, shelves, random)
        return List(3) { slot ->
            if (costs[slot] == 0) {
                Offer(slot, 0, emptyList(), null)
            } else {
                val list = enchantments(random, xpSeed, item, slot, costs[slot])
                val clue = if (list.isEmpty()) null else list[random.nextInt(list.size)]
                Offer(slot, costs[slot], list, clue)
            }
        }
    }

    /** The enchantments a slot gives; leaves [random] where the game continues from (the hint). */
    fun enchantments(random: Random, xpSeed: Int, item: TableItem, slot: Int, cost: Int): MutableList<EnchantLevel> {
        random.setSeed((xpSeed + slot).toLong())
        val list = select(random, item, cost)
        if (item.kind == ItemKind.Book && list.size > 1) list.removeAt(random.nextInt(list.size))
        return list
    }

    private fun select(random: Random, item: TableItem, cost: Int): MutableList<EnchantLevel> {
        val out = ArrayList<EnchantLevel>()
        val enchantability = item.enchantability
        if (enchantability <= 0) return out
        var power = cost + 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1)
        val change = (random.nextFloat() + random.nextFloat() - 1) * 0.15f
        power += Math.round(power * change)
        if (power < 1) power = 1

        val allowed = available(power, item)
        if (allowed.isEmpty()) return out
        out += weighted(random, allowed)
        while (random.nextInt(50) <= power) {
            for (chosen in out) allowed.removeAll { !TableEnchants.compatible(it.id, chosen.id) }
            if (allowed.isEmpty()) break
            out += weighted(random, allowed)
            power /= 2
        }
        return out
    }

    private fun available(power: Int, item: TableItem): MutableList<EnchantLevel> {
        val out = ArrayList<EnchantLevel>()
        for (e in TableEnchants.all) {
            if (!item.accepts(e.id)) continue
            for (level in e.max downTo 1) {
                if (power >= e.minPower(level) && power <= e.maxPower(level)) {
                    out += EnchantLevel(e.id, level)
                    break
                }
            }
        }
        return out
    }

    private fun weighted(random: Random, list: List<EnchantLevel>): EnchantLevel {
        var roll = random.nextInt(list.sumOf { TableEnchants[it.id].weight })
        for (e in list) {
            roll -= TableEnchants[e.id].weight
            if (roll < 0) return e
        }
        return list.last()
    }
}
