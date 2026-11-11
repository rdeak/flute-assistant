# Releasing Flute Assistant

Application id `hr.bertlab.fluteassistant`. Play requires an Android App Bundle (AAB) signed
with the **upload key**; Google holds the app signing key (Play App Signing).

## 1. Version

Edit `gradle.properties`:

```
fluteAssistant.versionName=1.0.0  # shown to people; a new value pushed to main is a release
fluteAssistant.versionCode=1      # local builds only: CI derives the code from the name
```

CI passes `-PfluteAssistant.versionCode=<major * 10000 + minor * 100 + patch>`, so 1.0.0 uploads
as code 10000 and 1.1.0 as 10100, always increasing as long as the name increases (keep minor and
patch below 100). `tools/release.sh version` prints what CI will use.

## 2. Signing credentials

`app/build.gradle.kts` signs the release build when it finds credentials, in this order:

1. Environment variables `FLUTE_ASSISTANT_STORE_FILE`, `FLUTE_ASSISTANT_STORE_PASSWORD`,
   `FLUTE_ASSISTANT_KEY_ALIAS` (default `upload`), `FLUTE_ASSISTANT_KEY_PASSWORD`
   (defaults to the store password).
2. `keystore.properties` in the project root (gitignored): `storeFile`, `storePassword`,
   `keyAlias`, `keyPassword`.

Without either, the release build is produced **unsigned** so the project still compiles anywhere.
CI gets the environment variables from repository secrets (section 3).

The upload keystore lives at `keystore/flute-assistant-upload.jks` (PKCS12, RSA 2048, alias
`upload`, valid until 2054). Both the keystore directory and `keystore.properties` are
gitignored. Never commit them and never paste the password into a chat or a ticket.

### Backup in AWS Secrets Manager

Keep the passwords and the keystore file itself in the secret
`flute-assistant/upload-keystore` in `eu-west-1` as JSON:

```
{"storePassword": "...", "keyPassword": "...", "keyAlias": "upload",
 "keystoreFileName": "flute-assistant-upload.jks", "keystoreBase64": "..."}
```

Create or refresh the backup from a machine that has the files. The password is read from the
properties file and never typed or displayed:

```sh
python3 - <<'PY' > /tmp/upload-keystore.json
import base64, json, pathlib
props = dict(l.split("=", 1) for l in pathlib.Path("keystore.properties").read_text().splitlines()
             if "=" in l and not l.startswith("#"))
print(json.dumps({"storePassword": props["storePassword"], "keyPassword": props["keyPassword"],
                  "keyAlias": props["keyAlias"], "keystoreFileName": "flute-assistant-upload.jks",
                  "keystoreBase64": base64.b64encode(pathlib.Path(props["storeFile"]).read_bytes()).decode()}))
PY
aws secretsmanager create-secret --region eu-west-1 --name flute-assistant/upload-keystore \
  --description "Android upload keystore for Flute Assistant: passwords, alias and the PKCS12 file as base64. Static credential, no rotation." \
  --secret-string file:///tmp/upload-keystore.json \
  --tags Key=Application,Value=FluteAssistant Key=Purpose,Value=android-upload-keystore
rm /tmp/upload-keystore.json
```

Use `put-secret-value` instead of `create-secret` to refresh an existing secret.

Restore on a new machine:

```sh
aws secretsmanager get-secret-value --region eu-west-1 --secret-id flute-assistant/upload-keystore \
  --query SecretString --output text | python3 -c '
import base64, json, os, pathlib, sys
s = json.load(sys.stdin); os.umask(0o077); pathlib.Path("keystore").mkdir(exist_ok=True)
pathlib.Path("keystore/" + s["keystoreFileName"]).write_bytes(base64.b64decode(s["keystoreBase64"]))
lines = ["storeFile=keystore/" + s["keystoreFileName"], "storePassword=" + s["storePassword"],
         "keyAlias=" + s["keyAlias"], "keyPassword=" + s["keyPassword"]]
pathlib.Path("keystore.properties").write_text("\n".join(lines) + "\n")'
```

With a secret-resolving launcher such as `asm-exec`, skip the properties file and inject the
environment variables at build time instead:

```sh
asm-exec \
  FLUTE_ASSISTANT_STORE_FILE=keystore/flute-assistant-upload.jks \
  FLUTE_ASSISTANT_STORE_PASSWORD='{{resolve:secretsmanager:flute-assistant/upload-keystore:SecretString:storePassword}}' \
  FLUTE_ASSISTANT_KEY_PASSWORD='{{resolve:secretsmanager:flute-assistant/upload-keystore:SecretString:keyPassword}}' \
  -- ./gradlew bundleRelease
```

If the upload key is ever lost, Play Console lets you request an upload key reset
(Setup > App signing); the app signing key held by Google is unaffected.

## 3. Release from CI

`.github/workflows/release.yml` runs on every push to `main` (design:
`docs/superpowers/specs/2026-10-23-release-workflow-design.md`). Each run does the full check
(`testDebugUnitTest lintRelease bundleRelease assembleRelease`) signed with the upload key, checks
that both the APK and the bundle carry the upload certificate (`tools/release.sh verify`; a build
without the secrets comes out unsigned and must never be published). When
`fluteAssistant.versionName` has no `v<name>` tag yet, the run also uploads the bundle to Play,
tags the commit and publishes GitHub Release `v<name>` with the bundle, the APK and the mapping
file and notes listing the commits since the previous release. A release is therefore: bump the
version line, push to `main`. A push that does not bump just runs the check and keeps nothing; a
failed run is read from its log or reproduced locally with the same Gradle command.

The run has three jobs: build, then the Play upload, then the GitHub Release. If the Play or
release job fails, use "Re-run failed jobs" (`gh run rerun <run-id> --failed`): it repeats only
that job and the ones after it and reuses the build, whose files a release run keeps for 7 days.

### Secrets, set once

The workflow signs with four repository secrets: `FLUTE_ASSISTANT_KEYSTORE_BASE64` (the PKCS12
file), `FLUTE_ASSISTANT_STORE_PASSWORD`, `FLUTE_ASSISTANT_KEY_ALIAS` and
`FLUTE_ASSISTANT_KEY_PASSWORD`. From the machine that has `keystore.properties` and the keystore,
with `gh` logged in to the repository:

```sh
tools/release.sh secrets   # pipes the values into gh secret set; nothing is shown or typed
gh secret list
```

After an upload key reset, run it again and update `UPLOAD_CERT_SHA256` in `tools/release.sh`
with the new certificate's SHA-256 from `apksigner verify --print-certs`.

### Redoing a release

An existing tag is skipped. To publish a version again, delete the release together with its tag
(`gh release delete v1.0.1 --cleanup-tag`) and re-run the workflow or push again. Play never
accepts the same version code twice, so after a Play upload bump the patch version instead.

### Play upload from CI

When a release is published, the same run first uploads the bundle to the **internal testing**
track. There is no key file: GitHub's identity token is traded for credentials of the service
account `github-play-upload@flute-assistent-ci.iam.gserviceaccount.com` (Workload Identity
Federation; Google Cloud warns against downloaded keys, so never use Add key on that account).
The Play API only works once a first bundle has been uploaded by hand, which is done.

One-time setup:

1. Play Console, users and permissions: the account above is invited with permission to release
   to testing tracks. It needs no Google Cloud roles for Play.
2. Google Cloud, project `flute-assistent-ci`:

```sh
PROJECT_ID=flute-assistent-ci
REPO=rdeak/flute-assistant
SA=github-play-upload@${PROJECT_ID}.iam.gserviceaccount.com

gcloud services enable androidpublisher.googleapis.com --project="$PROJECT_ID"

gcloud iam workload-identity-pools create github \
  --project="$PROJECT_ID" --location=global --display-name="GitHub Actions"

# Only this repository's main branch is admitted: forks of the public repo get nothing.
gcloud iam workload-identity-pools providers create-oidc flute-assistant \
  --project="$PROJECT_ID" --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'"

POOL=$(gcloud iam workload-identity-pools describe github \
  --project="$PROJECT_ID" --location=global --format="value(name)")
gcloud iam service-accounts add-iam-policy-binding "$SA" --project="$PROJECT_ID" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/$POOL/attribute.repository/$REPO"

# The provider's resource name goes into the repository variable below. The project number of
# flute-assistent-ci is 213106687720, so it is
# projects/213106687720/locations/global/workloadIdentityPools/github/providers/flute-assistant
gcloud iam workload-identity-pools providers describe flute-assistant \
  --project="$PROJECT_ID" --location=global --workload-identity-pool=github \
  --format="value(name)"
```

3. Repository variables (not secrets, neither value is sensitive), run from the repository:

```sh
gh variable set PLAY_SERVICE_ACCOUNT --body "github-play-upload@flute-assistent-ci.iam.gserviceaccount.com"
gh variable set PLAY_WORKLOAD_IDENTITY_PROVIDER --body "projects/213106687720/locations/global/workloadIdentityPools/github/providers/flute-assistant"
```

Both variables are required: if either is missing, a release run fails at the Google step before
anything is tagged. The upload goes before the tag, so a Google failure leaves no tag and the
failed jobs can be re-run; if Play accepted the bundle but the GitHub release failed, re-run the
failed jobs too: only the release job runs again, so Play is never sent the same version code
twice.

### What CI does not do

Moving a build from internal testing to a closed test or production stays in Play Console.
Instrumented tests do not run in CI.

## 4. Build and verify by hand

```sh
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab (upload this)
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (sideload testing)
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The certificate SHA-256 printed by `apksigner` must match the upload certificate shown in
Play Console > Setup > App signing after the first upload.

Before uploading, run the full check: `./gradlew testDebugUnitTest lintRelease bundleRelease`.

## 5. Play Console

See `play-console.md` for the store listing, data safety, permission and content rating
answers, and `privacy-policy.md` for the policy text (`privacy-policy.hr.md` is its Croatian
translation). The website in `site/` publishes both, at `/privacy/` and `/hr/privacy/`, and keeps
the old address `/release/privacy-policy.html` that the app and the Play Console link to
(`site/README.md`).

## 6. Launcher icon, store graphics and screenshots

The icon is the Android robot playing a flute, with one note in the air, on gold (chosen on
2026-11-07 from three robot drafts: the head over a flute, this one, and a big head with the flute
held across it like a grin): one vector picture (`res/drawable/ic_app_art.xml`, also shown in the app's top
bar) inset into a vector adaptive icon (`res/drawable/ic_launcher_*.xml`, with a one-colour version
for themed icons) plus legacy PNGs for Android 7 (`res/mipmap-*/`). The robot is Google's, modified
under CC BY 3.0: the attribution line in `NOTICE` is shown in the app's credits and must stay in
the Play listing's full description (`play-console.md`).
`tools/IconGen.java` renders the PNGs, the 512 px Play icon and the 1024x500 feature graphic from
the same geometry (run it from the repository root: the feature graphic uses the app's Nunito):

```sh
java -Djava.awt.headless=true tools/IconGen.java /tmp/icons
```

Phone screenshots come from the emulator: with the AVD running (`-no-audio`, see CLAUDE.md) and
a debug APK built, `tools/play-screenshots.sh` drives the four tabs in English and Croatian and
writes one 1080x1920 JPEG per tab (Play asks for 9:16) into `docs/release/play-listing/screenshots/`.
Re-run it after any visible change before uploading.

Tablet screenshots come from the same script: `tools/play-screenshots.sh tablet` fakes a tablet on the
phone AVD at exactly Play's ratios, so nothing is padded, and writes four shots per language into
`docs/release/play-listing/screenshots-tablet/`: `7-inch/<lang>/` (upright, 1296x2304) and `10-inch/<lang>/`
(sideways, 2880x1620). Play asks for at least
4 per tablet slot, 9:16 or 16:9, and no added text. Nothing uploads the listing: in Play Console open the
store listing of each language, put `7-inch/` in the 7-inch tablet slot and `10-inch/` in the 10-inch slot,
save, and send it for review. No Google page read on 2026-11-06 says tablet shots raise ranking.
