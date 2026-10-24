# Agent Instructions: Ame

This document establishes persistent instructions and security guardrails for all AI agents working on the Ame codebase.

---

## 🔒 1. Critical Keystore Guardrail (Zero Leakage & Zero Regeneration)

The official Android release signing key for Ame is permanently established:
- **Local Keystore**: `ame-release.jks`
- **Keystore Config**: `keystore.properties`
- **Integrity MD5 Checksum**: `1023458fc11e2cde98fdefa6dc9e2d1c`

### Strict Security Rules for All Agents:
1. **NEVER Regenerate or Delete**: Under no circumstances should an agent delete, overwrite, re-generate, or replace `ame-release.jks` or `keystore.properties`. Google Play Protect trust reputation is bound to this key; regenerating it resets device trust to zero and triggers malware scan warnings for users.
2. **NEVER Commit or Push Secrets**: Keystores (`*.jks`, `*.keystore`) and `keystore.properties` are strictly ignored by `.gitignore`. Agents must NEVER stage, commit, or push these files or any plaintext passwords/base64 strings to GitHub or any public branch.
3. **Preserve Integrity**: Before any release build, verify that `ame-release.jks` is intact:
   ```bash
   md5sum ame-release.jks
   # Expected output: 1023458fc11e2cde98fdefa6dc9e2d1c  ame-release.jks
   ```

---

## 🚀 2. Release & Build Protocol

### Local Release Verification:
- `./gradlew :app:assembleRelease` automatically signs release APKs when `keystore.properties` exists in the project root.
- After building, verify the signature via `apksigner`:
  ```bash
  $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-arm64-v8a-release.apk
  ```

### Automated CI/CD (GitHub Actions):
- Releases are automatically compiled, signed, and published via `.github/workflows/release.yml` when a version tag (`v*.*.*`) is pushed.
- CI/CD uses GitHub Actions repository secrets:
  - `SIGNING_KEYSTORE_BASE64`
  - `SIGNING_STORE_PASSWORD`
  - `SIGNING_KEY_ALIAS`
  - `SIGNING_KEY_PASSWORD`
- All releases published through GitHub Releases are immediately discoverable by the app's internal self-updater (`Updater.kt`).

---

## 🎨 3. Design & Architecture Standards

1. **Home Screen**:
   - Clean, uncluttered layout: Dynamic time-of-day greeting (uncropped), liquid glass profile button, and Magic UI Progressive Blur (`ProgressiveBlur.kt`).
   - Filter chips: Neutral high-contrast inversion (`Ink` fill and `pageGround` text when selected). No third-party green brand colors.
   - Curated shelves: Daily Discovery, Quick Picks (4-item snap column carousel), New Releases, and Play Again / My History.
2. **Permissions**:
   - Never commit or tag without explicit user authorization.
   - Maintain documentation integrity in `STATE.md`.
