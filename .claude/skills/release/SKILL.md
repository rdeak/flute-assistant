---
name: release
description: Use when cutting or checking a Flute Assistant release, bumping fluteAssistant.versionName, changing the release or listing GitHub workflows, signing secrets, the Play upload, store graphics or Play screenshots.
---

# Release

Versions live in `gradle.properties`. Pushing a new `fluteAssistant.versionName` to `main` makes
`.github/workflows/release.yml` tag `v<name>` and publish a GitHub Release with the signed bundle,
APK and mapping file; every push to `main` runs the full check (`testDebugUnitTest lintRelease
bundleRelease assembleRelease`, signed; no instrumented tests). Three jobs (build, then play,
then release; only a release run keeps a 7-day artifact for the later two) so "Re-run failed
jobs" repeats only what failed. `tools/release.sh` derives the versionCode from the name (major * 10000 +
minor * 100 + patch), checks both outputs carry the upload certificate (a build without the
secrets comes out unsigned on purpose, so the check is what stops it being published), writes
the notes from the git log, and `tools/release.sh secrets` sets the four signing secrets from
`keystore.properties` without showing them. A push that changes `docs/release/play-listing/` (the
listing text in `text/<lang>/`, icon, feature graphic, screenshots) runs the separate
`.github/workflows/listing.yml`, whose `tools/play-listing.sh` replaces the Play store listing in
English and Croatian and saves it unsent: "Send for review" in Play Console makes it public (the
service account needs "Manage store presence"; the workflow shares the release workflow's
concurrency group because two open Play edits of one account invalidate each other). A release also uploads the bundle to the Play
internal testing track through Workload Identity Federation (no key file; repository variables
`PLAY_WORKLOAD_IDENTITY_PROVIDER` and `PLAY_SERVICE_ACCOUNT`, both required); closed test and
production stay manual. Release signing reads the
`FLUTE_ASSISTANT_STORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` env vars, then the
gitignored `keystore.properties`; without either the release build is produced unsigned. Never
read or print the keystore password. Release builds use whole-app R8 (a package-scoped
optimisation crashed at startup inside androidx.startup). Process, Play Console answers and the
Secrets Manager backup recipe are in `docs/release/`. Launcher PNGs and store graphics come from
`java -Djava.awt.headless=true tools/IconGen.java /tmp/icons`. Store screenshots (one per tab, en and hr,
padded to 9:16) come from `tools/play-screenshots.sh` with the emulator up and a debug APK built.
