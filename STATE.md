# Project State: Ame

## Current Status: RELEASE v1.0.2
- Full deep rebrand from `Square` / `dev.lelonio.square` to `Ame` / `com.abhishek.ame` is complete and verified.
- Target device: Connected USB device Samsung Galaxy S24 FE (`RZCY41WALYB`, `arm64-v8a`).
- Signing: Configured with `ame-release.jks` and verified in GitHub Actions CI/CD with repository secrets.
- GitHub Repo: `https://github.com/AbhishekS04/ame` (Public).
- Release `v1.0.2`:
  - Clean top navbar with smooth downward Magic UI Progressive Blur, time-of-day greeting (uncropped, dynamically sized), and liquid glass circular profile button.
  - Neutral inverted filter chips (clean Ink/pageGround contrast, no Spotify green tint).
  - Strictly decluttered Home feed: Daily Discovery, 4-track Quick Picks snap carousel, New Releases, and My History / Play again.
  - Removed blocky featured release cards that overlapped chips.
  - Fully verified GitHub Actions release pipeline with `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`.
