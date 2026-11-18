package com.emniko.blockpedia.enchant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the player is in cracking: readings taken so far, the XP seed once known, and the player
 * seed once two XP seeds in a row are known. Saved so it survives closing the app mid-game.
 */
class EnchantSession(context: Context) {

    data class State(
        val readings: List<Reading> = emptyList(),
        /** Search progress 0..1 while the 2^32 search runs. */
        val searching: Float? = null,
        /** Seeds still possible; -1 before the first search. */
        val remaining: Int = -1,
        val tooMany: Boolean = false,
        val xpSeed: Int? = null,
        /** XP seed before the last enchantment, kept until the next one is cracked. */
        val previousXpSeed: Int? = null,
        /** Player random state; when known, [xpSeed] follows from it. */
        val playerSeed: Long? = null,
        val message: String? = null,
    )

    private val prefs = context.getSharedPreferences("enchanting", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var search: Job? = null
    @Volatile private var candidates: IntArray? = null

    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    fun addReading(reading: Reading) {
        val s = _state.value
        if (s.xpSeed != null || s.searching != null) return
        val readings = s.readings + reading
        _state.update { it.copy(readings = readings, message = null) }
        val known = candidates
        if (known != null) {
            search = scope.launch {
                settle(XpSeeds.filter(known, reading))
                save()
            }
        } else if (readings.size >= 2) {
            runSearch(readings)
        }
        save()
    }

    fun removeLastReading() {
        search?.cancel()
        candidates = null
        val readings = _state.value.readings.dropLast(1)
        _state.update { it.copy(readings = readings, searching = null, remaining = -1, tooMany = false, message = null) }
        if (readings.size >= 2) runSearch(readings)
        save()
    }

    /** Re-runs the search for saved readings after the app was closed mid-way. */
    fun resume() {
        val s = _state.value
        if (s.xpSeed == null && s.searching == null && candidates == null && s.readings.size >= 2 && !s.tooMany) {
            runSearch(s.readings)
        }
    }

    /** The player used the table (any slot). */
    fun enchanted() {
        val s = _state.value
        val player = s.playerSeed
        if (player != null) {
            val next = PlayerSeed.step(player)
            _state.update { it.copy(playerSeed = next, xpSeed = PlayerSeed.xpSeed(next), message = null) }
        } else {
            candidates = null
            _state.update {
                State(
                    previousXpSeed = s.xpSeed,
                    message = if (s.xpSeed != null) {
                        "Now find the new XP seed the same way. With two in a row the app works out your player seed, " +
                            "and after that it keeps up on its own."
                    } else {
                        null
                    },
                )
            }
        }
        save()
    }

    fun applyManipulation(m: Manipulation) {
        val player = _state.value.playerSeed ?: return
        val next = m.after(player)
        _state.update { it.copy(playerSeed = next, xpSeed = PlayerSeed.xpSeed(next), message = null) }
        save()
    }

    fun startOver() {
        search?.cancel()
        candidates = null
        _state.value = State()
        save()
    }

    private fun runSearch(readings: List<Reading>) {
        search?.cancel()
        _state.update { it.copy(searching = 0f, tooMany = false) }
        search = scope.launch {
            val found = XpSeeds.crack(readings) { p -> _state.update { it.copy(searching = p) } }
            if (found == null) {
                candidates = null
                _state.update { it.copy(searching = null, tooMany = true, remaining = -1) }
            } else {
                _state.update { it.copy(searching = null) }
                settle(found)
            }
            save()
        }
    }

    private fun settle(found: IntArray) {
        if (found.size != 1) {
            candidates = found
            _state.update { it.copy(remaining = found.size, tooMany = false) }
            return
        }
        candidates = null
        val seed = found[0]
        val previous = _state.value.previousXpSeed
        val player = previous?.let { PlayerSeed.fromXpSeeds(it, seed) }
        _state.update {
            it.copy(
                readings = emptyList(),
                remaining = -1,
                xpSeed = seed,
                previousXpSeed = null,
                playerSeed = player,
                message = when {
                    previous == null -> null
                    player != null -> "Player seed found. From now on the app follows your enchantments without new readings."
                    else -> "These two XP seeds aren't back to back, so something else used your random numbers in between " +
                        "(see the list below). Enchant something and find the next XP seed to try again."
                },
            )
        }
    }

    private fun load(): State = State(
        readings = prefs.getString("readings", "").orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { r ->
            r.split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 }?.let { Reading(it[0], it[1], it[2], it[3]) }
        },
        xpSeed = if (prefs.contains("xp")) prefs.getInt("xp", 0) else null,
        previousXpSeed = if (prefs.contains("prev")) prefs.getInt("prev", 0) else null,
        playerSeed = if (prefs.contains("player")) prefs.getLong("player", 0) else null,
    )

    private fun save() {
        val s = _state.value
        prefs.edit().apply {
            putString("readings", s.readings.joinToString(";") { "${it.shelves},${it.top},${it.middle},${it.bottom}" })
            if (s.xpSeed != null) putInt("xp", s.xpSeed) else remove("xp")
            if (s.previousXpSeed != null) putInt("prev", s.previousXpSeed) else remove("prev")
            if (s.playerSeed != null) putLong("player", s.playerSeed) else remove("player")
        }.apply()
    }
}
