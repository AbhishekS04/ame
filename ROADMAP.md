# Ame: Social & Profile Roadmap (100% Free & Zero-Card)

> **Status**: Planning & Conceptual Stage  
> **Testing Strategy**: Local-only compilation (`./gradlew assembleDebug`) and physical device testing via ADB across 2–3 test phones.  
> **Git Policy**: **NO git pushes** to remote branches/tags until all phases are tested and explicitly approved by the user.  
> **Cost & Card Constraint**: **₹0.00 / month forever & ZERO CREDIT CARD REQUIRED**.

---

## 🏛️ 1. The All-in-One Zero-Card Architecture (Supabase)

No GCP billing setup. No Cloudflare R2 card requirement. No Cloudinary disaster. 

Everything runs through **Supabase Free Tier** (which requires **NO credit card** to create or run):

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 AME ANDROID CLIENT                     │
                  └───────┬──────────────┬──────────────┬───────────┬──────┘
                          │              │              │           │
                          ▼              ▼              ▼           ▼
                   [ Supabase ]   [ Supabase ]   [ Supabase ] [ YouTube ]
                      Auth          Postgres       Storage     InnerTube
                   (Google/1-Tap)  (Profiles/      (Avatars/    (Audio)
                                    Streaks)        Banners)
                          │              │              │           │
                          ▼              ▼              ▼           ▼
                     Zero Passwords, 500 MB Text,   1 GB WebP   100% Free
                     Native Identity Holds 1M users  Images     CDN Streams
                     (No Card)      (No Card)      (No Card)   (Untouched)
```

| Service | Component | Quota (Free Tier - No Card Needed) | Real-World Scale |
| :--- | :--- | :--- | :--- |
| **Supabase Auth** | Google OAuth & Guest Auth | 50,000 Monthly Active Users | More than enough for massive community testing. |
| **Supabase Database** | PostgreSQL | 500 MB Database storage | Text only: holds **1,000,000+** profile & streak records. |
| **Supabase Storage** | S3-Compatible Buckets (`avatars`, `banners`) | 1 GB Storage + 2 GB Egress/month | With Ame's local WebP compression, holds **15,000–30,000** photos. |
| **Supabase Realtime** | WebSockets | 200 Concurrent Connections | Sub-50ms latency for Discord-style Listen Along. |
| **YouTube Music** | InnerTube Audio Engine | Unlimited Streams | Google CDN streams music at 0 server load. |

---

## 🔎 2. Spotify-Style Unified Search Architecture

The Search tab on the bottom bar will be the **one and only search hub**:
* Filter Chips: `All` · `Songs` · `Artists` · `Playlists` · `Albums` · **`Profiles`** (New!)
* **How it behaves**:
  * If user types `"Abhishek"` while on `Songs` or `All`: YouTube Music search results display.
  * If user taps **`Profiles`**: Ame queries the Supabase `profiles` table:
    ```sql
    SELECT id, username, display_name, avatar_url, streak_count, is_listening_to 
    FROM profiles 
    WHERE username ILIKE '%Abhishek%' OR display_name ILIKE '%Abhishek%' 
    LIMIT 25;
    ```
  * Results show: Round avatar, Display Name, `@handle`, current listening badge, and a **[Follow]** button.
  * Tapping any profile smoothly navigates to that person's dedicated Profile Page!

---

## ⚡ 3. Atomic Micro-Chunks Execution Plan

Broken down into small, rapid, bite-sized tasks for fast iteration and instant phone testing.

---

### 📦 CHUNK 1: Navigation Decoupling & Shell (Zero Network)
* [ ] **1.1**: Update bottom bar in `AmeApp.kt` — convert bottom profile tab to a dedicated **Settings** tab (`PhosphorIcons.Fill.GearSix` -> `Routes.SETTINGS`).
* [ ] **1.2**: Wire top-right app bar avatar button to new route `Routes.PROFILE`.
* [ ] **1.3**: Create baseline `ProfileScreen.kt` container with back navigation and animated transitions.
* *Verification*: Compile debug APK, test on Phone 1, verify smooth navigation between Home, Settings, and Profile.

---

### 📦 CHUNK 2: Search Tab "Profiles" Filter Chip
* [ ] **2.1**: In `SearchScreen.kt`, add `PROFILES` to the search `Kind` enum and filter chip row.
* [ ] **2.2**: Build `PersonSearchResultItem.kt` (round avatar, display name, `@username`, streak pill).
* [ ] **2.3**: Wire click listener on person search result to navigate to `Routes.PROFILE` with user ID.
* *Verification*: Verify typing query and switching chips in Search Screen displays mock person items properly.

---

### 📦 CHUNK 3: Local Offline Streak Tracking Engine (Room DB)
* [ ] **3.1**: Create Room DB table `listening_scrobble (track_id, timestamp, duration_played)`.
* [ ] **3.2**: Hook into playback events: log scrobble only when a song is played for >30 seconds (no skip-spamming).
* [ ] **3.3**: Build hardware-accelerated Jetpack Compose Canvas heatmap (52 weeks × 7 days) with 4-level accent glow.
* *Verification*: Play songs on phone, verify boxes light up on the heatmap with 0 ms lag and 120 FPS rendering.

---

### 📦 CHUNK 4: Profile Page UI (Spotify-Style)
*(Awaiting user's inspiration reference — ready to skin to exact specifications)*
* [ ] **4.1**: Hero banner with dynamic liquid blur scrim & gradient.
* [ ] **4.2**: Overlapping profile avatar with glowing border.
* [ ] **4.3**: User handle (`@username`), bio, follower/following counter.
* [ ] **4.4**: Streak heatmap widget embed.
* [ ] **4.5**: Public playlists grid + Top monthly songs list.
* *Verification*: Verify layout aesthetics, typography, and scrolling fluidity on device.

---

### 📦 CHUNK 5: Supabase Setup (100% Free, Zero-Card)
* [ ] **5.1**: Add official Supabase Kotlin dependencies (`supabase-kt` auth, postgrest, storage, realtime).
* [ ] **5.2**: Configure Supabase client singleton with project URL and public anon key.
* [ ] **5.3**: Implement Google One-Tap Sign-In with Supabase Auth.
* [ ] **5.4**: Seamless bridge to YouTube session cookies (zero manual typing).
* *Verification*: Test 1-tap sign-in on test phone; confirm user row is created in Postgres without touching a credit card.

---

### 📦 CHUNK 6: Avatar & Banner Uploads (Supabase Storage)
* [ ] **6.1**: Implement Android PhotoPicker for avatar and banner selection.
* [ ] **6.2**: On-device WebP compression: Avatars compressed to 256×256 (~20 KB), Banners to 1080×360 (~80 KB).
* [ ] **6.3**: Upload to Supabase `avatars` / `banners` storage buckets with public read policies.
* [ ] **6.4**: Coil disk and memory caching for instant offline image rendering.
* *Verification*: Pick photo on Phone 1, verify instant WebP upload and display.

---

### 📦 CHUNK 7: Social Friend Search & Cloud Profile Sync
* [x] **7.1**: Connect `SearchScreen.kt` `PROFILES` chip to Supabase Postgres search query.
* [x] **7.2**: Implement Follow / Unfollow database logic and persistent follower/following counts.
* [x] **7.3**: View other users' public profiles, playlists, and streak maps from Supabase.
* [x] **7.4**: Automatic cloud profile restoration on app data wipe (re-links PFP, banner, bio, and public playlists).
* *Verification*: Search profiles from Search Tab; view visitor profiles with privacy shields; verify data persists after wiping app data.

---

### ⏸️ CHUNK 8: Universal Playlist Sharing & Deep Linking (Under Consideration / On Hold)
> **Note**: Placed on hold per user request until a custom domain (e.g. `ame.app`) is acquired.
* [ ] **8.1**: Add "Share Playlist" action generating: `https://ame.app/playlist?id=PLxxxxxx&by=username`.
* [ ] **8.2**: Register Android `intent-filter` in `AndroidManifest.xml`.
* [ ] **8.3**: Build shared playlist preview sheet with **[+ Save to Library]** button.

---

### 📦 CHUNK 9: Discord-Style Live "Listen Along" (Jam Session)
* [x] **9.1**: Supabase Realtime WebSocket presence channel for broadcast (`JamEngine.kt`).
* [x] **9.2**: In-app "Listening to..." live equalizer badge on friend cards & profile view.
* [x] **9.3**: "Join Jam" button to sync track playback instantly with friends.
* *Verification*: Broadcast playing track via Supabase Realtime; friend's profile card shows live song with one-tap Join Jam.

---

## 📱 Testing Protocol (Local Phone Flashing Only)

```bash
# Step 1: Build local APK without touching git
./gradlew :app:assembleDebug

# Step 2: Install directly to connected test phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
