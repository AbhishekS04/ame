package com.abhishek.ame.data.social

import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class JamTrack(
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("track_title") val trackTitle: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("track_uri") val trackUri: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("is_playing") val isPlaying: Boolean = true,
    @SerialName("position_ms") val positionMs: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Discord-style Realtime Jam Session engine powered by Supabase Realtime WebSockets.
 * Broadcasts currently playing music status and listens for active jam sessions with sub-50ms latency.
 */
class JamEngine {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var channel: RealtimeChannel? = null
    private var listenJob: Job? = null

    private val _activeJams = MutableStateFlow<Map<String, JamTrack>>(emptyMap())
    val activeJams: StateFlow<Map<String, JamTrack>> = _activeJams.asStateFlow()

    init {
        connect()
    }

    fun connect() {
        if (channel != null) return
        scope.launch {
            runCatching {
                val chan = AmeSupabaseClient.client.realtime.channel("ame_jams")
                channel = chan
                chan.subscribe()

                listenJob?.cancel()
                listenJob = scope.launch {
                    chan.broadcastFlow<JamTrack>("track_status").collect { jam ->
                        val current = _activeJams.value.toMutableMap()
                        if (jam.isPlaying && jam.trackTitle.isNotBlank()) {
                            current[jam.userId] = jam
                        } else {
                            current.remove(jam.userId)
                        }
                        _activeJams.value = current
                    }
                }
            }.onFailure {
                android.util.Log.e("JamEngine", "Failed to connect to Supabase Realtime channel", it)
            }
        }
    }

    fun broadcastPlaying(
        userId: String,
        username: String,
        displayName: String,
        avatarUrl: String?,
        trackTitle: String,
        artistName: String,
        trackUri: String,
        artworkUrl: String?,
        isPlaying: Boolean,
        positionMs: Long = 0L,
    ) {
        scope.launch {
            runCatching {
                val chan = channel ?: run {
                    val c = AmeSupabaseClient.client.realtime.channel("ame_jams")
                    c.subscribe()
                    channel = c
                    c
                }
                val payload = JamTrack(
                    userId = userId,
                    username = username,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    trackTitle = trackTitle,
                    artistName = artistName,
                    trackUri = trackUri,
                    artworkUrl = artworkUrl,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                )
                chan.broadcast(
                    event = "track_status",
                    payload = BroadcastPayload.Json(Json.encodeToJsonElement(payload).jsonObject),
                )
            }.onFailure {
                android.util.Log.e("JamEngine", "Failed to broadcast jam presence", it)
            }
        }
    }

    fun stopBroadcasting(userId: String) {
        scope.launch {
            runCatching {
                val payload = JamTrack(
                    userId = userId,
                    username = "",
                    displayName = "",
                    trackTitle = "",
                    artistName = "",
                    trackUri = "",
                    isPlaying = false,
                )
                channel?.broadcast(
                    event = "track_status",
                    payload = BroadcastPayload.Json(Json.encodeToJsonElement(payload).jsonObject),
                )
            }
        }
    }
}
