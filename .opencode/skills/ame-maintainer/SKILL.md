---
name: ame-maintainer
description: Strict safety, signing, release, and documentation rules for work on the Ame Android repository
---

# Ame Maintainer Rules

Apply these rules to every task in this repository. Read `AGENTS.md` first when
the task involves signing, releases, permissions, security, or documentation.

## Protect signing credentials

- Never delete, overwrite, regenerate, replace, print, commit, or upload
  `ame-release.jks` or `keystore.properties`.
- Never expose keystore passwords, aliases, private keys, base64 keystores, or
  OAuth/API credentials in source, logs, diffs, chat, commits, or releases.
- Preserve the release keystore integrity checksum:
  `1023458fc11e2cde98fdefa6dc9e2d1c`.
- Keep local signing files ignored and permission `600`.
- Treat any suspected signing-key exposure as an incident: stop release work,
  avoid rotating the key automatically, and report the exposure clearly.

## Release and APK rules

- Do not build or publish a new APK unless the user explicitly asks for a
  release build or release preparation.
- Before a release build, verify the keystore checksum and confirm that
  `keystore.properties` points to the established keystore.
- Never distribute a debug-signed or unsigned APK as a release.
- Release CI must verify the APK certificate against the pinned Ame certificate
  fingerprint in `.github/workflows/release.yml`.
- After a release build, verify every APK with `apksigner --print-certs` and
  confirm the certificate identity before publishing.
- Never change the signing key to address Play Protect warnings. Changing it
  breaks updates and signing continuity; Play Protect reputation and false
  positives must be handled separately.
- Do not claim a build, signature check, Play Protect result, or release passed
  unless it was actually verified.

## Security rules

- Treat the Supabase anon key as public configuration, not as a secret; enforce
  security with Supabase Auth and RLS instead.
- Do not expose password hashes through public profile queries.
- Do not trust client-generated IDs for ownership or authorization.
- Keep deferred findings in `SECURITY_AUDIT_TODO.md` until explicitly fixed.
- For security changes, inspect callers, backend policies, manifests, release
  workflows, and storage behavior before editing.
- Do not weaken, remove, or bypass security checks merely to make a build pass.

## Safe coding and repository rules

- Preserve user changes and ignored local files; never use destructive reset,
  checkout, clean, or recursive deletion commands without explicit permission.
- Investigate existing patterns and tests before changing behavior.
- Keep changes focused and update related documentation when behavior or release
  policy changes.
- Run the narrowest relevant checks after edits. If Android tooling or another
  dependency is unavailable, report that limitation instead of claiming success.
- Never commit or push without explicit user authorization.
- Do not put credentials or private machine memory into repository files.

## Release checklist

1. Read `AGENTS.md`, `STATE.md`, and the relevant TODO/security documentation.
2. Confirm the user explicitly requested release preparation.
3. Verify keystore integrity and local file permissions without printing secrets.
4. Build the release APK with the established key.
5. Verify all APK signatures and the pinned certificate fingerprint.
6. Check version/tag consistency and inspect the final artifacts.
7. Only then ask for or perform any user-authorized commit, tag, push, or release.
