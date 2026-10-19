package com.emniko.blockpedia.seed

import java.math.BigInteger

enum class Edition(val label: String, val nativeId: Int) {
    Java("Java", 0),
    Bedrock("Bedrock", 1),
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
    val bedrock: Boolean = true,
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
    ;

    companion object {
        fun forEdition(edition: Edition): List<StructureKind> =
            entries.filter { edition == Edition.Java || it.bedrock }
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
    }

const val STRONGHOLD_ICON_URL = WIKI_IMAGES + "EnvSprite_stronghold.png"

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
