# HU03 — validación contra BackVynils real

`ArtistBackendRealTest` consulta directamente GET /musicians y GET /bands y compara
el contador, número de filas y nombres de la pantalla con esas respuestas. Comprueba
selección por ID/tipo para un músico y una banda, búsqueda y conservación al rotar.
No sustituye el repositorio de la aplicación ni usa respuestas HTTP simuladas.

La ejecución normal mantiene este escenario omitido salvo que se pase
`backendReal=true`. El workflow `HU03 Backend real` prepara PostgreSQL 14 y una
versión fija de BackVynils en el runner, carga los datos de ejemplo únicamente en
esa base efímera y ejecuta el escenario en API 34. No usa ni modifica bases del equipo.
No se ejecuta el SQL de ejemplo contra una base compartida: contiene DELETE.

Ejecución manual, con el backend local encendido y datos de ejemplo:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.backendReal=true \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ArtistBackendRealTest
```

El emulador usa por defecto http://10.0.2.2:3000/. Los reportes y logs del workflow
se guardan como artefacto `hu03-backend-real`. Añadir un escenario real no acredita
que haya pasado: consultar la ejecución del PR antes de cerrar #12.

Pendientes independientes: revisión visual en dispositivo, TalkBack y fuente ampliada,
perfilamiento físico, detalle HU04 y aprobación/integración del cambio.
