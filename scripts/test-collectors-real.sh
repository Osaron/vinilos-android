#!/usr/bin/env bash
set -euo pipefail
capture_results() {
  adb shell settings put system font_scale 1.0 || true
  mkdir -p collector-captures
  for name in coleccionistas-listado coleccionistas-busqueda; do
    adb pull "/sdcard/Download/$name.png" collector-captures/
    adb pull "/sdcard/Download/$name-letra-grande.png" collector-captures/
  done
}
trap capture_results EXIT
adb shell settings put system font_scale 1.0
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.backendReal=true \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.CollectorBackendRealTest
adb shell settings put system font_scale 1.5
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.backendReal=true \
  -Pandroid.testInstrumentationRunnerArguments.largeFont=true \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.CollectorBackendRealTest
