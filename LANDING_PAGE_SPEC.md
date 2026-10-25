# Ame: Single-Page Website & Landing Showcase Specification

> A modern, high-converting, single-page website design and content specification for **Ame** (Music Streaming for Android).

---

## 🎨 1. Concept & Brand Aesthetic

- **Visual Tone**: Dark, cinematic, neo-glassmorphism. Liquid glass buttons, progressive blur header, subtle ambient neon backdrops matching album covers.
- **Brand Identity**: **Ame-chan** mascot (glossy water droplet wearing studio monitor headphones with glowing audio driver rings) paired with the clean typographic wordmark **Ame**.
- **Hero Hook**: Bold, cheeky contrast against traditional paid streaming services.

---

## 🏛️ 2. Page Structure & Wireframe

```
┌────────────────────────────────────────────────────────┐
│ [💧 Ame]                 Features   Showcase   Credits  │
├────────────────────────────────────────────────────────┤
│                                                        │
│             ~~Subscribe to listen to music.~~          │
│                Music is free. Forever.                 │
│                                                        │
│   Zero ads. Zero subscriptions. Lossless audio quality,│
│   background playback, and live social listening.      │
│                                                        │
│            [ ⬇ Download for Android (v1.0.4) ]         │
│          No redirects · Direct APK · Free & Open Source│
│                                                        │
│               [ 📱 Interactive Device Mockup ]         │
│                                                        │
├────────────────────────────────────────────────────────┤
│ 📸 APP SHOWCASE (Curated Snapshots Carousel)           │
│  [Home & Quick Picks] [Social Profile] [Live Lyrics]   │
├────────────────────────────────────────────────────────┤
│ ✨ WHY AME? (Feature Grid)                             │
│  - Ad-Free Everywhere        - Audiophile DSP Engine   │
│  - Full Background Play      - Social Jam Sessions     │
│  - Data-Wipe Cloud Backup    - Offline Music Vault     │
├────────────────────────────────────────────────────────┤
│ 💡 INSPIRATIONS & SPECIAL THANKS (Open Source Roots)  │
│  - InnerTube & YouTube OSS   - ViMusic & RiMusic       │
│  - Jetpack Compose           - Supabase Architecture   │
├────────────────────────────────────────────────────────┤
│ 📦 FOOTER                                              │
│  GitHub Repo · Releases · MIT/GPL License · v1.0.4     │
└────────────────────────────────────────────────────────┘
```

---

## 📝 3. Detailed Section Copy & Content

### 🌟 Section 1: Navigation Bar
- **Logo**: `[💧 Ame]` (Mascot mark + text logo).
- **Navigation Links**:
  - `Features`
  - `Screenshots`
  - `Credits & Inspiration`
- **Right Action**: Liquid pill button: `[ GitHub ]` (linking to `https://github.com/AbhishekS04/ame`).

---

### 🚀 Section 2: Hero Section (The Strikethrough Hook)

#### Headlines:
```html
<h2 class="sub-strike">
  <span class="strikethrough">Subscribe to listen to music.</span>
</h2>
<h1 class="hero-title">
  Music is free. <span class="gradient-text">Forever.</span>
</h1>
```

#### Body Copy:
> **No paywalls. No 30-second unskippable ads. No background playback locked behind a paywall.**  
> Ame is an ultra-fast, native Android music streaming client powered by YouTube Music, engineered with audiophile DSP controls, liquid glass progressive blur, and real-time social listening sessions.

#### Call to Action (Direct APK Download):
- **Main Button**: `[ ⬇ Download for Android ]`
- **Button Microcopy**: `v1.0.4 · Direct APK (arm64-v8a) · Android 8.0+`
- **Direct Download Link**: 
  - Direct target: `https://github.com/AbhishekS04/ame/releases/latest/download/app-arm64-v8a-release.apk`
  - *Zero navigation required: Clicking instantly initiates the file download in the user's browser without opening GitHub.*

---

### 📸 Section 3: App Showcase (Interactive Snapshots)

Four curated feature cards displaying high-resolution frames of Ame in action:

| Screen | Caption | Highlight Details |
| :--- | :--- | :--- |
| **1. The Home Feed** | *Clean. Uncluttered. Yours.* | Time-of-day dynamic greeting, Magic UI progressive blur header, high-contrast mood filter chips, and 4-item snap column **Quick Picks**. |
| **2. Social Profile & Heatmap** | *Track your music journey.* | Full-bleed atmospheric cover banner, custom cloud avatars, follower counts, and a GitHub-style **52-week streak heatmap**. |
| **3. Synchronized Lyrics & Player** | *Every beat, word for word.* | Kinetic typography lyrics, album artwork radial lighting glow, and real-time DSP pitch/tempo shift. |
| **4. Live Listen Along (Jam)** | *Music is better with friends.* | Discord-style live playback presence with animated equalizer badges and one-tap **[Join Jam]** track synchronization. |

---

### ⚡ Section 4: Feature Breakdown

#### 1. 🚫 100% Ad-Free Experience
Enjoy uninterrupted streaming. No audio banner interruptions, no mid-song sponsorships, and no commercial tracking.

#### 2. 🔋 True Background & Lock-Screen Playback
Turn off your screen or switch between heavy games and apps. Ame maintains seamless, low-latency audio without battery draining.

#### 3. 🎚️ Studio DSP & Audiophile Processing
Powered by the high-performance Rust **Bungee DSP** audio engine:
- Pitch adjustment without tempo distortion.
- Tempo adjustment without chipmunk voice.
- Native 32-bit float audio pipeline.

#### 4. ☁️ Zero-Card Cloud Persistence (Powered by Supabase)
Wiped your phone or switched devices? Sign in with your Google account and your custom avatar, cover banner, bio, and public playlists restore instantly from the cloud.

#### 5. 🎧 Live Jam Sessions (Sub-50ms WebSockets)
Broadcast what you're playing in real-time. See what your friends are jamming to and sync your playback position with a single tap.

#### 6. 💾 Offline Vault
Download songs and albums straight to local storage for plane rides, road trips, and spotty network coverage.

---

### 🤝 Section 5: Developer Acknowledgments & Inspirations

> Ame was built with love by standing on the shoulders of open-source visionaries who proved that proprietary streaming apps don't have to dictate how we experience music.

- **The YouTube InnerTube Community**: Pioneers who reverse-engineered the private InnerTube protocol, providing clean, direct access to the world's largest audio catalog without Google API key rate limits.
- **ViMusic & RiMusic**: The legendary open-source Android projects that paved the way for lightweight YouTube Music frontends.
- **Jetpack Compose & Kotlin**: Enabling fluid 120 FPS animations, reactive state management, and edge-to-edge layouts.
- **Supabase**: Providing zero-credit-card, generous open-source cloud infrastructure (PostgreSQL, Storage buckets, and Realtime WebSockets).
- **Adam Glin (Phosphor Icons)**: For the icon set powering Ame's interface.

---

## 💻 4. Direct 1-Click APK Download Script (Vanilla JS)

To ensure the download button **never redirects to GitHub** and always downloads the freshest release automatically:

```html
<a id="download-btn" href="#" class="btn-download">
  <span class="icon">⬇</span>
  <div class="text">
    <span class="title">Download for Android</span>
    <span id="version-label" class="sub">Fetching latest release...</span>
  </div>
</a>

<script>
  (async function() {
    const REPO = "AbhishekS04/ame";
    const btn = document.getElementById("download-btn");
    const label = document.getElementById("version-label");

    // Fallback static URL if offline or rate-limited
    const fallbackUrl = `https://github.com/${REPO}/releases/latest/download/app-arm64-v8a-release.apk`;
    btn.href = fallbackUrl;

    try {
      const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`);
      if (!res.ok) throw new Error("API rate limit");
      const release = await res.json();
      
      // Find the 64-bit arm APK (or universal)
      const asset = release.assets.find(a => a.name.includes("arm64-v8a")) || 
                    release.assets.find(a => a.name.endsWith(".apk"));

      if (asset) {
        btn.href = asset.browser_download_url;
        label.textContent = `${release.tag_name} · Direct APK (${(asset.size / (1024 * 1024)).toFixed(1)} MB)`;
      } else {
        label.textContent = `${release.tag_name} · Direct APK`;
      }
    } catch (e) {
      label.textContent = "v1.0.4 · Direct APK (19 MB)";
    }
  })();
</script>
```

---

## 🛠️ 5. Recommended Implementation Options

1. **Option A: GitHub Pages (Zero-Cost & Free Hosting)**:
   - Create an `index.html` file in a `docs/` folder in the repository.
   - Host completely free on `https://abhisheks04.github.io/ame/`.
2. **Option B: Vercel / Cloudflare Pages**:
   - Single HTML/Tailwind CSS file deployed in 30 seconds with custom domain support.
