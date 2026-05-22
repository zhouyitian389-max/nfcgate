#!/bin/bash
set -euo pipefail

echo "=== Build Time Benchmark ==="
echo "Clean build..."
time ./gradlew clean :app:assembleDebug -x test

echo ""
echo "Incremental build..."
touch app/src/main/java/de/tu_darmstadt/seemoo/nfcgate/gui/MainActivity.java
time ./gradlew :app:assembleDebug -x test

echo ""
echo "With build cache..."
time ./gradlew clean :app:assembleDebug -x test --build-cache

echo "✓ Build benchmark complete"
