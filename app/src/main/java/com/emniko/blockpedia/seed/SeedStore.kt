package com.emniko.blockpedia.seed

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The world the seed tools work on, remembered across launches, plus the live engine for it. */
class SeedStore(context: Context) {

    private val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)

    data class Input(val seedText: String, val edition: Edition, val javaVersion: GameVersion)

    private val _input = MutableStateFlow(
        Input(
            seedText = prefs.getString("seed", "") ?: "",
            edition = runCatching { Edition.valueOf(prefs.getString("edition", "Java") ?: "Java") }.getOrDefault(Edition.Java),
            javaVersion = JavaVersions.firstOrNull { it.cubiomes == prefs.getString("version", null) } ?: JavaVersions.first(),
        )
    )
    val input: StateFlow<Input> = _input.asStateFlow()

    private var engineConfig: SeedConfig? = null
    private val engines = HashMap<Dimension, SeedEngine>()

    fun update(input: Input) {
        _input.value = input
        prefs.edit()
            .putString("seed", input.seedText)
            .putString("edition", input.edition.name)
            .putString("version", input.javaVersion.cubiomes)
            .apply()
    }

    /** The current world, or null with a reason. */
    fun config(input: Input = _input.value): Result<SeedConfig> {
        SeedNative.loadError?.let { return Result.failure(IllegalStateException("Seed tools aren't available on this device.")) }
        return when (val p = parseSeed(input.seedText, input.edition)) {
            is SeedParse.Error -> Result.failure(IllegalArgumentException(p.message))
            is SeedParse.Ok -> Result.success(
                SeedConfig(
                    seedText = input.seedText.trim(),
                    seed = p.seed,
                    edition = input.edition,
                    version = if (input.edition == Edition.Java) input.javaVersion else BedrockVersion,
                )
            )
        }
    }

    /** Engines for one world at a time: switching worlds frees the previous ones. */
    @Synchronized
    fun engine(config: SeedConfig, dimension: Dimension = Dimension.Overworld): SeedEngine {
        if (engineConfig != config) {
            engines.values.forEach { it.close() }
            engines.clear()
            engineConfig = config
        }
        return engines.getOrPut(dimension) { SeedEngine(config, dimension) }
    }
}
