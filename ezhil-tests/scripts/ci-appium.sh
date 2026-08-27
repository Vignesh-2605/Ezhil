#!/usr/bin/env bash
#
# Drive the on-device suite inside the CI emulator.
#
# This lives in a file rather than inline in the workflow because the emulator
# action runs each line of its `script:` block as a separate `sh -c`. Nothing
# carries between lines -- an `export` on one line is simply gone by the next,
# which is how APK_PATH arrived empty and `ls -la ""` failed the step.
set -euo pipefail

APK_PATH="${APK_PATH:?APK_PATH must be set by the workflow}"
APPIUM_URL="${APPIUM_URL:-http://127.0.0.1:4723}"

echo "APK:    $APK_PATH"
echo "Appium: $APPIUM_URL"

if [ ! -f "$APK_PATH" ]; then
  echo "No APK at $APK_PATH."
  echo "Available:"
  ls -la "$(dirname "$APK_PATH")" || true
  exit 1
fi

echo "Waiting for the emulator to finish booting…"
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
adb devices

echo "Starting Appium…"
appium --log-level warn > /tmp/appium.log 2>&1 &

for _ in $(seq 1 45); do
  if curl -sf "$APPIUM_URL/status" > /dev/null; then break; fi
  sleep 2
done
if ! curl -sf "$APPIUM_URL/status" > /dev/null; then
  echo "Appium never answered on $APPIUM_URL"
  cat /tmp/appium.log || true
  exit 1
fi
echo "Appium is up."

cd "$(dirname "$0")/.."
node suites/android-appium.js
status=$?

# The suite's own output is the report; the server log only matters when the
# session itself could not be established.
if [ $status -ne 0 ]; then
  echo "--- appium log ---"
  tail -80 /tmp/appium.log || true
fi
exit $status
