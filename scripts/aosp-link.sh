#!/usr/bin/env bash
# Links this repository into an AOSP checkout.
#
#   ANDROID_BUILD_TOP=/srv/aosp bash scripts/aosp-link.sh
#   cd /srv/aosp && . build/envsetup.sh && lunch droidos_arm64-userdebug && m
#
# AOSP and the kernel source are not vendored here: together they are hundreds of gigabytes and
# only build on Linux. This script wires the small parts this project owns into a tree you synced
# yourself with `repo`, which is how device trees are distributed in practice.

set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
top=${ANDROID_BUILD_TOP:-${1:-}}

if [ -z "$top" ]; then
    echo "aosp-link: set ANDROID_BUILD_TOP to your AOSP checkout" >&2
    exit 2
fi
if [ ! -d "$top/build/soong" ]; then
    echo "aosp-link: $top is not an AOSP tree (no build/soong directory)" >&2
    exit 1
fi

mkdir -p "$top/device/droidos"
ln -sfn "$root" "$top/droidos"
ln -sfn "$root/device/droidos/droidos_arm64" "$top/device/droidos/droidos_arm64"

echo "aosp-link: linked"
echo "  $top/droidos                              -> $root"
echo "  $top/device/droidos/droidos_arm64         -> $root/device/droidos/droidos_arm64"
echo
echo "next:"
echo "  bash scripts/stage-prebuilt.sh            # stage the shell APK first"
echo "  cd $top && . build/envsetup.sh && lunch droidos_arm64-userdebug && m"
