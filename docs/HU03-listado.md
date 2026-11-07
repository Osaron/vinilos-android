# HU03 — pantalla del listado de artistas

## Alcance

El catálogo de álbumes ofrece «Ver artistas». Se abre una pantalla con músicos y
bandas, foto, nombre, tipo, contador y búsqueda sin distinguir mayúsculas ni tildes.
Incluye carga, catálogo vacío, búsqueda sin resultados, error y reintento. «Volver a
álbumes» y Atrás usan el back stack. El ViewModel conserva búsqueda y selección en
rotación; no se promete persistencia tras muerte del proceso.

Se usó `docs/wireframes/flujo-visitante-y-selector-rol/Artistas.png` como referencia:
lista vertical y tarjetas. Diferencias: se muestra Músico/Banda en vez de país/género
(campos ausentes del contrato consultado); el acceso temporal es un botón desde el
catálogo, hasta incorporar la navegación global. No se incluye un botón inactivo
de coleccionistas ni se declara terminado el diseño global.

## Arquitectura y contrato con HU04

`ArtistCatalogFragment` → `ArtistCatalogViewModel` → `ArtistRepository` →
`ArtistServiceAdapter` → `ArtistService` (`GET /musicians` y `GET /bands`).
El repositorio se compone en `VinilosApplication` y puede sustituirse en pruebas.
Las peticiones Retrofit son suspendidas y las imágenes usan Coil.

Al tocar una tarjeta se conserva la selección en el ViewModel y se publica el
resultado `artist_selected` con `artistId` (Int), `artistType` (MUSICIAN/BAND) y
`artistName` (String). HU04 deberá consumirlo y abrir el detalle por tipo e ID.
Mientras HU04 no esté integrada se muestra confirmación de selección; no se simula
un detalle. La selección y DiffUtil usan tipo e ID, no únicamente ID.

## Validación

- Cinco pruebas JVM nuevas: búsqueda, vacío, error/reintento sin solicitudes
  duplicadas, selección por tipo e ID y selección eliminada al desaparecer.
- Cuatro pruebas Espresso nuevas con repositorios controlados: selección/búsqueda/
  rotación/regreso, catálogo vacío, búsqueda sin resultados y reintento.
- XML analizado localmente y revisión de formato con git diff --check.
- Se intentó `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`;
  la descarga de Gradle 8.9 falló con Network is unreachable. No se declaran pruebas
  aprobadas hasta comprobar la CI del PR.

## Pendientes antes del cierre

Revisión de otro integrante, CI, integración y recorrido contra backend real.
Validar visualmente las imágenes, tamaños de fuente y TalkBack en un dispositivo;
la revisión XML no sustituye una comprobación visual. El trabajo de detalle HU04 y
la navegación global siguen pendientes. Relacionado con #9, #11 y #12.
