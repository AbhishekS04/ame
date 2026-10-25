# Plan: User Profile Onboarding, Authentication & Social Experience

## Overview
Enable full account authentication and session restoration so users never lose their profile upon reinstalling or clearing app data. Introduce dual-tab **[Create Profile]** and **[Log In]** onboarding, password verification with cryptographic salting, streamlined profile logout, warm conversational greetings on the Home screen ("Howdy", "Hola", "Namaste"), and clean preview mode action controls.

---

## Technical Versions & Stack
- Android SDK: Compile 35, Target 35, Min 26
- Kotlin: 2.3.21 / Compose BOM: 2026.06.01
- Supabase-kt: 3.8.0 (Postgrest, Storage, Realtime)
- Jetpack Compose: Material3, Phosphor Icons
- Cryptography: SHA-256 with application pepper & handle salting

---

## Phased Work Breakdown

- [x] **Phase 1: Database & Data Layer Hardening (`SocialModels.kt`, `ProfileStore.kt`, `SocialRepository.kt`)**
  - [x] Add `@Transient` to non-persisted client fields (`isFollowing`, `publicPlaylists`) in `SocialProfile`.
  - [x] Remove hardcoded fallbacks and mock accounts.
  - [x] Add atomic `createProfile` and remote debounced availability check.

- [x] **Phase 2: Zero-Flicker Social Navigation & Dynamic Followers Sheet**
  - [x] Pre-seed visited profile to eliminate navigation flickering.
  - [x] Dynamic wrap-content height for `FollowersListSheet` to remove bottom void.
  - [x] Compress avatar and banners to WebP with auto-healing cloud sync.

- [ ] **Phase 3: Database Schema & Authentication Engine (`SocialModels.kt`, `ProfileStore.kt`)**
  - [ ] Add `password_hash` column to `SocialProfile` data model and Supabase table.
  - [ ] Implement `PasswordHasher` (SHA-256 with application pepper and username salting).
  - [ ] Update `createProfile` to accept and hash `password`.
  - [ ] Implement `loginProfile(username, password): Result<SocialProfile>` to query Supabase, verify password hash, and restore all session state and cached assets.
  - [ ] Implement `logoutProfile()` to clear local preferences, wipe cached images, and reset to fresh unauthenticated state.

- [ ] **Phase 4: ViewModel & App Integration (`MainViewModel.kt`, `AmeApp.kt`)**
  - [ ] Expose `loginProfile` and `logoutProfile` in `MainViewModel`.
  - [ ] Connect `loginProfile` and `logoutProfile` callbacks in `AmeApp.kt` to `ProfileScreen`.

- [ ] **Phase 5: UI Polish: Dual-Tab Auth, Logout & Preview Mode Clean-up (`ProfileScreen.kt`)**
  - [ ] Replace single create view with dual-tab **[Create Account]** and **[Log In]** segmented selector.
  - [ ] **Log In View**: Handle input, password input with show/hide toggle, error banner, and primary action button.
  - [ ] **Create Account View**: Avatar picker, Display Name, Username (with live remote check), Password (min 6 chars), Bio, Private Account switch, and primary action button.
  - [ ] Add **Log Out** button: inside `EditProfileDialog` and in the floating top navigation bar with confirmation dialog.
  - [ ] Remove top-right "Visitor Mode • Exit" pill (preview exit is handled inline beside the follow button).

- [ ] **Phase 6: Playful & Joyful Greetings (`HomeScreen.kt`)**
  - [ ] Replace time-of-day greetings ("Good morning", etc.) with lively, conversational greetings: `"Howdy"`, `"Hola"`, `"Namaste"`, `"Hey there"`, `"Hello"`, `"Welcome back"`.
  - [ ] Combine greeting with user's first name/handle for a warm, personalized app experience.

- [ ] **Phase 7: Verification, Compilation & Device Deployment**
  - [ ] Compile Release APK via `./gradlew :app:assembleRelease`.
  - [ ] Verify keystore integrity (`ame-release.jks`, MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
  - [ ] Stream and install onto Samsung Galaxy S24 FE via ADB and verify in Logcat.
