# 📋 Tomorrow's Action Plan: Ame Music

> **Status**: Queued for Next Development Session  
> **Target Version**: v1.0.5  
> **Focus**: Real Streak Engine, Pure Live Data, and Aesthetic Dynamic Profile Sharing.

---

## 🎯 Priority 1: Streak Engine Real-World Fix & Dummy Data Purge

- [ ] **1.1: Strict Scrobble Validation (No Skip-Spamming)**
  - Hook playback listener to verify a song has been actively played for **at least 30 seconds** before logging a `ListeningEvent`.
  - Prevent rapid forward-skipping from generating artificial listening activity.
- [ ] **1.2: Timezone-Aware Daily Streak Continuity**
  - Compute consecutive active days using midnight-to-midnight local boundaries.
  - Fix streak logic:
    - Listened today ➔ streak continues.
    - Listened yesterday but not yet today ➔ streak maintained at current count (awaiting today's song).
    - Missed yesterday ➔ streak resets to 1 upon playing a track.
- [ ] **1.3: Supabase Cloud Streak Synchronization**
  - Save the calculated streak days to the local Room database.
  - Automatically sync the verified streak count to the user's row in Supabase `profiles.streak_days`.
- [ ] **1.4: 100% Dummy Data Purge**
  - Completely remove the static `communityProfiles` mock list (`user_ame_official`, `user_curator_aurora`, `user_curator_zen`) in `SocialRepository.kt`.
  - Display strictly real, authenticated Supabase community users.
  - Add an aesthetic empty-state illustration when a search query yields no registered community members.

---

## ✨ Priority 2: Aesthetic Dynamic Profile Sharing

- [ ] **2.1: On-Device High-Res Story Card Generator (Bitmap Rendering)**
  - Build an off-screen Jetpack Compose canvas that renders a high-res (1080×1920 or 1200×630) cinematic sharing card:
    - User's actual profile avatar with ambient glow border.
    - Display name, `@username`, and bio.
    - Dynamic streak flame badge: `🔥 X Days Listening Streak`.
    - 52-week listening activity heatmap miniature.
    - Official Ame mascot watermark ("Curated on Ame Music").
  - Export canvas directly to a cached PNG bitmap file via `Android.graphics.Bitmap`.
- [ ] **2.2: Native Android Rich Share Sheet Integration**
  - Wire the **[Share Profile]** button in `ProfileScreen.kt`:
    - Launches `Intent.ACTION_SEND` with `image/png` URI via `FileProvider`.
    - Shares seamlessly to **Instagram Stories**, **WhatsApp**, **Telegram**, and **X / Twitter**.
    - Accompanied by a clean invitation caption: *"Listen along with me on Ame Music: @username"*.
- [ ] **2.3: Zero-Cost Web OpenGraph (OG) Link Preview (Optional Extension)**
  - Scaffold a lightweight preview page on GitHub Pages / Vercel with dynamic `<meta property="og:image">` tags so sharing the text link generates a preview card in messaging apps without requiring a custom domain.

---

## 🎧 Priority 3: Jam Sessions (Listen Along) Next Steps

- [ ] **3.1**: Add a dedicated **"Jam / Party"** action sheet in the Now Playing screen (showing active friends and listener count).
- [ ] **3.2**: Add an optional **"Live Friends Jamming"** carousel shelf on the Home Screen.
- [ ] **3.3**: Add a **"Private Jam"** toggle in Profile settings to mute presence broadcasting when desired.

---

## 🔐 Priority 4: Deferred Security Hardening

- [ ] Complete the findings and remediation checklist in [`SECURITY_AUDIT_TODO.md`](SECURITY_AUDIT_TODO.md).
