#!/bin/sh
set -eu

ARCH_OVERRIDE=${1:-}
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
APP_NAME=Feric
APP_VERSION=1.0.0
JAR_NAME=teacher-classroom-${APP_VERSION}.jar
DIST_DIR="$ROOT_DIR/dist"
INPUT_DIR="$ROOT_DIR/target/jpackage-input"
PNG_ICON="$ROOT_DIR/logos/feric-app-icon.png"
TARGET_OS=$(uname -s)
TARGET_ARCH=$(uname -m)
case "$TARGET_OS" in
  Darwin) OS_TAG=macos ;;
  Linux) OS_TAG=linux ;;
  *) OS_TAG=windows ;;
esac
case "$TARGET_ARCH" in
  arm64|aarch64) ARCH_TAG=arm64 ;;
  *) ARCH_TAG=x64 ;;
esac
if [ -n "$ARCH_OVERRIDE" ]; then
  ARCH_TAG="$ARCH_OVERRIDE"
fi
BUILD_SUFFIX="${OS_TAG}-${ARCH_TAG}"
MACOS_ZIP="$DIST_DIR/${APP_NAME}-macos-${ARCH_TAG}.zip"
LINUX_ZIP="$DIST_DIR/${APP_NAME}-linux-${ARCH_TAG}.zip"
WINDOWS_ZIP="$DIST_DIR/${APP_NAME}-windows-${ARCH_TAG}.zip"

cd "$ROOT_DIR"
if [ -n "${JAVA_HOME:-}" ]; then
  RUNTIME_IMAGE=$JAVA_HOME
elif [ "$(uname -s)" = "Darwin" ]; then
  RUNTIME_IMAGE=$(/usr/libexec/java_home)
else
  printf 'JAVA_HOME must point to a full JDK installation.\n' >&2
  exit 1
fi
if [ ! -x "$RUNTIME_IMAGE/bin/java" ] || [ ! -x "$RUNTIME_IMAGE/bin/javac" ]; then
  printf 'The packaging runtime must be a full JDK: %s\n' "$RUNTIME_IMAGE" >&2
  exit 1
fi

mvn -B clean package
JPACKAGE_RUNTIME=$RUNTIME_IMAGE
if [ "$(uname -s)" = "Linux" ]; then
  JPACKAGE_RUNTIME="$ROOT_DIR/target/feric-runtime"
  "$RUNTIME_IMAGE/bin/jlink" \
    --add-modules java.desktop,java.net.http,jdk.compiler,jdk.crypto.ec,jdk.httpserver \
    --output "$JPACKAGE_RUNTIME"
fi

mkdir -p "$DIST_DIR"
rm -rf "$INPUT_DIR" "$DIST_DIR/$APP_NAME" "$DIST_DIR/${APP_NAME}.app"
rm -f "$MACOS_ZIP" "$LINUX_ZIP" "$WINDOWS_ZIP"
mkdir -p "$INPUT_DIR"
cp "$ROOT_DIR/target/$JAR_NAME" "$INPUT_DIR/$JAR_NAME"
if [ "$TARGET_OS" = "Darwin" ]; then
  xattr -cr "$INPUT_DIR"
  ICONSET="$ROOT_DIR/target/Feric.iconset"
  mkdir -p "$ICONSET"
  for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$PNG_ICON" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
    double_size=$((size * 2))
    sips -z "$double_size" "$double_size" "$PNG_ICON" \
      --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
  done
  iconutil -c icns "$ICONSET" -o "$ROOT_DIR/logos/feric.icns"
  PACKAGE_DEST=$(mktemp -d "${TMPDIR:-/tmp}/Feric.XXXXXX")
  trap 'rm -rf "$PACKAGE_DEST"' EXIT HUP INT TERM
  ICON="$ROOT_DIR/logos/feric.icns"
else
  PACKAGE_DEST="$DIST_DIR"
  if [ "$(uname -s)" = "Linux" ]; then
    ICON="$PNG_ICON"
  else
    ICON="$ROOT_DIR/logos/feric.ico"
  fi
fi

strip_finder_info() {
  if xattr -l "$1" | grep -q '^com.apple.FinderInfo:'; then
    xattr -d com.apple.FinderInfo "$1"
  fi
}

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input "$INPUT_DIR" \
  --main-jar "$JAR_NAME" \
  --main-class com.classroom.live.AppLauncher \
  --runtime-image "$JPACKAGE_RUNTIME" \
  --icon "$ICON" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED" \
  --java-options "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" \
  --dest "$PACKAGE_DEST"

if [ "$TARGET_OS" = "Darwin" ]; then
  codesign --verify --deep --strict "$PACKAGE_DEST/${APP_NAME}.app"
  ditto -c -k --sequesterRsrc --keepParent \
    "$PACKAGE_DEST/${APP_NAME}.app" \
    "$MACOS_ZIP"
  ditto "$PACKAGE_DEST/${APP_NAME}.app" "$DIST_DIR/${APP_NAME}.app"
  strip_finder_info "$DIST_DIR/${APP_NAME}.app"
elif [ "$TARGET_OS" = "Linux" ]; then
  APP_RUNTIME="$DIST_DIR/$APP_NAME/lib/runtime"
  if [ ! -d "$APP_RUNTIME" ]; then
    printf 'jpackage did not create the Linux runtime directory: %s\n' "$APP_RUNTIME" >&2
    exit 1
  fi
  rm -rf "$APP_RUNTIME"
  cp -a "$RUNTIME_IMAGE" "$APP_RUNTIME"
  zip -qr "$LINUX_ZIP" "$DIST_DIR/$APP_NAME"
else
  zip -qr "$WINDOWS_ZIP" "$DIST_DIR/$APP_NAME"
fi

printf 'App image created in %s (%s)\n' "$DIST_DIR" "$BUILD_SUFFIX"
