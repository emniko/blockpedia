package com.emniko.blockpedia.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Bookmarks and reading history, stored as small JSON lists in SharedPreferences. */
class UserStore(context: Context) {

    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(load(KEY_FAVORITES))
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow(load(KEY_HISTORY))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun isFavorite(title: String): Boolean = title in _favorites.value

    fun toggleFavorite(title: String) {
        val current = _favorites.value
        val updated = if (title in current) current - title else listOf(title) + current
        _favorites.value = updated
        save(KEY_FAVORITES, updated)
    }

    fun removeFavorite(title: String) {
        val updated = _favorites.value - title
        _favorites.value = updated
        save(KEY_FAVORITES, updated)
    }

    fun addHistory(title: String) {
        val updated = (listOf(title) + _history.value.filterNot { it == title }).take(MAX_HISTORY)
        _history.value = updated
        save(KEY_HISTORY, updated)
    }

    fun removeHistory(title: String) {
        val updated = _history.value - title
        _history.value = updated
        save(KEY_HISTORY, updated)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save(KEY_HISTORY, emptyList())
    }

    private fun load(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun save(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 100
    }
}
