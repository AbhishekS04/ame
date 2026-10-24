package com.abhishek.ame.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores public or community playlists saved into the user's library from the Home screen.
 *
 * Persisted in local JSON storage so they remain in the user's library across app sessions
 * and offline.
 */
class SavedPlaylistStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _saved = MutableStateFlow<List<CatalogPlaylist>>(load())
    val saved: StateFlow<List<CatalogPlaylist>> = _saved.asStateFlow()

    fun isSaved(uri: String): Boolean = _saved.value.any { it.uri == uri }

    fun getSaved(): List<CatalogPlaylist> = _saved.value

    fun save(playlist: CatalogPlaylist): Boolean {
        val current = _saved.value
        if (current.any { it.uri == playlist.uri }) return false
        val updated = listOf(playlist) + current
        _saved.value = updated
        persist(updated)
        return true
    }

    fun remove(uri: String): Boolean {
        val current = _saved.value
        if (current.none { it.uri == uri }) return false
        val updated = current.filterNot { it.uri == uri }
        _saved.value = updated
        persist(updated)
        return true
    }

    private fun persist(list: List<CatalogPlaylist>) {
        runCatching {
            file.writeText(json.encodeToString(list))
        }
    }

    private fun load(): List<CatalogPlaylist> {
        return runCatching {
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<CatalogPlaylist>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val FILE_NAME = "ame_saved_public_playlists.json"
    }
}
