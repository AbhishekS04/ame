# How I work on Ame

One question decides everything at the end of a session:

> **Is this done and tested?**

- No → push the branch, walk away.
- Yes → open a PR, merge it.

Nothing is ever committed straight to `main`. Every change lives on a
short-lived branch with a funny animal name.

## Every session

Start fresh, on a new branch:

```bash
git switch main
git pull
git switch -c feature/funky-flamingo
```

Commit whenever you want a save point, push whenever you want a backup:

```bash
git add -A
git commit -m "what I changed"
git push -u origin feature/funky-flamingo
```

Push as often as you like. The branch is yours, nobody sees it unless
you open a PR.

## When the work is done and tested

Before opening the PR, run the same checks CI will run:

```bash
./gradlew :app:testDevUnitTest :app:lintDev :app:assembleDev
```

All green? Open the PR and read your own diff once:

```bash
gh pr create --fill
```

CI builds, tests, and lints the PR. The merge button stays disabled
until all three pass. Merge (squash), then clean up:

```bash
git switch main
git pull
git branch -d feature/funky-flamingo
```

## Release day (once per version)

1. On main, bump `versionCode` (+1) and `versionName` in
   `app/build.gradle.kts`. Use `1.2.1` for stable, `1.3.0-beta.1`
   for beta.
2. Write the release notes into `RELEASE_NOTES.md`.
3. Push that as a small PR and merge it, same as any change.
4. Tag and push the tag — this is the only manual trigger:

   ```bash
   git switch main
   git pull
   git tag v1.2.1
   git push origin v1.2.1
   ```

5. CI builds the signed APKs, checks the certificate, runs the tests,
   and creates a **draft** release with the APKs attached.
6. Open the draft, read it once, press **Publish**.

The in-app updater delivers the release to phones. Beta versions
(anything with a `-` in the name) are flagged prerelease
automatically.

## What CI now does

- **Every PR:** runs the unit tests, runs lint, builds the dev APK.
  Anything red blocks the merge.
- **Every tag:** runs tests and lint on the release variant, builds
  the signed APKs, verifies the signing certificate against the
  pinned SHA-256, and creates the draft release.
- **Publishing a release is always your button press.** CI never
  publishes on its own.

## One-time setup (if not done yet)

In GitHub → Settings → Branches → protection rule on `main`:
require a pull request (0 approvals), require the status checks to
pass, and include administrators.
