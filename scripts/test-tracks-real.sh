#!/usr/bin/env bash
# Valida el contrato de HU08 (#33) contra un BackVynils local y desechable.
# Uso: BACKVYNILS_URL=http://localhost:3000/ scripts/test-tracks-real.sh
# La prueba escribe canciones: no la apuntes a una instancia compartida.
set -euo pipefail
: "${BACKVYNILS_URL:=http://localhost:3000/}"
export BACKVYNILS_URL
curl --fail --silent "${BACKVYNILS_URL}albums" > /dev/null
./gradlew --no-daemon testDebugUnitTest --tests 'co.edu.uniandes.vinilos.TrackBackendRealTest' --rerun
