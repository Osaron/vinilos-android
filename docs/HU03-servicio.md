# HU03 — contrato y servicio del listado de artistas

Avance del 6 de noviembre de 2026. Relacionado con #10 y la parte de servicio de #12.

## Contrato comprobado

Se consultaron `src/musician/musician.controller.ts`, `src/band/band.controller.ts` y
`src/performer/performer.entity.ts` de [BackVynils, rama master](https://github.com/TheSoftwareDesignLab/BackVynils/tree/master/src).
El catálogo reúne `GET /musicians` y `GET /bands`. Campos comunes: id, name, image,
description. No se presupone un endpoint `/artists`.

`ArtistRepository.getArtists()` devuelve músicos seguidos de bandas. `Artist` conserva
`id` y `type` (`MUSICIAN` o `BAND`); la futura HU04 debe navegar con ambos valores a
`/musicians/{id}` o `/bands/{id}`. Esta es una propuesta de contrato para revisión del
equipo, no un acuerdo ya confirmado por Hernán.

El adaptador valida ID positivo y nombre no vacío. Los campos visuales ausentes se
normalizan a cadenas vacías. Si falla una ruta, se propaga el error y no se muestra
un catálogo parcial como si estuviera completo. La pantalla podrá reintentar.

## Verificación

Se añadieron siete pruebas JVM con MockWebServer: rutas y adaptación de ambos tipos,
catálogo vacío, solo bandas, identidad inválida, error parcial, recuperación tras HTTP
y desconexión de red. No equivalen a pruebas con backend real.

Se intentó `./gradlew testDebugUnitTest`: el wrapper no pudo descargar Gradle 8.9
(`Network is unreachable`). Las pruebas nuevas todavía no se declaran aprobadas;
la CI debe compilar y ejecutar la suite. `git diff --check` se usa para revisar formato.

## Pendiente

- Revisar este contrato con quien implemente HU04.
- Conectar el repositorio al contenedor de dependencias al incorporar la pantalla.
- Implementar diseño, ViewModel, estados, listado y selección (#9 y #11).
- Completar Espresso y validación contra backend real (#12).
- Revisar PR, CI e integración antes de cerrar #10; este avance no completa HU03.
