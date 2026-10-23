package com.abhishek.ame.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks playlists that the user has deleted or wants permanently hidden,
 * preventing them from reappearing when remote catalogues are refreshed.
 */
class DeletedPlaylistStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _deleted = MutableStateFlow(load())
    val deleted: StateFlow<Set<String>> = _deleted.asStateFlow()

    fun isDeleted(uri: String?, name: String? = null): Boolean {
        if (uri == null) return false
        // Hide "Episodes for Later" (SE / VLSE) system playlist
        if (uri.endsWith(":SE") || uri.endsWith(":VLSE") || uri.endsWith("/SE") || uri.endsWith("/VLSE")) {
            return true
        }
        if (name?.equals("Episodes for Later", ignoreCase = true) == true) {
            return true
        }
        return uri in _deleted.value
    }

    fun markDeleted(uri: String) {
        val updated = _deleted.value + uri
        _deleted.value = updated
        prefs.edit().putStringSet(KEY_DELETED, updated).apply()
    }

    fun restore(uri: String) {
        val updated = _deleted.value - uri
        _deleted.value = updated
        prefs.edit().putStringSet(KEY_DELETED, updated).apply()
    }

    private fun load(): Set<String> {
        val saved = prefs.getStringSet(KEY_DELETED, emptySet()).orEmpty()
        return saved.toSet()
    }

    private companion object {
        const val FILE_NAME = "square_deleted_playlists"
        const val KEY_DELETED = "deleted_uris"
    }
}
