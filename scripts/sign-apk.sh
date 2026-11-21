#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "$0")/.."

tag=${1:-}
if [[ -n "$tag" && ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-beta\.[1-9][0-9]*)?$ ]]; then
    echo 'Usage: bash scripts/sign-apk.sh [vX.Y.Z or vX.Y.Z-beta.N]' >&2
    exit 1
fi

build_tools="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/35.0.0"
apk=app/build/outputs/apk/release/app-release-unsigned.apk
debug_beta=false
if [[ ( -z "$tag" || "$tag" == *-beta.* ) && -z "${ANDROID_KEYSTORE_BASE64:-}" &&
    -z "${ANDROID_KEYSTORE_PASSWORD:-}" && -z "${ANDROID_KEY_ALIAS:-}" && -z "${ANDROID_KEY_PASSWORD:-}" ]]; then
    debug_beta=true
    apk=app/build/outputs/apk/debug/app-debug.apk
fi
badging=$("$build_tools/aapt2" dump badging "$apk")
version=$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<< "$badging")
if [[ -z "$tag" ]]; then
    if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[1-9][0-9]*)?-build\.[1-9][0-9]*$ ]]; then
        printf 'Expected an APK versionName with a CI build number, got %s\n' "$version" >&2
        exit 1
    fi
    tag="v$version"
elif [[ "$tag" != "v$version" ]]; then
    printf 'Tag %s does not match APK versionName %s\n' "$tag" "$version" >&2
    exit 1
fi

output_dir=app/build/release-artifacts
mkdir -p "$output_dir"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf 'release_tag=%s\n' "$tag" >> "$GITHUB_OUTPUT"
fi
if [[ "$debug_beta" == true ]]; then
    echo 'No signing secrets configured. Preparing the Beta debug APK.'
    "$build_tools/apksigner" verify --verbose "$apk"
    cp -- "$apk" "$output_dir/yixin-android-$version-debug.apk"
    exit 0
fi

: "${ANDROID_KEYSTORE_BASE64:?Set ANDROID_KEYSTORE_BASE64}"
: "${ANDROID_KEYSTORE_PASSWORD:?Set ANDROID_KEYSTORE_PASSWORD}"
: "${ANDROID_KEY_ALIAS:?Set ANDROID_KEY_ALIAS}"
export ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-$ANDROID_KEYSTORE_PASSWORD}"

work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$work_dir/release.keystore"

"$build_tools/zipalign" -P 16 -f 4 "$apk" "$work_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$work_dir/release.keystore" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
    --out "$work_dir/signed.apk" "$work_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$work_dir/signed.apk"

mv -- "$work_dir/signed.apk" "$output_dir/yixin-android-$version.apk"
