#!/usr/bin/env bash
set -euo pipefail
mkdir -p hu12-evidence
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell getprop > hu12-evidence/device.txt
restore_font() { adb shell settings put system font_scale 1.0 || true; }
trap restore_font EXIT
for scale in 1.0 1.3; do
  adb shell settings put system font_scale "$scale"
  adb shell am force-stop co.edu.uniandes.vinilos
  adb logcat -c
  adb shell am instrument -w -r -e class co.edu.uniandes.vinilos.BandMusicianFlowTest co.edu.uniandes.vinilos.test/androidx.test.runner.AndroidJUnitRunner | tee "hu12-evidence/instrumentation-font-$scale.txt"
  adb logcat -d > "hu12-evidence/logcat-font-$scale.txt"
  mkdir -p "hu12-evidence/font-$scale"
  adb pull /sdcard/Android/data/co.edu.uniandes.vinilos/files/. "hu12-evidence/font-$scale/"
  if ! grep -q 'OK (8 tests)' "hu12-evidence/instrumentation-font-$scale.txt"; then
    echo 'La ejecución no completó los ocho casos de HU12.'
    exit 1
  fi
done
