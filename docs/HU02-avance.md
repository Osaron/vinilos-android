# HU02 - Avance del detalle de álbum

## Alcance implementado

- Navegación desde el catálogo usando el `albumId` seleccionado.
- Consulta `GET /albums/{albumId}` mediante Retrofit.
- Separación DTO, Service Adapter, Repository, ViewModel y vista.
- Detalle con portada, intérpretes, fecha, género, sello, descripción y tracks.
- Estados de carga, error y reintento.
- Navegación de regreso al catálogo mediante el back stack.

## Pruebas incluidas

- Contrato HTTP y mapeo del detalle con MockWebServer.
- Validación de identificadores inconsistentes.
- Estados de éxito, error y reintento del ViewModel.
- Flujo Espresso catálogo -> detalle -> catálogo con repositorio controlado.

## Verificación automatizada

La ejecución de GitHub Actions
[40234412802](https://github.com/Osaron/vinilos-android/actions/runs/40234412802)
terminó correctamente después de actualizar HU02 sobre `develop`. Verificó:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

## Verificación pendiente en dispositivo

El equipo de desarrollo todavía debe ejecutar y registrar:

```bash
./gradlew connectedAndroidTest
```

`connectedAndroidTest` requiere un emulador o dispositivo activo. La compatibilidad mínima debe comprobarse en Android API 21 y, si es posible, también en una API moderna.

## Integración

HU01 fue integrada mediante el PR #36. La rama `feature/HU02-detalle-album`
fue actualizada sobre `develop` y el PR #38 apunta a esa rama base. Sus tres
commits están atribuidos a `@h-gordillog` y el PR está listo para revisión.
