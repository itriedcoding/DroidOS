#!/usr/bin/env bash
# Installs a self-contained build toolchain inside .tools/ so nothing on the host
# system is modified and no administrator rights are required.
#
#   JDK 17 (Temurin)  -> .tools/jdk
#   Gradle 8.9        -> .tools/gradle
#   Android SDK       -> .tools/android-sdk (platform-tools, android-35, build-tools 35.0.0)
#
# Usage:  ./scripts/bootstrap-toolchain.sh
# Then:   ./scripts/build.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
SDK="$TOOLS/android-sdk"
GRADLE_VERSION="8.9"
CMDLINE_TOOLS_BUILD="16111833"

mkdir -p "$TOOLS"

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) OS=windows; SDK_ZIP_OS=win ;;
  Darwin*)                  OS=osx;     SDK_ZIP_OS=mac ;;
  *)                        OS=linux;   SDK_ZIP_OS=linux ;;
esac

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64 | amd64) JDK_ARCH=x64 ;;
  aarch64 | arm64) JDK_ARCH=aarch64 ;;
  *) echo "Unsupported CPU architecture: $ARCH" >&2; exit 1 ;;
esac

download() { # url dest
  if [ -f "$2" ]; then
    echo "  cached  $(basename "$2")"
    return 0
  fi
  echo "  fetch   $1"
  curl -fL --retry 4 --retry-delay 2 --connect-timeout 20 -o "$2.part" "$1"
  mv "$2.part" "$2"
}

extract() { # zip dest
  mkdir -p "$2"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$1" -d "$2"
  else
    # Git Bash on Windows has no unzip; PowerShell's Expand-Archive is equivalent.
    local win_zip win_dest
    win_zip="$(cygpath -w "$1")"
    win_dest="$(cygpath -w "$2")"
    powershell.exe -NoProfile -NonInteractive -Command \
      "Expand-Archive -LiteralPath '$win_zip' -DestinationPath '$win_dest' -Force"
  fi
}

# ---------------------------------------------------------------- JDK 17
if [ ! -x "$TOOLS/jdk/bin/java" ] && [ ! -x "$TOOLS/jdk/bin/java.exe" ]; then
  echo "==> JDK 17 (Temurin)"
  download \
    "https://api.adoptium.net/v3/binary/latest/17/ga/$OS/$JDK_ARCH/jdk/hotspot/normal/eclipse" \
    "$TOOLS/jdk-$OS-$JDK_ARCH.zip"
  rm -rf "$TOOLS/jdk" "$TOOLS/jdk-extract"
  extract "$TOOLS/jdk-$OS-$JDK_ARCH.zip" "$TOOLS/jdk-extract"
  inner="$(find "$TOOLS/jdk-extract" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
  mv "$inner" "$TOOLS/jdk"
  rm -rf "$TOOLS/jdk-extract"
else
  echo "==> JDK 17 already installed"
fi

export JAVA_HOME="$TOOLS/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ---------------------------------------------------------------- Gradle
export GRADLE_USER_HOME="$TOOLS/gradle-home"
if [ ! -x "$TOOLS/gradle/bin/gradle" ] && [ ! -f "$TOOLS/gradle/bin/gradle.bat" ]; then
  echo "==> Gradle $GRADLE_VERSION"
  download \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    "$TOOLS/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$TOOLS/gradle"
  extract "$TOOLS/gradle-$GRADLE_VERSION-bin.zip" "$TOOLS"
  mv "$TOOLS/gradle-$GRADLE_VERSION" "$TOOLS/gradle"
else
  echo "==> Gradle already installed"
fi

# ---------------------------------------------------------------- Android SDK
if [ ! -d "$SDK/cmdline-tools/latest/bin" ]; then
  echo "==> Android command-line tools"
  download \
    "https://dl.google.com/android/repository/commandlinetools-$SDK_ZIP_OS-${CMDLINE_TOOLS_BUILD}_latest.zip" \
    "$TOOLS/cmdline-tools.zip"
  rm -rf "$TOOLS/cmdline-tools-unpack"
  extract "$TOOLS/cmdline-tools.zip" "$TOOLS/cmdline-tools-unpack"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TOOLS/cmdline-tools-unpack/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TOOLS/cmdline-tools-unpack"
else
  echo "==> Android command-line tools already installed"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
[ -f "$SDKMANAGER.bat" ] && SDKMANAGER="$SDKMANAGER.bat"

echo "==> Accepting SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true

echo "==> Installing SDK packages (platform-tools, android-35, build-tools 35.0.0)"
"$SDKMANAGER" --sdk_root="$SDK" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# local.properties is git-ignored and regenerated per machine.
# Forward slashes are valid for both Windows and POSIX in .properties files.
printf 'sdk.dir=%s\n' "$SDK" >"$ROOT/local.properties"

echo
echo "Toolchain ready."
echo "  JAVA_HOME = $JAVA_HOME"
echo "  Gradle    = $TOOLS/gradle"
echo "  SDK       = $SDK"
echo "Next: ./scripts/build.sh"
