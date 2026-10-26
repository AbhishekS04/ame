package com.abhishek.ame.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Apple Music's own pictures of an artist.
 *
 * Spotify has one square photograph per artist and nothing else. Apple's
 * catalogue has the two things an artist page in that style is made of: a wide
 * editorial portrait, and the artist's name drawn in a face chosen for them —
 * what their client puts across the top of the picture instead of setting the
 * name in the system font.
 *
 * How it is reached: the public web player carries a developer token inside its
 * own JavaScript, and that token opens the same catalogue endpoint the site
 * reads. Nothing here is authenticated as a person and nothing is written; this
 * asks for the artwork of a public catalogue page. It is, all the same, not an
 * interface Apple publishes, so every part of it is written to fail quietly: no
 * token, no match, a changed page shape or a refused request all end as "no
 * pictures", and the artist page draws its own name over Spotify's photo
 * exactly as it did before this file existed.
 *
 * Nothing calls this on the playback path. It is one lookup when an artist page
 * opens, cached for the session.
 */
object AppleCatalog {

    private const val TAG = "AppleCatalog"
    private const val SITE = "https://beta.music.apple.com"
    private const val API = "https://amp-api.music.apple.com/v1/catalog"
    private const val LOOKUP_TIMEOUT_MS = 8_000L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The token, and the lock that keeps a burst of pages from fetching five. */
    private var token: String? = null
    private val tokenLock = Mutex()

    /**
     * Artists already looked up, by the name that was asked for.
     *
     * Successful answers are cached. Transient failures and cancellations are
     * deliberately not cached, so a brief outage cannot hide artwork for the
     * rest of the process.
     */
    private val cache = mutableMapOf<String, Any?>()

    /**
     * And the same answers on disk, so the second visit costs nothing.
     *
     * The cost this saves is not the request. It is that the page opens on
     * Spotify's picture and changes to Apple's a second later, in front of the
     * reader — every single time, on every cold start. Kept for a month:
     * artwork does get replaced, and a stale photograph of somebody is worse
     * than one more search.
     */
    private var store: android.content.SharedPreferences? = null

    /** Called once, from the application. Without it this simply never caches. */
    fun attach(context: android.content.Context) {
        // Named for the shape of what is stored, not for the catalogue: the
        // rows hold finished URLs with their sizes baked in, so asking for
        // bigger pictures means the old rows are answers to a different
        // question. A new name retires them in one line.
        store = context.getSharedPreferences(
            "apple-catalog-v8",
            android.content.Context.MODE_PRIVATE,
        )
        // Stored weakly: this object lives for the process lifetime and must not
        // hold a strong reference to anything from a Context chain.
        connectivityRef = java.lang.ref.WeakReference(
            context.applicationContext.getSystemService(android.net.ConnectivityManager::class.java),
        )
    }


    private fun remembered(key: String): String? {
        val prefs = store ?: return null
        val row = prefs.getString(key, null) ?: return null
        val cut = row.indexOf('\n')
        if (cut < 0) return null
        val written = row.take(cut).toLongOrNull() ?: return null
        if (System.currentTimeMillis() - written > CACHE_MS) {
            prefs.edit().remove(key).apply()
            return null
        }
        return row.substring(cut + 1)
    }

    /**
     * Whether the catalogue may be asked at all.
     *
     * Offline this object still answers — from the rows written down on the way
     * past, which is what makes a downloaded record open on its own photograph
     * in a tunnel — but it never reaches for the network, and a miss is not
     * remembered as one: the answer offline is "not here yet", not "there is
     * none", and caching the first would keep the picture away for the rest of
     * the run.
     *
     * Also gated on the connection being unmetered (Wi-Fi or ethernet). Apple's
     * catalogue exists to enrich the visual experience — the app works correctly
     * without it — and reaching out for large editorial photos over mobile data
     * is not a trade the listener has agreed to. A cached answer is always used
     * regardless of connection type; only a live lookup is skipped.
     */
    private fun mayAsk(): Boolean {
        if (com.abhishek.ame.playback.OfflineMode.active.value) return false
        return true
    }

    /** Held weakly so AppleCatalog (an object) does not leak a Context. */
    private var connectivityRef: java.lang.ref.WeakReference<android.net.ConnectivityManager>? = null


    private fun remember(key: String, value: String) {
        store?.edit()?.putString(key, System.currentTimeMillis().toString() + "\n" + value)?.apply()
    }

    /**
     * What one record's page can use.
     *
     * The tall picture is the difference between the two headers the reference
     * shows: a record that has one opens as a photograph, and a record that does
     * not opens as its sleeve. The sleeve is here too, because Apple's copy of
     * it is the better scan more often than not.
     */
    @kotlinx.serialization.Serializable
    data class Album(
        val heroUrl: String?,
        val coverUrl: String?,
        /**
         * The colour Apple tints the page with, as six hex digits.
         *
         * Filed with the artwork rather than worked out from it, which is why
         * it is worth taking: their page for a record is the colour their
         * editors chose for that record, and a dominant-colour pass over the
         * same picture lands somewhere near it and often on a skin tone.
         */
        val bgColor: String? = null,
        /** The picture's own proportions, width over height. */
        val heroAspect: Float? = null,
        /** The ink the catalogue picked for that ground; see textColor1. */
        val textHex: String? = null,
        /**
         * The four inks filed with the picture, brightest first.
         *
         * They are not a theme — they are colours taken out of this artwork and
         * chosen to sit together on it, which is exactly what a light moving
         * behind the cover needs. A palette worked out here instead pulls
         * whatever the swatch extractor finds most vivid, and on a crimson
         * sleeve with a black rose on it that came back blue.
         */
        val inkPalette: List<String> = emptyList(),
        /** What Apple's editors wrote about it, where they wrote anything. */
        val notes: String? = null,
        /**
         * The cover as a moving picture, where the label made one.
         *
         * An HLS playlist of a few seconds that loops. Not every record has
         * one; the ones that do are what the reference's header plays instead of
         * showing a still.
         */
        val motionUrl: String? = null,
    )

    /** What one artist's page can use. Either field may be absent on its own. */
    @kotlinx.serialization.Serializable
    data class Artist(
        /** The portrait Apple identifies them by. */
        val heroUrl: String?,
        /** The page's own tint, as six hex digits; see Album.bgColor. */
        val bgColor: String? = null,
        /** The picture's own proportions, width over height. */
        val heroAspect: Float? = null,
        /** The ink the catalogue picked for that ground; see textColor1. */
        val textHex: String? = null,
        /**
         * The four inks filed with the picture, brightest first.
         *
         * They are not a theme — they are colours taken out of this artwork and
         * chosen to sit together on it, which is exactly what a light moving
         * behind the cover needs. A palette worked out here instead pulls
         * whatever the swatch extractor finds most vivid, and on a crimson
         * sleeve with a black rose on it that came back blue.
         */
        val inkPalette: List<String> = emptyList(),
        /** What Apple's editors wrote about them, where they wrote anything. */
        val bio: String? = null,
        /** Where they are from, or where the band was formed. */
        val origin: String? = null,
        /** Their name, drawn. White on transparent, meant to sit over the photo. */
        val logoUrl: String?,
    )

    /**
     * The pictures for an artist, or null.
     *
     * Matched by name, which is the only key the two catalogues share. The match
     * has to be exact, ignoring case: "Queen" searched loosely returns a dozen
     * acts with Queen in their name, and a page that shows the wrong person's
     * face is worse than one that shows no face at all.
     */
    suspend fun artist(name: String, storefront: String = storefront()): Artist? {
        if (name.isBlank()) return null
        val key = "artist $storefront/${name.lowercase()}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] as Artist? }

        remembered(key)?.let { row ->
            val kept = runCatching { json.decodeFromString<Artist>(row) }.getOrNull()
            if (kept != null) {
                synchronized(cache) { cache[key] = kept }
                return kept
            }
        }

        if (!mayAsk()) return null

        val found = ask(name) { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { lookUpArtist(name, storefront) } }

        found?.let { runCatching { remember(key, json.encodeToString(it)) } }
        found?.let { synchronized(cache) { cache[key] = it } }
        return found
    }

    /**
     * The full-height picture for a record, where there is one.
     *
     * This is the difference the reference's own pages show: most records open
     * with a tall photograph filling the screen, and the rest — the ones the
     * editors never made one for — open with the square sleeve. Asking for it is
     * how a client can tell which of the two a record is.
     */
    suspend fun album(
        name: String,
        artist: String,
        storefront: String = storefront(),
    ): Album? {
        if (name.isBlank()) return null
        // "album3": answers filed before 3000px artwork was requested
        // chose the edition without one and kept it for a month.
        val key = "album3 $storefront/${artist.lowercase()}/${name.lowercase()}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] as Album? }

        remembered(key)?.let { row ->
            val kept = runCatching { json.decodeFromString<Album>(row) }.getOrNull()
            if (kept != null) {
                synchronized(cache) { cache[key] = kept }
                return kept
            }
        }

        if (!mayAsk()) return null

        val found = ask(name) { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { lookUpAlbum(name, artist, storefront) } }

        found?.let { runCatching { remember(key, json.encodeToString(it)) } }
        found?.let { synchronized(cache) { cache[key] = it } }
        return found
    }

    /**
     * One song's own cover, for when its record cannot be found.
     *
     * Spotify's largest cover is 640 across, which is soft the moment it fills a
     * phone. Apple files a song's artwork at three thousand and more, and a song
     * is easier to match than a record: the title and the artist are exactly
     * what the queue already knows.
     */
    suspend fun song(
        name: String,
        artist: String,
        /**
         * The record the queue says this song is on.
         *
         * A song row in the catalogue carries the artwork of whichever release
         * it was found on, and a song that also appears on compilations is
         * found on one of those as often as on its own record — which is how a
         * Laura Pausini duet ended up wearing the sleeve of a restaurant
         * playlist called "Pizza Time". Naming the record turns the search
         * result into a choice rather than the first row.
         */
        album: String = "",
        storefront: String = storefront(),
    ): String? {
        if (name.isBlank()) return null
        val key = "song2 $storefront/${artist.lowercase()}/${name.lowercase()}/${album.lowercase()}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] as String? }

        remembered(key)?.let { row ->
            val kept = row.takeIf { it.isNotBlank() }
            if (kept != null) {
                synchronized(cache) { cache[key] = kept }
                return kept
            }
        }

        if (!mayAsk()) return null

        val found = ask(name) { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { lookUpSong(name, artist, album, storefront) } }

        found?.let { runCatching { remember(key, it) } }
        found?.let { synchronized(cache) { cache[key] = it } }
        return found
    }

    /**
     * A lookup's answer, or null when it failed; never null because it was
     * cancelled by a caller.
     *
     * Cancellation remains cancellation, while provider failures simply allow
     * the existing YouTube/Spotify artwork fallback to remain visible.
     */
    private suspend fun <T> ask(name: String, lookUp: suspend () -> T?): T? =
        try {
            lookUp()
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            android.util.Log.w(TAG, "no artwork for $name: ${failure.message}")
            null
        }

    private suspend fun lookUpSong(
        name: String,
        artist: String,
        album: String,
        storefront: String,
    ): String? {
        // Prefer the Apple Music catalog response: its artwork templates support
        // the full 3000px master, while iTunes Search is capped at 1200px.
        val plain = plain(name)
        val credited = credited(artist)
        val results = search("${lead(artist)} $plain", "songs", storefront)
        if (results != null) {
            val matches = results.mapNotNull { it.jsonObject["attributes"]?.jsonObject }
                .filter {
                    val filed = it["name"]?.jsonPrimitive?.content.orEmpty()
                    filed.equals(name, ignoreCase = true) ||
                        plain(filed).equals(plain, ignoreCase = true)
                }
                .filter { row ->
                    val filed = row["artistName"]?.jsonPrimitive?.content.orEmpty()
                    filed.equals(artist, ignoreCase = true) ||
                        credited.any { filed.contains(it, ignoreCase = true) } ||
                        credited.any { it.contains(filed, ignoreCase = true) }
                }

            val match = matches.firstOrNull { row ->
                val filed = row["albumName"]?.jsonPrimitive?.content.orEmpty()
                album.isNotBlank() && sameTitle(filed, album)
            } ?: matches.firstOrNull { row ->
                val filed = row["albumName"]?.jsonPrimitive?.content.orEmpty()
                filed.isNotBlank() && !sameTitle(filed, name)
            } ?: matches.firstOrNull()

            val ampCover = match?.get("artwork")?.jsonObject?.template()?.let { size(it, COVER_PX, COVER_PX) }
            if (ampCover != null) return ampCover
        }

        // iTunes is still Apple's catalog and is the reliable fallback when the
        // storefront endpoint has no token or has not indexed the release.
        return lookUpSongViaItunes(name, artist, album, storefront)
    }

    private suspend fun lookUpSongViaItunes(
        name: String,
        artist: String,
        album: String,
        storefront: String = storefront(),
    ): String? = withContext(Dispatchers.IO) {
        val plainTitle = plain(name)
        val leadArtist = lead(artist)

        val queries = mutableListOf<String>()
        if (leadArtist.isNotBlank() && plainTitle.isNotBlank()) {
            queries.add("$leadArtist $plainTitle")
        }
        if (album.isNotBlank() && plainTitle.isNotBlank()) {
            queries.add("$plainTitle $album")
        }
        if (plainTitle.isNotBlank()) {
            queries.add(plainTitle)
        }
        if (name != plainTitle && name.isNotBlank()) {
            queries.add(name)
        }

        for (query in queries) {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val urls = listOf(
                "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5&country=$storefront",
                "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5",
            )
            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "iTunes/12.12.0 (Windows; Microsoft Windows 10 x64)")
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(request).execute().use { res ->
                        if (!res.isSuccessful) return@use
                        val body = res.body?.string() ?: return@use
                        if (!body.startsWith("{")) return@use
                        val json = org.json.JSONObject(body)
                        val results = json.optJSONArray("results") ?: return@use
                        if (results.length() == 0) return@use

                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val trackName = item.optString("trackName")
                            val artistName = item.optString("artistName")
                            val art100 = item.optString("artworkUrl100")
                            if (art100.isBlank()) continue

                            val titleMatches = sameTitle(trackName, name) || sameTitle(trackName, plainTitle)
                            val artistMatches = artist.isNotBlank() && (
                                sameTitle(artistName, artist) ||
                                sameTitle(artistName, leadArtist) ||
                                artistName.contains(leadArtist, ignoreCase = true) ||
                                leadArtist.contains(artistName, ignoreCase = true)
                            )

                            // A title-only or artist-only hit can be a different song or
                            // compilation. Require both identities; the album only helps
                            // choose the edition after the track identity is proven.
                            if (titleMatches && (artist.isBlank() || artistMatches)) {
                                val highResCover = art100
                                    .replace(Regex("""/\d+x\d+bb\.(jpg|png)"""), "/1200x1200bb.$1")
                                    .replace("100x100bb.jpg", "1200x1200bb.jpg")
                                    .replace("100x100.jpg", "1200x1200.jpg")
                                android.util.Log.i(TAG, "iTunes matched 1200x1200 studio cover for '$name': $highResCover")
                                return@withContext highResCover
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "iTunes query error for '$query': ${e.message}")
                }
            }
        }
        null
    }

    private suspend fun lookUpAlbumViaItunes(
        name: String,
        artist: String,
        storefront: String = storefront(),
    ): Album? = withContext(Dispatchers.IO) {
        val plainAlbum = plain(name)
        val leadArtist = lead(artist)
        val query = if (leadArtist.isNotBlank()) "$leadArtist $plainAlbum" else plainAlbum
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "https://itunes.apple.com/search?term=$encoded&entity=album&limit=5&country=$storefront",
            "https://itunes.apple.com/search?term=$encoded&entity=album&limit=5",
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "iTunes/12.12.0 (Windows; Microsoft Windows 10 x64)")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val body = res.body?.string() ?: return@use
                    if (!body.startsWith("{")) return@use
                    val json = org.json.JSONObject(body)
                    val results = json.optJSONArray("results") ?: return@use
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val collectionName = item.optString("collectionName")
                        val art100 = item.optString("artworkUrl100")
                        if (art100.isBlank()) continue
                        if (sameTitle(collectionName, name) || sameTitle(collectionName, plainAlbum)) {
                            val highResCover = art100
                                .replace(Regex("""/\d+x\d+bb\.(jpg|png)"""), "/1200x1200bb.$1")
                                .replace("100x100bb.jpg", "1200x1200bb.jpg")
                                .replace("100x100.jpg", "1200x1200.jpg")
                            android.util.Log.i(TAG, "iTunes matched 1200x1200 album cover for '$name': $highResCover")
                            return@withContext Album(
                                heroUrl = null,
                                coverUrl = highResCover,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.d(TAG, "iTunes album search error: ${e.message}")
            }
        }
        null
    }

    /** Every name on a track's artist line, which is one string with commas. */
    private fun credited(artist: String): List<String> = artist
        .split(",", "&", "feat.", "Feat.")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    /**
     * The name to search under: the first one credited.
     *
     * The whole line is what the record is *not* filed under. "Laura Pausini,
     * James Blunt Primavera in anticipo" asks the catalogue for a record by a
     * duo of that name and comes back with whatever is nearest — a compilation
     * with both of them on it, as often as not. The lead name alone finds the
     * record, and the guests are still checked for below.
     */
    private fun lead(artist: String): String = credited(artist).firstOrNull() ?: artist

    /**
     * Whether two titles name the same record, decorations aside.
     *
     * Spotify and Apple disagree about editions constantly — "(Deluxe)",
     * "(Special Edition)", "- Remastered" — and an exact comparison throws away
     * the right record over a bracket.
     */
    private fun sameTitle(a: String, b: String): Boolean =
        a.equals(b, ignoreCase = true) || plain(a).equals(plain(b), ignoreCase = true)

    private suspend fun lookUpArtist(name: String, storefront: String): Artist? {
        val results = search(name, "artists", storefront) ?: return null
        val match = results.firstOrNull {
            it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                ?.equals(name, ignoreCase = true) == true
        } ?: return null

        val attributes = match.jsonObject["attributes"]?.jsonObject ?: return null
        val editorial = attributes["editorialArtwork"]?.jsonObject

        // The identity portrait rather than one of the wide banners: this is a
        // phone, the header is taller than it is wide, and cropping a 4:1 banner
        // into it leaves a strip of somebody's forehead.
        //
        // Cropped `va`, which is what the catalogue itself recommends for this
        // picture — the vertical crop, framed on the face. The default `bb`
        // fits the whole square inside the frame and pads the rest, which on a
        // header this tall is two bars of flat colour.
        // The picture and the colour have to come out of the same row.
        //
        // The colour was read off `artwork` alone while the picture could come
        // from either that or the editorial one — so an artist filed with only
        // an editorial portrait had a page with no colour at all, and fell back
        // to a tone worked out from the photograph. On a portrait that is skin
        // and hair, which is how a page the catalogue draws in dark brown
        // arrived as pink.
        // The tall editorial portrait first, and the square identity picture
        // only as a fallback.
        //
        // `artwork` on an artist is a 2400x2400 square. Asked for a 3:4 header
        // it is cropped to fit, and a square cropped that hard on a phone-height
        // header arrives as an eye and an ear. The catalogue files a portrait
        // drawn for exactly this slot — superHeroTall, 1680x2240 — and asking
        // for the shape it already is leaves nothing to crop.
        val heroArt = editorial?.get("superHeroTall")?.jsonObject
            ?: editorial?.get("staticDetailTall")?.jsonObject
            ?: attributes["artwork"]?.jsonObject
            ?: editorial?.get("subscriptionHero")?.jsonObject
        val hero = heroArt?.template()

        // The colour, though, comes off the identity picture where there is
        // one. The tall portrait carries the colour of its own padding — white,
        // usually — while the square one carries the colour the catalogue uses
        // for the artist's page: dark brown for the artist whose page is dark
        // brown.
        val colourArt = attributes["artwork"]?.jsonObject ?: heroArt

        val logo = listOf("musicContentColorLogoTrimmed", "brandLogo", "logo")
            .firstNotNullOfOrNull { editorial?.get(it)?.jsonObject }

        if (hero == null && logo == null) return null
        return Artist(
            // At the picture's own proportions rather than forced into a
            // portrait shape.
            //
            // Some artists are filed with a tall portrait drawn for this slot
            // and some with only a square identity picture. Asking every one of
            // them for 3:4 crops the square ones hard, and a square face cropped
            // to a phone-tall header is an eye and an ear. Asked for the shape
            // it already is, nothing is cut; the page below is what makes up the
            // height, the way the reference does it.
            heroUrl = hero?.let {
                val ratio = heroRatio(heroArt)
                if (ratio == null) {
                    size(it, HERO_W, HERO_H, crop = "va")
                } else {
                    size(it, HERO_W, (HERO_W / ratio).toInt().coerceAtLeast(1))
                }
            },
            heroAspect = heroRatio(heroArt),
            bgColor = colourArt?.get("bgColor")?.jsonPrimitive?.content,
            // The catalogue files the ink beside the ground, picked for it.
            // Working one out ourselves from the ground's brightness is a
            // guess at a decision somebody already made, and it shows: theirs
            // is a near-white with a trace of the photograph in it.
            textHex = colourArt?.get("textColor1")?.jsonPrimitive?.content,
            inkPalette = inkPaletteOf(colourArt),
            bio = attributes["artistBio"]?.jsonPrimitive?.content,
            origin = attributes["bornOrFormed"]?.jsonPrimitive?.content,
            // At the picture's own proportions, whatever they are. Every crop
            // code Apple has either cuts the sides off a name — which is how
            // "BRUNO MARS" arrives as "UNO MA" — or pads it onto white. Asking
            // for the shape it already is leaves nothing to crop.
            logoUrl = logo?.template()?.let { url ->
                val width = logo["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: LOGO_W
                val height = logo["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: LOGO_H
                size(
                    url,
                    LOGO_W,
                    (LOGO_W.toLong() * height / width).toInt().coerceAtLeast(1),
                    format = "png",
                    crop = "sr",
                )
            },
        )
    }

    private suspend fun lookUpAlbum(name: String, artist: String, storefront: String): Album? {
        val query = "${lead(artist)} ${plain(name)}".trim().ifBlank { name }
        val results = search(query, "albums", storefront)
            ?: search("${lead(artist)} $name".trim(), "albums", storefront)

        if (results != null) {
            val credited = credited(artist)
            val candidates = results.mapNotNull { it.jsonObject["attributes"]?.jsonObject }
                .filter { sameTitle(it["name"]?.jsonPrimitive?.content.orEmpty(), name) }
                .filter { row ->
                    val filed = row["artistName"]?.jsonPrimitive?.content.orEmpty()
                    filed.equals(artist, ignoreCase = true) ||
                        credited.any { filed.contains(it, ignoreCase = true) } ||
                        credited.any { it.contains(filed, ignoreCase = true) }
                }

            fun single(row: kotlinx.serialization.json.JsonObject): Boolean =
                row["isSingle"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true ||
                    row["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() == 1

            fun exact(row: kotlinx.serialization.json.JsonObject): Boolean =
                row["name"]?.jsonPrimitive?.content.orEmpty()
                    .substringBefore(" - ")
                    .trim()
                    .equals(name.substringBefore(" - ").trim(), ignoreCase = true)

            val match = candidates.maxByOrNull { row ->
                (if (row.tall() != null || row.motion() != null) 4 else 0) +
                    (if (exact(row)) 2 else 0) +
                    (if (single(row)) 0 else 1)
            }
            if (match != null) {
                val tall = match.tall()
                val cover = match["artwork"]?.jsonObject?.template()
                val motion = match.motion()
                val motionUrl = motion?.stringValue("video")
                    ?.takeIf(::isRemoteUrl)
                val motionStill = motion?.templateValue("previewFrame")
                val notes = match["editorialNotes"]?.jsonObject
                    ?.let { it["standard"] ?: it["short"] }
                    ?.jsonPrimitive?.content

                val pageArt = match["editorialArtwork"]?.jsonObject
                    ?.get("staticDetailTall")?.jsonObject
                    ?: match["artwork"]?.jsonObject

                if (tall != null || cover != null) {
                    val highResCover = cover?.let { size(it, COVER_PX, COVER_PX) }
                    android.util.Log.i(TAG, "Apple Music Storefront matched album '$name' (motion=${motionUrl != null}): cover=$highResCover")
                    return Album(
                        heroUrl = (tall ?: motionStill)?.let { size(it, DETAIL_W, DETAIL_H) },
                        motionUrl = motionUrl,
                        bgColor = pageArt?.get("bgColor")?.jsonPrimitive?.content,
                        textHex = pageArt?.get("textColor1")?.jsonPrimitive?.content,
                        inkPalette = inkPaletteOf(pageArt),
                        coverUrl = highResCover,
                        notes = notes,
                    )
                }
            }
        }

        // Fallback to iTunes Search API (1200x1200bb studio art)
        return lookUpAlbumViaItunes(name, artist, storefront)
    }

    /** An artwork row's own proportions, width over height. */
    private fun heroRatio(art: JsonObject?): Float? {
        val w = art?.get("width")?.jsonPrimitive?.content?.toFloatOrNull() ?: return null
        val h = art["height"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return null
        return (w / h).takeIf { it.isFinite() && it > 0f }
    }

    private fun isRemoteUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    /** The four text colours filed with an artwork row, in order. */
    private fun inkPaletteOf(art: JsonObject?): List<String> =
        listOf("textColor1", "textColor2", "textColor3", "textColor4")
            .mapNotNull { art?.get(it)?.jsonPrimitive?.content }

    /**
     * A record's moving cover, the tall one first and the square one after it,
     * which is the same order the still pictures are chosen in: the header is
     * a portrait.
     */
    private fun kotlinx.serialization.json.JsonObject.motion(): kotlinx.serialization.json.JsonObject? =
        listOf("motionDetailTall", "motionTallVideo3x4", "motionDetailSquare")
            .mapNotNull { get("editorialVideo")?.jsonObject?.get(it)?.jsonObject }
            .firstOrNull { it.stringValue("video")?.let(::isRemoteUrl) == true }

    /** Apple has returned both a string and an object containing `url` here. */
    private fun JsonObject.stringValue(key: String): String? = runCatching {
        val value = get(key) ?: return@runCatching null
        value.jsonPrimitive.content
    }.getOrNull() ?: runCatching {
        get(key)?.jsonObject?.get("url")?.jsonPrimitive?.content
    }.getOrNull()

    private fun JsonObject.templateValue(key: String): String? = runCatching {
        get(key)?.jsonObject?.template()
    }.getOrNull() ?: stringValue(key)

    /**
     * The full-height picture a record's page opens with, where it has one.
     *
     * Two keys, because the catalogue does not always file both: the detail
     * picture is the one their own record page opens with, and the hero is what
     * a wide screen shows — on plenty of records, including some very well known
     * ones, only the second exists. Reading just the first was why a record
     * could open as its sleeve while an edition of it opened as a photograph.
     */
    private fun kotlinx.serialization.json.JsonObject.tall(): String? =
        get("editorialArtwork")?.jsonObject?.let { art ->
            art["staticDetailTall"]?.jsonObject?.template()
                ?: art["superHeroTall"]?.jsonObject?.template()
        }

    private suspend fun search(
        term: String,
        types: String,
        storefront: String,
    ): List<kotlinx.serialization.json.JsonElement>? {
        val bearer = token() ?: return null
        val url = "$API/$storefront/search" +
            "?term=${java.net.URLEncoder.encode(term, "UTF-8")}" +
            // Enough rows to see past a title's singles, remixes and live
            // takes to the record itself; ten was not always.
            "&types=$types&limit=25" +
            "&extend=editorialArtwork,editorialVideo,editorialNotes,artistBio,bornOrFormed"

        val body = get(url, bearer) ?: return null
        return json.parseToJsonElement(body)
            .jsonObject["results"]?.jsonObject
            ?.get(types)?.jsonObject
            ?.get("data")?.jsonArray
            ?.toList()
    }

    /**
     * A title with everything but the title taken out.
     *
     * Guests in brackets, "- Remastered 2019", the decorative characters some
     * labels put in a name: none of it is how the other catalogue files the
     * song, and all of it stops a search from finding it.
     */
    private fun plain(title: String): String = title
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .substringBefore(" - ")
        .replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** The catalogue to ask, from wherever the phone says it is. */
    private fun storefront(): String =
        java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }

    /**
     * The developer token the web player uses, read out of the player itself.
     *
     * Fetched once per run and kept: it is a JWT with months on it, and the only
     * way to know it has gone stale is a request being refused — which is what
     * clears it.
     */
    private suspend fun token(): String? = tokenLock.withLock {
        token?.let { return it }

        // The one written down last time, if it has not run out.
        //
        // Scraping it is not a cheap call: it is the site's front page and then
        // the player's whole JavaScript bundle, which is several megabytes, and
        // it was being paid on the first artwork lookup of every cold start —
        // in front of the listener, with the cover held back until it finished.
        // The token these carry is good for weeks; see its own expiry below.
        keptToken()?.let {
            token = it
            return it
        }

        val page = get(SITE, null) ?: return null
        // The player's own bundle, whichever hash it carries today.
        val script = Regex("""/assets/[^"']*index[^"']*\.js""").find(page)?.value ?: return null
        val bundle = get("$SITE$script", null) ?: return null
        val jwt = Regex("""eyJ[\w-]+\.[\w-]+\.[\w-]+""").find(bundle)?.value ?: return null

        token = jwt
        rememberToken(jwt)
        return jwt
    }

    /**
     * The token from a previous run, while it lasts.
     *
     * Kept with the moment it expires rather than a time-to-live of our own: the
     * expiry is in the token, and a guess would either throw away good ones or
     * hold a dead one and spend a request finding out.
     */
    private fun keptToken(): String? {
        val prefs = store ?: return null
        val jwt = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_TOKEN_UNTIL, 0L)
        if (System.currentTimeMillis() > expiresAt - TOKEN_MARGIN_MS) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_UNTIL).apply()
            return null
        }
        return jwt
    }

    private fun rememberToken(jwt: String) {
        val prefs = store ?: return
        // The middle third of a JWT is its claims, base64url without padding.
        val expiresAt = runCatching {
            val claims = jwt.split('.').getOrNull(1) ?: return@runCatching 0L
            val json = String(
                android.util.Base64.decode(
                    claims,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
                ),
            )
            Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLong()?.times(1000)
                ?: 0L
        }.getOrDefault(0L)

        if (expiresAt <= System.currentTimeMillis()) return
        prefs.edit()
            .putString(KEY_TOKEN, jwt)
            .putLong(KEY_TOKEN_UNTIL, expiresAt)
            .apply()
    }

    private suspend fun get(url: String, bearer: String?): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (bearer != null) {
                    header("Authorization", "Bearer $bearer")
                    // The endpoint answers 403 without them: it is the site's
                    // own API and it checks that the caller is the site.
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                // Rotated, or refused. Either way the one we hold is no use —
                // including the one on disk, or the next run would start by
                // being refused again.
                token = null
                store?.edit()?.remove(KEY_TOKEN)?.remove(KEY_TOKEN_UNTIL)?.apply()
                return@withContext null
            }
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        }
    }

    /** Apple gives a URL with the size still to be filled in. */
    private fun kotlinx.serialization.json.JsonObject.template(): String? =
        get("url")?.jsonPrimitive?.content

    private fun size(
        template: String,
        width: Int,
        height: Int,
        /**
         * WebP, which is what the catalogue's own client asks for.
         *
         * Not a small saving: measured on a tall editorial picture at the size
         * this app uses, 174KB as a JPEG against 30KB as WebP — the same
         * pixels, a sixth of the bytes — and on a square sleeve 950KB against
         * 272KB. These are kept beside every downloaded song, so it is the
         * difference between a library of artwork and a library of audio with
         * artwork attached. Dropping the resolution instead would have saved a
         * third and cost real sharpness.
         */
        format: String = "webp",
        crop: String = "bb",
    ): String {
        val filled = template
            .replace("{w}", width.toString())
            .replace("{h}", height.toString())
            .replace("{f}", format)
            .replace("{c}", crop)

        // Most templates carry the crop code and the file type baked into the
        // last segment rather than as placeholders: `.../{w}x{h}bb.jpg`. The
        // logo has to come back as a PNG cropped `sr`, or the transparency it is
        // made of is flattened onto white and the artist name arrives as a white
        // slab — so the segment is rewritten rather than filled in.
        return filled.replace(
            Regex("/\\d+x\\d+[a-z]{2}\\.(jpg|png|webp)$"),
            "/" + width + "x" + height + crop + "." + format,
        )
    }

    /** How long a remembered answer is still the right one. */
    private const val CACHE_MS = 30L * 24 * 60 * 60 * 1000

    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_UNTIL = "token-until"

    /** Retired a day early, so a lookup never races the expiry. */
    private const val TOKEN_MARGIN_MS = 24L * 60 * 60 * 1000

    /**
     * Sized for the screen it lands on, not for the header it was written for.
     *
     * These pictures are cropped to fill — a phone is far taller and narrower
     * than the 3:4 the catalogue files them in — so the width that reaches the
     * screen is only a fraction of what is asked for, and the rest is scaled up.
     * At 900 across, a picture filling a 1080-wide screen was already being
     * enlarged before the crop took its share, which is what made it grainy.
     */
    private const val HERO_W = 1400
    private const val HERO_H = 1867

    /** The name is wide and short, and has to stay legible at full width. */
    private const val LOGO_W = 900
    private const val LOGO_H = 276

    /** A sleeve, large enough for the header and for the page's colour. */
    private const val COVER_PX = 3000

    /**
     * A record's own full-height picture, at the shape Apple files it in.
     *
     * Large, for the reason above and one more: the player draws this over the
     * whole screen, so the crop throws away most of its width. Not the source's
     * full 2048x2732 — the decoder is asked for the size the screen needs, and
     * a file twice as large only costs the download.
     */
    private const val DETAIL_W = 1600
    private const val DETAIL_H = 2134
}
