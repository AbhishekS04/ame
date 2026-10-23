# Square Android Music Player: UI Customization Guide

This guide details the architecture, design tokens, screen mappings, and safe extension boundaries of **Square** so you can completely redesign its user interface while preserving its native playback engine, multi-backend support (Spotify & YouTube Music), and DSP audio pipeline.

---

## 1. Architectural Overview & Boundaries

Square is structured in four decoupled layers:

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│   Jetpack Compose (Material3 + Custom Liquid Glass Design)  │
│   • SquareApp.kt (NavHost + Floating Bar + NowPlayingSheet) │
│   • Screens: Home, Browse/Radio, Library, Player, Search   │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlows / Callbacks
┌──────────────────────────────▼──────────────────────────────┐
│                    ViewModel & Data Layer                   │
│   • MainViewModel.kt, Data classes, Repositories, Caches    │
│   • Dual backend catalogs: Spotify + YouTube Music          │
└──────────────────────────────┬──────────────────────────────┘
                               │ MediaController Binder / Actions
┌──────────────────────────────▼──────────────────────────────┐
│                   Playback Engine (Kotlin)                  │
│   • PlaybackService (MediaSessionService, Media3 Player)    │
│   • ExoPlayer + CrossfadePlayer + FFmpeg software decoder   │
│   • AudioEffects (Equalizer, Reverb, Pitch/Speed)           │
└──────────────────────────────┬──────────────────────────────┘
                               │ JNI
┌──────────────────────────────▼──────────────────────────────┐
│                  Native Core (Rust & C++)                   │
│   • native/ (Rust librespot core -> libsquarecore.so)       │
│   • native-dsp/bungee (C++ time-stretch / pitch shifting)   │
└─────────────────────────────────────────────────────────────┘
```

### Safe Boundary Rules
- **Safe to Modify**: Everything under `app/src/main/java/dev/lelonio/square/ui/`. You can change styling, layouts, animations, typography, colors, component hierarchies, and navigation presentation without risking audio pipeline instability.
- **Do Not Touch for UI Work**:
  - `dev.lelonio.square.playback.*` (`PlaybackService`, `CrossfadePlayer`, `AudioEffects`, `YouTubePlayerFactory`)
  - `dev.lelonio.square.backend.*` (Spotify session management, InnerTube extractor bridge)
  - `native/` and `app/src/main/cpp/` (Rust librespot core and C++ Bungee DSP)

---

## 2. Screen-to-File Mapping

| Screen / Feature | Primary Source Files | Role & Description |
| :--- | :--- | :--- |
| **App Root & Navigation** | [`SquareApp.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/SquareApp.kt) | Top-level scaffold, `NavHost`, tab switching, bottom bar positioning, `NowPlayingSheet` coordination. |
| **Activity Shell** | [`MainActivity.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/MainActivity.kt) | Edge-to-edge system bars, `MediaController` lifecycle, PiP handling. |
| **Home / Feed** | [`ui/home/HomeScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt) | Welcome banner, listening history ("Play Again"), algorithmic feed, YouTube Music recommendation shelves, friend activity. |
| **Browse / New & Radio** | [`ui/browse/NewScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/browse/NewScreen.kt)<br>[`ui/browse/RadioScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/browse/RadioScreen.kt) | New album releases, daily rotating artist/track radio seeds. |
| **Search** | [`ui/search/SearchScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/search/SearchScreen.kt) | Search text input, real-time query suggestions, search history trail, categorized results (songs, albums, artists, playlists). |
| **Library & Playlists** | [`ui/library/LibraryScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/library/LibraryScreen.kt)<br>[`ui/library/PlaylistScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt) | User playlists, liked songs, local audio library, artist discographies, album detail headers, pin/sort actions. |
| **Now Playing Screen** | [`ui/player/PlayerScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt)<br>[`ui/player/PlayerPanel.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/PlayerPanel.kt) | Expanded player: dynamic ambient artwork background, gestures, transport buttons (play, pause, next, prev, shuffle, repeat), sub-panels. |
| **Mini Player** | [`ui/player/MiniPlayer.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/MiniPlayer.kt)<br>[`ui/player/FloatingMiniPlayer.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/FloatingMiniPlayer.kt) | Collapsed pill player docked above the bottom bar with scrub progress and drag-to-expand gesture. |
| **Player Sheet Host** | [`ui/player/NowPlayingSheet.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/NowPlayingSheet.kt) | Physics-driven vertical swipe sheet transitioning smoothly between MiniPlayer and PlayerScreen. |
| **Lyrics & Karaoke** | [`ui/player/LyricsView.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/LyricsView.kt)<br>[`ui/player/ClassicLyricsView.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/ClassicLyricsView.kt) | Time-synced lyrics with active line highlighting, translation toggles, and karaoke pitch dial. |
| **Queue & History** | [`ui/player/QueueList.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/QueueList.kt) | Drag-to-reorder queue, swipe-to-remove, autoplay toggles, and undo snackbar. |
| **Audio Controls & DSP** | [`ui/player/Waveform.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/Waveform.kt)<br>[`ui/settings/EqualizerPage.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/settings/EqualizerPage.kt) | Waveform seek bar, Bungee speed/pitch sliders, multi-band graphic equalizer, and reverb controls. |
| **Settings** | [`ui/settings/SettingsScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt)<br>[`ui/settings/GlassSettings.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/settings/GlassSettings.kt) | Audio quality, backend selection, cover art providers, cache controls, and UI glass toggles. |

---

## 3. Theme, Design Tokens, & Styling

The design system is located in `app/src/main/java/dev/lelonio/square/ui/theme/`:

1. **Colors & Scheme** ([`Theme.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/theme/Theme.kt)):
   - `SquareTheme`: Wraps `MaterialTheme` with dynamic dark/light color schemes.
   - Dynamic Album Seed: The theme dynamically extracts the dominant artwork color (`seed`) and lifts/deepens it for contrast.
   - Key color tokens:
     - `Ink`: Dynamic text and icon color (light on dark theme, dark on light theme).
     - `InkDim`: Secondary text (`Ink.copy(alpha = 0.66f)`).
     - `DarkFilm` / `LightFilm`: Translucent surface layers (`Color.White.copy(alpha = 0.10f)` vs `0.55f`).
     - `DefaultAccent`: Fallback purple (`0xFF7C5CE6`).
2. **Typography** ([`Fonts.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/theme/Fonts.kt)):
   - Uses `Inter` variable font loaded from `res/font/inter.ttf` across weights 300 to 900.
   - Supports user toggle to system font via `PreferencesStore.systemFont`.
3. **Artwork Extraction** ([`ArtworkColor.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/theme/ArtworkColor.kt)):
   - Extracts palette and background tone from Coil image requests for dynamic background tinting.
4. **Liquid Glass & Backdrops** (`ui/glass/`):
   - Custom backdrop blur, specular highlights, and continuous rounded rect shapes (squircle curvature).
   - If you want a flat or minimal Material3 design, you can bypass the glass shaders and replace them with standard `Surface` or `Card` modifiers.

---

## 4. How to Build & Install

### Prerequisites
- JDK 17+ (configured via Gradle JVM).
- Android SDK at `/home/abhishek/Android/Sdk` (`compileSdk = 37`, `minSdk = 26`).
- Rust toolchain (`rustc`, `cargo`, `cargo-ndk`) with targets:
  - `aarch64-linux-android`
  - `armv7-linux-androideabi`

### Build Tasks
Run from project root (`/run/media/abhishek/BBC/best-projects/Square`):

```bash
# Build the native Rust core (runs automatically when needed):
./gradlew :app:cargoBuild

# Build all debug APKs (arm64, armeabi-v7a, and universal):
./gradlew :app:assembleDebug
```

Generated APKs location:
`app/build/outputs/apk/debug/`
- `app-arm64-v8a-debug.apk` (Recommended for modern phones)
- `app-universal-debug.apk` (All ABIs included)

### Install on Device via ADB
Connect your phone with USB Debugging enabled, then run:

```bash
/home/abhishek/Android/Sdk/platform-tools/adb devices
/home/abhishek/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```
