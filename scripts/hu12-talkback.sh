#!/usr/bin/env bash
set -euo pipefail
mkdir -p hu12-talkback-evidence downloads
curl --fail --location --retry 2 https://f-droid.org/repo/com.android.talkback_50007000.apk -o downloads/talkback.apk
curl --fail --location --retry 2 https://f-droid.org/repo/com.reecedunn.espeak_22.apk -o downloads/espeak.apk
printf '%s\n' '66c93480f736ceeb7bff906ee08aebe232bf22d820dac6d7bb80f29bd52e3211  downloads/talkback.apk' '0a7822fec54d7f7ae759ffedc9afeaa77f809587b171fb2101998787821ec790  downloads/espeak.apk' | sha256sum -c -
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest
adb root
adb wait-for-device
adb install -r downloads/talkback.apk
adb install -r downloads/espeak.apk
python3 - <<'PYDATA'
import zipfile, io
from pathlib import Path
with zipfile.ZipFile('downloads/espeak.apk') as apk:
    data = apk.read('res/t8.zip')
with zipfile.ZipFile(io.BytesIO(data)) as voices:
    voices.extractall('downloads/voices')
PYDATA
adb shell mkdir -p /data/user_de/0/com.reecedunn.espeak/app_voices
adb push downloads/voices/espeak-ng-data /data/user_de/0/com.reecedunn.espeak/app_voices/
engine_uid=$(adb shell cmd package list packages -U com.reecedunn.espeak | tr -d '\r' | sed -nE 's/.*uid:([0-9]+).*/\1/p')
[ -n "$engine_uid" ]
adb shell chown -R "$engine_uid:$engine_uid" /data/user_de/0/com.reecedunn.espeak/app_voices
adb shell restorecon -R /data/user_de/0/com.reecedunn.espeak/app_voices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put secure tts_default_synth com.reecedunn.espeak
adb shell am force-stop com.android.talkback
adb push scripts/talkback/preferences.xml /data/local/tmp/hu12-talkback-preferences.xml
adb shell mkdir -p /data/user/0/com.android.talkback/shared_prefs
adb shell cp /data/local/tmp/hu12-talkback-preferences.xml /data/user/0/com.android.talkback/shared_prefs/com.android.talkback_preferences.xml
reader_uid=$(adb shell cmd package list packages -U com.android.talkback | tr -d '\r' | sed -nE 's/.*uid:([0-9]+).*/\1/p')
[ -n "$reader_uid" ]
adb shell chown -R "$reader_uid:$reader_uid" /data/user/0/com.android.talkback/shared_prefs
adb shell restorecon -R /data/user/0/com.android.talkback/shared_prefs
adb shell settings put secure enabled_accessibility_services com.android.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure touch_exploration_enabled 1
adb shell setprop log.tag.SpeechController VERBOSE
adb shell getprop > hu12-talkback-evidence/device.txt
adb shell dumpsys package com.android.talkback > hu12-talkback-evidence/talkback-package.txt
cleanup() {
  adb shell settings put secure accessibility_enabled 0 || true
  adb shell settings put system font_scale 1.0 || true
}
trap cleanup EXIT
for scale in 1.0 1.3; do
  adb shell settings put system font_scale "$scale"
  adb shell am force-stop co.edu.uniandes.vinilos
  adb logcat -c
  set +e
  timeout 180s adb shell am instrument -w -r -e hu12TalkBack true -e hu12FontScale "$scale" -e class co.edu.uniandes.vinilos.BandMusicianTalkBackTest co.edu.uniandes.vinilos.test/androidx.test.runner.AndroidJUnitRunner | tee "hu12-talkback-evidence/instrumentation-font-$scale.txt"
  instrument_status=${PIPESTATUS[0]}
  set -e
  adb logcat -d > "hu12-talkback-evidence/logcat-font-$scale.txt"
  adb shell dumpsys accessibility > "hu12-talkback-evidence/accessibility-font-$scale.txt"
  mkdir -p "hu12-talkback-evidence/font-$scale"
  for artifact in $(adb shell run-as co.edu.uniandes.vinilos ls files | tr -d '\r' | grep '^hu12-talkback-'); do
    adb exec-out run-as co.edu.uniandes.vinilos cat "files/$artifact" > "hu12-talkback-evidence/font-$scale/$artifact"
  done
  [ "$instrument_status" = "0" ]
  grep -q 'OK (1 test)' "hu12-talkback-evidence/instrumentation-font-$scale.txt"
done
