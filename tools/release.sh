#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.
# Release helpers behind .github/workflows/release.yml; every subcommand also runs locally.
#
#   tools/release.sh version          name, code, tag and whether v<name> is still untagged, as key=value lines
#   tools/release.sh verify APK AAB   fail unless both are signed with the upload certificate
#   tools/release.sh notes NAME CODE  release notes (markdown) on stdout
#   tools/release.sh secrets          set the four CI signing secrets from keystore.properties (local, once)
#
# The version code is derived from fluteAssistant.versionName in gradle.properties:
# major * 10000 + minor * 100 + patch, so 1.0.0 is 10000 and 1.1.0 is 10100. It rises with every
# bump, needs nothing remembered, and the build is reproducible from the tag. Minor and patch
# must stay below 100. Design: docs/superpowers/specs/2026-10-23-release-workflow-design.md.
set -eu
cd "$(dirname "$0")/.." || exit 2

# SHA-256 of the upload certificate (CN=Flute Assistant Upload Key). It is public: every release
# APK carries it. Change it only after an upload key reset in Play Console.
UPLOAD_CERT_SHA256=ae720c86489b69c7187fb6ddbb75f81c48c18539e4ba8b1e10a81339b2fa9c75

prop() { # prop FILE KEY: the value of KEY=... in a properties file, empty when absent
  sed -n "s/^$2=//p" "$1" | head -1 | tr -d '\r'
}

cmd_version() {
  local name major minor patch code release
  name=$(prop gradle.properties fluteAssistant.versionName | tr -d '[:space:]')
  if ! [[ "$name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "fluteAssistant.versionName must be major.minor.patch, got '$name'" >&2
    exit 1
  fi
  major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[3]}
  if (( 10#$minor > 99 || 10#$patch > 99 )); then
    echo "minor and patch must stay below 100 so the version code keeps increasing: $name" >&2
    exit 1
  fi
  code=$(( 10#$major * 10000 + 10#$minor * 100 + 10#$patch ))
  # Ask the remote, not the local clone: it is the tag's home and the release's race condition.
  set +e
  git ls-remote --exit-code --tags origin "refs/tags/v$name" >/dev/null 2>&1
  local status=$?
  set -e
  case $status in
    0) release=false ;;
    2) release=true ;;
    *) echo "could not list the tags of origin (git ls-remote exit $status)" >&2; exit 1 ;;
  esac
  printf 'name=%s\ncode=%s\ntag=v%s\nrelease=%s\n' "$name" "$code" "$name" "$release"
}

check_cert() { # check_cert FILE DIGEST [DETAIL]
  if [ "$2" = "$UPLOAD_CERT_SHA256" ]; then
    echo "$1: signed with the upload certificate"
  else
    echo "$1: NOT signed with the upload certificate (got '${2:-nothing}')" >&2
    # Certificates are public, so showing what the tool said is safe and makes a red run readable.
    [ -z "${3:-}" ] || printf '%s\n' "$3" | sed 's/^/  | /' >&2
    exit 1
  fi
}

# Every SHA-256 digest in apksigner's output as lowercase hex, one per line. It matches on the
# digest, not on the label in front of it: the label differs between build-tools versions and
# for a rotated key ("Signer #1 ..." versus "Signer (minSdkVersion=...) ...").
cert_digests() {
  grep -i 'sha-\{0,1\}256' | tr -d ':' | grep -o -E '[0-9A-Fa-f]{64}' | tr 'A-F' 'a-f' | sort -u
}

cmd_verify() {
  local apk=$1 aab=$2 sdk apksigner digest block out status=0
  sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
  apksigner=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
  [ -n "$apksigner" ] || { echo "apksigner not found under $sdk/build-tools" >&2; exit 1; }
  # apksigner exits non-zero for an unsigned APK, which is what a missing secret produces.
  out=$("$apksigner" verify --print-certs "$apk" 2>&1) || status=$?
  if [ "$status" -ne 0 ]; then
    echo "$apk: apksigner rejected the APK (exit $status), it is unsigned or broken" >&2
    printf '%s\n' "$out" | sed 's/^/  | /' >&2
    exit 1
  fi
  digest=$(printf '%s\n' "$out" | cert_digests)
  check_cert "$apk" "$digest" "$out"
  # A bundle is jar-signed: its certificate sits in META-INF/<alias>.RSA; an unsigned one has none.
  block=$(mktemp)
  if ! unzip -p "$aab" 'META-INF/*.RSA' > "$block" 2>/dev/null; then
    rm -f "$block"
    echo "$aab: no signature block, the bundle is unsigned" >&2
    exit 1
  fi
  digest=$(keytool -printcert -file "$block" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')
  rm -f "$block"
  check_cert "$aab" "$digest"
}

cmd_notes() {
  local name=$1 code=$2 prev
  prev=$(git tag --list 'v*' --sort=-v:refname | grep -v "^v$name\$" | head -1 || true)
  printf 'Flute Assistant %s (version code %s).\n\n' "$name" "$code"
  if [ -z "$prev" ]; then
    echo 'First release.'
    return
  fi
  printf 'Changes since %s:\n\n' "$prev"
  git log --no-merges --pretty='- %s' "$prev..HEAD"
  if [ -n "${GITHUB_SERVER_URL:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
    printf '\nFull diff: %s/%s/compare/%s...v%s\n' "$GITHUB_SERVER_URL" "$GITHUB_REPOSITORY" "$prev" "$name"
  fi
}

cmd_secrets() {
  # Runs on the machine that has keystore.properties and the keystore. Values go to gh through
  # pipes, so nothing is shown or typed; alias and key password are only set when the file has them.
  [ -f keystore.properties ] || { echo "keystore.properties not found in $(pwd)" >&2; exit 1; }
  local store value
  store=$(prop keystore.properties storeFile)
  [ -f "$store" ] || { echo "storeFile '$store' from keystore.properties does not exist" >&2; exit 1; }
  printf '%s' "$(base64 < "$store")" | gh secret set FLUTE_ASSISTANT_KEYSTORE_BASE64
  printf '%s' "$(prop keystore.properties storePassword)" | gh secret set FLUTE_ASSISTANT_STORE_PASSWORD
  for pair in keyAlias:FLUTE_ASSISTANT_KEY_ALIAS keyPassword:FLUTE_ASSISTANT_KEY_PASSWORD; do
    value=$(prop keystore.properties "${pair%%:*}")
    if [ -n "$value" ]; then
      printf '%s' "$value" | gh secret set "${pair##*:}"
    else
      echo "${pair%%:*} not in keystore.properties: ${pair##*:} left unset, the build uses its default"
    fi
  done
  echo "Secrets set. Check with: gh secret list"
}

case "${1:-}" in
  version) cmd_version ;;
  verify)  [ $# -eq 3 ] || { echo "usage: tools/release.sh verify APK AAB" >&2; exit 2; }; cmd_verify "$2" "$3" ;;
  notes)   [ $# -eq 3 ] || { echo "usage: tools/release.sh notes NAME CODE" >&2; exit 2; }; cmd_notes "$2" "$3" ;;
  secrets) cmd_secrets ;;
  *) sed -n '2,7p' "$0" >&2; exit 2 ;;
esac
