package com.emniko.blockpedia.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Stores raw API responses on disk so opened pages stay readable offline. */
class PageCache(context: Context) {

    private val dir = File(context.filesDir, "pages").apply { mkdirs() }

    data class Entry(val body: String, val ageMs: Long)

    fun read(key: String): Entry? {
        val f = file(key)
        if (!f.exists()) return null
        return runCatching { Entry(f.readText(), System.currentTimeMillis() - f.lastModified()) }.getOrNull()
    }

    fun write(key: String, body: String) {
        runCatching {
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            trim()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun count(): Int = dir.listFiles()?.size ?: 0

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun trim() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_PAGES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_PAGES).forEach { it.delete() }
    }

    private fun file(key: String): File = File(dir, sha1(key))

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_PAGES = 600
    }
}
