# HU04 Decisiones técnicas para detalle de artista

## Propósito

Este documento fija las decisiones de arquitectura, concurrencia, memoria y pruebas que se aplicarán a HU04 antes de implementar el detalle de músicos y bandas. Las decisiones se basan en el módulo 6 del curso y en la arquitectura que ya funciona en HU01, HU02, HU03, HU05 y HU07.

Rama de trabajo: `feature/HU04-detalle-artista`.

Base revisada: `0655539001a07640ed55b58ca5a3cd71164ac89a` de `origin/develop`.

## Material revisado

- Coursera: Niveles y tipos de pruebas.
- Coursera: Cómo evitar bloqueos en el hilo principal.
- Coursera: Codelab Corrutinas en Kotlin.
- Codelab enlazado por el curso: [Cómo ejecutar código asíncrono haciendo uso de corrutinas en Kotlin](https://thesoftwaredesignlab.github.io/SE4MACodelabs/coroutines-kotlin/index.html#0).
- Libro enlazado por el curso: [The single thread game](https://uniandes-se4ma.gitlab.io/books/chapter12/the-single-thread-game.html).
- Libro enlazado por el curso: [Using multi-threading like a boss](https://uniandes-se4ma.gitlab.io/books/chapter12/using-multi-threading-like-a-boss.html).
- Libro enlazado por el curso: [Memory bloats](https://uniandes-se4ma.gitlab.io/books/chapter11/memory-bloats.html).

## Hallazgos aplicables

### Niveles y propósitos de prueba

El curso distingue unidad, integración, sistema y aceptación según la composición que se prueba. También separa el nivel de prueba de su propósito: funcional o no funcional, positivo o negativo, y caja negra o caja blanca.

Para HU04 se aplicará la siguiente distribución:

| Nivel | Objetivo en HU04 | Herramienta o entorno |
|---|---|---|
| Unidad | Estados, reintentos, selección de tipo e ID y cancelación del ViewModel | JUnit y coroutines-test |
| Integración | Ruta Retrofit, respuesta, adaptación a dominio y errores HTTP/red | MockWebServer |
| Sistema | Navegación HU03 a HU04, renderizado, rotación y regreso | Espresso con repositorio controlado |
| Aceptación | Recorrido real con músico y banda contra BackVynils | Emulador API 35 y backend local |

Cada nivel tendrá escenarios positivos y negativos. La suite rápida de unidad e integración se ejecutará en cada cambio; los recorridos más costosos se reservarán para CI y validación del corte.

### Hilo principal

La interfaz, los eventos y el renderizado comparten el hilo principal. Una operación larga allí puede producir retrasos y ANR. Por esa razón:

- La vista solo enviará eventos y renderizará estados.
- No se procesará red, JSON ni datos pesados dentro del Fragment.
- Retrofit conservará métodos `suspend`; su integración asíncrona evita bloquear el hilo principal durante la red.
- No se añadirá `runBlocking` al código de producción.
- No se añadirá `GlobalScope`.
- La actualización de estado será breve y se realizará desde el ViewModel.
- Las imágenes se cargarán con Coil y un placeholder local.

No se agregará `withContext(Dispatchers.IO)` alrededor de una llamada suspend de Retrofit solo por costumbre. Se usará un dispatcher explícito únicamente si aparece trabajo bloqueante o de procesamiento que lo requiera y se puede demostrar en una prueba o medición.

### Corrutinas y cancelación

El ciclo de vida del trabajo asíncrono debe corresponder al objeto que necesita el resultado.

- `ArtistDetailViewModel` lanzará la consulta con `viewModelScope`.
- Al destruir definitivamente el ViewModel, su trabajo quedará cancelado.
- `CancellationException` se volverá a lanzar y no se convertirá en estado de error.
- Un indicador interno evitará solicitudes duplicadas durante carga o reintento.
- El estado sobrevivirá una rotación mediante el mismo ViewModel.
- El Fragment observará con `viewLifecycleOwner.repeatOnLifecycle(STARTED)`.
- El trabajo de la vista terminará al destruirse su `View`; no se conservará el binding como propiedad de larga duración.

### Memoria

Las causas relevantes para esta historia son referencias fuertes innecesarias, objetos retenidos después de navegar y manejo inadecuado de imágenes.

- El ViewModel no recibirá ni conservará `Activity`, `Fragment`, `View`, binding o `Context`.
- Solo conservará identificadores, tipo de artista, estado inmutable y dependencias de datos.
- El binding será local a `onViewCreated`, siguiendo el patrón de `AlbumDetailFragment`.
- Coil administrará la carga de imágenes; el modelo no guardará bitmaps.
- No se mantendrán listas, vistas ni adaptadores del catálogo dentro del detalle.
- La revisión de #52 repetirá navegación HU03 → HU04 → Atrás para buscar crecimiento o instancias retenidas.

## Decisiones de arquitectura

### Separar catálogo y detalle

`ArtistRepository` es actualmente una `fun interface` con `getArtists()` y se usa con lambdas en pruebas. Agregar un segundo método rompería esos dobles y ampliaría innecesariamente el contrato del catálogo.

Se creará un contrato independiente para el detalle, con un nombre equivalente a `ArtistDetailRepository`. Este contrato recibirá tipo e ID y retornará un modelo de dominio de detalle.

Beneficios:

- HU03 permanece estable.
- Las pruebas existentes no requieren cambios ajenos a HU04.
- Los dobles de HU04 son pequeños y específicos.
- La composición de dependencias sigue explícita en `VinilosApplication`.

### Service Adapter

El servicio tendrá rutas independientes para músico y banda. El Service Adapter decidirá la ruta usando `ArtistType`, validará identidad y campos obligatorios y transformará DTO a dominio. El DTO no llegará a la UI.

El contrato definitivo de campos se fijará después de consultar respuestas reales de BackVynils. No se mostrarán datos inventados para igualar el wireframe.

### Estado de interfaz

El detalle usará un único estado observable con estas situaciones mutuamente verificables:

- carga;
- contenido;
- error recuperable.

El reintento no borrará el tipo ni el ID. Mientras exista una solicitud activa, otra pulsación no iniciará una segunda consulta.

### Navegación

HU03 ya publica `artistId`, `artistType` y `artistName`. HU04 consumirá esos valores y validará ID positivo y tipo reconocido. La pantalla de detalle no volverá a buscar al artista por nombre.

La navegación de regreso debe conservar el ViewModel de HU03, incluida la consulta escrita y la lista visible. La implementación seguirá el patrón actual con back stack y Fragment Result, salvo que el análisis de la fase de diseño encuentre una alternativa más consistente con el proyecto.

## Matriz inicial de pruebas

| ID | Nivel | Escenario | Resultado esperado |
|---|---|---|---|
| HU04-U01 | Unidad | Cargar músico | Estado pasa de carga a contenido del ID solicitado |
| HU04-U02 | Unidad | Cargar banda | Estado pasa de carga a contenido del ID solicitado |
| HU04-U03 | Unidad | Error de repositorio | Estado de error recuperable |
| HU04-U04 | Unidad | Reintento | Nueva consulta y recuperación a contenido |
| HU04-U05 | Unidad | Pulsaciones repetidas | Una sola consulta activa |
| HU04-U06 | Unidad | Cancelación | Se propaga `CancellationException` sin mostrar error |
| HU04-I01 | Integración | Músico por ID | Método y ruta correctos; DTO adaptado a dominio |
| HU04-I02 | Integración | Banda por ID | Método y ruta correctos; DTO adaptado a dominio |
| HU04-I03 | Integración | 404 | Error propagado de forma controlada |
| HU04-I04 | Integración | 5xx | Error propagado de forma controlada |
| HU04-I05 | Integración | Desconexión | Error propagado de forma controlada |
| HU04-I06 | Integración | Identidad o respuesta inválida | No se publica contenido inconsistente |
| HU04-S01 | Sistema | Seleccionar músico en HU03 | Abre el músico seleccionado |
| HU04-S02 | Sistema | Seleccionar banda en HU03 | Abre la banda seleccionada |
| HU04-S03 | Sistema | Carga pendiente | Indicador visible y contenido oculto |
| HU04-S04 | Sistema | Error y reintento | Mensaje visible y recuperación exitosa |
| HU04-S05 | Sistema | Regreso | Vuelve a HU03 y conserva búsqueda |
| HU04-S06 | Sistema | Rotación | Conserva detalle sin segunda consulta |
| HU04-A01 | Aceptación | Músico real | Datos coinciden con BackVynils |
| HU04-A02 | Aceptación | Banda real | Datos coinciden con BackVynils |
| HU04-A03 | Aceptación | Backend detenido y restaurado | Error claro y reintento exitoso |

## Riesgos que deben comprobarse

- Los endpoints de músico y banda pueden retornar relaciones con estructuras diferentes.
- El wireframe puede pedir campos que BackVynils no entrega.
- Una banda y un músico pueden compartir el mismo ID; por eso el tipo es parte de la identidad.
- El `ArtistDto` actual del catálogo puede ser insuficiente para el detalle.
- Una imagen remota grande o inválida puede afectar memoria o diseño; se requiere placeholder y prueba visual.
- La navegación actual solo publica un Fragment Result y todavía no abre HU04.

## Criterio para comenzar implementación

Antes de crear DTO o layout se deben obtener respuestas reales de al menos un músico y una banda, comparar sus campos con el wireframe y documentar las diferencias. Esa verificación corresponde a la siguiente fase.

## Contrato confirmado en BackVynils local

Validación completada el 2026-11-08 contra `GET /musicians/100` y `GET /bands/101` (HTTP 200), además de las rutas de listado. Los detalles completos y la comparación campo por campo están en [`../evidencias/w5-hernan/hu04/pruebas/CONTRATO_BACKEND_2026-11-08.md`](../../evidencias/w5-hernan/hu04/pruebas/CONTRATO_BACKEND_2026-11-08.md) desde el directorio de trabajo `moviles`.

El modelo común puede contener identidad (`id` + `type`), nombre, imagen opcional, descripción y álbumes. Se conservan como campos opcionales `birthDate` para músico y `creationDate` para banda. El performer no ofrece país ni género/estilo; esos textos del wireframe no se deben inventar. El género de cada álbum sí está disponible. La sección de álbumes obtiene su cantidad de la lista. `performerPrizes` solo expone identificador y fecha, por lo que no se incluye en la pantalla sin completar su contrato.

Para errores, el endpoint no fue manipulado manualmente: 404, 5xx y desconexión se probarán con MockWebServer y el manejo del estado se probará en las fases de implementación.
