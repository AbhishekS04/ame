package com.abhishek.ame.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks the listener has liked ("Tus me gusta").
 *
 * Persisted on this device and updated whenever a heart is added or removed,
 * so the notification, the lock screen and the player share one state without
 * waiting on a network round-trip.
 */
class LikedStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()

    private val _liked = MutableStateFlow(load())
    val likedTracks: StateFlow<Set<String>> = _liked.asStateFlow()

    private val _likedIds = MutableStateFlow(computeIds(_liked.value))
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _likedKeys = MutableStateFlow(loadKeys())
    val likedKeys: StateFlow<Set<String>> = _likedKeys.asStateFlow()

    fun isLiked(uri: String?, title: String? = null, artist: String? = null): Boolean {
        if (uri == null) return false
        if (uri in _liked.value) return true
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() }
        if (id != null && id in _likedIds.value) return true
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            val key = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
            if (key in _likedKeys.value) return true
        }
        return false
    }

    fun seed(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val updated = _liked.value + uris
        if (updated.size != _liked.value.size) {
            _liked.value = updated
            _likedIds.value = computeIds(updated)
            save(updated)
        }
    }

    fun seedTracks(tracks: Collection<CatalogTrack>) {
        if (tracks.isEmpty()) return
        seed(tracks.map { it.uri })
        val newKeys = tracks.mapNotNull { track ->
            if (track.name.isNotBlank() && track.artist.isNotBlank()) {
                "${track.name.trim().lowercase()}|${track.artist.trim().lowercase()}"
            } else null
        }.toSet()
        if (newKeys.isNotEmpty()) {
            val updatedKeys = _likedKeys.value + newKeys
            if (updatedKeys.size != _likedKeys.value.size) {
                _likedKeys.value = updatedKeys
                saveKeys(updatedKeys)
            }
        }
    }

    /**
     * The whole set for one source, as that source has it now: the songs under
     * [prefix] become exactly [uris], and the other source's are left alone.
     * Unlike [seed] this also takes back a heart removed somewhere else.
     */
    fun replaceSource(prefix: String, uris: Collection<String>) {
        val updated = _liked.value.filterNot { it.startsWith(prefix) }.toSet() + uris.filter { it.startsWith(prefix) }
        if (updated != _liked.value) {
            _liked.value = updated
            _likedIds.value = computeIds(updated)
            save(updated)
        }
    }

    fun add(uri: String, title: String? = null, artist: String? = null) {
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() }
        val counterparts = if (id != null) {
            setOf(uri, "ytmusic:track:$id", "ytmusic:video:$id")
        } else setOf(uri)
        val updated = _liked.value + counterparts
        _liked.value = updated
        _likedIds.value = computeIds(updated)
        save(updated)

        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            val key = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
            val updatedKeys = _likedKeys.value + key
            _likedKeys.value = updatedKeys
            saveKeys(updatedKeys)
        }
    }

    fun remove(uri: String, title: String? = null, artist: String? = null) {
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() }
        val updated = _liked.value.filterNot { 
            it == uri || (id != null && it.substringAfterLast(':') == id)
        }.toSet()
        _liked.value = updated
        _likedIds.value = computeIds(updated)
        save(updated)

        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            val key = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
            val updatedKeys = _likedKeys.value - key
            _likedKeys.value = updatedKeys
            saveKeys(updatedKeys)
        }
    }

    fun toggle(uri: String, title: String? = null, artist: String? = null): Boolean {
        val nowLiked = !isLiked(uri, title, artist)
        if (nowLiked) add(uri, title, artist) else remove(uri, title, artist)
        return nowLiked
    }

    private fun computeIds(uris: Set<String>): Set<String> =
        uris.mapNotNullTo(mutableSetOf()) { it.substringAfterLast(':').takeIf { id -> id.isNotBlank() } }

    private fun load(): Set<String> =
        prefs.getStringSet(KEY_LIKED, null)?.toSet() ?: emptySet()

    private fun loadKeys(): Set<String> =
        prefs.getStringSet(KEY_LIKED_KEYS, null)?.toSet() ?: emptySet()

    private fun save(tracks: Set<String>) {
        val snapshot = tracks.toSet()
        scope.launch {
            saveLock.withLock {
                prefs.edit().putStringSet(KEY_LIKED, snapshot).apply()
            }
        }
    }

    private fun saveKeys(keys: Set<String>) {
        val snapshot = keys.toSet()
        scope.launch {
            saveLock.withLock {
                prefs.edit().putStringSet(KEY_LIKED_KEYS, snapshot).apply()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "ame_liked_tracks"
        const val KEY_LIKED = "liked_tracks"
        const val KEY_LIKED_KEYS = "liked_title_artists"
    }
}
