# Implementation Plan: Apple Music-Grade Synchronization & Seamless Playback Engine

## Phase 2 Objectives

1. **Rapid-Skip Out-of-Order Artwork Race Elimination (`MainViewModel.kt`, `HeroBackdrop.kt`)**:
   - Introduce monotonic atomic request generation counter (`artRequestGeneration`) in `MainViewModel`.
   - Invalidate any in-flight coroutine or prefetch job whose generation doesn't match active track.
   - Decouple `HeroBackdrop` from stale bitmaps immediately upon `trackKey` change: dissolve to ambient background blur instead of holding old track's cover when `pending == true`.
   - Enforce strict queue-item ID matching to eliminate random banners appearing from 3–4 songs ago.

2. **Fast-Skip Progress Bar Zeroing & Timeline Sync (`PlayerState.kt`, `GlassProgressBar.kt`)**:
   - In `PlayerState.kt`, register `Player.Listener` for `onMediaItemTransition` and `onPositionDiscontinuity`.
   - Snap `position.longValue = 0L` the instant a track changes, eliminating the 150–250ms polling delay.
   - In `GlassProgressBar.kt`, track `mediaId` and clear `committedScrub` immediately on track switch.
   - Clamp progress calculations so `positionMs > durationMs` never renders at 50%.

3. **Smooth Seek-Bar Gliding & Drag Physics (`GlassProgressBar.kt`)**:
   - Separate raw target progress from rendered visual progress.
   - Keep 1:1 instantaneous response during active user touch drag.
   - Apply spring interpolation (`animateFloatAsState` with damping = 0.85f, stiffness = 380f) on programmatic seeks, skips, and track starts.

4. **3-Color Dynamic Buffered Chunk Indicator (`GlassProgressBar.kt`, `PlayerState.kt`, `PlayerScreen.kt`)**:
   - Track `bufferedPositionMs` from ExoPlayer (`player.bufferedPosition`).
   - Calculate `bufferedFraction = (bufferedPositionMs / durationMs).coerceIn(0f, 1f)`.
   - Render 3-layer progressive seek bar:
     1. Unbuffered liquid glass rail.
     2. 3-Color Buffered Chunk Gradient: Ambient Cover Tint -> Electric Ultraviolet/Cyan Shimmer -> Specular White Edge.
     3. Active Elapsed Track with pristine glowing white thumb.

5. **Lossless Audio Chunk Buffering & Next-Track Upstream Warmup (`PlaybackService.kt`, `YouTubePlayerFactory.kt`)**:
   - Verify `DefaultLoadControl` chunk buffer settings (`minBufferMs = 50s`, `maxBufferMs = 120s`, `bufferForPlaybackMs = 1.2s`).
   - Warm up next track's audio stream chunk in queue before current track ends.

6. **Zero-Flicker Handover for Motion Covers (`MotionCover.kt`, `HeroBackdrop.kt`)**:
   - Keep high-resolution static artwork visible behind `TextureView`.
   - Crossfade video only after `onRenderedFirstFrame` fires to eliminate black frame flashes.

7. **Verification & Testing**:
   - Build release APK (`./gradlew :app:assembleRelease`).
   - Verify keystore MD5 `1023458fc11e2cde98fdefa6dc9e2d1c`.
   - Deploy to Samsung Galaxy S24 FE (`RZCY41WALYB`) via ADB and verify.
