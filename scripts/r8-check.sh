#!/bin/bash
set -euo pipefail

echo "=== R8 Configuration Check ==="
echo "Checking release build and mapping..."
./gradlew :app:assembleRelease

MAPPING_FILE="app/build/outputs/mapping/release/mapping.txt"
if [ -f "$MAPPING_FILE" ]; then
  echo "✓ Mapping file generated: $MAPPING_FILE"
  wc -l "$MAPPING_FILE"
else
  echo "✗ Mapping file not found"
fi

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$RELEASE_APK" ]; then
  SIZE_KB=$(($(stat -f%z "$RELEASE_APK" 2>/dev/null || stat -c%s "$RELEASE_APK") / 1024))
  echo "Release APK size: ${SIZE_KB} KB"
fi
