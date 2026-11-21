#!/usr/bin/env bash
# Corte de una entrega: toma el APK que compiló y probó GitHub Actions y comprueba
# el que quedó publicado en la release. Requiere git, gh (autenticado) y sha256sum.
#
#   scripts/corte-apk.sh preparar  <tag> <id-ejecución-CI> [carpeta]
#   scripts/corte-apk.sh verificar <tag> [carpeta]
#
# "preparar" no publica nada: deja el APK renombrado y su SHA-256 listos para adjuntar.
set -euo pipefail

uso() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
falla() { echo "ERROR: $*" >&2; exit 1; }
sha256() { sha256sum "$1" | cut -d' ' -f1; }

# Muestra versionName, versionCode y minSdk si aapt está disponible; no es obligatorio.
insignia() {
  local aapt
  aapt=$(command -v aapt 2>/dev/null || ls "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/build-tools/*/aapt* 2>/dev/null | head -n 1 || true)
  if [ -n "$aapt" ]; then
    "$aapt" dump badging "$1" | grep -E "^(package|sdkVersion)" || true
  else
    echo "aapt no disponible: versionName, versionCode y minSdk quedan por comprobar."
  fi
}

preparar() {
  local tag=$1 run=$2 destino=${3:-build/corte}
  local apk="$destino/vinilos-$tag-debug.apk"

  git rev-parse -q --verify "refs/tags/$tag^{commit}" > /dev/null \
    || falla "el tag $tag no existe en este clon (git fetch --tags)."
  local commit_tag arbol_tag commit_ci arbol_ci conclusion
  commit_tag=$(git rev-parse "refs/tags/$tag^{commit}")
  arbol_tag=$(git rev-parse "refs/tags/$tag^{tree}")

  conclusion=$(gh run view "$run" --json conclusion --jq .conclusion)
  [ "$conclusion" = "success" ] || falla "la ejecución $run terminó en '$conclusion'."
  gh run view "$run" --json jobs --jq '.jobs[] | "\(.name): \(.conclusion)"'

  commit_ci=$(gh run view "$run" --json headSha --jq .headSha)
  git cat-file -e "$commit_ci^{commit}" 2> /dev/null || git fetch -q origin "$commit_ci"
  arbol_ci=$(git rev-parse "$commit_ci^{tree}")
  [ "$arbol_ci" = "$arbol_tag" ] \
    || falla "el código probado ($commit_ci) no es el del tag ($commit_tag): árboles $arbol_ci y $arbol_tag."

  rm -rf "$destino/ci" && mkdir -p "$destino/ci"
  gh run download "$run" --name vinilos-debug --dir "$destino/ci"
  cp "$destino/ci/app-debug.apk" "$apk"

  echo
  echo "Tag:            $tag -> $commit_tag"
  echo "Commit probado: $commit_ci (mismo árbol $arbol_tag)"
  echo "CI:             $(gh run view "$run" --json url --jq .url)"
  echo "APK:            $apk ($(wc -c < "$apk" | tr -d ' ') bytes)"
  echo "SHA-256:        $(sha256 "$apk")"
  insignia "$apk"
}

verificar() {
  local tag=$1 destino=${2:-build/corte}/descarga-$1
  rm -rf "$destino" && mkdir -p "$destino"
  gh release download "$tag" --pattern '*.apk' --dir "$destino"

  local apk publicado calculado
  for apk in "$destino"/*.apk; do
    calculado=$(sha256 "$apk")
    publicado=$(gh release view "$tag" --json assets \
      --jq ".assets[] | select(.name == \"$(basename "$apk")\") | .digest" | sed 's/^sha256://')
    echo "APK:            $(basename "$apk") ($(wc -c < "$apk" | tr -d ' ') bytes)"
    echo "SHA-256:        $calculado"
    [ "$calculado" = "$publicado" ] || falla "GitHub registra $publicado para ese archivo."
    gh release view "$tag" --json body --jq .body | grep -q "$calculado" \
      || falla "la descripción de la release no menciona ese SHA-256."
    echo "Coincide con el archivo publicado y con la descripción de la release."
    insignia "$apk"
  done
}

case "${1:-}" in
  preparar) [ $# -ge 3 ] || uso; preparar "$2" "$3" "${4:-}" ;;
  verificar) [ $# -ge 2 ] || uso; verificar "$2" "${3:-}" ;;
  *) uso ;;
esac
