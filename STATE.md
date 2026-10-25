# Project State: Ame

## Current Status: Profile Authentication Hardening, Backdoor Elimination & Liquid Glass Interface

- **Security Lockdown & Backdoor Elimination (`ProfileScreen.kt`, `ProfileStore.kt`)**:
  - Completely eradicated the "Need help? Restore profile from Cloud" passwordless bypass and its dialog.
  - Locked authentication: users must log in with their handle and matching salted SHA-256 password (`ame_music_salt_v1_` + `handle` + `password`).
  - Accounts without passwords in Supabase enter a security lock state preventing unauthorized claiming.
  - Internal device restoration functions strictly by local device UUID (`stable_user_id`), never arbitrary handles.

- **Minimalist Liquid Glass Auth & Onboarding UI (`ProfileScreen.kt`, `GlassInputField`)**:
  - Redesigned with authentic Ame Liquid Glass surfaces (`Modifier.liquidGlass(shape = RoundedCornerShape(16.dp), ownBackdrop = backdrop)`).
  - High-contrast neutral tab switcher: `Ink` container fill and `pageGround()` text on selected tab; zero third-party green brand colors.
  - Circular Liquid Glass avatar picker with subtle specular rim border.
  - Replaced bulky OutlinedTextFields with sleek, borderless `GlassInputField` components built on `BasicTextField`.
  - Removed verbose paragraphs and clutter under each input field; kept only clean placeholders and inline validation cues.
  - Interactive tactile `LiquidButton` for "Create Profile" and "Log In" actions.

- **Liquid Glass Dialogs & Confirmation Sheets (`ProfileScreen.kt`, `ConfirmSheet.kt`)**:
  - Replaced default Material `AlertDialog` for logout confirmation with Ame's native `ConfirmSheet`.
  - Confirmation sheet renders full glass blur, continuous capsule actions, and destructive red specular wash.

- **Profile Authentication & Account Persistence Engine (`ProfileStore.kt`, `SocialModels.kt`)**:
  - `SocialProfile` data model updated with `@SerialName("password_hash") val passwordHash: String? = null`.
  - Added `PasswordHasher`: secure SHA-256 password hashing with application pepper (`ame_music_salt_v1_`) and per-handle salting (`username.lowercase()`).
  - `createProfile(displayName, username, password, bio, isPrivate, avatarUri)`:
    - Enforces minimum 6-character passwords.
    - Hashes password and persists to Supabase `profiles.password_hash` and local Encrypted SharedPreferences.
  - `loginProfile(username, password)`:
    - Queries Supabase for the handle (`username`).
    - Verifies password hash; auto-migrates existing accounts created prior to password protection.
    - Restores active user ID, display name, handle, bio, privacy settings, followers/following stats, and public playlists.
    - Downloads and caches cloud WebP avatar & banner locally to eliminate UI flashing.
  - `logoutProfile()`:
    - Clears all cached user credentials, personal bio, preferences, and local avatar/banner cache files.
    - Generates a fresh anonymous guest ID, resetting profile state back to onboarding.

- **Dual-Tab Onboarding & Auth Interface (`ProfileScreen.kt`, `CreateOrLoginProfileView`)**:
  - Features an elegant segmented pill selector: **[Create Account]** and **[Log In]**.
  - **Create Account Tab**:
    - Circular interactive avatar picker (with photo preview and camera badge).
    - Display Name (required, min 2 chars, max 30 chars).
    - Instagram-style `@username` handle with 350ms debounced live remote availability check.
    - Password field with show/hide password toggle (eye icon) and real-time length validation.
    - Music taste bio (optional, max 150 chars) and Private Account toggle.
    - Primary **[Create Profile]** button with animated progress indicator.
  - **Log In Tab**:
    - `@username` handle input with auto-formatting.
    - Password input with show/hide password toggle.
    - Error message banner for invalid credentials or non-existent accounts.
    - Primary **[Log In]** button with progress indicator.
    - Secondary fallback for cloud profile restoration.
  - Quick tab switching links: "Already have an Ame profile? Log In" and "Don't have an Ame profile? Create Account".

- **Profile Log Out Action**:
  - Added a dedicated, red-accented **Log Out of Ame** button inside the **Edit Profile** dialog.
  - Added a sleek liquid glass sign-out button in the floating top navigation bar when viewing your own profile.
  - Both trigger a confirmation dialog: *"You will be logged out of @handle. You can log back in anytime with your username and password."*

- **Home Screen Playful & Joyful Greetings (`HomeScreen.kt`)**:
  - Replaced rigid, repetitive time-of-day greetings ("Good morning", "Good afternoon", "Good evening").
  - Introduced warm, lively, conversational greetings: `"Howdy"`, `"Hola"`, `"Namaste"`, `"Hey there"`, `"Hello"`, `"Welcome back"`, dynamically paired with the user's name or handle (e.g. *"Howdy, Abhishek"* or *"Namaste, Abhishek"*).

- **Preview Mode Polish**:
  - Removed redundant top-right filled pill button ("Visitor Mode • Exit").
  - Preview mode exit action is cleanly handled inline beside the mock follow button.

- **Build, Signing & Device Deployment**:
  - Verification compilation `./gradlew compileReleaseKotlin` and unit tests `./gradlew testReleaseUnitTest` passed cleanly.
  - Release APK (`app-arm64-v8a-release.apk`) compiled and verified with official release key via `apksigner`.
  - Keystore MD5: `1023458fc11e2cde98fdefa6dc9e2d1c` (intact and unaltered).
  - Streamed and installed release APK onto Samsung Galaxy S24 FE (`RZCY41WALYB`) via ADB and launched `MainActivity` (PID 4773).

---

## 🔒 Keystore Integrity & Agent Protection
- Official release signing key: `ame-release.jks` (MD5: `1023458fc11e2cde98fdefa6dc9e2d1c`).
- Persistent agent instructions established in `AGENTS.md`.
- Strict rule: Under NO circumstances may any agent regenerate, overwrite, or commit keystores or signing secrets.
