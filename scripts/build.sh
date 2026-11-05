#!/usr/bin/env bash
# Builds DroidOS Shell with the local toolchain installed by bootstrap-toolchain.sh.
#
#   ./scripts/build.sh            -> assembleDebug + unit tests
#   ./scripts/build.sh release    -> assembleRelease (debug-signed so it installs)
#   ./scripts/build.sh test       -> unit tests only
#   ./scripts/build.sh lint       -> Android lint
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TOOLS="$ROOT/.tools"
if [ ! -d "$TOOLS/jdk" ]; then
  echo "No local toolchain found. Run ./scripts/bootstrap-toolchain.sh first." >&2
  exit 1
fi

export JAVA_HOME="$TOOLS/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle-home"
export ANDROID_HOME="$TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Prefer the locally bootstrapped distribution: it is already on disk, so builds never
# depend on the wrapper downloading a second Gradle copy. Fall back to ./gradlew for
# machines that only have the wrapper.
if [ -f "$TOOLS/gradle/bin/gradle.bat" ]; then
  GRADLE="$TOOLS/gradle/bin/gradle.bat"
elif [ -x "$TOOLS/gradle/bin/gradle" ]; then
  GRADLE="$TOOLS/gradle/bin/gradle"
elif [ -f "$ROOT/gradlew" ]; then
  GRADLE="$ROOT/gradlew"
else
  echo "No Gradle found. Run ./scripts/bootstrap-toolchain.sh first." >&2
  exit 1
fi

TASK="${1:-debug}"
case "$TASK" in
  debug)   "$GRADLE" --no-daemon :app:assembleDebug :app:testDebugUnitTest ;;
  release) "$GRADLE" --no-daemon :app:assembleRelease :app:testDebugUnitTest ;;
  test)    "$GRADLE" --no-daemon :app:testDebugUnitTest ;;
  lint)    "$GRADLE" --no-daemon :app:lintDebug ;;
  clean)   "$GRADLE" --no-daemon clean ;;
  *)       "$GRADLE" --no-daemon "$@" ;;
esac

echo
echo "Artifacts:"
find "$ROOT/app/build/outputs" -name '*.apk' -printf '  %p (%s bytes)\n' 2>/dev/null || true
