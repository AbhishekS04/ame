# Rebranding Plan: Square/lelonio -> Ame/AbhishekS04

## Chunk 1: Settings Page, Visible UI & Diagnostic Traces
- [x] Fix Settings Page footer: "Square 1.0.0" -> "Ame 1.0.0" (`SettingsScreen.kt`)
- [x] Fix Report generation: "Square 1.0.0" -> "Ame 1.0.0", file name "ame-report-..." (`Report.kt`)
- [x] Fix User-Agents in lyrics/services: LRCLIB (`Ame (https://github.com/AbhishekS04/ame)`), LyricsOVH, BetterLyrics, SponsorBlock
- [x] Update notification channel IDs: `ame_login`, `ame_update`
- [x] Update SharedPreferences names: `ame_tokens`, `ame_keys`, `ame_youtube_account`, `ame_widget`, `ame_widget_playlists`
- [x] Rebrand internal log tags: `AmePlayer`, `AmeYT`, `AmeYouTube`, etc.
- [x] Rebrand Action / Intent string constants: `com.abhishek.ame.*`

## Chunk 2: Resources & XML Layouts
- [x] Rebrand theme: `Theme.Square` -> `Theme.Ame` in `themes.xml` and `AndroidManifest.xml`
- [x] Rename widget layouts and XML configs:
  - `widget_square.xml` -> `widget_ame.xml`
  - `widget_square_tile.xml` -> `widget_ame_tile.xml`
  - `square_widget_info.xml` -> `ame_widget_info.xml`
  - `square_widget_tile_info.xml` -> `ame_widget_tile_info.xml`
- [x] Update XML descriptions in `locales_config.xml`, `automotive_app_desc.xml`

## Chunk 3: Core Component Renaming
- [x] Rename `SquareApplication.kt` -> `AmeApplication.kt` (class `AmeApplication`)
- [x] Rename `SquareApp.kt` -> `AmeApp.kt` (composable `AmeApp`)
- [x] Rename `SquareWidget.kt` -> `AmeWidget.kt` (class `AmeWidget`)
- [x] Update references across all UI components and `AndroidManifest.xml`

## Chunk 4: Kotlin Package Namespace Migration (`dev.lelonio.square` -> `com.abhishek.ame`)
- [x] Move directory tree `app/src/main/java/dev/lelonio/square` -> `app/src/main/java/com/abhishek/ame`
- [x] Update `namespace` in `app/build.gradle.kts` to `com.abhishek.ame`
- [x] Update package declarations in all 287 Kotlin files
- [x] Update import statements across all files
- [x] Update `baseline-prof.txt` rules to `Lcom/abhishek/ame/...`

## Chunk 5: Native Rust & JNI Bridge Rebranding (`libsquarecore.so` -> `libamecore.so`)
- [x] Update `native/Cargo.toml` crate name to `amecore`
- [x] Update JNI exports in `native/src/ffi.rs` to `Java_com_abhishek_ame_nativecore_NativeBridge_*`
- [x] Update `NativeBridge.kt` to load `amecore`
- [x] Rebuild native libraries for `arm64-v8a` and `armeabi-v7a` via `cargo ndk`

## Chunk 6: Build Verification, Device Installation & Release Update
- [x] Clean and assemble signed release APKs (`./gradlew clean :app:assembleRelease`)
- [x] Verify APK signature with `ame-release.jks`
- [x] Install release APK to connected phone (`RZCY41WALYB`) via ADB
- [x] Launch on device and verify logs (no crashes, clean startup)
- [x] Re-upload updated release APKs to GitHub Release `v1.0.0`
