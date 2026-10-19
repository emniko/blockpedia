package com.emniko.blockpedia.seed

import java.math.BigInteger

enum class Edition(val label: String, val nativeId: Int) {
    Java("Java", 0),
    Bedrock("Bedrock", 1),
}

enum class Dimension(val label: String, val nativeId: Int) {
    Overworld("Overworld", 0),
    Nether("Nether", -1),
    End("End", 1),
    ;

    companion object {
        /** Bedrock's End terrain isn't modelled, so it has no End map. */
        fun forEdition(edition: Edition): List<Dimension> =
            if (edition == Edition.Java) entries.toList() else listOf(Overworld, Nether)
    }
}

/** A Java version range that shares one world generator. [cubiomes] is the native version name. */
data class GameVersion(val label: String, val cubiomes: String)

val JavaVersions = listOf(
    GameVersion("26.3", "26.3"),
    GameVersion("26.2", "26.2"),
    GameVersion("26.1", "26.1"),
    GameVersion("1.21.11", "1.21.11"),
    GameVersion("1.21.9 – 1.21.10", "1.21.9"),
    GameVersion("1.21.6 – 1.21.8", "1.21.6"),
    GameVersion("1.21.5", "1.21.5"),
    GameVersion("1.21.4", "1.21.4"),
    GameVersion("1.21.2 – 1.21.3", "1.21.3"),
    GameVersion("1.21 – 1.21.1", "1.21.1"),
    GameVersion("1.20", "1.20"),
    GameVersion("1.19", "1.19"),
    GameVersion("1.18", "1.18"),
)

/** Bedrock shares Java's overworld biomes since 1.18; the newest generator is used. */
val BedrockVersion = GameVersion("Latest", "26.3")

data class Pos(val x: Int, val z: Int) {
    fun distanceTo(x0: Int, z0: Int): Double {
        val dx = (x - x0).toDouble()
        val dz = (z - z0).toDouble()
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}

data class SeedConfig(val seedText: String, val seed: Long, val edition: Edition, val version: GameVersion)

sealed interface SeedParse {
    data class Ok(val seed: Long) : SeedParse
    data class Error(val message: String) : SeedParse
}

private val NUMBER = Regex("^[+-]?\\d+$")
private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
private val ULONG_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

/**
 * Numbers are used as-is (Bedrock can show unsigned 64-bit seeds, which wrap to the same value).
 * Java turns text seeds into numbers with String.hashCode(); Bedrock text seeds aren't supported.
 */
fun parseSeed(text: String, edition: Edition): SeedParse {
    val t = text.trim()
    if (t.isEmpty()) return SeedParse.Error("Enter a seed")
    if (NUMBER.matches(t)) {
        val n = BigInteger(t.removePrefix("+"))
        return if (n < LONG_MIN || n > ULONG_MAX) SeedParse.Error("Seed is out of range") else SeedParse.Ok(n.toLong())
    }
    return when (edition) {
        Edition.Java -> SeedParse.Ok(t.hashCode().toLong())
        Edition.Bedrock -> SeedParse.Error("Bedrock seeds are numbers. Find yours in the world settings.")
    }
}

/**
 * Structure types, using cubiomes' StructureType ids. [maxScale] is the coarsest map scale
 * (blocks per biome cell) at which the type is still computed and drawn.
 */
enum class StructureKind(
    val id: Int,
    val label: String,
    val letter: Char,
    val color: Long,
    val maxScale: Int,
    val dimension: Dimension = Dimension.Overworld,
    val java: Boolean = true,
    val bedrock: Boolean = true,
    /** Non-zero for point layers computed by the native layer code (SMP_* ids) instead of a structure id. */
    val pointLayer: Int = 0,
) {
    Village(5, "Village", 'V', 0xFFF4B740, 64),
    Outpost(10, "Pillager outpost", 'P', 0xFF9E9E9E, 64),
    DesertTemple(1, "Desert temple", 'D', 0xFFE8CF7E, 64),
    JungleTemple(2, "Jungle temple", 'J', 0xFF66BB6A, 64),
    WitchHut(3, "Witch hut", 'W', 0xFFAB7FDC, 64),
    Igloo(4, "Igloo", 'I', 0xFFB3E5FC, 64),
    Monument(8, "Ocean monument", 'M', 0xFF26C6DA, 256),
    Mansion(9, "Woodland mansion", 'H', 0xFFA1887F, 256),
    AncientCity(13, "Ancient city", 'A', 0xFF4DD0E1, 64),
    TrailRuins(24, "Trail ruins", 'T', 0xFFD7A86E, 64),
    TrialChambers(25, "Trial chambers", 'C', 0xFFFF8A65, 16),
    AbandonedCamp(26, "Abandoned camp", 'K', 0xFFBCAAA4, 16),
    RuinedPortal(11, "Ruined portal", 'R', 0xFFCE93D8, 16),
    Shipwreck(7, "Shipwreck", 'S', 0xFF8D6E63, 16),
    OceanRuin(6, "Ocean ruins", 'O', 0xFF80CBC4, 16),
    BuriedTreasure(14, "Buried treasure", 'X', 0xFFFFD54F, 4),
    // Decided per chunk in Java; Bedrock's rule isn't documented.
    Mineshaft(15, "Mineshaft", 'Z', 0xFFB0855A, 16, bedrock = false),
    Fortress(18, "Nether fortress", 'F', 0xFFE57373, 64, Dimension.Nether, bedrock = false),
    Bastion(19, "Bastion remnant", 'B', 0xFF9E9E9E, 64, Dimension.Nether, bedrock = false),
    // Bedrock places one of the two per region; which one isn't documented.
    NetherComplex(18, "Fortress or bastion", 'F', 0xFFE57373, 64, Dimension.Nether, java = false),
    NetherPortal(12, "Ruined portal (Nether)", 'R', 0xFFCE93D8, 16, Dimension.Nether),
    EndCity(21, "End city", 'E', 0xFFE1BEE7, 64, Dimension.End, bedrock = false),

    // Point layers with Y levels (Java only).
    Geode(-1, "Amethyst geode", 'G', 0xFFB39DDB, 4, bedrock = false, pointLayer = 1),
    DesertWell(-1, "Desert well", 'L', 0xFFE6D3A3, 16, bedrock = false, pointLayer = 2),
    EndGateway(-1, "End gateway", 'Y', 0xFF80DEEA, 16, Dimension.End, bedrock = false, pointLayer = 3),
    CopperVein(-1, "Copper ore vein", 'U', 0xFFE07A4F, 4, bedrock = false, pointLayer = 4),
    IronVein(-1, "Iron ore vein", 'N', 0xFFD8AF93, 4, bedrock = false, pointLayer = 5),
    ;

    fun availableIn(edition: Edition): Boolean = if (edition == Edition.Java) java else bedrock

    companion object {
        fun forEdition(edition: Edition): List<StructureKind> = entries.filter { it.availableIn(edition) }

        fun forWorld(edition: Edition, dimension: Dimension): List<StructureKind> =
            entries.filter { it.dimension == dimension && it.availableIn(edition) }
    }
}

private const val WIKI_IMAGES = "https://minecraft.wiki/images/"

/** The structure's icon on the Minecraft Wiki, loaded at runtime like other wiki images. */
val StructureKind.iconUrl: String
    get() = WIKI_IMAGES + when (this) {
        StructureKind.Village -> "EnvSprite_new-village.png"
        StructureKind.Outpost -> "EnvSprite_pillager-outpost.png"
        StructureKind.DesertTemple -> "EnvSprite_desert-pyramid.png"
        StructureKind.JungleTemple -> "EnvSprite_jungle-pyramid.png"
        StructureKind.WitchHut -> "EnvSprite_swamp-hut.png"
        StructureKind.Igloo -> "EnvSprite_igloo.png"
        StructureKind.Monument -> "EnvSprite_monument.png"
        StructureKind.Mansion -> "EnvSprite_mansion.png"
        StructureKind.AncientCity -> "EnvSprite_ancient-city.png"
        StructureKind.TrailRuins -> "EnvSprite_trail-ruins.png"
        StructureKind.TrialChambers -> "EnvSprite_trial-chambers.png"
        StructureKind.AbandonedCamp -> "EnvSprite_abandoned-camp.png"
        StructureKind.RuinedPortal -> "EnvSprite_ruined-portal.png"
        StructureKind.Shipwreck -> "EnvSprite_shipwreck.png"
        StructureKind.OceanRuin -> "EnvSprite_ocean-ruin.png"
        StructureKind.BuriedTreasure -> "BlockSprite_chest.png"
        StructureKind.Mineshaft -> "EnvSprite_abandoned-mineshaft.png"
        StructureKind.Fortress, StructureKind.NetherComplex -> "EnvSprite_fortress.png"
        StructureKind.Bastion -> "EnvSprite_bastion-remnant.png"
        StructureKind.NetherPortal -> "EnvSprite_ruined-portal.png"
        StructureKind.EndCity -> "EnvSprite_end-city.png"
        StructureKind.Geode -> "EnvSprite_amethyst-geode.png"
        StructureKind.DesertWell -> "EnvSprite_desert-well.png"
        StructureKind.EndGateway -> "EnvSprite_end-gateway.png"
        StructureKind.CopperVein -> "BlockSprite_block-of-raw-copper.png"
        StructureKind.IronVein -> "BlockSprite_block-of-raw-iron.png"
    }

const val APPLE_ICON_URL = WIKI_IMAGES + "ItemSprite_enchanted-golden-apple.png"

/** A map feature: a structure (y unknown) or a point layer with a Y level and extra info. */
data class MapPoint(val pos: Pos, val y: Int? = null, val info: Int = 0)

/** cubiomes' structure id for strongholds (loot lookups). */
const val STRONGHOLD_ID = 27

/** Structures whose chest loot the library can generate (Java). */
val StructureKind.hasLoot: Boolean
    get() = this in setOf(
        StructureKind.DesertTemple, StructureKind.JungleTemple, StructureKind.Igloo, StructureKind.Outpost,
        StructureKind.Shipwreck, StructureKind.BuriedTreasure, StructureKind.RuinedPortal, StructureKind.NetherPortal,
        StructureKind.Fortress, StructureKind.Bastion, StructureKind.EndCity, StructureKind.AbandonedCamp,
    )

/** Structures that can hold enchanted golden apples and whose loot is supported. */
val StructureKind.canHaveApples: Boolean
    get() = this == StructureKind.DesertTemple || this == StructureKind.RuinedPortal ||
        this == StructureKind.NetherPortal || this == StructureKind.Bastion

/** Structures with variants worth showing (zombie village, giant portal, igloo basement, end ship). */
val StructureKind.hasVariants: Boolean
    get() = this == StructureKind.Village || this == StructureKind.RuinedPortal ||
        this == StructureKind.NetherPortal || this == StructureKind.Igloo || this == StructureKind.EndCity

/** Where to dig: exact Y when known, otherwise heights documented on the Minecraft Wiki. */
fun yLevelText(kind: StructureKind, point: MapPoint): String? = when (kind) {
    StructureKind.AncientCity -> "Floor at Y -51"
    StructureKind.TrialChambers -> "Start room at Y -40 to -20; most rooms between Y -20 and 0"
    StructureKind.Mineshaft -> "Underground (often at the surface in badlands)"
    StructureKind.Geode -> point.y?.let { "Geode at about Y $it" }
    StructureKind.CopperVein -> point.y?.let { "Copper ore found at Y $it (veins run from Y 0 to 50)" }
    StructureKind.IronVein -> point.y?.let { "Iron ore found at Y $it (veins run from Y -60 to -8)" }
    else -> null
}

const val STRONGHOLD_Y_TEXT = "Underground, at any height"

/** Readable text for variant flags from the native engine. */
fun variantText(flags: Int): List<String> = buildList {
    if (flags and 1 != 0) add("Zombie village")
    if (flags and 2 != 0) add("Giant portal")
    if (flags and 4 != 0) add("Buried underground")
    if (flags and 8 != 0) add("Has a hidden basement")
    if (flags and 16 != 0) add("Has an end ship, with elytra")
}

const val STRONGHOLD_ICON_URL = WIKI_IMAGES + "EnvSprite_stronghold.png"

/** Monster spawners inside the structure (Minecraft Wiki, "Monster Spawner"), or null. */
val StructureKind.spawners: String?
    get() = when (this) {
        StructureKind.Mineshaft -> "Cave spider spawners, among cobwebs"
        StructureKind.Mansion -> "Spider spawner, only in a rare secret room"
        StructureKind.Fortress -> "Blaze spawners, usually two"
        StructureKind.Bastion -> "Magma cube spawner, in treasure bastions only"
        StructureKind.NetherComplex -> "Blaze spawners if it's a fortress; magma cube spawner if it's a treasure bastion"
        StructureKind.TrialChambers -> "Trial spawners"
        else -> null
    }

const val STRONGHOLD_SPAWNERS = "Silverfish spawner in the end portal room"

/** Biome display data from the native engine. */
object BiomePalette {
    private val colors: IntArray by lazy {
        IntArray(256).also { if (SeedNative.loadError == null) SeedNative.nativeBiomeColors(it) }
    }
    private val names = HashMap<Long, String>()

    fun color(id: Int): Int = if (id in 0..255) colors[id] else 0xFF000000.toInt()

    fun name(mc: Int, id: Int): String = synchronized(names) {
        names.getOrPut((mc.toLong() shl 32) or (id.toLong() and 0xffffffffL)) {
            val raw = SeedNative.nativeBiomeName(mc, id) ?: "unknown"
            raw.split('_').joinToString(" ").replaceFirstChar { it.uppercaseChar() }
        }
    }
}

/** Compass direction from (x0, z0) toward (x, z); north is -Z in Minecraft. */
fun direction(x0: Int, z0: Int, x: Int, z: Int): String {
    val dx = (x - x0).toDouble()
    val dz = (z - z0).toDouble()
    if (dx == 0.0 && dz == 0.0) return "here"
    val angle = (Math.toDegrees(kotlin.math.atan2(dx, -dz)) + 360.0) % 360.0
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[((angle + 22.5) / 45.0).toInt() % 8]
}
