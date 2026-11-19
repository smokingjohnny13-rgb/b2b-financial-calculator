#!/usr/bin/env sh

set -e

APP_BASE_NAME=${0##*/}
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

if command -v gradle >/dev/null 2>&1; then
  exec gradle -p "$APP_HOME" "$@"
fi

WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "ERROR: $WRAPPER_PROPS not found." >&2
  exit 1
fi

DIST_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | tr -d '\r' | sed 's/\\:/:/g')
if [ -z "$DIST_URL" ]; then
  echo "ERROR: distributionUrl not found in $WRAPPER_PROPS" >&2
  exit 1
fi

ZIP_NAME=$(basename "$DIST_URL")
DIST_DIR_NAME="${ZIP_NAME%-bin.zip}"
DIST_DIR_NAME="${DIST_DIR_NAME%-all.zip}"

GRADLE_CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$DIST_DIR_NAME"
mkdir -p "$GRADLE_CACHE_ROOT"

HASH_ID=$(printf "%s" "$DIST_URL" | cksum | awk '{print $1}')
TARGET_DIR="$GRADLE_CACHE_ROOT/$HASH_ID"
mkdir -p "$TARGET_DIR"

ZIP_PATH="$TARGET_DIR/$ZIP_NAME"
if [ ! -f "$ZIP_PATH" ]; then
  echo "Downloading Gradle distribution from $DIST_URL..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$ZIP_PATH" "$DIST_URL"
  else
    echo "ERROR: Neither curl nor wget is available to download Gradle." >&2
    exit 1
  fi
fi

UNPACK_MARKER="$TARGET_DIR/.unpacked"
if [ ! -f "$UNPACK_MARKER" ]; then
  unzip -qo "$ZIP_PATH" -d "$TARGET_DIR"
  touch "$UNPACK_MARKER"
fi

GRADLE_BIN=$(find "$TARGET_DIR" -maxdepth 3 -type f -name "gradle" | head -n 1)
if [ -z "$GRADLE_BIN" ]; then
  echo "ERROR: Could not locate gradle binary inside $TARGET_DIR" >&2
  exit 1
fi

chmod +x "$GRADLE_BIN"
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
