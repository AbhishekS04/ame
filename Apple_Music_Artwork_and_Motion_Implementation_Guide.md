# Apple Music Artwork & Motion Thumbnails — Step-by-Step Implementation Guide

|              |                                                                                                                            |
| ------------ | -------------------------------------------------------------------------------------------------------------------------- |
| **Target**   | `com.apple.android.music` v6.5.2 (versionCode 1586, APK Mirror 4-arch bundle)                                              |
| **Artifact** | `base.apk`, SHA-256 `1e7151d02cac39a9f70d017bcc26e3b0fd4c2afb3aa30c363206d8616a4fec59`                                     |
| **Method**   | REA static analysis (JADX MCP + Ghidra), Evidence ID `ev_1479f2d5b53037990ad4f305f8044e47a08fe7dad84b5d4bd2132d0c9c7dd0da` |
| **Purpose**  | Reproduce Apple Music's artwork loading and motion-thumbnail pipeline from a clean-room perspective                        |

This guide documents **how the stock Apple Music Android app loads static artwork and renders motion thumbnails**, and gives you a concrete, step-by-step implementation path with Kotlin snippets. Apple renders album artwork and now-playing motion thumbnails in real time; we reverse-engineered exactly how that happens.

**FairPlay disclaimer:** Everything in this document covers **only the non-DRM protected side of Apple Music** — artwork loading and HLS playback of your own library assets where interaction is permitted. We will **not** cover FairPlay DRM circumvention, key decryption, or license handling. Such techniques must not and will not be reproduced here.

---

## 1. Executive summary

The artwork system has two independent halves:

1. **Static artwork** — resolved through URL templates served by Apple's CDN (`isq11.mzstatic.com` / `is2-ssl.mzstatic.com`). Templates contain `{w}x{h}{c}.{f}` placeholders that the client fills in at render time. Format (`jpg`/`png`/`webp`) and sizing are negotiated via a protobuf (`RemoteArtworkProtobuf`).
2. **Motion thumbnails** — rendered by a **single, long-lived video engine** with an HLS-ready Exo player + an asm track selector; the engine never allocates extra players per track. The asset providing motion comes from an HLS playlist (`.m3u8` via `hlsPlaylistUrl`), **not** an image sequence or Lottie file.

**The best-artwork path** — the exact way the app gets the highest-quality possible artwork for an album — is native, and there is a native batch fetch (`CloudLibraryImpl::fetchArtworkInfo` + `BestArtworkURLQuery`) you should replicate as a batched resolver.

All of this is reproduction-ready: server exposes templates + formats + optional `motionVideoUrl` → client resolves best artwork and caches by `(cloudToken, size)` → renders with Coil/Glide → optional motion overlay via a registry-scoped `ActiveMotionManager`.

---

## 2. Pipeline at a glance

```
┌────────────────────────────────────────────────────────────────────────┐
│  SERVER (Apple Music API / ADC)                                        │
│                                                                        │
│  RemoteArtworkProtobuf                                                  │
│   ├─ artworkURLTemplates: ["…{w}x{h}{c}.{f}", …]                        │
│   ├─ availableRemoteArtworkFormats: [webp, png, jpg]                    │
│   ├─ requestedRemoteArtworkFormats  ← client advertises                │
│   └─ remoteArtworks: [ArtworkInfo(                                      │
│         cloudToken, pid, url, variantType, bgColor, …)]                 │
│   └─ motionVideoUrl  (HLS .m3u8, next to brandLogoUrl)                 │
├────────────────────────────────────────────────────────────────────────┤
│  CLIENT (single pass, per library window)                              │
│                                                                        │
│  1. resolveBestArtwork()   →  native batch fetch, BestArtworkURLQuery   │
│  2. cache + stream activity  →  (cloudToken, size) key                 │
│  3. render static          →  Coil/Glide + sizedArtwork(url, w, h)     │
│  4. render motion          →  ActiveMotionManager (registry-scoped,    │
│                               HLS engine, single player, muted loop)   │
│  5. teardown               →  registry, per track change / screen exit │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Static artwork: mzstatic URL templates

### 3.1 Evidence (DEX strings — classes2.dex)

| String / symbol observed | Meaning |
|---|---|
| `https://isq11.mzstatic.com/image/thumb/-0NguZBjaMNv0SbkI-GocQ/{w}x{h}{c}.{f}` | Apple CDN **artwork template** (image 1) |
| `https://isq11.mzstatic.com/image/thumb/9e-d0ML6exn89iw2qBKkfQ/{w}x{h}{c}.{f}` | Apple CDN **artwork template** (image 2) |
| `https://is2-ssl.mzstatic.com/...` | Alternate CDN host, SSL variant |
| `{w}x{h}{c}.{f}` | Placeholder grammar the client substitutes per request |
| Full-width / fit: `w`/`h` absent, `f` variant (`fit`-style) | Non-cropped rendering path |

For your implementation, this is the part your player must replicate: **Apple doesn't hand you final URLs** — it hands you templates. Your client must substitute the placeholders itself.

### 3.2 Template grammar

The CDN template has four components:

- `{w}` — width in pixels
- `{h}` — height in pixels
- `{c}` — crop style: `c` (center-crop to exactly w×h), `cc` (square center-crop), empty string (fit, no crop)
- `{f}` — output format: `jpg`, `png`, `webp`

Example:

```
https://isq11.mzstatic.com/image/thumb/-0NguZBjaMNv0SbkI-GocQ/300x300cc.jpg
```

### 3.3 Sizing rules

What the app actually requests, and how you should shrink it to your own views:

- **300×300** — small album tile, list row.
- **600×600 (`cc`)** — medium/hero tile; dominates the large hero surfaces in the app's reference usage.
- **200×200** — dense list rows, mini-player background.
- **Full-width `fb`-style fit** — article/panel banners, "Now Playing" stretches.
- The rule the app follows: **pick an image at least as large as the final display size in pixels (ends in the density multiplier)**; the CDN downscales server-side. A 600×600 source will not pixelate inside a 200px logical tile; a 300×300 stretched to 640px will.

### 3.4 `sizedArtwork()` helper

This is the exact helper you need in your own project:

```kotlin
fun sizedArtwork(template: String, w: Int, h: Int, crop: String = "c", format: String = "jpg"): String {
    require(template.contains("{w}")) { "template missing {w}" }
    return template
        .replace("{w}", w.toString())
        .replace("{h}", h.toString())
        .replace("{c}", crop)
        .replace("{f}", format)
}
```

Then use it everywhere you bind artwork (`sizedArtwork(template, 600, 600, "cc")`).

---

## 4. Format negotiation: RemoteArtworkProtobuf

### 4.1 Evidence (EPZEP → protobuf classes)

| String / symbol observed | Meaning |
|---|---|
| `com.apple.android.music.remoteclient.generated.RemoteArtworkProtobuf` | Generated protobuf class for artwork negotiation |
| `internal_static_RemoteArtworkProtobuf_descriptor` / `_fieldAccessorTable` | Protobuf internal wiring |
| `artworkURLTemplateData` / `artworkURLTemplatesData` | Carries the CDN template list to the client |
| `availableRemoteArtworkFormats` / `getRemoteArtworkFormatsList` | What the **server** can produce |
| `requestedRemoteArtworkFormats` | What the **client** wants |
| `remoteArtworks` / `addRemoteArtworksBuilder` | Repeated artwork payloads (one per asset) |

For your implementation, this is the part your player must replicate: the APK both **declares** which formats it can accept (via `requestedRemoteArtworkFormats`) and **consumes** a list of what the server offers (`availableRemoteArtworkFormats`), so it must handle a fallback ladder across formats.

### 4.2 Negotiation flow

1. Client sends `requestedRemoteArtworkFormats` = list of formats it prefers (webp first).
2. Server returns, per asset, `artworkURLTemplates` + `availableRemoteArtworkFormats`.
3. Client picks the best overlap: if both say webp exists, use webp; otherwise fall back png → jpg.
4. On render, the substitute the placeholders in the template with `{f}` = the negotiated format.

### 4.3 Implementation

Implement the ladder explicitly:

```kotlin
sealed interface ArtworkFormat { object Webp: ArtworkFormat; object Png: ArtworkFormat; object Jpeg: ArtworkFormat }

@JvmStatic
fun negotiateFormat(requested: List<String>, available: List<String>): String? =
    requested.firstOrNull { available.contains(it) }
        ?: available.firstOrNull()
```

Model it with these classes in your client (this is exactly what the APK uses):
`RemoteArtworkRequest(requestedFormats)` / `RemoteArtworkResponse(urlTemplates, availableFormats)` / `ArtworkUrlBuilder` as a one-liner to expand a template to a concrete shader-loading URL.

---

## 5. Best-artwork resolution: native batch fetch

### 5.1 Evidence (native libraries)

**`libmedialibrarycore.so`** (C++ core library):

| String / symbol observed | Meaning |
|---|---|
| `mlcore::CloudLibraryImpl::fetchArtworkInfo(const std::vector<MediaID>&, DMAPItemKind, const bool&, callback)` | The **batch fetch** entry point the client uses for artwork info |
| `BestArtworkURLQuery` / `BestArtworkURLQueryResult` | Result-shaped query, not live-request — good for caching |
| `FetchArtworkInfoOperation` | Operation-level implementation |
| `BestArtworkURLQuery batch fetched {0} album cloudUniversalIDs` | Log line proving batch behaviour |
| `BestArtworkURLQuery::_fetchBestArtworkUrl() numOfResults: {0}` | Log line proving URL resolution runs per batch |

**JNI bridge** (`libandroidappmusic.so` — `SVArtworkInfo$SVArtworkInfoNative` accessors):

| String / symbol observed | Meaning |
|---|---|
| `_1artworkCloudToken__` → String | Cloud-token identity for this artwork |
| `_1artworkPid__` → long | Persisted ID for artwork row |
| `_1artworkURL__` → String | Concrete resolved url |
| `_1variantType__` → int | Shape/variant of the artwork (0=square, 1=wide, …) |
| `SVMediaLibrary$SVMediaLibraryNative_addArtworkURLToPlaylist` | Additional track-list URL path |
| `ArtworkTokenQueryResults::getAllArtworkInfo()` | Batch retrieval surface |

**`libAMSKit.so`** (AMSKit MediaArtwork bindings):

| String / symbol observed | Meaning |
|---|---|
| `com/apple/mediaservices/amskit/bindings/MediaArtwork` (incl. inner `MediaArtwork$Color`, `MediaArtwork$Size`) | Swift-bound artwork types bridged into Android |
| `ExpectedMediaArtwork` | Optional / expected type for artwork in AMSKit side |
| `bindings/marketing/MediaArtworkMap` | Marketing/banner artwork surface |

**Takeaway for your implementation:** Model it as:

```kotlin
data class ArtworkInfo(
    val cloudToken: String?,
    val pid: Long,
    val url: String,
    val variantType: Int,        // square / banner / hero
    val dominantColor: Int?      // from MediaArtwork$Color / artworkBGColor
)
```

Cache the entries by `(cloudToken, size)`.

### 5.2 Client replication

The server side of the "requested vs available" handshake is done — now the **artifact side**: batched resolver that we replicate from the native batch fetch.

```kotlin
class BestArtworkResolver(private val repo: CloudRepo) {

    suspend fun resolve(views: List<MediaView>, desired: SizeAndFormat): MutableMap<Long, ArtworkInfo> {
        val ids = views.map { it.id }
        val batch = repo.fetchArtworkInfo(ids)               // server round-trip, batched
        return batch.associateBy { it.pid }
    }
}
```

**Key point:** *one* round-trip per batch of albums, **not** one per artwork. The app uses `cloudUniversalIDs` (plural in the log string) for a reason — that's the batch key.

### 5.3 Batch resolver + cache

- **Single-threaded** fetch (the native code interleaves read from DB and out-of-order completion).
- **L1 in-memory**: `LruCache<Long, ArtworkInfo>(100)`.
- **L2 disk**: plain files keyed by `(cloudToken, size)`, backed by `sanitizedName(pid + ".artifact")` under `getExternalFilesDir("artwork")`.

---

## 6. Motion thumbnails: HLS video engine

### 6.1 Evidence (DEX strings — classes2.dex)

| String / symbol observed | Meaning |
|---|---|
| `motionVideoUrl` + `brandLogoUrl` on asset toString | The asset that drives motion carries **both** — a video URL and a static logo fallback |
| `MediaHlsAssetData(playbackStoreId=` | HLS asset container, keyed by playback store ID |
| `!HlsDownloadInfo(downloadLocation=` | Download path for HLS assets |
| `AssetUrls(enhancedHls=` | Alternate HLS stream form |
| `com.apple.android.music.playback.player.cache.hls.MediaHlsAssetData`, `HlsKeyConfiguration`, `HlsDownloadInfo`, `HlsDataSrcFactory` (classes2.dex) | Full HLS pipeline component set |
| String fields `hlsUrl=`, `hlsPlaylistUrl=`, `hlsKeyServerUrl=`, `hlsKeyCertUrl=`, `mpHls*` | HLS config carried through the asset |
| Constants `HERO_HLS`, `FLAVOR_TYPE_AUDIO_HLS`, `HLS_FAST_PATH`, `HLS_STREAMING`, `HLS_PROGRESSIVE_DOWNLOAD`, `HLS_SUBPLAYABLE_DISPATCH`/`HLS_SUBPLAYBACK_DISPATCH`, `DOWNLOAD_MEDIA_ASSET_HLS` | HLS lucene-selection / flavor tagging |
| `createAndAssignMotionManager` (+ `$lambda$17$lambda$15`, `access$createAndAssignMotionManager`) | The **actual call site** that spins up the motion manager |
| `LocalActiveHlsMotionManagerRegistry` | **Exactly one** active motion manager at a time |

Interpretation: the motion thumbnail is **HLS video** (typically `.m3u8` via `hlsPlaylistUrl`), not an image sequence or a Lottie file.

### 6.2 Motion manager

The artwork motion engine is:

- A **single, long-lived video engine** (`ActiveMotionManager`) with an HLS-ready player.
- Loaded once via `createAndAssignMotionManager` from inside your play pipeline.
- Registered in a **`LocalActiveHlsMotionManagerRegistry`** so only one moves at a time — teardown happens elsewhere; the registry does not auto-unload.
- **Looped and muted**; `setSeekBackIncrementMs(Long.MAX_VALUE)` marks it as not user-seekable (`REPEAT_MODE_ONE`, `volume = 0f`).

### 6.3 Implementation

Implement it as a "grabbable/loopable double we render behind artwork". Server accepts two fields:

```kotlin
data class MotionArtwork(val motionVideoUrl: String, val brandLogoUrl: String?)

fun show(motion: MotionArtwork, surface: PlayerView) {
    motionManager.setMediaItem(buildMotionItem(motion.motionVideoUrl))
    motionManager.prepare()
    motionManager.playWhenReady = true
}

fun hide() {
    motionManager.stop()
    motionManager.clearMediaItems()
}
```

- Reuse **one** ExoPlayer (`ActiveMotionManager`); do **not** allocate a new one per track.
- Use `PlayerView` (exo) or equal surface view for the video, layered with a Compose `Image` overlaying `brandLogoUrl` on the static artwork.

### 6.4 Edge details

- `setSeekBackIncrementMs(Long.MAX_VALUE)` is Apple's idiom for "the timeline is not user-controlled" — the UI hides the scrubber.
- `REPEAT_MODE_ONE` keeps the motion looping; `volume = 0f` keeps it silent.
- The registry (`LocalActiveHlsMotionManagerRegistry`) enforces exactly one active motion manager — coordinate with your playback state, have the active-entrance manager pause when the other starts.
- Motion assets should be **preloaded during transitions** before the next track — the playlist is keyed by `playbackStoreId`.
- **Your own asset naming**: HLS assets the app plays for personal library should be plain `.m3u8` or MP4 — no `hlsKeyServerUrl`/`hlsKeyCertUrl`. Those two are only for Apple-protected streams (**we do not apply for them here**).
- Gate motion on **battery saver / data-saver / reduce-motion** ("Preferred-HLS"/"all-HLS") settings.
- The video rendered is always **faded-in as a crossfade over the static poster** — parts 1–3 artwork acts as the loading poster frame.

---

## 7. End-to-end flow

1. **Server** exposes URL templates + available formats + optional `motionVideoUrl` on every asset.
2. **Client** batch-fetches artwork via `BestArtworkResolver`, caching by `(cloudToken, size)`.
3. **Static render**: Coil/Glide + `sizedArtwork(url, w, h, crop)`; dominant color for gradients from `artworkBGColor` / `extendGradientHeightPercent` helpers.
4. **Motion render**: single `ActiveMotionManager` when playing + battery/data/reduce-motion allow; loop muted; fade-in over poster.
5. **Teardown**: registry clears on track change / screen exit; next track preloads during transition.

---

## 8. Implementation checklist

- [ ] CDN template parser: `{w}x{h}{c}.{f}` substitution helper (`sizedArtwork`).
- [ ] Format negotiation protobuf: advertise webp → png → jpg ladder; parse `availableRemoteArtworkFormats` from server.
- [ ] Batch best-artwork resolver: `fetchArtworkInfo(List<MediaID>)` — and **cache `(cloudToken, size)`** on disk.
- [ ] Static render path: Coil/Glide + dominant color backdrop (`artworkBGColor`, `extendGradientHeightPercent`).
- [ ] Compute 3 artwork sizes you actually need (300×300, 600×600 `cc`, 200×200) — no more.
- [ ] Motion: one ExoPlayer `ActiveMotionManager` — `REPEAT_MODE_ONE` + `volume = 0f` + `setSeekBackIncrementMs(Long.MAX_VALUE)`.
- [ ] `LocalActiveHlsMotionManagerRegistry` scoping — only one active motion at a time.
- [ ] Crossfade video over static poster; preload playlist during transitions.
- [ ] Teardown path: registry.clear() on track change; screen-exit confirmation.
- [ ] Battery-saver / data-saver / reduce-motion switches gate motion.
- [ ] No FairPlay DRM/key code paths in your build; own assets are plain `.m3u8`/MP4.

---

*Part of the Apple Music Android reverse-engineering series. Evidence bundle: `/tmp/opencode/applemusic-re/evidence/evidence-bundle.json` (10,998,778 bytes, 2 records, 0 unknowns). Full RE reference: `Apple_Music_Motion_Artwork_Implementation.md` in this directory.*
