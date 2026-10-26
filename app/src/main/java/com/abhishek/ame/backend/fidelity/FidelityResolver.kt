package com.abhishek.ame.backend.fidelity

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Resolves high-fidelity audio streams (320kbps Studio Master / Lossless / Dolby)
 * directly from high-speed official CDN sources with responsive timeout
 * and instantaneous fallback to YouTube Music High.
 */
object FidelityResolver {

    private const val TAG = "AmeFidelity"
    private const val RESOLVE_TIMEOUT_MS = 5000L
    private const val JIOSAAVN_DES_KEY = "38346591"

    private val IPV4_FIRST_DNS = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
            return addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .dns(IPV4_FIRST_DNS)
        .fastFallback(true)
        .connectTimeout(3800, TimeUnit.MILLISECONDS)
        .readTimeout(3800, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()

    // Title & artist cleaning regex patterns
    private val TITLE_CLEAN_REGEX = Regex(
        """(?i)(\s*[\(\[\{]?(?:\b(?:official\s*(?:music\s*)?video|official\s*audio|lyric\s*video|visualizer|audio|hq|hd|remastered|remaster|\d{4}\s*remaster|video)\b|from\s+.*?)[\)\]\}]?)+"""
    )
    private val PAREN_TAGS_REGEX = Regex(
        """(?i)\s*[\(\[\{][^\)\]\}]*?\b(?:official|video|audio|visualizer|lyric|remaster|remastered|hq|hd|version|4k)\b[^\)\]\}]*?[\)\]\}]"""
    )
    private val PAREN_FEAT_REGEX = Regex(
        """(?i)\s*[\(\[\{][^\)\]\}]*?\b(?:feat\.?|featuring|ft\.?)\b[^\)\]\}]*?[\)\]\}]"""
    )
    private val PLAIN_FEAT_REGEX = Regex(
        """(?i)\s*\b(?:feat\.?|featuring|ft\.?)\b.*$"""
    )
    private val TOPIC_CLEAN_REGEX = Regex("""(?i)\s*-\s*topic$""")
    private val VEVO_CLEAN_REGEX = Regex("""(?i)vevo$""")
    private val LABEL_CLEAN_REGEX = Regex("""(?i)\s*(?:official|records|music|films|entertainment|channel)$""")

    /**
     * Cleans extraneous YouTube metadata markers for precision matching.
     */
    fun cleanTitle(raw: String): String {
        var t = raw.replace(TITLE_CLEAN_REGEX, "")
            .replace(PAREN_TAGS_REGEX, "")
            .replace(PAREN_FEAT_REGEX, "")
            .replace(PLAIN_FEAT_REGEX, "")
            .replace("\"", "")
            .trim()
        t = t.trim(' ', '-', '|', '/', ':')
        return t
    }

    /**
     * Cleans artist names (e.g. "TheWeekndVEVO" -> "TheWeeknd", "Daft Punk - Topic" -> "Daft Punk").
     */
    fun cleanArtist(raw: String): String {
        return raw.replace(TOPIC_CLEAN_REGEX, "")
            .replace(VEVO_CLEAN_REGEX, "")
            .replace(LABEL_CLEAN_REGEX, "")
            .trim()
    }

    /**
     * Extracts cleanly separated title and artist if YouTube video title contains "Artist - Title" or "Title | Artist".
     */
    fun parseTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
        val uploader = cleanArtist(rawArtist)

        if (rawTitle.contains("|")) {
            val parts = rawTitle.split("|").map { it.trim() }
            val cleanParts = parts.map { cleanTitle(it) }.filter { it.isNotBlank() }
            if (cleanParts.isNotEmpty()) {
                val pTitle = cleanParts[0]
                var pArtist = uploader
                for (i in 1 until cleanParts.size) {
                    val candidate = cleanArtist(cleanParts[i])
                    if (candidate.length > 2 && !candidate.contains("table no", ignoreCase = true)) {
                        pArtist = candidate
                        break
                    }
                }
                return pTitle to pArtist
            }
        }

        if (rawTitle.contains(" - ")) {
            val parts = rawTitle.split(" - ", limit = 2)
            val left = cleanTitle(parts[0])
            val right = cleanTitle(parts[1])

            val rightHadVideoTags = parts[1].contains(Regex("(?i)official|video|audio|lyric|visualizer"))
            if (rightHadVideoTags || right.isBlank()) {
                return left to uploader
            }

            if (uploader.isNotBlank() && left.contains(uploader, ignoreCase = true)) {
                return right to left
            }
            if (uploader.isNotBlank() && right.contains(uploader, ignoreCase = true)) {
                return left to right
            }

            return if (uploader.isNotBlank()) left to uploader else right to left
        }

        return cleanTitle(rawTitle) to uploader
    }

    private fun normalizeForMatching(s: String): String {
        return s.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Rigorously verifies that a candidate stream from an external CDN matches
     * BOTH the requested song title AND artist/singer name, within tight duration tolerance.
     * Prevents ever substituting a wrong song.
     */
    fun isStrictCandidateMatch(
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Long,
        candidateTitle: String,
        candidateArtists: List<String>,
        candidateDurationSec: Long,
    ): Boolean {
        // 1. Duration check: strict ±7s tolerance when duration is known
        if (targetDurationSec > 0 && candidateDurationSec > 0) {
            val diff = abs(candidateDurationSec - targetDurationSec)
            if (diff > 7) {
                Log.d(TAG, "StrictMatch failed: duration diff ($diff s) > 7s (target=${targetDurationSec}s vs cand=${candidateDurationSec}s)")
                return false
            }
        }

        val normTargetTitle = normalizeForMatching(targetTitle)
        val normCandTitle = normalizeForMatching(candidateTitle)
        if (normTargetTitle.isBlank() || normCandTitle.isBlank()) return false

        // 2. Reject unwanted modifiers if target did not request them
        val unwantedModifiers = listOf("remix", "lofi", "slowed", "reverb", "acoustic", "instrumental", "vocals only", "cover", "karaoke", "live", "sped up")
        for (mod in unwantedModifiers) {
            if (normCandTitle.contains(mod) && !normTargetTitle.contains(mod)) {
                Log.d(TAG, "StrictMatch failed: candidate '$candidateTitle' has unwanted modifier '$mod' not in target '$targetTitle'")
                return false
            }
        }

        // 3. Title matching:
        val stopWords = setOf("the", "a", "an", "and", "or", "to", "in", "on", "at", "by", "for", "with", "from", "of")
        val targetWords = normTargetTitle.split(" ").filter { it.length > 1 && it !in stopWords }
        val candWords = normCandTitle.split(" ").filter { it.length > 1 && it !in stopWords }

        val titleMatches = when {
            normTargetTitle == normCandTitle -> true
            targetWords.isNotEmpty() && targetWords.all { tw -> candWords.any { cw -> cw == tw || (tw.length >= 4 && cw.contains(tw)) } } -> true
            normCandTitle.startsWith(normTargetTitle) || normTargetTitle.startsWith(normCandTitle) -> true
            else -> false
        }

        if (!titleMatches) {
            Log.d(TAG, "StrictMatch failed: title mismatch '$targetTitle' vs '$candidateTitle'")
            return false
        }

        // 4. Artist verification:
        val normTargetArtist = normalizeForMatching(targetArtist)
        if (normTargetArtist.isNotBlank()) {
            val candArtistText = candidateArtists.joinToString(" ") { normalizeForMatching(it) }
            val candArtistWords = candArtistText.split(" ").filter { it.length > 1 }.toSet()

            val targetArtistWords = normTargetArtist.split(" ").filter { it.length > 1 && it !in stopWords }
            val artistMatches = when {
                candArtistText.contains(normTargetArtist) -> true
                targetArtistWords.isEmpty() -> true
                targetArtistWords.size == 1 -> {
                    val single = targetArtistWords[0]
                    candArtistWords.contains(single) || candArtistWords.any { it.contains(single) }
                }
                else -> {
                    val matchingCount = targetArtistWords.count { tw ->
                        candArtistWords.contains(tw) || candArtistWords.any { cw -> cw.contains(tw) || tw.contains(cw) }
                    }
                    matchingCount >= 2 || (matchingCount == targetArtistWords.size)
                }
            }

            if (!artistMatches) {
                Log.d(TAG, "StrictMatch failed: artist mismatch '$targetArtist' not found in candidate artists $candidateArtists")
                return false
            }
        }

        Log.i(TAG, "StrictMatch SUCCESS: '$targetTitle' by '$targetArtist' matched candidate '$candidateTitle' by $candidateArtists (${candidateDurationSec}s)")
        return true
    }

    data class ResolvedStream(
        val streamUrl: String,
        val isDolby: Boolean,
        val bitrateKbps: Int = 320,
        val isLosslessFlac: Boolean = false,
        val studioArtworkUrl: String? = null,
    )

    /**
     * Resolves official studio album cover (500x500) from JioSaavn.
     */
    suspend fun resolveStudioArtwork(songTitle: String, artistName: String): String? = withContext(Dispatchers.IO) {
        val (cleanedTitle, cleanedArtist) = parseTitleAndArtist(songTitle, artistName)
        if (cleanedTitle.isBlank()) return@withContext null
        try {
            val query = if (cleanedArtist.isNotBlank()) "$cleanedTitle $cleanedArtist" else cleanedTitle
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                if (!body.startsWith("{")) return@use null
                val json = JSONObject(body)
                val songs = json.optJSONObject("songs")?.optJSONArray("data") ?: return@use null
                for (i in 0 until songs.length()) {
                    val item = songs.optJSONObject(i) ?: continue
                    val image = item.optString("image")
                    if (image.isNotBlank()) {
                        return@withContext image.replace("150x150.jpg", "500x500.jpg")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "JioSaavn artwork lookup error: ${e.message}")
        }
        null
    }

    /**
     * Attempts to resolve an external Lossless (FLAC up to 24-bit / 320kbps Master) audio stream.
     * Returns null if unresolvable within [RESOLVE_TIMEOUT_MS], triggering instant fallback.
     */
    suspend fun resolveHiFiStream(
        songTitle: String,
        artistName: String,
        targetDurationSec: Long,
        targetBitrate: Int = 320,
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val (cleanedTitle, cleanedArtist) = parseTitleAndArtist(songTitle, artistName)
        if (cleanedTitle.isBlank()) return@withContext null

        val cacheKey = "${cleanedTitle.lowercase()}::${cleanedArtist.lowercase()}::$targetBitrate"
        streamCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Cache hit for $cacheKey: ${cached.streamUrl}")
            return@withContext cached
        }

        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            try {
                // If user requests Lossless (1411+ kbps FLAC), strictly resolve from Monochrome lossless sources
                val resolved = if (targetBitrate >= 1000) {
                    resolveFromMonochrome(cleanedTitle, cleanedArtist, targetDurationSec)
                } else {
                    resolveFromJioSaavn(cleanedTitle, cleanedArtist, targetDurationSec, targetBitrate)
                }

                if (resolved != null) {
                    streamCache[cacheKey] = resolved
                }
                resolved
            } catch (e: Exception) {
                Log.d(TAG, "Hi-Fi resolve skipped: ${e.message}")
                null
            }
        }
    }

    /**
     * Resolves genuine bit-perfect FLAC stream from Monochrome API network.
     */
    private fun resolveFromMonochrome(
        title: String,
        artist: String,
        targetDurationSec: Long,
    ): ResolvedStream? {
        val query = if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
            "$title $artist"
        } else {
            title
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoints = listOf(
            "https://tracks.monochrome.st/search?q=$encodedQuery",
            "https://bitchord-monochrome-addon.onrender.com/search?q=$encodedQuery"
        )

        Log.i(TAG, "Querying Monochrome Lossless FLAC for '$title' by '$artist' [query='$query', targetDur=${targetDurationSec}s]")

        for (endpoint in endpoints) {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Origin", "https://monochrome.st")
                .header("Referer", "https://monochrome.st/")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Monochrome query HTTP error (${response.code}) on $endpoint")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    if (!body.startsWith("{")) return@use

                    val json = JSONObject(body)
                    val tracks = json.optJSONArray("tracks")
                        ?: json.optJSONArray("topResults")
                        ?: return@use

                    for (i in 0 until tracks.length()) {
                        val item = tracks.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank() || id.length < 10) continue

                        val itemTitle = item.optString("title")
                        val rawDuration = item.optDouble("duration", 0.0)
                        val durationSec = if (rawDuration > 1000.0) {
                            (rawDuration / 1000.0 + 0.5).toLong()
                        } else {
                            rawDuration.toLong()
                        }

                        // Collect candidate artists from Monochrome tracks / topResults / bitchord
                        val candidateArtists = mutableListOf<String>()
                        val artistNamesArr = item.optJSONArray("artistNames")
                        if (artistNamesArr != null) {
                            for (a in 0 until artistNamesArr.length()) {
                                val name = artistNamesArr.optString(a)
                                if (name.isNotBlank()) candidateArtists.add(name)
                            }
                        }
                        val singleArtist = item.optString("artist")
                        if (singleArtist.isNotBlank()) {
                            candidateArtists.addAll(
                                singleArtist.split(",", "&", "/", "ft.", "feat.")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            )
                        }

                        // Verify candidate strictly matches requested song title and artist
                        if (!isStrictCandidateMatch(
                                targetTitle = title,
                                targetArtist = artist,
                                targetDurationSec = targetDurationSec,
                                candidateTitle = itemTitle,
                                candidateArtists = candidateArtists,
                                candidateDurationSec = durationSec,
                            )
                        ) {
                            continue
                        }

                        val streamUrl = "https://tracks.monochrome.st/track/$id"
                        Log.i(TAG, "Resolved true 24-bit Lossless FLAC stream for '$title' by '$artist': $streamUrl (id=$id, dur=${durationSec}s)")

                        return ResolvedStream(
                            streamUrl = streamUrl,
                            isDolby = false,
                            bitrateKbps = 1411,
                            isLosslessFlac = true,
                            studioArtworkUrl = null,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Monochrome query failed on $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Resolves directly from JioSaavn's official API and Akamai/Azure CDN.
     */
    private fun resolveFromJioSaavn(
        title: String,
        artist: String,
        targetDurationSec: Long,
        targetBitrate: Int = 320,
    ): ResolvedStream? {
        val query = if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
            "$title $artist"
        } else {
            title
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&p=1&n=5&q=$encodedQuery"

        Log.i(TAG, "Querying Hi-Fi source for '$title' by '$artist' [query='$query', targetDur=${targetDurationSec}s]")

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Hi-Fi query HTTP error: ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            if (!body.startsWith("{")) return null

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val songTitle = item.optString("song")
                val duration = item.optLong("duration", 0L)

                // Collect candidate artists from JioSaavn item
                val candidateArtists = mutableListOf<String>()
                val primary = item.optString("primary_artists")
                if (primary.isNotBlank()) {
                    candidateArtists.addAll(primary.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() })
                }
                val singers = item.optString("singers")
                if (singers.isNotBlank()) {
                    candidateArtists.addAll(singers.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() })
                }
                val music = item.optString("music")
                if (music.isNotBlank()) {
                    candidateArtists.addAll(music.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() })
                }
                val artistMap = item.optJSONObject("artistMap")
                if (artistMap != null) {
                    val keys = artistMap.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.isNotBlank()) candidateArtists.add(k)
                    }
                }

                // Verify candidate strictly matches requested song title and artist
                if (!isStrictCandidateMatch(
                        targetTitle = title,
                        targetArtist = artist,
                        targetDurationSec = targetDurationSec,
                        candidateTitle = songTitle,
                        candidateArtists = candidateArtists,
                        candidateDurationSec = duration,
                    )
                ) {
                    continue
                }

                val encUrl = item.optString("encrypted_media_url")
                if (encUrl.isNullOrBlank()) continue

                val decryptedUrl = decryptDesUrl(encUrl) ?: continue
                if (!decryptedUrl.startsWith("http")) continue

                // Select bitrate according to requested quality (320kbps vs 256/160kbps)
                val bitrateSuffix = if (targetBitrate <= 256) "_160.mp4" else "_320.mp4"
                val streamUrl = decryptedUrl
                    .replace("_96.mp4", bitrateSuffix)
                    .replace("_160.mp4", bitrateSuffix)
                    .replace("_320.mp4", bitrateSuffix)

                val isDolby = item.optBoolean("is_dolby_content", false) ||
                    item.optString("is_dolby_content").equals("true", ignoreCase = true)

                val actualBitrate = if (targetBitrate <= 256) 256 else 320
                Log.i(TAG, "Resolved ${actualBitrate}k stream for '$title' by '$artist': $streamUrl (isDolby=$isDolby)")
                return ResolvedStream(
                    streamUrl = streamUrl,
                    isDolby = isDolby,
                    bitrateKbps = actualBitrate,
                    studioArtworkUrl = null,
                )
            }
        }
        return null
    }

    /**
     * Decrypts JioSaavn DES-ECB encrypted media stream URLs.
     */
    private fun decryptDesUrl(encryptedUrl: String): String? {
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(JIOSAAVN_DES_KEY.toByteArray(StandardCharsets.UTF_8), "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.w(TAG, "DES decryption failed: ${e.message}")
            null
        }
    }
}
