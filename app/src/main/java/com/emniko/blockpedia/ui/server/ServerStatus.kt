package com.emniko.blockpedia.ui.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** What a server list ping says about a server. */
sealed interface ServerStatus {
    data class Online(val players: Int, val max: Int, val version: String?) : ServerStatus
    /** Aternos answers for sleeping servers itself, marking them offline, starting or queued. */
    data class Waking(val state: String) : ServerStatus
    data object Offline : ServerStatus
    data object Unknown : ServerStatus
}

/**
 * Pings the server through the free mcsrvstat.us service (Java Edition). Its answers are cached
 * for about a minute, so a freshly started server can take a moment to show as online.
 */
suspend fun fetchServerStatus(http: OkHttpClient, address: String): ServerStatus = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.mcsrvstat.us/3/" + address)
        // The app's client already sends Blockpedia's User-Agent, which the service asks for.
        .cacheControl(CacheControl.Builder().noCache().build())
        .build()
    try {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext ServerStatus.Unknown
            val json = JSONObject(response.body?.string() ?: return@withContext ServerStatus.Unknown)
            if (!json.optBoolean("online")) return@withContext ServerStatus.Offline
            val version = json.optString("version").takeIf { it.isNotBlank() }
            val motd = json.optJSONObject("motd")?.optJSONArray("clean")?.let { lines ->
                (0 until lines.length()).joinToString(" ") { lines.optString(it) }
            }.orEmpty()
            // A sleeping Aternos server: its proxy answers with the state in the version or MOTD.
            val text = "$version $motd".lowercase()
            when {
                "offline" in text -> ServerStatus.Offline
                "starting" in text || "loading" in text || "preparing" in text -> ServerStatus.Waking("Starting")
                "queue" in text || "waiting" in text -> ServerStatus.Waking("In queue")
                "stopping" in text || "saving" in text -> ServerStatus.Waking("Stopping")
                else -> {
                    val players = json.optJSONObject("players")
                    ServerStatus.Online(players?.optInt("online") ?: 0, players?.optInt("max") ?: 0, version)
                }
            }
        }
    } catch (e: Exception) {
        ServerStatus.Unknown
    }
}
