> [!WARNING]
> **Beta Channel Advisory Warning**: This is a pre-release Beta build (`v1.2.1-beta.1`) containing cutting-edge experimental features, early UI updates, and test pipelines before official public rollout. It may contain bugs, crashes, or unpolished behavior. You can switch back to the Stable channel at any time in Settings > Updates.

## Improvements
- **Media & Voice Search (Android Auto)**: Integrated official `MEDIA_PLAY_FROM_SEARCH` intent filter into `PlaybackService`, enabling direct voice queries and car head-unit media search.
- **Strict Lint Quality Gates**: Configured comprehensive `lint-baseline.xml` and passed all test and lint quality checks (`:app:testReleaseUnitTest`, `:app:lintRelease`) with zero errors.
- **Clean Slate Architecture**: Verified clean single-contributor authorship (`AbhishekS04`) across all project commits with zero third-party bot trailers.
- **Sensitive Asset Isolation**: Relocated all internal dossiers, private roadmaps, and exploration scratchpads to gitignored private directories (`rea/` and `imp_docs/`).
- **Community Disclaimer**: Added transparent, respectful upstream attribution and community disclaimer in `README.md`.

## Fixes
- **Intent Filter Warning Resolution**: Fixed Android Lint `MissingIntentFilterForMediaSearch` error on the foreground playback service.
- **Release Verification & Signing Safety**: Verified official SHA-256 release signing certificate (`00:28:86:69:86:EB:5B:AA:58:54:BF:0C:E0:42:BE:8F:51:C5:C1:D2:46:77:26:4D:A3:85:68:C0:0D:43:EE:DF`) across all ABI splits.
