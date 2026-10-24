# Project State: Ame

## Current Status: RELEASE v1.0.4 (Social Discovery, Cloud Profiles & Pixel-Perfect UI)
- **Playlist Artwork Fix**:
  - Eliminated image stretching/squashing: calculates `minOf(width, height)`, center-crops bitmap to 1:1 square, scales down cleanly to 1080×1080 JPEG at 92% quality.
  - Directly syncs custom playlist cover art to YouTube Music via InnerTube (zero Supabase overhead for playlist covers).
- **Supabase Cloud Profile & Data Wipe Resilience**:
  - Custom profile pictures (PFP) and banner covers (BG) are compressed locally and uploaded to Supabase Storage (`avatars` and `banners` buckets).
  - Profile metadata (Display Name, `@username`, bio, privacy status, follower counts, and public playlists) synced to Supabase Postgres.
  - Instant username availability detection: live debounced verification prevents duplicate usernames.
  - Automatic cloud restoration on app data wipe: logging back into YouTube account seamlessly retrieves avatar, banner, bio, and public playlists from Supabase.
- **Social Discovery & Live Listen Along**:
  - Added `PROFILES` filter chip to Search tab with dynamic community discovery.
  - Discord-style live "Listen Along" (Jam Session) via Supabase Realtime WebSockets (`JamEngine.kt`) broadcasting sub-50ms playback presence with animated equalizer badges and one-tap **[Join Jam]** sync.
- **Chunk 8 Status**:
  - Placed on hold / under consideration pending custom domain setup (`ame.app`).
- **Release Verification**:
  - Local release build (`./gradlew :app:assembleRelease`) tested and verified with `ame-release.jks`.
  - Signature checked via `apksigner`: SHA-256 `0028866986eb5baa5854bf0ce042be8f51c5c1d24677264da38568c00d43eedf`.
  - Keystore MD5: `1023458fc11e2cde98fdefa6dc9e2d1c` (intact).

## 🔒 Keystore Integrity & Agent Protection
- Official release signing key: `ame-release.jks` (MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
- Persistent agent instructions established in `AGENTS.md`.
- Strict rule: Under NO circumstances may any agent regenerate, overwrite, or commit keystores or signing secrets.
