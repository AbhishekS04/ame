# Square UI Redesign: Actionable Roadmap & Checklist

This document outlines the recommended step-by-step sequence for redesigning Square's interface. Each phase is self-contained so you can iteratively test visual updates on your device without breaking audio playback or state synchronization.

---

## Recommended Execution Order

### Phase 1: Design Tokens & Foundations
Establish your visual identity, palette, typography, and base components first.
- [ ] **Design Tokens & Color Palette**:
  - Edit [`Theme.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/theme/Theme.kt)
  - Configure background, surface, primary accent, and contrast rules.
  - Decide whether to keep dynamic album art tinting (`ArtworkColor.kt`) or use a custom static/monochrome scheme.
- [ ] **Typography & Fonts**:
  - Edit [`Fonts.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/theme/Fonts.kt)
  - Update `FontFamily` (e.g., custom Google fonts, weights, line heights, letter spacing).
- [ ] **Shapes & Surface Treatments**:
  - Review `ui/glass/` (Liquid glass, blurs, and continuous rounded rectangles).
  - Either customize the glass shaders or substitute standard clean elevation/card surfaces.

---

### Phase 2: Navigation Shell & Mini Player
The persistent bottom layer that anchors user navigation across the entire app.
- [ ] **Floating Bottom Tab Bar**:
  - Edit [`SquareBottomBar.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/glass/SquareBottomBar.kt) and [`FloatingTabBar.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/glass/floatingtabbar/FloatingTabBar.kt)
  - Adjust icon styling, active indicators, margins, and elevation.
- [ ] **Mini Player (Collapsed State)**:
  - Edit [`FloatingMiniPlayer.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/FloatingMiniPlayer.kt) and [`MiniPlayer.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/MiniPlayer.kt)
  - Redesign progress bar/indicator, track title marquee, play/pause and next button layouts.
- [ ] **Sheet Transition Physics**:
  - Edit [`NowPlayingSheet.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/NowPlayingSheet.kt)
  - Fine-tune drag damping, snap thresholds, and spring animations.

---

### Phase 3: Now Playing Screen (Primary Audio Stage)
The centerpiece of any music player application.
- [ ] **Header & Album Cover Stage**:
  - Edit [`PlayerScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt)
  - Customize album art sizing, rounded corners, drop shadows, and canvas video integration.
- [ ] **Track Metadata & Action Rows**:
  - Title, artist, album clickable links, like/favorite button, and context menu actions.
- [ ] **Scrubber & Transport Controls**:
  - Customize [`WaveformSeekBar.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/glass/WaveformSeekBar.kt) or seekbar styling.
  - Redesign primary play/pause hero button, previous/next, shuffle, and repeat buttons.
- [ ] **Sub-Panel Selector Tabs**:
  - Edit [`PlayerPanel.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/PlayerPanel.kt)
  - Update the panel switchers for Queue, Lyrics, Audio Effects, and Device Picker.

---

### Phase 4: Lyrics, Queue, & Audio Effects
The interactive companion views inside the player.
- [ ] **Synchronized Lyrics**:
  - Edit [`LyricsView.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/LyricsView.kt)
  - Customize active lyric line scaling, opacity fade on past/future lines, and auto-scroll behavior.
- [ ] **Up Next / Playback Queue**:
  - Edit [`QueueList.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/player/QueueList.kt)
  - Reorder handles, swipe-to-dismiss visual cues, and track item rows.
- [ ] **Equalizer & Sound Effects**:
  - Edit [`EqualizerPage.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/settings/EqualizerPage.kt)
  - Sliders for pitch/speed (Bungee), reverb presets, and EQ frequency bands.

---

### Phase 5: Content Discovery & Library
Content presentation and user catalog browsing.
- [ ] **Home & Recommended Shelves**:
  - Edit [`HomeScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt)
  - Hero banner cards, grid layouts for quick-listen items, and horizontal carousel shelves.
- [ ] **Playlist & Album Detail Views**:
  - Edit [`PlaylistScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt)
  - Collapsing header artwork, play/shuffle hero action bar, track row numbering, and duration formatting.
- [ ] **Library Tab**:
  - Edit [`LibraryScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/library/LibraryScreen.kt)
  - Filter chips (Playlists, Albums, Artists, Downloaded/Local), grid vs list toggles.
- [ ] **Search Experience**:
  - Edit [`SearchScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/search/SearchScreen.kt)
  - Instant search input bar, category filter tags, search history pills, and live result cards.

---

### Phase 6: Settings & Secondary Screens
Polish and configuration views.
- [ ] **Settings Screen**:
  - Edit [`SettingsScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt)
  - Grouped setting cards, switch styles, and dialog pickers.
- [ ] **Browse & Radio**:
  - Edit [`NewScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/browse/NewScreen.kt) and [`RadioScreen.kt`](file:///run/media/abhishek/BBC/best-projects/Square/app/src/main/java/dev/lelonio/square/ui/browse/RadioScreen.kt)
