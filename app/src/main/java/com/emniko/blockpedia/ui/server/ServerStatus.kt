package com.emniko.blockpedia.ui.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/** What a server list ping says about a server. */
sealed interface ServerStatus {
    data class Online(val players: Int, val max: Int, val version: String?, val motd: String?) : ServerStatus
    /** Aternos answers for sleeping servers itself, marking them offline, starting or queued. */
    data class Waking(val state: String, val motd: String?) : ServerStatus
    data class Offline(val motd: String? = null) : ServerStatus
    data object Unknown : ServerStatus
}

/**
 * Pings the server directly from the phone with Minecraft's server list ping (Java Edition), the
 * same request the game's multiplayer screen sends. Live, with no third-party cache.
 */
suspend fun fetchServerStatus(http: OkHttpClient, address: String): ServerStatus = withContext(Dispatchers.IO) {
    val (host, explicitPort) = splitAddress(address)
    // Aternos addresses point to the real port with an SRV record, like the game resolves them.
    val (target, port) = explicitPort?.let { host to it } ?: (resolveSrv(http, host) ?: (host to 25565))
    val json = try {
        ping(host, target, port)
    } catch (e: Exception) {
        return@withContext ServerStatus.Offline()
    }
    interpret(json)
}

private fun splitAddress(address: String): Pair<String, Int?> {
    val i = address.lastIndexOf(':')
    if (i > 0) address.substring(i + 1).toIntOrNull()?.let { return address.substring(0, i) to it }
    return address to null
}

/** _minecraft._tcp SRV lookup through DNS over HTTPS (Android has no SRV API before 10). */
private fun resolveSrv(http: OkHttpClient, host: String): Pair<String, Int>? = try {
    val request = Request.Builder()
        .url("https://dns.google/resolve?name=_minecraft._tcp.$host&type=SRV")
        .cacheControl(CacheControl.Builder().noCache().build())
        .build()
    http.newCall(request).execute().use { response ->
        val answers = JSONObject(response.body?.string().orEmpty()).optJSONArray("Answer") ?: return null
        (0 until answers.length()).map { answers.getJSONObject(it) }
            .firstOrNull { it.optInt("type") == 33 }
            ?.optString("data")
            ?.trim()?.split(Regex("\\s+"))
            ?.takeIf { it.size == 4 }
            ?.let { (_, _, p, t) -> t.trimEnd('.') to p.toInt() }
    }
} catch (e: Exception) {
    null
}

/** Handshake (next state: status) + status request; returns the server's JSON answer. */
private fun ping(host: String, target: String, port: Int): JSONObject {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(target, port), 6000)
        socket.soTimeout = 6000
        val out = socket.getOutputStream()

        val handshake = ByteArrayOutputStream().apply {
            writeVarInt(0x00)            // packet id
            writeVarInt(767)             // protocol version (any works for status)
            writeString(host)            // the name the player typed: proxies like Aternos route by it
            write(port shr 8 and 0xFF)
            write(port and 0xFF)
            writeVarInt(1)               // next state: status
        }.toByteArray()
        out.write(ByteArrayOutputStream().apply { writeVarInt(handshake.size); write(handshake) }.toByteArray())
        out.write(byteArrayOf(1, 0x00))  // status request
        out.flush()

        val input = DataInputStream(socket.getInputStream())
        readVarInt(input)                // packet length
        if (readVarInt(input) != 0x00) error("Unexpected packet")
        val length = readVarInt(input)
        if (length !in 1..(1 shl 20)) error("Bad length")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }
}

private fun interpret(json: JSONObject): ServerStatus {
    val version = json.optJSONObject("version")?.optString("name")?.let(::plain)?.takeIf { it.isNotBlank() }
    val motd = chatText(json.opt("description")).let(::plain).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
    // A sleeping Aternos server: its proxy answers with the state in the version name.
    val state = version.orEmpty().lowercase()
    return when {
        "offline" in state -> ServerStatus.Offline(motd)
        "starting" in state || "loading" in state || "preparing" in state -> ServerStatus.Waking("Starting", motd)
        "queue" in state || "waiting" in state -> ServerStatus.Waking("In queue", motd)
        "stopping" in state || "saving" in state -> ServerStatus.Waking("Stopping", motd)
        else -> {
            val players = json.optJSONObject("players")
            ServerStatus.Online(players?.optInt("online") ?: 0, players?.optInt("max") ?: 0, version, motd)
        }
    }
}

/** Text of a chat component: a plain string, or an object with "text" and "extra". */
private fun chatText(value: Any?): String = when (value) {
    is String -> value
    is JSONObject -> buildString {
        append(value.optString("text"))
        value.optJSONArray("extra")?.let { append(chatText(it)) }
    }
    is JSONArray -> (0 until value.length()).joinToString("") { chatText(value.opt(it)) }
    else -> ""
}

/** Drops § formatting codes and decoration symbols Aternos puts around its state. */
private fun plain(text: String): String = text.replace(Regex("§."), "").trim()

private fun ByteArrayOutputStream.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if (v and 0x7F.inv() == 0) {
            write(v)
            return
        }
        write(v and 0x7F or 0x80)
        v = v ushr 7
    }
}

private fun ByteArrayOutputStream.writeString(s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    write(bytes)
}

private fun readVarInt(input: InputStream): Int {
    var result = 0
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) error("End of stream")
        result = result or (b and 0x7F shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
        if (shift > 35) error("VarInt too long")
    }
}
