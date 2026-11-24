# HU14 · Asignar un premio a un músico o una banda

Issue [#139](https://github.com/Osaron/vinilos-android/issues/139) · rama `feature/HU14-premio-artista` · responsable Donaldo · revisa Hernán.

## Alcance, rol y criterios

Como **coleccionista** quiero registrar qué premio recibió un artista y cuándo. La acción solo aparece con un coleccionista activo, igual que HU12 y HU15; un visitante ve los premios pero no puede asignarlos.

| Criterio de la wiki | Cómo se cumple |
|---|---|
| El premio y la fecha son obligatorios | **Asignar premio** queda deshabilitado hasta tener los dos y un texto dice qué falta |
| Se conserva el artista y su tipo incluso al rotar | ID y tipo viajan en los argumentos del fragmento; premio y fecha se guardan en `SavedStateHandle` |
| Tras guardar y recargar se muestra el reconocimiento en el artista correcto | El detalle tiene una sección **Premios** que se vuelve a leer del backend al regresar |
| Verificar asociaciones repetidas con el backend | Verificado: el backend las duplica. La app lo impide (ver contrato) |

Estimación propuesta: 5 puntos (contrato con cruce de dos lecturas, formulario con fecha y siete escenarios de interfaz).

## Contrato comprobado con BackVynils

Probado el 23 de noviembre de 2026 contra una instancia aislada en Docker (commit `7178f83`, base de datos propia con `sql/data.sql`). Fuente: [controlador](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/performerprize/performerprize.controller.ts) y [servicio](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/performerprize/performerprize.service.ts).

| Solicitud | Respuesta observada |
|---|---|
| `GET /prizes` | 200. Cada premio trae `id`, `name`, `organization`, `description` y `performerPrizes` con `id` y `premiationDate` |
| `GET /musicians/{id}` · `GET /bands/{id}` | 200. `performerPrizes` solo trae `id` y `premiationDate`, **sin el nombre del premio** |
| `POST /prizes/{prizeId}/musicians/{id}` con `{"premiationDate":"2020-05-01"}` | 200 con `premiationDate`, `prize`, `performer` e `id`. Al leer de nuevo: `2020-05-01T00:00:00.000Z` |
| `POST /prizes/{prizeId}/bands/{id}` con fecha ISO completa | 200, mismo formato |
| El mismo POST dos veces | 200 las dos veces: **quedan dos entregas iguales** |
| `premiationDate: "01/05/2020"` | 200, pero guarda **5 de enero** (lo lee como mes/día) |
| `premiationDate: "no-fecha"` | 400 `"premiationDate" must be a valid date` |
| Cuerpo sin `premiationDate` | **500** |
| Premio o artista inexistente | 404 |
| ID de banda en la ruta `musicians` (o al revés) | 200: el POST **no comprueba el tipo** |

Decisiones que salen de ahí:

1. **La fecha se envía como `yyyy-MM-dd`.** Es el único formato corto que el backend guarda sin ambigüedad. En pantalla se muestra `dd/MM/yyyy`.
2. **La app evita los repetidos.** Antes de enviar lee los premios del artista; si ya tiene ese premio con esa fecha no envía. El mismo premio en otra fecha sí se permite (un Grammy de 1978 y otro de 1985 son entregas distintas).
3. **El tipo se valida con la lectura.** `GET /musicians/{id}` responde 404 si el ID es de una banda, así que la lectura previa por la ruta del tipo protege al POST, que no lo comprueba.
4. **El nombre del premio se obtiene cruzando** los IDs de `performerPrizes` del artista con los de `GET /prizes`.
5. **Sin reintentos automáticos** de OkHttp. Si se pierde la respuesta, la app consulta al artista antes de ofrecer reintentar.

Para HU13 (Hernán): `POST /prizes` exige `name`, `description` y `organization` como texto no vacío. HU14 solo necesita `id`, `name` y `organization` de `GET /prizes`, así que un premio creado por HU13 aparece en el selector sin cambios.

## Diseño: frames para Figma

Flow 11 en el [Figma del equipo](https://www.figma.com/design/MkdUukCRycnXvpfRPQj8Tp/Vinilos---Wireframes-UX-UI), reutilizando el detalle de artista (HU04), el botón secundario y las opciones de 48 dp del selector de HU15.

| Frame | Contenido | Sale hacia |
|---|---|---|
| `HU14-01 Detalle · premios` | Detalle de músico o banda con la sección **Premios** (un renglón por premio: nombre · organización · fecha) y **+ Agregar premio** | Toque en **+ Agregar premio** → `HU14-02` |
| `HU14-01b Detalle · sin coleccionista` | Igual, sin el botón | — |
| `HU14-02 Asignar · cargando` | Atrás, título **Asignar premio**, «Artista · Músico/Banda», indicador de carga | Carga correcta → `HU14-03` · fallo → `HU14-02b` |
| `HU14-02b Asignar · error de carga` | **No se pudo cargar. Reintentar** | Reintentar → `HU14-02` |
| `HU14-02c Asignar · sin premios` | «Todavía no hay premios registrados. Crea uno primero.» | Atrás → `HU14-01` |
| `HU14-03 Asignar · vacío` | Lista de premios (radio), «Sin fecha elegida.», **Elegir fecha**, ayuda «Elige un premio y la fecha…», botón deshabilitado | Elegir premio → `HU14-04` · **Elegir fecha** → `HU14-05` |
| `HU14-04 Asignar · premio elegido` | Premio marcado, botón aún deshabilitado | **Elegir fecha** → `HU14-05` |
| `HU14-05 Selector de fecha` | Calendario del sistema, sin fechas futuras, **Cancelar** / **Aceptar** | Aceptar → `HU14-06` · Cancelar → frame anterior |
| `HU14-06 Asignar · listo` | Premio marcado, «Fecha de entrega: 01/05/2020», **Cambiar fecha**, **Asignar premio** habilitado | Asignar → `HU14-07` |
| `HU14-06b Asignar · repetido` | «Este artista ya tiene ese premio con esa fecha…», botón deshabilitado | Cambiar premio o fecha → `HU14-06` |
| `HU14-07 Asignar · guardando` | «Asignando premio…», controles deshabilitados | Éxito → `HU14-08` · fallo → `HU14-07b` |
| `HU14-07b Asignar · error al guardar` | «No se pudo asignar el premio. Conservamos el premio y la fecha para reintentar.» | Asignar → `HU14-07` |
| `HU14-08 Detalle · premio asignado` | Aviso «Premio asignado.» y el premio nuevo en la lista | — |

## Implementación

Misma arquitectura que HU15: Fragment → ViewModel → Repository → Service Adapter.

| Archivo | Papel |
|---|---|
| `model/Prize.kt` | `Prize`, `ArtistPrize` y `PrizeDate` (validación y formato de la fecha) |
| `data/ArtistPrizeService.kt` | Retrofit, DTO, `ArtistPrizeServiceAdapter` y `RemoteArtistPrizeRepository` |
| `ui/artists/AssignPrizeViewModel.kt` | Estado del formulario y `ArtistPrizesViewModel` para la sección del detalle |
| `ui/artists/AssignPrizeFragment.kt` · `fragment_assign_prize.xml` | Formulario: premio, fecha con `DatePickerDialog` (API 21+), estados |
| `ui/artists/ArtistDetailFragment.kt` · `fragment_artist_detail.xml` | Sección **Premios**, botón y recarga al volver |
| `VinilosApplication.kt` | `artistPrizeRepository`, sustituible en pruebas |

Accesibilidad: títulos marcados como encabezado, opciones y botones de 48 dp, descripción completa por opción («Seleccionar premio Grammy Award, de NARAS») y por renglón del detalle («Grammy Award, de NARAS, recibido el 10/12/1978»), y fecha, ayuda y errores en regiones que TalkBack anuncia solas. Los botones del calendario están en español aunque el dispositivo esté en otro idioma.

## Casos de prueba

| Caso | Prueba | Qué verifica |
|---|---|---|
| ESC-53 | `ArtistPrizeFlowTest` | Sin coleccionista activo se ven los premios pero no **+ Agregar premio** |
| ESC-54 | `ArtistPrizeFlowTest` | Músico: elegir premio y fecha, rotar (se conservan), guardar, aviso y premio nuevo en el detalle; se envió tipo MUSICIAN, ID 50 |
| ESC-55 | `ArtistPrizeFlowTest` | Banda: el envío usa tipo BAND y su ID |
| ESC-56 | `ArtistPrizeFlowTest` | Un rechazo conserva premio y fecha; el reintento guarda |
| ESC-57 | `ArtistPrizeFlowTest` | Mismo premio y misma fecha: bloqueado. Otra fecha: permitido |
| ESC-58 | `ArtistPrizeFlowTest` | Fallo de red al cargar: **Reintentar** recupera; **Atrás** vuelve al detalle sin enviar |
| ESC-59 | `ArtistPrizeFlowTest` | Accessibility Test Framework sin ERROR en detalle y formulario, descripciones y 48 dp |
| Global | `AppAccessibilityTest` | La pantalla de HU14 entra en la revisión de toda la app |
| Unitarias | `AssignPrizeViewModelTest` (15) | Obligatorios, fecha futura, repetidos, doble toque, borrador tras error, respuesta perdida sin duplicar, recreación |
| Unitarias | `ArtistPrizeServiceAdapterTest` (12, MockWebServer) | Rutas por tipo, cuerpo con la fecha, cruce de lecturas, datos inválidos, confirmación inconsistente, 404, sin reintento |
| Backend real | `ArtistPrizeBackendRealTest` (5) + `artist-prizes-real.yml` | Tras asignar solo cambian los premios del artista elegido (músico y banda), el nombre se recupera al recargar, premio inexistente 404, tipo equivocado 404 |

## Verificación

Ejecutado el 23 de noviembre de 2026 en local (JDK 17, Windows):

- `testDebugUnitTest`: 234 pruebas, 0 fallos, 15 omitidas (las de backend real, sin `BACKVYNILS_URL`).
- Con `BACKVYNILS_URL` apuntando al BackVynils aislado: las 5 de `ArtistPrizeBackendRealTest` aprobadas.
- `lintDebug`: 0 errores; ninguna advertencia en los archivos de HU14.
- `assembleDebug` y `assembleDebugAndroidTest`: compilan.

Pendiente:

- `ArtistPrizeFlowTest` y `AppAccessibilityTest` en emulador: no se ejecutaron en local (no hay emulador en este equipo). Las corre la CI en API 21, 29 y 34 al subir la rama.
- Recorrido manual con TalkBack y con texto ampliado.
- Frames en Figma (tabla de arriba) y enlace en la wiki.
- Revisión de Hernán.

Para repetir la prueba de contrato (escribe datos: usar solo una instancia desechable):

```bash
BACKVYNILS_URL=http://localhost:3000/ ./gradlew testDebugUnitTest --tests '*ArtistPrizeBackendRealTest' --rerun
```

En Windows, si la ruta del proyecto tiene tildes y las pruebas fallan con `ClassNotFoundException`, ejecutarlas desde un enlace sin tildes (`mklink /J C:\vinilos <ruta del proyecto>`).

## Cierre W8: CI, tag, APK y SHA-256

Estado al 23 de noviembre: `main` publica `v0.4.1-w7` con `versionCode 6`; `develop` todavía declara `versionCode 5` y `versionName 0.4.0`. El corte final necesita un código mayor que 6.

1. Jueves 26: con los PR aprobados de HU11, HU13, HU14 y HU15 en `develop`, decidir qué entra en el candidato.
2. Rama `release/v0.5.0-w8` desde `develop`: subir a `versionCode 7` y `versionName 0.5.0`, actualizar el README. PR a `main`.
3. Esperar la CI del PR en verde (unitarias, lint, API 21, 29 y 34 y los workflows de backend real). Anotar el ID de la ejecución.
4. Integrar, crear el tag `v0.5.0-w8` sobre el commit de `main` y ejecutar `scripts/corte-apk.sh preparar v0.5.0-w8 <id-ejecución>`: comprueba que el código probado es el del tag y deja el APK con su SHA-256.
5. Publicar la release con el APK y el hash, y ejecutar `scripts/corte-apk.sh verificar v0.5.0-w8` para cotejar el archivo descargado.
6. Pasar el enlace y el SHA-256 a Óscar (instalación en API 21) y a Tibisay (wiki y Acta 08).
