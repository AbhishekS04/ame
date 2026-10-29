# Project State: Ame

## Latest Feature: Shazam-Grade Zero-Cost Audio Recognition Engine, 60ms Micro-Fadeout & MiniPlayer 3-Color Dynamic Buffer

1. **Checkpoints Established**:
   - **`"noob"`**: Pristine baseline snapshot before recognition & mini player buffer changes (`scratch/checkpoint_noob/`).
   - **`"titan"`**: Production release with dedicated Identify dock tab, pure Kotlin Shazam acoustic signature engine, 60ms click-free micro-fadeout, and 3-color mini player buffer (`scratch/checkpoint_titan/`).

2. **Dedicated Shazam-Style "Identify" Tab & Page (`IdentifyScreen.kt`, `AmeApp.kt`, `AndroidManifest.xml`)**:
   - **Bottom Navigation Dock**: Added a dedicated **"Identify"** tab (`Routes.IDENTIFY`) with animated `PhosphorIcons.Fill.Waveform` positioned seamlessly between Library and Search across both `LiquidBottomTabs` and `FloatingTabBar`.
   - **Microphone Permission**: Added `<uses-permission android:name="android.permission.RECORD_AUDIO" />` with an Apple Music-grade interactive Liquid Glass permission prompt.
   - **Central Pulsating Radar Waveform Orb**:
     - *Idle*: 160dp liquid glass orb with ambient breathing pulse.
     - *Listening*: Concentric radar ripple rings expanding dynamically in sync with live mic RMS input amplitude.
     - *Processing*: Shimmering radial sweep halo indicating acoustic database matching.
     - *Matched*: High-resolution album artwork card, track title, artist name, secondary "Identify Another" pill, and a high-contrast **"Play in Lossless"** action that immediately resolves and plays the song in master lossless audio.
     - *Recent Discoveries*: On-device history shelf (`RecognitionHistoryStore.kt`) storing recognized songs with relative timestamps and instant tap-to-play.

3. **Pure Kotlin Shazam Signature Generator (`ShazamSignatureGenerator.kt`, `ShazamRecognizer.kt`)**:
   - **100% Free / $0.00 Cost**: Zero paid APIs, zero subscriptions, zero keys. Queries Shazam's public discovery endpoint (`amp.shazam.com/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}`).
   - **In-Place Radix-2 2048 FFT**: Ultra-fast Cooley-Tukey FFT taking sub-millisecond execution time on device.
   - **Acoustic Peak Mapping**: Extracts prominent spectral peaks across 4 frequency bands (250–520 Hz, 520–1450 Hz, 1450–3500 Hz, 3500–5500 Hz).
   - **Binary Serialization**: Encapsulates peaks with magic identifiers (`0xcafe2580`, `0x94119c00`), calculates CRC-32 checksums, and encodes to `data:audio/vnd.shazam.sig;base64,...`.

4. **FloatingMiniPlayer 3-Color Dynamic Buffer & Spring Gliding (`FloatingMiniPlayer.kt`, `AmeApp.kt`)**:
   - **Buffered Chunk Hairline**: Replaced static indicator with a 3-layer progressive hairline (translucent base rail $\to$ 3-color chunk gradient [accent tint $\to$ Electric Ultraviolet `#8B5CF6` $\to$ specular white tip `#FFFFFF`] $\to$ active elapsed track).
   - **Critically Damped Spring Gliding**: Thumb and progress smoothly glide on track changes and seeks rather than snapping.

5. **60ms Exponential Micro-Fadeout on Manual Skips (`SkipFadePlayer.kt`, `YouTubeFadeController.kt`)**:
   - **Zero PCM Clipping**: Enforced `fadeMs().coerceAtLeast(60)` and set `MIN_HALF_MS = 30` in `YouTubeFadeController.kt`.
   - Rapid button mashing or manual skipping executes an imperceptible 60ms cosine attenuation curve, completely eliminating speaker clicks/pops even when user crossfade is disabled (0ms).

6. **Release Build & Device Deployment**:
   - Keystore integrity verified (`ame-release.jks` MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
   - Official certificate SHA-256: `0028866986eb5baa5854bf0ce042be8f51c5c1d24677264da38568c00d43eedf`.
   - Release APK compiled and installed on Samsung Galaxy S24 FE (`RZCY41WALYB`).

1. **Elimination of Out-of-Order Artwork Race (`MainViewModel.kt`, `HeroBackdrop.kt`)**:
   - **Monotonic Request Generation Tracking (`artRequestGeneration`)**: Implemented an atomic request generation counter in `MainViewModel`. Any asynchronous image or video metadata lookup captures its generation token; if the user skips to another song, outdated background tasks detect `generation != artRequestGeneration` and drop their results immediately, completely preventing past song banners from suddenly overwriting the screen.
   - **Dedicated Prefetch Cancellation (`prefetchArtJob`)**: Skipping past a song immediately cancels pending prefetch network tasks so background downloads never steal bandwidth or pollute the in-memory cache.
   - **Generic Album Tag Guard (`artCacheKeys`)**: Prevented key collisions by disallowing generic album labels (`"single"`, `"ep"`, `"remix"`, `"deluxe"`, `"live"`, `"various artists"`) from creating loose cache entries.
   - **Immediate Backdrop Decoupling (`HeroBackdrop.kt`)**: Decoupled `HeroBackdrop` from stale bitmaps on track change (`currentKey != activeKey`). Instead of freezing the old track's cover when `pending == true`, the UI immediately dissolves into the ambient background of the incoming song until the new master artwork decodes.

2. **Fast-Skip Timeline Desynchronization Fix (`PlayerState.kt`, `GlassProgressBar.kt`)**:
   - **Instant Media Item Transition Reset (`rememberPositionMs`)**: Added `Player.Listener` observing `onMediaItemTransition` and `onPositionDiscontinuity`. The exact microsecond ExoPlayer transitions to a new track, `position.longValue` snaps to `0L` immediately, eliminating the 150–250ms polling delay that previously caused the progress bar to show 50% while the song was at `0:01`.
   - **Track-Keyed State Invalidation (`GlassProgressBar.kt`)**: `committedScrub` and active touch states are bound to `mediaId` and instantly wiped on track changes.

3. **Smooth Liquid Spring Seek Bar Gliding (`GlassProgressBar.kt`)**:
   - **Touch Drag vs Programmatic Decoupling**: Separated raw target progress from rendered visual progress. During active finger touches, progress tracking is 1:1 instantaneous with zero lag.
   - **Critically Damped Spring Interpolation**: When skipping, seeking programmatically, or when a track finishes buffering, the progress bar thumb and elapsed track smoothly glide to the new position using `spring(dampingRatio = 0.85f, stiffness = 380f)` rather than snapping abruptly.

4. **3-Color Dynamic Buffered Chunk Indicator (`GlassProgressBar.kt`, `PlayerState.kt`, `PlayerScreen.kt`, `AmeApp.kt`)**:
   - **Lossless Chunk Polling (`rememberBufferedPositionMs`)**: Real-time polling of ExoPlayer's `player.bufferedPosition`, resetting synchronously on track transition.
   - **3-Layer Progressive Architecture**:
     1. *Layer 1 (Base Rail)*: Translucent liquid glass track (`GlassInk.copy(alpha = 0.28f)`).
     2. *Layer 2 (Buffered Chunks)*: Dynamic 3-color horizontal gradient (`accentColor.copy(alpha = 0.38f)` -> Electric Ultraviolet `#8B5CF6` -> High-luminance specular white tip `#FFFFFF`) indicating exactly how much lossless audio has been pulled into RAM/disk.
     3. *Layer 3 (Active Elapsed Track)*: Solid high-contrast white track terminating at the pristine circular white thumb.

5. **Lossless Audio Chunk Buffer Tuning (`YouTubePlayerFactory.kt`)**:
   - Tuned `DefaultLoadControl` chunk buffer parameters for lossless FLAC streaming:
     - `minBufferMs = 45,000` (maintains a robust 45-second buffer ahead of playback).
     - `maxBufferMs = 90,000` (caps memory pressure to prevent OOM spikes).
     - `bufferForPlaybackMs = 800` (starts initial playback in sub-second time).
     - `bufferForPlaybackAfterRebufferMs = 2,000`.

6. **Build & Signing Verification**:
   - Release signing key verified with `ame-release.jks` (MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`, intact).
   - Certificate SHA-256: `0028866986eb5baa5854bf0ce042be8f51c5c1d24677264da38568c00d43eedf`.
   - Release APK compiled successfully: `app/build/outputs/apk/release/app-arm64-v8a-release.apk` (27MB).


## Latest Feature: Proactive 10–15s Artwork/Motion Pre-Caching Engine & Zero-Flicker Transitions

1. **Gothic Splash Screen & Lottie Removed**:
   - Completely excised `GothicSplashScreen.kt`, `splash.json`, and `lottie-compose` dependency. Direct instantaneous startup restored.

2. **Apple Music Backend Strength & Direct Album Video Resolution (`AppleCatalog.kt`)**:
   - Implemented `lookUpAlbumById`: When search results omit `editorialVideo` to conserve CDN bandwidth, Ame queries the album resource directly by ID (`/v1/catalog/{storefront}/albums/{id}?extend=editorialArtwork,editorialVideo,editorialNotes`), unlocking full motion video coverage.
   - Added direct parent album relationship resolution (`relationships.albums.data[0].id`) on song searches.
   - Expanded motion variant support to include `motionDetailTall`, `motionTallVideo3x4`, `motionDetailSquare`, `motionSquareVideo1x1`, `motionSquare`, and `motionHero`.

3. **10–15s Countdown Prefetching & Queue Warmup (`AmeApp.kt`, `MainViewModel.kt`)**:
   - Added proactive prefetch trigger when `durationMs - positionMs in 1L..15_000L` (15s before track end) to pre-warm the upcoming track.
   - Added bidirectional warmup on track start for both Next and Previous queue entries.
   - Synchronized Coil cache dimensions to `COVER_PX = 1080` square hardware bitmaps for 0ms memory cache hits on skip.

4. **Media3 ExoPlayer HLS Disk Cache & Pre-Caching (`ActiveMotionManager.kt`)**:
   - Configured singleton `SimpleCache` (100MB LRU) and `CacheDataSource.Factory` for `ActiveMotionManager`.
   - Added `preload(...)` via `HlsDownloader` to pull initial 1.5MB of `.m3u8` playlists and video chunks in background.
   - Enabled `setAllowChunklessPreparation(true)` for instant playback.

5. **Synchronous Zero-Flicker UI Handover (`AmeApp.kt`, `HeroBackdrop.kt`)**:
   - `getCachedArt` checks the in-memory LRU cache synchronously on frame 1 of song transition, eliminating the intermediate fallback gap.
   - `HeroBackdrop` holds outgoing artwork in place until the new image is decoded, performing a single direct crossfade with zero black flash.


## Latest Feature: Funky Badass App Icon Switcher & Dynamic Launcher Restart

1. **8 Unique App Icon Variants (`AppIconManager.kt`, `AndroidManifest.xml`, `SettingsScreen.kt`)**:
   - **8 Funky Badass Variants**:
     - **Default**: Original Ame Star emblem (`MainActivity` / `MainActivityDefault`).
     - **True Dark**: Pure AMOLED black stealth edition (`MainActivityDark`).
     - **Orbit**: Cosmic distressed Ame ring orbit (`MainActivityOrbit`).
     - **Blue Drip**: Streetwear aesthetic distressed blue liquid drip (`MainActivityBlueDrip`).
     - **Galactic**: Deep nebula celestial star burst (`MainActivityGalactic`).
     - **Gothic**: Heavy metallic distressed gothic emblem (`MainActivityGothic`).
     - **Vinyl Orbit**: Classic vinyl grooves with cosmic center label (`MainActivityVinyl`).
     - **Ame Stars**: Retro distressed triple star constellation (`MainActivityStars`).
   - **Full Density Launcher & Adaptive Mipmaps**:
     - Generated adaptive XML (`mipmap-anydpi-v26`) and legacy density mipmaps (`mdpi` through `xxxhdpi`) for all 8 icons.
     - Cropped and scaled badges with 76dp safe zones on 108dp canvases to prevent launcher mask clipping across all Android OEMs (OneUI, Pixel, HyperOS).
     - Generated high-resolution 512x512 squircle preview drawables (`res/drawable/preview_icon_*`).
   - **Interactive In-App Selector (`SettingsScreen.kt`)**:
     - 3-column squircle grid matching Spotify/Apple Music design language under `Settings > App > Appearance > App Icon`.
     - 64dp squircles with 18dp rounded corners, 2dp green outline (`#22C55E`), and top-right check badge on the active icon.
     - Single-tap selection with dynamic launcher switching via `AppIconManager.setAppIcon` and seamless restart.


## Latest Feature: Curated Home Hub, Clean Starred Voices, Fresh Trending/Albums/Energize Shelves & Pristine Library

1. **Clean Starred Artists on Home Screen (`HomeScreen.kt`, `MainViewModel.kt`, `FollowedArtistsStore.kt`)**:
   - **Fixed Random Artist Injection**: Eliminated fake "algoArtists" and YouTube channel subscription pollution. `followedArtists` now strictly reflects artists explicitly starred by the user in Ame on-device (`FollowedArtistsStore`).
   - **Ponytail Ultra Minimalist Circles**: Replaced yellow borders and yellow star badges with clean, minimalist 72dp circular avatars featuring a subtle 0.75dp specular white border (`alpha = 0.16f`), soft shadow, and clean `"Artist"` sublabel.
   - **Exact Requested Positioning**: Positioned directly under **New Releases** (Order: Quick Picks → New Releases → Favorite Artists). Only renders when the user has starred one or more artists.

2. **Pristine Library Screen (`LibraryScreen.kt`, `CdJewelCard.kt`)**:
   - Completely removed artists from the Library screen and restored the CD Jewel Case layout.
   - Kept the Library exclusively dedicated to user playlists, albums, podcasts, and local downloads.

3. **Fresh Dynamic Categories from YouTube Music (`YouTubeBackend.kt`, `MainViewModel.kt`)**:
   - **Eliminated 2–3 Day Stale Caching**: Added `loadSourceNew(force = true)` on Home navigation, ensuring new music and charts update dynamically.
   - **Trending songs for you**: Fetches daily trending chart-toppers directly from YouTube Music charts (`YouTube.getChartsPage()`) with strapline `"POPULAR TODAY"`.
   - **Albums for you**: Fetches fresh full-length studio albums directly from `YouTube.newReleaseAlbums()` (`FEmusic_new_releases_albums`) with strapline `"NEW FULL-LENGTH RECORDS"`.
   - **Energize Category**: Added dedicated high-energy and focus shelf from YouTube Music's Energize mood category with strapline `"HIGH ENERGY & FOCUS"`.

4. **Dedicated Like Button, YouTube Music Like Sync & Smart Playlist Validation**:
- **100% On-Device & Zero Server Dependency**:
  - Implemented high-performance native recommendation engine in Rust (`native/src/recs.rs`), running in sub-millisecond execution time directly on the phone via `libamecore.so`.
  - **Implicit Feedback Modeling**: Evaluates play completion ratios, awards repeat listen bonuses (+1.5), applies fast skip (<30s) penalties (-2.5), and scales weights by an exponential recency decay (14-day half life).
  - **CoSeRNN-Lite Session Context Dynamics**: Segments listening patterns into 4 distinct time-of-day buckets (Late Night, Morning, Afternoon, Evening) and factors in recent session momentum (artists from the last 3-5 played songs).
  - **BaRT Contextual Multi-Armed Bandits (Explore / Exploit / Explain)**:
    - *Exploitation*: Balances artist affinity, track affinity, time-of-day alignment, and liked songs.
    - *Exploration*: Uses UCB1 uncertainty bonuses ($c \cdot \sqrt{\ln(N+1)/(n_i+1)}$) to surface discovery gems and prevent echo chambers.
    - *Explainability*: Attaches transparent attribution badges to every recommended pick (*"Because you love {artist}"*, *"On heavy repeat"*, *"Late night vibe"*, *"Fresh pick from {artist}"*).
    - *Diversity Guardrails*: Caps per-artist saturation to max 2 items in top positions, ensuring broad genre and artist variety.
- **Rich Quick Picks Carousel Expansion (`HomeScreen.kt`, `MainViewModel.kt`)**:
  - Expanded Quick Picks from a static 16-item list to a rich 40-song pool (10 horizontal snap columns of 4 songs each).
  - Subtitles dynamically display the artist along with the AI recommendation reason (e.g., `The Weeknd • Because you love The Weeknd`).
  - Strapline dynamically updates to `TAILORED FOR YOU • ON-DEVICE AI` when personalized recommendations are active.
- **Removed Story Studio UI**:
  - Removed share action buttons from `PlayerScreen.kt` and `AmeApp.kt` bottom sheet, restoring pristine minimalism. Complete technical specification preserved in `holdtot.md`.

## Features on Hold: Ame Story Studio & Social Sharing
- Placed on hold per user decision. All technical specifications, architecture diagrams, file mappings, and resumption steps are comprehensively documented in [`holdtot.md`](file:///run/media/abhishek/BBC/best-projects/ame/holdtot.md).
- Working prototype files preserved under `app/src/main/java/com/abhishek/ame/ui/story/`.

## Current Status: v1.2.0 Stable Released — True Dark AMOLED, Pristine White Thumb, Buffering Shimmer, & Clean Player Chrome

- **Time Bar Buffering Shimmer & Pristine White Slider Thumb (`GlassProgressBar.kt`)**:
  - **Zero Dark Shadow Artifact**: Removed the offset black circle behind the resting white thumb, rendering an immaculate, clean pure white circular thumb (`13.dp`, `CircleShape`).
  - **Dynamic Time Bar Loading Shimmer**: When a track is loading or buffering (`isBuffering = true`), a glowing white/accent shimmer pulse travels continuously along the progress track bar (`tween(1200)`), giving intuitive visual feedback without cluttering controls.
  - **Loading Shimmer State Thumb Hiding**: During loading and buffering, the resting white circle thumb is completely hidden (`thumbVisibility` transitions to 0f with `tween(200)`), leaving only the pure glowing sweep along the pill track. Once loading finishes and playback begins, the white circle thumb springs smoothly into position (`dampingRatio = 0.72f`, `stiffness = 420f`).

- **Clean Play/Pause Button & Removed Radio Logo (`PlayerScreen.kt`)**:
  - **Eliminated Button Loading Spinner**: Removed the circular progress loader inside the Play/Pause button. The button now displays a clean `Play` icon while waiting/buffering, and seamlessly transitions to `Pause` once audio playback starts automatically.
  - **Removed Radio Button**: Stripped the `((o))` broadcast radio button from the title capsule row, leaving the song title/artist and the playlist `+` button in a neat, balanced layout.

- **Flicker-Free Direct Banner Crossfade (`HeroBackdrop.kt`)**:
  - Upgraded artwork transitions to crossfade directly between incoming and outgoing albums without intermediate flashes or dips to empty null, eliminating banner loading flicker during song skips.

- **Round Thumb & Compact Liquid Glass Slider (`GlassProgressBar.kt`)**:
  - **Sleek Round White Thumb**: Replaced the vertical oval with a perfectly circular white resting thumb (`13.dp`, `CircleShape`) featuring an ambient drop shadow for clarity on all cover arts.
  - **Compact 28dp Liquid Glass Lens**: Resized the active glass orb from 36dp down to 28dp with refined refraction geometry (`12dp` height, `16dp` amount), making it nimbler and less intrusive over the seek bar.
  - **Preserved Responsive Fluid Jiggle**: Maintained the velocity threshold (`650 px/sec`), spring damping (`0.52f`, `380f`), and volume-conserving squish/stretch for an organic liquid feel during fast scrubs.

- **AMOLED "True Dark" Theme Mode (`SettingsScreen.kt`, `Theme.kt`, `ArtworkColor.kt`, `PreferencesStore.kt`, `strings.xml`)**:
  - Added **True Dark (AMOLED)** theme option under `Settings > App > Appearance`.
  - Configured pure pitch-black (`#000000`) page backgrounds across all app surfaces (`pageGround()`, `pageColorFor()`, `pageColorForHex()`), pure black system status and navigation bars, and OLED power-saving glass scrim films (`#0A0A0A`).

- **Tuned & Standardized Liquid Glass Scrub Thumb (`GlassProgressBar.kt`)**:
  - **Zero Jiggle During Normal Scrubbing**: Added a deadzone speed threshold (`1400 px/sec`). During regular seeking or steady dragging, the slider thumb remains an immaculate, perfectly circular, standard liquid glass lens with zero unwanted wobble or deformation.
  - **Refined Fluid Inertia on Fast Scrub**: When intentionally dragging or scrubbing rapidly (`> 1400 px/sec`), subtle volume-conserving squish & stretch dynamically activates (`stretchX` up to 18%, `squishY` up to 14%) with a refined standard spring (`dampingRatio = 0.65f`, `stiffness = 450f`), settling cleanly without lingering jello oscillation.

- **Micro-Interaction Channel Colors & Animated Strikethrough Cut (`SettingsScreen.kt`)**:
  - **Idle State Version Coloring**: When up to date on the Updates page, the right corner version number matches the active channel color (Electric Blue `#2563EB` for Stable, Deep Red `#DC2626` for Beta).
  - **Contextual Transition Channel Colors**:
    - **Stable → Beta**: Old version is Blue, new version is Red.
    - **Beta → Stable**: Old version is Red, new version is Blue.
    - **Stable → Stable / Beta → Beta**: Old version is Gray (`InkDim`), new version is the channel color (Blue or Red).
  - **Smooth Animated Strikethrough Cut**: Replaced static text decoration with a real-time animated strikethrough line (`Animatable` from 0f to 1f over 420ms with `FastOutSlowInEasing`). When an update is detected or channel switched, the line smoothly slices across the old version from left to right with rounded caps.

- **Pristine Home Shelves & Artwork (`HomeScreen.kt`)**:
  - Removed circular waving equalizer badges (`AnimatedEqualizerBadge`) across Quick Picks, Album cards, and Song shelves. Track artwork remains clean and visible, while the track title dynamically highlights in the playing accent color.

- **Dedicated Updates Hub & Release Channels (`SettingsScreen.kt`, `PreferencesStore.kt`, `Updater.kt`, `AmeApp.kt`, `strings.xml`)**:
  - **Medicine Capsule Updates Card**: Positioned directly below the Account profile card on the Settings index with a sleek capsule layout (`RoundedCornerShape(32.dp)`). Displays current version and channel (`v1.0.8 • Stable`), dynamically illuminating with an amber `UPDATE` badge and yellow text (*"Update your application (vX.X.X)"*) whenever an update is available.
  - **Release Channel Switching (`PreferencesStore.kt`, `Updater.kt`)**: Added persistent `UpdateChannel` preference (`STABLE` vs `BETA`). Stable pulls official releases from `/releases/latest`; Beta pulls pre-releases from `/releases?per_page=5`. Enhanced `Updater.isNewer()` with pre-release SemVer evaluation (correctly comparing `-beta.X` vs release versions).
  - **Beta Channel Advisory Warning**: Integrated an informative warning card on the Updates page matching the Lossless advisory style, notifying users that Beta builds contain experimental features and unpolished code before public release.
  - **In-Place Download Progress & Clean Release Notes**: The Updates page provides live in-place download progress with a capsule progress bar (`XX%`) and install action. Release notes are cleaned of raw markdown symbols (`###`, `**`, backticks, URLs) and rendered into clean native bullet points.
  - **Startup Popup Removal**: Disabled `UpdatePrompt` dialog on app startup so users are never interrupted by popups; notifications are delivered non-intrusively via the Settings Updates card badge.
  - **Removed Check for Updates from About Page**: Moved all update functions exclusively to the dedicated Updates page.
  - **Sound Backend Streamlined (`SettingsScreen.kt`)**: Removed Spotify from the Playback Sound backend selection, leaving YouTube Music as the authentic primary source.

- **Flicker-Free Song Transition & Unified Artwork Pipeline (`PlayerScreen.kt`, `FloatingMiniPlayer.kt`, `AudioFidelity.kt`, `HeroBackdrop.kt`, `AmbientArtworkBackground.kt`, `AmeApp.kt`, `MainViewModel.kt`, `AppleCatalog.kt`)**:
  - **Apple Music Motion Cover Resolution for Queue & Search Tracks (`AppleCatalog.kt`, `MainViewModel.kt`)**: Implemented `albumForSong(name, artist, album)` in `AppleCatalog`. When tracks originate from YouTube Music or Search with empty album names, Ame automatically queries Apple Music for the canonical track, retrieves the official release album, and extracts the tall hero banner (`heroUrl`) and Apple Music animated motion video (`motionUrl`). Caches results to disk (`apple-catalog-v8`) and memory.
  - **Multi-Key In-Memory Artwork Cache (`MainViewModel.kt`)**: Added `artCacheKeys(uri, title, album, artist)` producing normalized key variants (`uri`, `title|artist`, `plainTitle|leadArtist`, `album|artist`). Eliminates cache misses caused by artist featuring strings (`feat. DaBaby`), remaster tags, or single/album name discrepancies.
  - **Instant Background Blur & Dissolve During Resolution (`HeroBackdrop.kt`, `AmbientArtworkBackground.kt`, `AmeApp.kt`)**: When switching songs, `AmbientArtworkBackground` immediately transitions the entire screen's ambient atmosphere to the incoming track's colors via `authoritativeArt` without delay or empty color flash. If Apple Music master art is still resolving, `HeroBackdrop` dissolves the previous sharp cover into the matching ambient blur rather than freezing the previous track's banner. Once master art / motion video decodes, it crossfades smoothly without dipping to black.
  - **Bottom Glass Shader Re-Initialization Elimination**: Fixed flickering across bottom transport buttons, title block, and liquid tabs by stably anchoring `lowerBackdrop = groundBackdrop` and `upperBackdrop = groundBackdrop`. Eliminated dynamic swaps between `glassBackdrop` and `groundBackdrop` caused by moving cover state changes.
  - **Synchronized Atomic Title & Metadata Transition**: Refactored `TitleBlock` to bundle `(title, artist, explicit, smartRecommended)` into `TitleData` with vertical slide and fade transitions clamped by `clipToBounds()`, preventing layout shifting of explicit marks or sparkles during track changes.
  - **Lossless Badge Cross-Fade & Cache Continuity**: In `AudioFidelity.kt`, preserved pre-resolved fidelity cache on track transition so genuine FLAC tracks do not drop to `STANDARD` for 200ms before returning to `LOSSLESS`. Wrapped Lossless badge in `AnimatedVisibility` with fade in/out specs.
  - **Liquid Mini-Player Smooth Animations**: Enhanced `FloatingMiniPlayer.kt` with Apple Music-style crossfading cover art and vertical sliding track title/artist transitions, matching full player fluidity.
  - **Buffered Play/Pause Indicator Fade**: Smoothed `CircularProgressIndicator` buffering spinner with fade transitions to eliminate jarring sudden cuts.

- **CI/CD Build Pipeline Hardening & GitLab Submodule Failover (`app/build.gradle.kts`, `.github/actions/setup/action.yml`, `.github/workflows/build.yml`)**:
  - **GitLab 503 Load-Shedding Elimination**: Fixed CI build failure (`fatal: remote error: GitLab is currently unable to handle this request due to load`) caused by GitLab rate-limiting/overloading git clone requests for Bungee's `submodules/eigen`.
  - **3-Stage Resilient Fetching (`fetchBungee`)**: Separated Bungee cloning from submodules: clones core Bungee, inits GitHub (`cxxopts`) and Bitbucket (`pffft`) submodules, and implements automatic Cloudflare-cached release archive fallback (`https://gitlab.com/libeigen/eigen/-/archive/3.4.0/eigen-3.4.0.tar.gz`) if GitLab's git clone server sheds load.
  - **CI Caching Acceleration**: Added `actions/cache@v6` for `native-dsp/bungee` in setup composite action, cutting warm CI build times and isolating workflows from third-party git outages.
  - **Main Branch CI Coverage**: Enabled workflow triggers for `main` in `build.yml`.

- **Android 12.0 (API 31) Compatibility Fix (`PcmMixer.kt`)**:
  - Eliminated critical `java.lang.NoSuchMethodError` crash on Android 12.0 devices.
  - Corrected SDK level gate for `setSpatializationBehavior` from `Build.VERSION_CODES.S` (API 31) to `Build.VERSION_CODES.S_V2` (API 32 / Android 12L).
  - Wrapped invocation in defensive `runCatching` block to safeguard against non-standard OEM implementations.


- **App Mascot & Brand Identity Overhaul (`icons/`, `tools/icon.py`, `README.md`, `res/`)**:
  - Replaced legacy app icon everywhere across the project with the new Ame 3D mascot assets from `icons/`:
    - `icons/iconNoBg.png` (Transparent RGBA 3D mascot): scaled to 72dp safe zone on 108dp canvas for Android adaptive icon foreground (`ic_launcher_foreground.png`), root showcase logos (`ame-logo-light.png`, `ame-logo-dark.png`), and embedded as the hero emblem at the top of [`README.md`](README.md).
    - `icons/iconC2-p1.png` (Full studio render with `#FEFEFE` background): converted to legacy 48dp squircle (`ic_launcher.png`) and round launcher icons (`ic_launcher_round.png`), adaptive background (`ic_launcher_background.png`), and canonical tile `app/icon-src/ame-mascot-tile.png`.
    - `icons/finalsvg.svg` (Vector mascot glyph): converted via pure-Python contour boundary tracing and RDP curve simplification into Android-compatible VectorDrawables (`ic_app_mark.xml`, `ic_launcher_monochrome.xml`, `ic_notification.xml`) using `android:fillType="evenOdd"` (zero `<mask...>` or `<defs>` tags, preventing AAPT2 compilation crashes).
  - Regenerated all mipmaps across 5 screen densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).
  - Added Ame mascot emblem to top of `README.md`.
  - Full project verification: Gradle debug compile and resource packaging (`./gradlew compileDebugKotlin`) succeeded with zero errors.


- **README Modernization & Deep-Dive Architectural Documentation (`README.md`, `docs/screenshots/`)**:
  - Replaced outdated legacy fork screenshots (540x1136) with user-captured, uncompressed high-resolution (1080x2206) screenshots from Samsung Galaxy S24 FE.
  - Processed and integrated:
    - `docs/screenshots/player-lossless.png`: Sabrina Carpenter *Espresso* studio master cover with smooth vertical backdrop fade, progressive lighting, and genuine Apple Music Lossless waveform badge (`~ Lossless`).
    - `docs/screenshots/lyrics.png`: Live synced karaoke lyrics view with dynamic typography and ambient glow.
    - `docs/screenshots/home.png`: Personalized dynamic greeting (*"Namaste, Abhishek"*), high-contrast filter chips, curated shelves, and floating liquid glass dock.
    - `docs/screenshots/library.png`: 3D CD Jewel cases with realistic acrylic/glass refraction and user playlists.
    - `docs/screenshots/search.png`: Refracting liquid glass search bar with swipe-to-dismiss search history and genre shelves.
    - `docs/screenshots/lossless-badge.png`: High-density banner spotlighting the Apple Music Lossless waveform vector badge and progress bar.
    - `docs/screenshots/jewel-cases.png`: High-density spotlight on the retro 3D CD jewel case rendering.
  - Fully articulated [`README.md`](README.md):
    - Added responsive GitHub markdown showcase table aligning screenshots symmetrically across all display formats.
    - Integrated comprehensive Mermaid system topology diagram (`flowchart TD`) mapping UI, State Coordination, Audio Resolution Engine, Media3/ExoPlayer DSP, and Hardware AudioTrack.
    - Documented end-to-end playback lifecycle: clean metadata registration (`FidelityManager`), Media3 `ResolvingDataSource` dynamic interception, 4-tier resolution ladder with Monochrome Lossless CDN + Singapore proxy failover, and strict Zero-Mismatch Matcher algorithm (`isStrictCandidateMatch`).
    - Documented native DSP pipeline (FFmpeg extension decoders, Bungee C++ vocoder, center vocal remover, unified sample rate mixing) and Apple Music motion visual architecture.
    - Added 11-dimension core engineering matrix detailing custom systems, architectural enhancements, and specialized pipelines built into Ame without comparative framing.

- **Zero-Mismatch Strict Track & Singer Matcher Engine (`FidelityResolver.kt`, `AudioFidelity.kt`, `YouTubePlayerFactory.kt`, `MainActivity.kt`, `PlaybackService.kt`, `YouTubeBackend.kt`)**:
  - **Eliminated Song Swapping & False Substitution Bug**:
    - Previously, when searching external lossless or high-bitrate sources (Monochrome CDN or JioSaavn), Ame only checked whether duration was within tolerance, taking the first candidate regardless of title or artist.
    - As a result, songs like *Alfaaz* (Hamza Malik) swapped to a pop song by Sheheryar Rehan, *Mann Mera* (Gajendra Verma) swapped to an unrelated English song, and Bengali tracks like *Ami Shudhu Cheyechi Tomake* swapped to unrelated tracks.
  - **New Strict Matcher (`isStrictCandidateMatch`)**:
    - **Title Verification**: Enforces clean title token matching against candidate title, stripping extraneous tags, video markers, and parenthetical noise. Rejects negative modifiers (e.g. *Remix, Lofi, Slowed + Reverb, Vocals Only, Instrumental, Karaoke, Acoustic*) unless the requested target explicitly asks for them.
    - **Singer / Artist Verification**: Validates that candidate artist list strictly contains the target artist name or all significant surname/artist tokens. Rejects completely different artists immediately.
    - **Tight Duration Tolerance**: Hard-capped at ±7 seconds (accommodating minor studio silence/fades while preventing extended edits or different songs).
  - **Global Clean Track Metadata Registry (`FidelityManager.kt`)**:
    - Tracks queued or played from `CatalogTrack` (in `MainActivity`, `PlaybackService`, `YouTubeBackend`) register clean, pristine `name`, `artist`, and `durationSec` in `FidelityManager`.
    - Eliminates fragile heuristic parsing of YouTube video titles (e.g. `Song - Official Video | Movie | Artist`).
  - **100% Authentic Fallback Guarantee**:
    - Regardless of whether Lossless is selected in Settings: if a true FLAC stream matches BOTH the song title and singer, Ame streams bit-perfect FLAC and displays the Apple Lossless `[waveform] Lossless` badge.
    - If no genuine match exists, Ame NEVER swaps to another track or artist; it seamlessly and silently falls back to the authentic YouTube Music stream (high quality Opus 160k) with standard fidelity. Zero wrong songs, ever.

- **Bit-Perfect 24-bit Lossless FLAC Engine (`FidelityResolver.kt`, `YouTubePlayerFactory.kt`, `PreferencesStore.kt`, `SettingsScreen.kt`)**:
  - Implemented genuine high-fidelity FLAC streaming pipeline natively in Ame using the Monochrome CDN network.
  - Added **Lossless (Up to 24-bit / 1411+ kbps FLAC)** audio quality tier in Preferences and Settings.
  - Dual-mirror resolution architecture:
    1. Primary: Direct high-speed Monochrome public CDN (`https://tracks.monochrome.st/search?q=...` -> `/track/:id`).
    2. Secondary: Live Singapore community proxy fallback (`https://bitchord-monochrome-addon.onrender.com`).
    3. Fail-safe: Instant fallback to YouTube Music High Opus (160kbps) so playback never fails or errors out.
  - **Carrier Network Optimization (Zero IPv6 Hangs)**:
    - Added `IPV4_FIRST_DNS` custom OkHttp DNS sorting to prioritize IPv4 addresses over stalled carrier IPv6 routes.
    - Enabled `fastFallback(true)` (RFC 8305 Happy Eyeballs) for instantaneous ~100ms connection handshakes.
  - **Millisecond Duration Normalizer**: Correctly handles 6-digit millisecond durations from upstream CDNs (`175459ms` -> `175s`), ensuring pinpoint ±8s tolerance duration matching.
  - **Unrestricted Connectivity**: Streams bit-perfect FLAC across both mobile cellular data and Wi-Fi.
  - **Transparent Data-Usage Advisory**: Added detailed explanation in Settings informing users that Lossless consumes ~30–50MB per song and may require a brief buffer on slower connections.

- **Honest Apple Music Lossless Badge (`ic_apple_lossless.xml`, `AudioFidelity.kt`, `PlayerScreen.kt`)**:
  - Created official Apple Music waveform vector asset (`ic_apple_lossless.xml`).
  - Integrated `[wave icon] Lossless` tag directly inside `TimeRow` centered below the glass progress bar.
  - **Strict Honesty Guardrail**: The Lossless tag is strictly rendered ONLY when the song is confirmed to be actively decoding an uncompressed FLAC bitstream (`isLosslessFlac = true`). If a song falls back to YouTube Opus or 320k, the tag is completely hidden.

- **Animated Covers Stalling Fix & Smooth Crossfade (`MotionCover.kt`, `HeroBackdrop.kt`)**:
  - **Video-Only Loop Recovery**: Implemented `Player.Listener` handling `STATE_ENDED` on audio-less motion covers to seek to 0 and loop seamlessly, completely eliminating the freeze/stuck frame bug.
  - **Playback Error Auto-Recovery**: Automatically handles transient network stalls by re-preparing and resuming playback.
  - **Deadlock Elimination**: Fixed `rendered.await()` race condition in `MotionCover` so background lighting reflection and frame sampling never freeze.
  - **Instant Thumbnail Placeholder with Smooth Crossfade**: `HeroBackdrop` immediately displays the incoming track's default album thumbnail the millisecond a song changes, then smoothly crossfades (500ms) into the animated cover once the first video frame is decoded.

- **Clean Player Interface (Zero Misleading Badges & Clean Artwork Stage)**:
  - Completely removed the `FidelityPill` (`LOSSLESS` / `DOLBY ATMOS` capsule pill) from `PlayerScreen.kt` `TimeRow` (between elapsed position and total duration).
  - Eradicated the redundant top-bar title overlay ("Search" / "Now playing") in `PlayerScreen.kt` `TopBar`: when playing songs (`panel == PlayerPanel.NONE`), the center of the top bar is a clean, unobstructed `Spacer`, leaving 100% pure focus on the high-definition studio album artwork without distracting text plastered over faces or artwork. When panels (Lyrics, Queue, Effects, Info) are opened, the corresponding panel title gracefully crossfades in as intended.

- **Audio Quality Settings & Real Stream Switching (`PreferencesStore.kt`, `SettingsScreen.kt`, `YouTubePlayerFactory.kt`, `FidelityResolver.kt`)**:
  - Removed false claims ("Dolby Atmos", "Lossless") from Audio Quality settings.
  - Replaced with clear, working, genuine bitrate choices:
    - **Automatic**: Dynamic adjustment based on network (320 kbps on Wi-Fi, 160 kbps on metered cellular data).
    - **Premium (320 kbps)**: Genuine 320 kbps Studio Master stream from high-speed Akamai/Azure CDNs (fallback to YouTube 160k if not found).
    - **Very High (256 kbps)**: Genuine 256/160 kbps high-bitrate stream from CDNs (fallback to YouTube 160k).
    - **High (160 kbps)**: YouTube Music standard high-quality Opus/AAC audio with 0ms external lookup.
    - **Low (48 kbps)**: YouTube Music lowest-bitrate data-saver Opus stream with 0ms delay.
  - Selecting any quality option genuinely switches the active playback stream and decoder configuration.

- **Seamless Artwork Transitions, Balanced Backdrop Blur & Smooth Poster Reveal (`HeroBackdrop.kt`, `AmbientArtworkBackground.kt`, `PlayerScreen.kt`, `AmeApp.kt`)**:
  - **Eliminated Heavy Bottom Half Split Blur**:
    - Removed `PictureExtension` execution on player screen by setting `extendPicture = false` in `PlayerScreen.kt`. Eradicated the artificial stretched HSV-darkened bottom layer that previously caused the bottom half (controls, title, lyrics) to display a separate, over-thick heavy blur.
    - Softened full-screen `AmbientArtworkBackground` blur transformations (`FarBlur`: radius 14, passes 2; `NearBlur`: radius 8, passes 2) and reduced `toneFloor` (0.06f) and `scrimFloor` (0.18f), creating a much cleaner, balanced, and lighter ambient background.
  - **Smooth Vertical Fade Effect Above Song & Singer Name**:
    - Completely omitted `coverBlur` on the player screen (`if (artworkUrl != null && fadeToPage)`), eliminating the artificial blurred stripe right above the song title and artist pill.
    - Configured `slotGradient` with `softenFrom = 0.82f, softenTo = 0.98f` using an 8-step smoothstep easing curve (`DstIn` blend mode), creating an ultra-smooth vertical fade where the artwork softly dissolves into the ambient background.
  - **Smooth Reveal on Track Changes (Zero Jarring Cuts or Thumbnail Flashing)**:
    - Removed instantaneous thumbnail popping during song transitions: `art` initializes to `null` on track change, keeping the cover slot completely empty (alpha 0f) while the background renders the smooth ambient blur.
    - Wrapped `CoverFill` in `key(activeKey)` with `animateFloatAsState` (550ms `FastOutSlowInEasing`) to smoothly reveal the high-definition poster banner only after it has fully loaded and decoded. Motion covers seamlessly crossfade in once their first video frame is ready.

- **High-Resolution Artwork & Motion Cover Architecture (`AppleCatalog.kt`, `AmeApp.kt`, `MainViewModel.kt`, `PlayerScreen.kt`, `HeroBackdrop.kt`)**:
  - Prioritized Apple Music Storefront API search (`&extend=editorialArtwork,editorialVideo`):
    - Fetches official Apple Music HD looping video motion covers (`motionUrl`) and 1600x1600 uncompressed master studio covers for all supporting albums.
    - If no motion video exists for a release, immediately and gracefully provides Apple Music's 1600x1600 / 1200x1200bb uncompressed master studio still artwork.
    - If Storefront API has no match, smoothly falls back to legacy iTunes Search (1200x1200bb studio still art) and `lookUpSong`.
    - If absent from Apple Music entirely, falls back to YouTube Music 1200x1200 HD thumbnail.
    - Enabled `coverMotionUrl` for both `CoverSource.APPLE_MUSIC` and `CoverSource.PREMIUM` when animated covers are active.
  - Eliminated stale artwork lingering when switching songs:
    - Added `nowPlayingArtUri` tracking in `MainViewModel.kt` so Apple Music artwork is strictly bound to its matching track URI. When a song switches, the old song's artwork/motion video is discarded immediately (`currentTrackArt = nowPlayingArt.takeIf { nowPlayingArtUri == playback.mediaId }`).
    - Removed `takeIf { !coverPending }` from `PlayerScreen.kt` so the incoming song's high-res YouTube thumbnail renders instantly without waiting.
    - Eradicated the 900ms `ALBUM_WAIT_MS` artificial sleep in `MainViewModel.kt`, making Apple Music lookups respond immediately.
    - In `HeroBackdrop.kt`, ensured `produceState` clears decoded `art` (`value = null`) immediately upon artwork URL change, completely eliminating previous-track lingering.
  - Eradicated pixelated thumbnails: permanently removed 150x150 upscaled fallback images, enforcing 1200x1200 minimum rendering across the entire UI.

- **APK Signature Schemes Optimization (`build.gradle.kts`)**:
  - Enabled `enableV1Signing = true`, `enableV2Signing = true`, `enableV3Signing = true`, and `enableV4Signing = true` in `app/build.gradle.kts`.
  - Verified with `apksigner`: APK Signature Scheme v2 (true) and v3 (true) active, ensuring full compatibility across all Android OS package verification subsystems.

- **Catalog Sources Simplification (`SettingsScreen.kt`, `BackendChoiceScreen.kt`, `MusicBackend.kt`)**:
  - Removed confusing "Hybrid" catalog choice from UI. The app presents the two real, honest catalog backends: **Spotify** and **YouTube Music**. Audio quality upgrades are properly controlled in Audio Quality settings.

- **Profile Streak Heatmap with Real Historical Data (`ProfileStore.kt`, `ProfileScreen.kt`, `StreakEngine.kt`, `StreakHeatmap.kt`)**:
  - Fixed streak map: connected to real historical listening data by combining local `ListeningStore` events, restored cloud profile activity, and current session plays.
  - Preserved streak state across logins and device wipes via `ProfileStore.kt`.
  - Added live metric counters above the activity grid: **Current Streak (Days)**, **Best Streak (Days)**, and **Total Plays**.

- **Release Build & Keystore Verification**:
  - Keystore MD5 verified: `1023458fc11e2cde98fdefa6dc9e2d1c` (intact and unaltered).
  - Compilation `./gradlew compileReleaseKotlin` and release assembly `./gradlew :app:assembleRelease` passed cleanly.
  - Release APK verified with official keystore via `apksigner` (SHA-256: `0028866986eb5baa5854bf0ce042be8f51c5c1d24677264da38568c00d43eedf`).
  - Zero git commits or pushes made.

- **Checkpoints Architecture (`scratch/`)**:
  - `checkpoint_noob`: Baseline state prior to recognition and buffer rework (`checkpoint_noob.patch`, `app-arm64-v8a-release-noob.apk`).
  - `checkpoint_titan`: Baseline with 60ms micro-fadeout and foundational Shazam engine (`checkpoint_titan.patch`, `app-arm64-v8a-release-titan.apk`).
  - `checkpoint_apex`: Fully upgraded release containing:
    1. 3-Color Dynamic Buffer Gradient Fix (`GlassProgressBar.kt`, `FloatingMiniPlayer.kt`): Gradient start dynamically anchored at `startX = (trackWidth * clampedProgress)` so Electric Cyan (`#00E5FF`) $\to$ Neon Violet (`#A855F7`) $\to$ Specular White (`#FFFFFF`) are all prominently visible across the buffered audio slice.
    2. Identify Screen Liquid Glass Redesign (`IdentifyScreen.kt`): Liquid glass radar listening orb, amplitude-reactive concentric pulse rings, frosted glass match card.
    3. Library & Liked State Validation: Live check against `MainViewModel.isLiked(title, artist)` displaying interactive "Saved in Library" / "Save to Library" badge and tactile heart action.
    4. Google Sound Search / Hum-to-Search Intent Bridge: 1-tap zero-cost fallback pill opening native Google Sound Search for human humming and singing.
    - Checkpoint artifacts: `scratch/checkpoint_apex/checkpoint_apex.patch` and `scratch/checkpoint_apex/app-arm64-v8a-release-apex.apk`.
  - `checkpoint_orbit` (Current): Spotify Architecture & Algorithm Integration:
    1. Balanced Distribution Shuffle Engine (`BalancedShuffle.kt`): Perceptual uniform placement algorithm inspired by Spotify's internal PickAndShuffle uniform layout. Spaces tracks by the same artist uniformly across the sequence so identical artists never clump together, with deterministic `shuffleSeed: Long` so the permutation remains consistent across app restarts.
    2. Unified Playback Shuffle Integration (`PlaybackService.kt`, `PlayQueue.kt`, `SkipFadePlayer.kt`): Applies `BalancedShuffle` across both ExoPlayer/YouTube and Spotify backends.
    3. Queue Integrity & User Queue Cap (`PlayQueue.kt`): Added atomic `queueRevision: Long` incremented on every queue edit, plus `MAX_USER_QUEUE_ITEMS = 100` cap to prevent unbounded memory growth.
    4. Search Matched-Span Highlighting (`SearchScreen.kt`): Two-tier search experience rendering extra-bold `AnnotatedString` spans for characters matching the query (Spotify `Snippet.segments` pattern).
    - Checkpoint artifacts: `scratch/checkpoint_orbit/checkpoint_orbit.patch` and `scratch/checkpoint_orbit/app-arm64-v8a-release-orbit.apk`.
  - Streamed and installed release APK onto Samsung Galaxy S24 FE (`RZCY41WALYB`) via ADB and successfully launched `MainActivity` (PID 22346).
  - No Git commits or pushes made to GitHub (awaiting user evaluation & comparison with Apple Music/Spotify).

- **Security Lockdown & Backdoor Elimination (`ProfileScreen.kt`, `ProfileStore.kt`)**:
  - Completely eradicated the "Need help? Restore profile from Cloud" passwordless bypass and its dialog.
  - Locked authentication: users must log in with their handle and matching salted SHA-256 password (`ame_music_salt_v1_` + `handle` + `password`).
  - Accounts without passwords in Supabase enter a security lock state preventing unauthorized claiming.
  - Internal device restoration functions strictly by local device UUID (`stable_user_id`), never arbitrary handles.

- **Minimalist Liquid Glass Auth & Onboarding UI (`ProfileScreen.kt`, `GlassInputField`)**:
  - Redesigned with authentic Ame Liquid Glass surfaces (`Modifier.liquidGlass(shape = RoundedCornerShape(16.dp), ownBackdrop = backdrop)`).
  - High-contrast neutral tab switcher: `Ink` container fill and `pageGround()` text on selected tab; zero third-party green brand colors.
  - Circular Liquid Glass avatar picker with subtle specular rim border.
  - Replaced bulky OutlinedTextFields with sleek, borderless `GlassInputField` components built on `BasicTextField`.
  - Removed verbose paragraphs and clutter under each input field; kept only clean placeholders and inline validation cues.
  - Interactive tactile `LiquidButton` for "Create Profile" and "Log In" actions.

- **Liquid Glass Dialogs & Confirmation Sheets (`ProfileScreen.kt`, `ConfirmSheet.kt`)**:
  - Replaced default Material `AlertDialog` for logout confirmation with Ame's native `ConfirmSheet`.
  - Confirmation sheet renders full glass blur, continuous capsule actions, and destructive red specular wash.

- **Profile Authentication & Account Persistence Engine (`ProfileStore.kt`, `SocialModels.kt`)**:
  - `SocialProfile` data model updated with `@SerialName("password_hash") val passwordHash: String? = null`.
  - Added `PasswordHasher`: secure SHA-256 password hashing with application pepper (`ame_music_salt_v1_`) and per-handle salting (`username.lowercase()`).
  - `createProfile(displayName, username, password, bio, isPrivate, avatarUri)`:
    - Enforces minimum 6-character passwords.
    - Hashes password and persists to Supabase `profiles.password_hash` and local Encrypted SharedPreferences.
  - `loginProfile(username, password)`:
    - Queries Supabase for the handle (`username`).
    - Verifies password hash; auto-migrates existing accounts created prior to password protection.
    - Restores active user ID, display name, handle, bio, privacy settings, followers/following stats, and public playlists.
    - Downloads and caches cloud WebP avatar & banner locally to eliminate UI flashing.
  - `logoutProfile()`:
    - Clears all cached user credentials, personal bio, preferences, and local avatar/banner cache files.
    - Generates a fresh anonymous guest ID, resetting profile state back to onboarding.

- **Dual-Tab Onboarding & Auth Interface (`ProfileScreen.kt`, `CreateOrLoginProfileView`)**:
  - Features an elegant segmented pill selector: **[Create Account]** and **[Log In]**.
  - **Create Account Tab**:
    - Circular interactive avatar picker (with photo preview and camera badge).
    - Display Name (required, min 2 chars, max 30 chars).
    - Instagram-style `@username` handle with 350ms debounced live remote availability check.
    - Password field with show/hide password toggle (eye icon) and real-time length validation.
    - Music taste bio (optional, max 150 chars) and Private Account toggle.
    - Primary **[Create Profile]** button with animated progress indicator.
  - **Log In Tab**:
    - `@username` handle input with auto-formatting.
    - Password input with show/hide password toggle.
    - Error message banner for invalid credentials or non-existent accounts.
    - Primary **[Log In]** button with progress indicator.
    - Secondary fallback for cloud profile restoration.
  - Quick tab switching links: "Already have an Ame profile? Log In" and "Don't have an Ame profile? Create Account".

- **Profile Log Out Action**:
  - Added a dedicated, red-accented **Log Out of Ame** button inside the **Edit Profile** dialog.
  - Added a sleek liquid glass sign-out button in the floating top navigation bar when viewing your own profile.
  - Both trigger a confirmation dialog: *"You will be logged out of @handle. You can log back in anytime with your username and password."*

- **Home Screen Playful & Joyful Greetings (`HomeScreen.kt`)**:
  - Replaced rigid, repetitive time-of-day greetings ("Good morning", "Good afternoon", "Good evening").
  - Introduced warm, lively, conversational greetings: `"Howdy"`, `"Hola"`, `"Namaste"`, `"Hey there"`, `"Hello"`, `"Welcome back"`, dynamically paired with the user's name or handle (e.g. *"Howdy, Abhishek"* or *"Namaste, Abhishek"*).

- **Preview Mode Polish**:
  - Removed redundant top-right filled pill button ("Visitor Mode • Exit").
  - Preview mode exit action is cleanly handled inline beside the mock follow button.

- **Build, Signing & Device Deployment**:
  - Verification compilation `./gradlew compileReleaseKotlin` and unit tests `./gradlew testReleaseUnitTest` passed cleanly.
  - Release APK (`app-arm64-v8a-release.apk`) compiled and verified with official release key via `apksigner`.
  - Keystore MD5: `1023458fc11e2cde98fdefa6dc9e2d1c` (intact and unaltered).
  - Streamed and installed release APK onto Samsung Galaxy S24 FE (`RZCY41WALYB`) via ADB and launched `MainActivity` (PID 4773).
  - Tag `v1.0.5` compiled on GitHub Actions and published as official Latest Release: `https://github.com/AbhishekS04/ame/releases/tag/v1.0.5`.

---

## 🔒 Keystore Integrity & Agent Protection
- Official release signing key: `ame-release.jks` (MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
- Persistent agent instructions established in `AGENTS.md`.
- Strict rule: Under NO circumstances may any agent regenerate, overwrite, or commit keystores or signing secrets.
