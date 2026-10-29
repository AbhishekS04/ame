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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class FollowedArtistEntry(
    val uri: String,
    val title: String,
    val artworkUrl: String? = null,
) {
    fun toSearchItem(): SearchItem = SearchItem(
        uri = uri,
        title = title,
        subtitle = "",
        artworkUrl = artworkUrl,
    )

    companion object {
        fun from(item: SearchItem): FollowedArtistEntry = FollowedArtistEntry(
            uri = item.uri,
            title = item.title,
            artworkUrl = item.artworkUrl,
        )
    }
}

/**
 * Followed (starred) artists, kept locally on-device.
 *
 * Persists the artists the listener follows so they appear reliably
 * in the Library regardless of backend account sign-in state.
 */
class FollowedArtistsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FollowedArtistEntry.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _followed = MutableStateFlow(load())
    val followed: StateFlow<List<SearchItem>> = _followed.asStateFlow()

    fun isFollowing(uri: String?, name: String? = null): Boolean {
        if (uri.isNullOrBlank() && name.isNullOrBlank()) return false
        val list = _followed.value
        if (!uri.isNullOrBlank() && list.any { it.uri == uri }) return true
        if (!name.isNullOrBlank() && list.any { it.title.equals(name.trim(), ignoreCase = true) }) return true
        return false
    }

    /**
     * Toggles an artist's follow state.
     * Returns true if the artist is now followed, false if unfollowed.
     */
    fun toggle(artist: SearchItem): Boolean {
        val current = _followed.value
        val exists = current.any {
            it.uri == artist.uri ||
            (it.title.isNotBlank() && artist.title.isNotBlank() && it.title.equals(artist.title.trim(), ignoreCase = true))
        }
        val updated = if (exists) {
            current.filterNot {
                it.uri == artist.uri ||
                (it.title.isNotBlank() && artist.title.isNotBlank() && it.title.equals(artist.title.trim(), ignoreCase = true))
            }
        } else {
            listOf(artist) + current.filterNot {
                it.uri == artist.uri ||
                (it.title.isNotBlank() && artist.title.isNotBlank() && it.title.equals(artist.title.trim(), ignoreCase = true))
            }
        }
        _followed.value = updated
        scope.launch {
            lock.withLock {
                val entries = updated.map { FollowedArtistEntry.from(it) }
                prefs.edit()
                    .putString(KEY_ARTISTS, json.encodeToString(serializer, entries))
                    .apply()
            }
        }
        return !exists
    }

    /**
     * Updates an artist's artwork if a better portrait is discovered.
     */
    fun updateArtwork(uri: String?, name: String?, artworkUrl: String?) {
        if (artworkUrl.isNullOrBlank()) return
        val current = _followed.value
        var changed = false
        val updated = current.map { item ->
            val match = (!uri.isNullOrBlank() && item.uri == uri) ||
                (!name.isNullOrBlank() && item.title.equals(name.trim(), ignoreCase = true))
            if (match && item.artworkUrl != artworkUrl) {
                changed = true
                item.copy(artworkUrl = artworkUrl)
            } else {
                item
            }
        }
        if (changed) {
            _followed.value = updated
            scope.launch {
                lock.withLock {
                    val entries = updated.map { FollowedArtistEntry.from(it) }
                    prefs.edit()
                        .putString(KEY_ARTISTS, json.encodeToString(serializer, entries))
                        .apply()
                }
            }
        }
    }

    fun add(artist: SearchItem) {
        val current = _followed.value
        if (current.any { it.uri == artist.uri }) return
        val updated = listOf(artist) + current
        _followed.value = updated
        scope.launch {
            lock.withLock {
                val entries = updated.map { FollowedArtistEntry.from(it) }
                prefs.edit()
                    .putString(KEY_ARTISTS, json.encodeToString(serializer, entries))
                    .apply()
            }
        }
    }

    fun remove(uri: String) {
        val current = _followed.value
        val updated = current.filterNot { it.uri == uri }
        if (updated.size != current.size) {
            _followed.value = updated
            scope.launch {
                lock.withLock {
                    val entries = updated.map { FollowedArtistEntry.from(it) }
                    prefs.edit()
                        .putString(KEY_ARTISTS, json.encodeToString(serializer, entries))
                        .apply()
                }
            }
        }
    }

    private fun load(): List<SearchItem> {
        val raw = prefs.getString(KEY_ARTISTS, null) ?: return emptyList()
        val entries = runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
        return entries
            .filter { it.uri.isNotBlank() && it.title.isNotBlank() }
            .map { it.toSearchItem() }
    }

    private companion object {
        const val FILE_NAME = "ame_followed_artists"
        const val KEY_ARTISTS = "artists"
    }
}
