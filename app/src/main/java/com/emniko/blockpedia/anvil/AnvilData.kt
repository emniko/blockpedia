package com.emniko.blockpedia.anvil

/**
 * Java Edition anvil data (26.x). Multipliers are the "anvil cost" of each enchantment:
 * [itemMult] when it comes from an enchanted item, [bookMult] when it comes from a book.
 * Source: Minecraft Wiki, Anvil mechanics; Lunge from the game's enchantment data (anvil_cost 2).
 */
data class Enchant(val id: String, val name: String, val max: Int, val itemMult: Int, val bookMult: Int)

object Enchants {
    val all: List<Enchant> = listOf(
        Enchant("protection", "Protection", 4, 1, 1),
        Enchant("fire_protection", "Fire Protection", 4, 2, 1),
        Enchant("blast_protection", "Blast Protection", 4, 4, 2),
        Enchant("projectile_protection", "Projectile Protection", 4, 2, 1),
        Enchant("feather_falling", "Feather Falling", 4, 2, 1),
        Enchant("thorns", "Thorns", 3, 8, 4),
        Enchant("respiration", "Respiration", 3, 4, 2),
        Enchant("aqua_affinity", "Aqua Affinity", 1, 4, 2),
        Enchant("depth_strider", "Depth Strider", 3, 4, 2),
        Enchant("frost_walker", "Frost Walker", 2, 4, 2),
        Enchant("soul_speed", "Soul Speed", 3, 8, 4),
        Enchant("swift_sneak", "Swift Sneak", 3, 8, 4),
        Enchant("sharpness", "Sharpness", 5, 1, 1),
        Enchant("smite", "Smite", 5, 2, 1),
        Enchant("bane_of_arthropods", "Bane of Arthropods", 5, 2, 1),
        Enchant("knockback", "Knockback", 2, 2, 1),
        Enchant("fire_aspect", "Fire Aspect", 2, 4, 2),
        Enchant("looting", "Looting", 3, 4, 2),
        Enchant("sweeping_edge", "Sweeping Edge", 3, 4, 2),
        Enchant("lunge", "Lunge", 3, 2, 1),
        Enchant("density", "Density", 5, 2, 1),
        Enchant("breach", "Breach", 4, 4, 2),
        Enchant("wind_burst", "Wind Burst", 3, 4, 2),
        Enchant("efficiency", "Efficiency", 5, 1, 1),
        Enchant("fortune", "Fortune", 3, 4, 2),
        Enchant("silk_touch", "Silk Touch", 1, 8, 4),
        Enchant("power", "Power", 5, 1, 1),
        Enchant("punch", "Punch", 2, 4, 2),
        Enchant("flame", "Flame", 1, 4, 2),
        Enchant("infinity", "Infinity", 1, 8, 4),
        Enchant("multishot", "Multishot", 1, 4, 2),
        Enchant("piercing", "Piercing", 4, 1, 1),
        Enchant("quick_charge", "Quick Charge", 3, 2, 1),
        Enchant("impaling", "Impaling", 5, 4, 2),
        Enchant("loyalty", "Loyalty", 3, 1, 1),
        Enchant("riptide", "Riptide", 3, 4, 2),
        Enchant("channeling", "Channeling", 1, 8, 4),
        Enchant("luck_of_the_sea", "Luck of the Sea", 3, 4, 2),
        Enchant("lure", "Lure", 3, 4, 2),
        Enchant("unbreaking", "Unbreaking", 3, 2, 1),
        Enchant("mending", "Mending", 1, 4, 2),
        Enchant("binding_curse", "Curse of Binding", 1, 8, 4),
        Enchant("vanishing_curse", "Curse of Vanishing", 1, 8, 4),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): Enchant = byId.getValue(id)

    /** Groups whose members can't be on the same item. */
    private val exclusive: List<Set<String>> = listOf(
        setOf("protection", "fire_protection", "blast_protection", "projectile_protection"),
        setOf("sharpness", "smite", "bane_of_arthropods", "density", "breach", "impaling"),
        setOf("fortune", "silk_touch"),
        setOf("infinity", "mending"),
        setOf("multishot", "piercing"),
        setOf("depth_strider", "frost_walker"),
        setOf("riptide", "loyalty"),
        setOf("riptide", "channeling"),
    )

    fun compatible(a: String, b: String): Boolean =
        a == b || exclusive.none { a in it && b in it }
}

private val DURABLE = listOf("unbreaking", "mending", "vanishing_curse")
private val WEARABLE = DURABLE + "binding_curse"
private val ARMOR = listOf("protection", "fire_protection", "blast_protection", "projectile_protection", "thorns")
private val TOOL = listOf("efficiency", "fortune", "silk_touch")
private val WEAPON = listOf("sharpness", "smite", "bane_of_arthropods")

/** Items that can be enchanted in an anvil, with the enchantments each accepts (Java 26.x). */
enum class Gear(val label: String, val enchants: List<String>) {
    Sword("Sword", WEAPON + listOf("knockback", "fire_aspect", "looting", "sweeping_edge") + DURABLE),
    Axe("Axe", WEAPON + TOOL + DURABLE),
    Pickaxe("Pickaxe", TOOL + DURABLE),
    Shovel("Shovel", TOOL + DURABLE),
    Hoe("Hoe", TOOL + DURABLE),
    Spear("Spear", WEAPON + listOf("knockback", "fire_aspect", "looting", "lunge") + DURABLE),
    Mace("Mace", listOf("density", "breach", "smite", "bane_of_arthropods", "wind_burst", "fire_aspect") + DURABLE),
    Bow("Bow", listOf("power", "punch", "flame", "infinity") + DURABLE),
    Crossbow("Crossbow", listOf("multishot", "piercing", "quick_charge") + DURABLE),
    Trident("Trident", listOf("impaling", "loyalty", "riptide", "channeling") + DURABLE),
    Helmet("Helmet", ARMOR + listOf("respiration", "aqua_affinity") + WEARABLE),
    TurtleShell("Turtle Shell", ARMOR + listOf("respiration", "aqua_affinity") + WEARABLE),
    Chestplate("Chestplate", ARMOR + WEARABLE),
    Leggings("Leggings", ARMOR + "swift_sneak" + WEARABLE),
    Boots("Boots", ARMOR + listOf("feather_falling", "depth_strider", "frost_walker", "soul_speed") + WEARABLE),
    FishingRod("Fishing Rod", listOf("luck_of_the_sea", "lure") + DURABLE),
    Elytra("Elytra", WEARABLE),
    Shield("Shield", DURABLE),
    Shears("Shears", listOf("efficiency") + DURABLE),
    Brush("Brush", DURABLE),
    FlintAndSteel("Flint and Steel", DURABLE),
    CarrotOnAStick("Carrot on a Stick", DURABLE),
    WarpedFungusOnAStick("Warped Fungus on a Stick", DURABLE),
}

private val ROMAN = listOf("I", "II", "III", "IV", "V")

fun roman(level: Int): String = ROMAN.getOrElse(level - 1) { level.toString() }

/** "Sharpness V", or just "Mending" for single-level enchantments. */
fun enchantLabel(id: String, level: Int): String {
    val e = Enchants[id]
    return if (e.max == 1) e.name else "${e.name} ${roman(level)}"
}
