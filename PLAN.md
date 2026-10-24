# Plan: Library Page Redesign (Vinyl Sleeve & Multi-Color Records)

## Overview
Implement the Library page matching `/run/media/abhishek/BBC/best-projects/ame/inspo-ame/library.png` and `/run/media/abhishek/BBC/best-projects/ame/inspo-ame/temp.svg`.
Features:
- 2-Column Vinyl Grid with 1.62 aspect ratio cards.
- Sleeve on left showing custom playlist artwork (or clean off-white paper texture for blank covers).
- Sliding-out vinyl record on right with realistic concentric groove rings, specular arc reflections, center label with circular artwork crop, and spindle hole.
- Dynamic vinyl disc coloring: curated palette of 16 vibrant vinyl editions (Ruby, Sapphire, Emerald, Gold, Purple, Orange, Pink, Cyan, etc.) deterministically mapped per playlist.
- Header matching `library.png`: "Library", item count, 3 action buttons (Recap/Stats, Add, View mode).
- Filter chips: Solid white active pill for "Everything", dark pills for "Playlists", "Albums", etc.
- Sort button: "↓↑ Recently opened" with popup menu.
- Search bar: Full-width rounded search field below sort row and above vinyl grid.
- Testing only (no commit or release without permission).

## Work Breakdown
- [ ] Step 1: Build `VinylCover.kt` with `VinylTheme` palette system, Canvas-based vinyl record rendering (grooves, shine, center label with spindle hole), and album sleeve.
- [ ] Step 2: Update `LibraryScreen.kt` to 2-column grid layout, relocate search bar below sort row, match header and filter chip styles to `library.png`.
- [ ] Step 3: Verify build via `./gradlew :app:assembleDebug` or `:app:assembleRelease`.
- [ ] Step 4: Self-audit & test on connected device if available or verify clean build.
