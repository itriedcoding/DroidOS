#!/usr/bin/env bash
# Stages the built release APK where the device tree's Soong import expects it.
#
#   bash scripts/stage-prebuilt.sh
#
# The staged file is git-ignored so the repository stays small and a binary copy can never drift
# from the source that produced it: re-run this whenever the shell changes.

set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
apk="$root/app/build/outputs/apk/release/app-release.apk"
destination="$root/device/droidos/droidos_arm64/prebuilt"

if [ ! -f "$apk" ]; then
    echo "stage-prebuilt: $apk does not exist; run scripts/build.sh release first" >&2
    exit 1
fi

mkdir -p "$destination"
cp "$apk" "$destination/DroidOSShell.apk"

size=$(wc -c < "$destination/DroidOSShell.apk")
checksum=$(sha256sum "$destination/DroidOSShell.apk" | cut -d' ' -f1)

echo "stage-prebuilt: $destination/DroidOSShell.apk"
echo "  bytes    $size"
echo "  sha256   $checksum"
