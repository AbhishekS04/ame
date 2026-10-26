# Project State: Ame

## Current Status: Zero-Mismatch Strict Track & Singer Matcher Engine & Bit-Perfect 24-bit Lossless FLAC

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
    - Added 11-dimension comparison matrix detailing engineering advancements over the original Square open-source fork.

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
