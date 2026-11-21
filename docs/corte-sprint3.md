# Corte del Sprint 3

Preparación del corte final de la Iteración 3. Responsable: Donaldo. Estado al 20 de noviembre de 2026: propuesta para acordar en la revisión de este PR. La publicación del APK final corresponde a la semana del 23 al 28 de noviembre, desde el código aprobado.

## Qué se acuerda

| Tema | Propuesta |
|---|---|
| Código del corte | `develop` con HU09, HU10 y HU12 integradas y aprobadas. Hoy `develop` está en `4575bd2` (PR #119); faltan por cerrar el PR #120 y los issues abiertos de HU09, HU10 y HU12. |
| Versión | `versionName 0.4.0`, `versionCode 5`. La release vigente es 0.3.1 (4). |
| Rama | `release/v0.4.0-w8`, creada desde `main` con el contenido de `develop`. |
| Tag y release | `v0.4.0-w8` sobre el commit de `main`, marcada como *Latest*. Las releases anteriores se conservan. |
| Revisión | PR de la rama de release hacia `main`, con la aprobación de un integrante distinto del autor y la CI completa en verde. |
| APK | El archivo `vinilos-debug` que compila la CI de ese PR, sin recompilar. Se publica como `vinilos-v0.4.0-w8-debug.apk` con su SHA-256 y el enlace a la ejecución. |
| Comprobación | Descarga desde el enlace público, SHA-256 e instalación y arranque en un emulador con API 21. |

## Ajustes que entran con esta preparación

**Versión de `develop`.** `develop` seguía en 0.2.0 (`versionCode 2`) porque la versión solo se subía en las ramas de release. Un APK compilado desde `develop` no se podía instalar con `adb install -r` sobre la release v0.3.1-w6 (`versionCode 4`): Android rechaza la instalación por ser una versión anterior. Queda en 0.4.0 (5), que además es la versión del corte, de modo que la rama de release ya no necesita un cambio propio de versión.

**README.** `main` tenía la sección *Descargar el APK* y el estado de la Iteración 2, que no habían vuelto a `develop`. Se trae el README de `main` y se le agrega un enlace a este documento. Después de este cambio, todo lo que hay en `main` está en `develop` y la rama de release puede tomar el contenido de `develop` sin perder nada. El estado y la tabla de descargas del Sprint 3 se actualizan en la rama de release, cuando exista el enlace definitivo.

**Prueba de certificados.** `LegacyImageTlsTest` fallaba en computadores con Docker Desktop, que es un requisito del README: Docker asocia `127.0.0.1` al nombre `kubernetes.docker.internal` y la prueba esperaba `localhost`. Ahora fija el host y da el mismo resultado en cualquier computador. No cambia el código de la app.

## Pasos del corte

1. Confirmar que `develop` contiene lo aprobado y que su CI está en verde.
2. Crear la rama de release desde `main` con el contenido de `develop`:

   ```bash
   git fetch origin --tags
   git checkout -b release/v0.4.0-w8 origin/main
   git restore --source=origin/develop --staged --worktree .
   git diff --cached --quiet origin/develop && echo "mismo contenido que develop"
   git commit -m "Release v0.4.0-w8: cierre de la Iteración 3"
   ```

3. Actualizar en esa rama el estado y la tabla *Descargar el APK* del README. Llevar el mismo cambio a `develop`.
4. Abrir el PR hacia `main`. La CI ejecuta `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` y `connectedDebugAndroidTest` en API 21, 29 y 34.
5. Con el PR aprobado y la CI en verde, integrarlo y crear el tag `v0.4.0-w8` sobre el commit resultante de `main`.
6. Preparar el APK a partir de la ejecución de CI del PR:

   ```bash
   scripts/corte-apk.sh preparar v0.4.0-w8 <id-de-la-ejecución>
   ```

   El script se detiene si la ejecución no terminó bien o si el código que probó no es el mismo del tag (compara los árboles Git, porque el *squash and merge* cambia el commit pero no el contenido). Deja el APK renombrado en `build/corte/` y muestra tag, commit probado, enlace de CI, tamaño, SHA-256 y versión.
7. Publicar la release con el APK en *Assets* y, en la descripción, el SHA-256, el enlace a la CI, el PR y el commit de `main`.
8. Comprobar lo publicado:

   ```bash
   scripts/corte-apk.sh verificar v0.4.0-w8
   ```

   Descarga el APK de la release y comprueba que su SHA-256 coincide con el que registra GitHub y con el de la descripción.
9. Instalar el APK descargado en un emulador con API 21 (`adb install -r`), abrir la app con BackVynils activo y registrar versión, resultado y captura en la wiki. Enlazar tag, APK y resultados desde la página de artefactos del Sprint 3.

El script se probó con la release anterior: `preparar v0.3.1-w6 43782097855` reproduce el SHA-256 publicado y `verificar v0.3.1-w6` confirma la descarga. Con una ejecución de otro código (43784032999) se detiene por diferencia de árboles.

## Contrato de HU12

Revisión del 20 de noviembre contra BackVynils `7178f83`, que es el commit que usan los workflows y sigue siendo el último del repositorio. Queda para confirmar con Tibisay en la revisión de este PR.

| Solicitud | Respuesta comprobada |
|---|---|
| `POST /bands/{bandId}/musicians/{musicianId}`, sin cuerpo | 200 con el músico y su banda en `band`. Repetirla con la misma banda devuelve 200 y no duplica. |
| El mismo `POST` con banda o músico inexistente | 404 con `message` que indica cuál de los dos falta. |
| El mismo `POST` con un identificador no numérico | 500. La app no llega a enviarlo: el adaptador exige enteros positivos. |
| `GET /bands/{bandId}/musicians/` | 200 con los integrantes; 404 si la banda no existe. |
| `GET /bands/{bandId}/musicians/{musicianId}` | 200 si el músico pertenece a la banda; 412 si no. La app no lo usa. |

**Identificación.** Bandas y músicos se guardan en tablas distintas y sus identificadores son independientes: un mismo número puede ser una banda y un músico diferentes. En los datos de ejemplo la banda es 101 y el músico 100. El orden en la ruta es siempre banda y luego músico. `BandMusicianServiceAdapter` comprueba que la respuesta traiga `id` igual al músico enviado y `band.id` igual a la banda enviada, y rechaza la confirmación si no coinciden. El selector recibe `bandId` desde el detalle de la banda y devuelve `bandId` y `musicianId`.

**Un músico, una banda.** El backend guarda una sola banda por músico. Asociarlo a otra banda reemplaza la anterior; el diálogo de confirmación ya lo advierte.

**Punto por decidir.** Después de un error de red, la app verifica con el listado de integrantes antes de ofrecer el reintento. `GET /bands/{bandId}/musicians/{musicianId}` responde lo mismo con una sola fila (200 o 412). No hace falta cambiarlo para el corte.

## BackVynils en los emuladores del equipo

- La app usa `http://10.0.2.2:3000/` por defecto, que es el puerto 3000 del computador visto desde el emulador. No se necesita configuración si BackVynils corre en el mismo computador.
- Para otra instancia: `./gradlew assembleDebug -PVINILOS_API_BASE_URL=http://<host>:3000/`. La URL debe terminar en `/`. El APK publicado en la release siempre apunta a `10.0.2.2`.
- HTTP sin cifrar solo está permitido en la variante debug (`app/src/debug/AndroidManifest.xml`). El APK de entrega es debug, por lo que funciona también en API 28 o superior.
- Backend aislado, sin tocar otros datos ni dejar archivos en el clon de BackVynils:

  ```bash
  git clone https://github.com/TheSoftwareDesignLab/BackVynils.git && cd BackVynils
  git checkout 7178f83662022904dc9e88b8a2292568954b56cf
  docker network create vinilos-net
  docker run -d --name vinilos-db --network vinilos-net -e POSTGRES_DB=vinyls -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:14
  docker build -t vinilos-backend .
  docker run -d --name vinilos-api --network vinilos-net -p 3000:3000 -e DB_HOST=vinilos-db -e DB_USER=postgres -e DB_PASS=postgres -e DB_NAME=vinyls -e USE_SSL=false vinilos-backend
  sed '1s/^\xEF\xBB\xBF//' sql/data.sql | docker exec -i vinilos-db psql -q -U postgres -d vinyls -v ON_ERROR_STOP=1
  curl http://localhost:3000/albums
  ```

  `sql/data.sql` empieza con una marca BOM que `psql` no acepta; el `sed` la quita. Los datos quedan con cuatro álbumes (100 a 103), la banda 101, el músico 100 y los coleccionistas 100 y 101. Para borrar todo: `docker rm -f vinilos-api vinilos-db && docker network rm vinilos-net`.
- Pruebas de contrato desde el computador, sin emulador:

  ```bash
  HU12_REAL_URL=http://127.0.0.1:3000/ BACKVYNILS_URL=http://127.0.0.1:3000/ ./gradlew testDebugUnitTest
  ```

  Sin esas variables, las pruebas contra el backend real se omiten. Crean datos con prefijo identificable y no los borran; por eso conviene el backend aislado.
- Pruebas instrumentadas contra el backend real: se activan con `-Pandroid.testInstrumentationRunnerArguments.backendReal=true` (ver `scripts/test-collectors-real.sh`).

## Comprobación de este cambio

Ejecución local del 20 de noviembre de 2026 sobre esta rama, con JDK 17, Android SDK 35, Windows 11 y BackVynils `7178f83` aislado en Docker:

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- 175 pruebas unitarias y de integración, 0 fallos, 0 omitidas. Incluyen las 4 de `BandMusicianBackendRealTest` y las 3 de `TrackBackendRealTest` contra el backend real.
- Lint: 0 errores, 19 advertencias.
- APK debug: `versionName 0.4.0`, `versionCode 5`, `minSdk 21`, leído con `aapt`.

No se ejecutaron pruebas instrumentadas en local porque el computador no tiene emulador instalado. La instalación en emulador y la matriz API 21, 29 y 34 quedan a cargo de la CI del PR y de la verificación del APK final.

Si la ruta del proyecto tiene tildes y las pruebas unitarias fallan con `ClassNotFoundException` en todas las clases, ejecutar Gradle desde una unidad sin tildes: `subst V: "<ruta del proyecto>"` y luego `V:\gradlew.bat -p V:\ testDebugUnitTest`.
