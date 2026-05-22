#!/bin/bash
set -euo pipefail

echo "=== Converting PNG to WebP ==="
RESOURCES_DIR="app/src/main/res"

if ! command -v cwebp >/dev/null 2>&1; then
  echo "⚠ cwebp not found. Install webp package first."
  exit 0
fi

find "$RESOURCES_DIR" -type f -name "*.png" \
  ! -name "*.9.png" \
  ! -path "*/mipmap-anydpi*" | while read -r PNG_FILE; do
    WEBP_FILE="${PNG_FILE%.png}.webp"
    echo "Converting: $PNG_FILE -> $WEBP_FILE"
    cwebp -m 6 -q 75 "$PNG_FILE" -o "$WEBP_FILE" >/dev/null
  done

echo "✓ Resource optimization complete"
