#!/usr/bin/env bash
set -euo pipefail
capture_results() {
  mkdir -p collector-captures
  adb pull /sdcard/Download/coleccionistas-listado.png collector-captures/
  adb pull /sdcard/Download/coleccionistas-busqueda.png collector-captures/
}
trap capture_results EXIT
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.backendReal=true \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.CollectorBackendRealTest
