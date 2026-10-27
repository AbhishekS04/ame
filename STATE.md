# Project State: Ame

## Current Status: v1.1.0 Released — Standardized Liquid Glass Slider & Animated Micro-Interaction Updates

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
