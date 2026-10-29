package com.abhishek.ame.backend.recs

import com.abhishek.ame.data.CatalogTrack
import com.abhishek.ame.data.ListeningEvent
import com.abhishek.ame.nativecore.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar

@Serializable
data class RankedTrackResult(
    val uri: String,
    val name: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val score: Float = 0f,
    val exploit: Float = 0f,
    val explore: Float = 0f,
    val explanation: String = "",
    val badge: String? = null,
)

/**
 * On-Device Spotify-grade Recommendation Coordinator.
 *
 * Dispatches candidate pools and local listening signals to `amecore` via JNI
 * for BaRT Contextual Multi-Armed Bandit ranking and CoSeRNN session context re-ranking.
 */
object RecommendationEngine {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val trackListSerializer = ListSerializer(CatalogTrack.serializer())
    private val eventListSerializer = ListSerializer(ListeningEvent.serializer())
    private val rankedSerializer = ListSerializer(RankedTrackResult.serializer())

    suspend fun rankQuickPicks(
        candidates: List<CatalogTrack>,
        events: List<ListeningEvent>,
        likedUris: Set<String>,
        recentTracks: List<CatalogTrack>,
    ): List<CatalogTrack> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val currentTimeMs = System.currentTimeMillis()

        // Extract last recent artists and tracks for session momentum and immediate recency
        val recentArtists = recentTracks.take(8).map { it.artist }.filter { it.isNotBlank() }.distinct()
        val recentUris = recentTracks.take(10).map { it.uri }.filter { it.isNotBlank() }
        val contextJson = buildString {
            append("{\"hour\":").append(currentHour)
            append(",\"day_of_week\":").append(dayOfWeek)
            append(",\"timestamp\":").append(currentTimeMs)
            append(",\"recent_artists\":[")
            recentArtists.forEachIndexed { i, a ->
                if (i > 0) append(",")
                append("\"").append(a.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
            }
            append("],\"recent_tracks\":[")
            recentUris.forEachIndexed { i, u ->
                if (i > 0) append(",")
                append("\"").append(u.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
            }
            append("],\"recent_uris\":[")
            recentUris.forEachIndexed { i, u ->
                if (i > 0) append(",")
                append("\"").append(u.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
            }
            append("]}")
        }

        val candidatesJson = runCatching {
            json.encodeToString(trackListSerializer, candidates)
        }.getOrDefault("[]")

        // Keep last 300 events for instantaneous serialization and computation
        val historyJson = runCatching {
            json.encodeToString(eventListSerializer, events.takeLast(300))
        }.getOrDefault("[]")

        val likedJson = buildString {
            append("[")
            likedUris.forEachIndexed { i, uri ->
                if (i > 0) append(",")
                append("\"").append(uri.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
            }
            append("]")
        }

        val outputJson = runCatching {
            NativeBridge.rankCandidates(
                candidatesJson = candidatesJson,
                historyJson = historyJson,
                likedUrisJson = likedJson,
                contextJson = contextJson,
            )
        }.getOrNull() ?: return@withContext candidates

        val rankedResults = runCatching {
            json.decodeFromString(rankedSerializer, outputJson)
        }.getOrDefault(emptyList())

        if (rankedResults.isEmpty()) return@withContext candidates

        val candidateMap = candidates.associateBy { it.uri }
        rankedResults.mapNotNull { r ->
            val orig = candidateMap[r.uri]
            orig?.copy(
                smartRecommended = true,
                recommendationReason = r.explanation.ifBlank { null },
            )
        }
    }
}
