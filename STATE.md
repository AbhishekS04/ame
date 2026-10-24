# Project State: Ame

## Current Status: RELEASE v1.0.3
- **Mascot Logo Overhaul**:
  - Replaced legacy square-wave mark everywhere across the codebase with the official Ame music mascot: **"Ame-chan"** (a glossy liquid droplet wearing studio monitor headphones with glowing audio driver rings, happy smiling eyes `^ ^`, and glowing blush cheeks).
  - Generated full-density adaptive launcher icons (`ic_launcher_foreground.png`, `ic_launcher_background.png`, `ic_launcher.png`, `ic_launcher_round.png`) across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi`.
  - Updated vector drawables:
    - [`ic_app_mark.xml`](app/src/main/res/drawable/ic_app_mark.xml) — crisp silhouette with compound EvenOdd face cutouts for in-app UI, [`AppGlyph`](app/src/main/java/com/abhishek/ame/ui/components/Wordmark.kt), and all widgets (`widget_ame_compact`, `widget_ame_small`, `widget_ame_tile`, `widget_header`).
    - [`ic_launcher_monochrome.xml`](app/src/main/res/drawable/ic_launcher_monochrome.xml) — Material You dynamic themed icon.
    - [`ic_notification.xml`](app/src/main/res/drawable/ic_notification.xml) — clean media notification silhouette.
  - Replaced master source files in `app/icon-src/` with `ame-mascot-3d.png`, `ame-mascot-tile.png`, and `ame-mascot.svg`; purged legacy wave files (`icona.png`, `wave-glow.png`, `wave-square.svg`).
  - Updated icon builder script [`tools/icon.py`](tools/icon.py).
- **Default Onboarding Experience**:
  - Made YouTube Music (`BackendId.YOUTUBE_MUSIC`) the default out-of-the-box backend.
  - Fresh installs bypass the initial `BackendChoiceScreen` and Spotify setup screens, allowing users to land directly on the Home feed immediately.
  - Preserved full Spotify integration and setup in the codebase, switchable anytime via Settings.
- **Release Verification**:
  - Local release build (`./gradlew :app:assembleRelease`) tested and verified with `ame-release.jks`.
  - Signature checked via `apksigner`: SHA-256 `0028866986eb5baa5854bf0ce042be8f51c5c1d24677264da38568c00d43eedf`.
  - Ready for deployment via GitHub Releases for in-app self-updater (`Updater.kt`).

## 🔒 Keystore Integrity & Agent Protection
- Official release signing key: `ame-release.jks` (MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
- Persistent agent instructions established in `AGENTS.md`.
- Strict rule: Under NO circumstances may any agent regenerate, overwrite, or commit keystores or signing secrets.
