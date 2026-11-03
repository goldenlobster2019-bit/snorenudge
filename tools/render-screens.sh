#!/usr/bin/env bash
# Renders every Wear OS screen to PNG on the JVM (Roborazzi + Robolectric native graphics, no device/emulator)
# and copies the images to docs/design/current/.
#
# Usage: tools/render-screens.sh
# Needs: ANDROID_HOME (defaults to /root/android-sdk), network on the first run (Maven artifacts and
# Robolectric's android-all jar, cached afterwards; Maven Central may answer 429, just rerun).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/design/current"
export ANDROID_HOME="${ANDROID_HOME:-/root/android-sdk}"

cd "$ROOT"
rm -rf app/build/screenshots
# cleanTest forces a rerun so the images are always regenerated (Gradle would otherwise say UP-TO-DATE).
./gradlew :app:cleanTestDebugUnitTest :app:recordRoborazziDebug --console=plain "$@"

mkdir -p "$OUT"
rm -f "$OUT"/[0-9][0-9]*_*.png
cp app/build/screenshots/*.png "$OUT"/
echo "Screens written to $OUT:"
ls -l "$OUT"/*.png
