# On Hold: Ame Story Studio & Social Sharing Feature (`holdtot.md`)

> **Status**: ON HOLD  
> **Date Put on Hold**: October 28, 2026  
> **Author / Context**: Abhishek Singh / Ame Music Player  
> **Target Version**: Future release candidate (tested locally on Samsung Galaxy S24 FE)

---

## 📌 Executive Summary

The **Ame Story Studio** is an in-app social sharing engine that transforms any playing or catalog song into high-aesthetic, studio-grade visual posters, stickers, and 15-second video clips with sound. Designed specifically for **Instagram Stories** and **Snapchat**, it mimics and elevates the official Spotify and Apple Music social sharing experience with Ame's signature Liquid Glass language.

The concept and working prototype have been built, debugged, and verified locally. It is placed on hold here with all technical blueprints and file touchpoints documented for seamless resumption.

---

## 🎨 Design Philosophy & UX Standards

1. **Centerpiece Focus (Snapchat / Spotify Style)**:
   - Deep dark, distraction-free studio canvas (`#0A0B0E`) with zero bleed or interference from the underlying player.
   - Prominent, realistic floating centerpiece card with ambient drop shadows (`softShadow(24.dp)`) and specular rim lighting.
2. **True Full-Bleed Instagram Gradient Background**:
   - Rather than sending an ugly cropped box with pixelated blur, the system extracts rich, warm, and dark-muted tones from the song's album art using AndroidX `Palette`.
   - Sends a **100% transparent sticker** (`interactive_asset_uri`) to Instagram, accompanied by `top_background_color` and `bottom_background_color` hex codes. Instagram natively paints the entire 9:16 screen in a rich, smooth vertical gradient, floating the Ame card in the center.
3. **Snapchat Lenses & Palette Switcher**:
   - **Frame Styles**:
     - `Glass Card`: The flagship Spotify/Apple centerpiece card with high-res cover art, bold song title, artist, Ame mascot logo, and Lossless badge.
     - `Vinyl Flex`: Stylized 3D vinyl record with realistic grooves sliding out from a frosted glass album jacket.
     - `Minimal Pill`: Ultra-sleek floating frosted capsule pill with thumbnail art, typography, and brand watermark.
     - `Hero Cinema`: Full-bleed cinematic cover art poster with bottom progressive gradient and glass caption.
   - **Format / Aspect Ratios**:
     - `9:16 Story` (1080×1920) for Instagram Stories, Snapchat, and TikTok.
     - `1:1 Square` (1080×1080) for Instagram Feed, Threads, and Twitter/X.
     - `4:5 Portrait` (1080×1350) for Instagram vertical posts.
   - **Color Palettes**:
     - `Album Aura`: Dynamically resolved from album artwork.
     - `True Dark`: Pure AMOLED midnight black (`#000000`).
     - `Midnight`: Deep royal indigo and navy.
     - `Sunset`: Warm coral, magenta, and gold gradient.

---

## 🛠️ Architecture & File Manifest

### 1. New Dedicated Package (`app/src/main/java/com/abhishek/ame/ui/story/`)

| File | Purpose |
| :--- | :--- |
| [`StoryModels.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/story/StoryModels.kt) | Core data models and enums: `StoryTrackInfo`, `StoryFrameStyle`, `StoryFormat`, `StoryColorPalette`. |
| [`StoryBitmapGenerator.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/story/StoryBitmapGenerator.kt) | High-performance Canvas renderer producing 1080p bitmaps. Resolves true cover art palettes, renders frame styles, eliminates box artifacts, and outputs transparent stickers or full-bleed ambient posters. |
| [`StoryVideoGenerator.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/story/StoryVideoGenerator.kt) | 15-second H.264/AAC MP4 video story generator. Resolves audio dynamically from local storage or downloads the first 2MB of YouTube M4A stream on the fly. Interleaves video frames and audio packets in `MediaMuxer` with synchronized timestamps. |
| [`StoryShareHelper.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/story/StoryShareHelper.kt) | Intent dispatchers for `com.instagram.share.ADD_TO_STORY` (sticker URI, top/bottom hex colors, deep link), Snapchat (`com.snapchat.android`), and system share sheet. |
| [`AmeStoryStudio.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/story/AmeStoryStudio.kt) | Full Snapchat-inspired Compose UI dialog: dark immersive canvas, live ambient preview, lens chip switcher, palette chips, ratio toggle, and social dispatch bar (Snapchat Yellow, Instagram Gradient, With Sound, Save/Share). |

### 2. Integration Touchpoints in Existing Codebase

| File | Changes Made |
| :--- | :--- |
| [`app/src/main/AndroidManifest.xml`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/AndroidManifest.xml) | Added `<package android:name="com.snapchat.android" />` and `<package android:name="com.instagram.android" />` to `<queries>`, along with `com.instagram.share.ADD_TO_STORY` intent queries. |
| [`app/src/main/res/xml/report_paths.xml`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/res/xml/report_paths.xml) | Added `<cache-path name="stories" path="stories/" />` to expose FileProvider URIs for shared story stickers and video clips. |
| [`app/src/main/res/values/strings.xml`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/res/values/strings.xml) | Added strings for Story Studio, frame styles, formats, palettes, Snapchat, and Instagram actions. |
| [`app/src/main/java/com/abhishek/ame/data/DownloadStore.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/data/DownloadStore.kt) | Added `fun audioFileFor(trackUri: String): File?` to retrieve offline audio paths. |
| [`app/src/main/java/com/abhishek/ame/ui/player/PlayerScreen.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/player/PlayerScreen.kt) | Made `RoundGlassButton` internal; added 40dp glass share button (`PhosphorIcons.Regular.ShareNetwork`) in the Player title capsule row. |
| [`app/src/main/java/com/abhishek/ame/ui/AmeApp.kt`](file:///run/media/abhishek/BBC/best-projects/ame/app/src/main/java/com/abhishek/ame/ui/AmeApp.kt) | Added `storyTrack` state; wired up `onShareStory` from `PlayerScreen` and "Share Story" action in `TrackSheet`; rendered `AmeStoryStudio`. |

---

## 🔑 Crucial Technical Insights & Lessons Learned

### 1. Instagram Story Intent Background Color
- **The Issue**: Sending a bitmap that already contains a cropped background causes Instagram to treat the whole thing as a floating sticker, leaving black bars around it.
- **The Fix**: `interactive_asset_uri` must be a **transparent sticker** containing only the card and its drop shadow. Passing `top_background_color` and `bottom_background_color` extras prompts Instagram's native camera engine to generate the full-screen 9:16 vertical gradient.

### 2. MediaMuxer Synchronization for Stories with Sound
- **The Issue**: Android's `MediaMuxer` enforces strictly monotonic timestamps across all tracks. Writing all 450 video frames first and then writing audio frames causes a timestamp clash (`timestamp 0 < 15,000,000`), leading to silent videos or crashes on Exynos/Snapdragon chips.
- **The Fix**: Video frames and AAC audio samples must be **interleaved in lockstep** based on `presentationTimeUs` so that timestamps advance chronologically together.

### 3. Dynamic Audio Extraction for Streamed Tracks
- If a track has not been downloaded, `StoryVideoGenerator` uses `YouTubeStreams.info(id)` to query the stream info, extracts the `.m4a` (AAC) stream URL, and downloads the first 2MB via `OkHttpClient` with HTTP `Range: bytes=0-2097151`. This takes ~300ms and yields ~90 seconds of valid AAC audio without requiring the user to wait for a full track download.

---

## 🚀 Re-Activation Checklist (When Resuming)

When returning to this feature:
1. [ ] Check if `app/src/main/java/com/abhishek/ame/ui/story/` files are present.
2. [ ] Review UI design to see if interactive draggable canvas or lyrics sharing should be incorporated.
3. [ ] Verify compilation with `./gradlew compileReleaseKotlin`.
4. [ ] Test on target device via `adb install -r ...`.
5. [ ] Gather user feedback on social story formats and finalize for release.
