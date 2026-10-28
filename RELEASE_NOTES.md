> [!WARNING]
> **Beta Channel Advisory Warning**: This is a pre-release Beta build (v1.2.0-beta.1) containing cutting-edge experimental features, early UI updates, and test pipelines before official public rollout. It may contain bugs, crashes, or unpolished behavior. You can switch back to the Stable channel at any time in Settings > Updates.

## Highlights
- **Funky Badass App Icon Switcher**: Choose from 8 custom handcrafted app icons under Settings > App > Appearance (Default, True Dark, Orbit, Blue Drip, Galactic, Gothic, Vinyl Orbit, Ame Stars) with dynamic launcher switching and seamless restart.
- **On-Device Rust Recommendation Engine**: Integrated high-performance native core (libamecore.so) powering personalized Quick Picks using implicit feedback modeling, repeat listen bonuses, and BaRT contextual multi-armed bandits.
- **Curated Home Hub Reorganization**: Favorite artists relocated right under From the Community and strictly limited to explicitly starred voices (no random channel injection). Fresh trending songs and full-length album shelves directly from YouTube Music charts.
- **Community Playlist Library Bookmarking**: Added Save to Library option inside the three-dot playlist menu for community playlists, plus direct thumbnail bookmarking.

## Fixes
- **Like & Dislike Sync**: Fixed like button state desynchronization on tracks played from Quick Picks and playlists. Like button reliably syncs with YouTube Music and your local Liked library with pure high-contrast red heart feedback.
- **Playlist Validation & Feedback**: Fixed issue where songs appeared as Already in playlist when they were not. Added clear feedback when adding/removing songs to and from user playlists.
- **Autoplay & Endless Mix Continuity**: Fixed endless autoplay halting after the final track; properly fetches continuous matching tracks from the same genre and artist space without placeholder cards or false playlist marks.
- **Artist Profile Picture Sync**: Fixed starred artist avatars falling back to album covers; now accurately retrieves and displays the artist's high-resolution profile picture.

## Improvements
- **Expanded Quick Picks Carousel**: Expanded carousel from 16 to 40 personalized songs (10 snap columns of 4 songs each) with dynamic reason badges (e.g. Because you love {artist}, On heavy repeat).
- **App Notifications Architecture**: Added infrastructure to notify users when new updates drop or new music arrives.
- **OLED & Adaptive Icons**: Full density adaptive launcher mipmaps (mdpi to xxxhdpi) with 76dp safe zones for all Android OEM launchers.
