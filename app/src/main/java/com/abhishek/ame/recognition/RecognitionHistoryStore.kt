package com.abhishek.ame.recognition

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device persistence for recognized songs history.
 */
class RecognitionHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ame_recognition_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<RecognizedTrack>>(emptyList())
    val history: StateFlow<List<RecognizedTrack>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return
        val list = mutableListOf<RecognizedTrack>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RecognizedTrack(
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    )
                )
            }
        }
        _history.value = list
    }

    fun addTrack(track: RecognizedTrack) {
        val current = _history.value.toMutableList()
        // Deduplicate recent identical matches
        current.removeAll { it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true) }
        current.add(0, track)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        _history.value = current
        save(current)
    }

    fun removeTrack(track: RecognizedTrack) {
        val current = _history.value.toMutableList()
        current.removeAll { it.timestamp == track.timestamp && it.title == track.title }
        _history.value = current
        save(current)
    }

    fun clear() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(tracks: List<RecognizedTrack>) {
        val array = JSONArray()
        for (t in tracks) {
            val obj = JSONObject().apply {
                put("title", t.title)
                put("artist", t.artist)
                put("album", t.album)
                put("coverUrl", t.coverUrl ?: "")
                put("timestamp", t.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history_json"
        private const val MAX_HISTORY = 50
    }
}
