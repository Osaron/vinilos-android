#!/usr/bin/env bash
set -euo pipefail
capture_results() {
  mkdir -p collector-captures
  adb pull /sdcard/Android/data/co.edu.uniandes.vinilos/files/coleccionistas-listado.png collector-captures/ || true
  adb pull /sdcard/Android/data/co.edu.uniandes.vinilos/files/coleccionistas-busqueda.png collector-captures/ || true
}
trap capture_results EXIT
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.backendReal=true \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.CollectorBackendRealTest
