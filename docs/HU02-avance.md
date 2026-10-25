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

## Verificación pendiente

El equipo de desarrollo debe ejecutar y registrar:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleAndroidTest
./gradlew connectedAndroidTest
```

`connectedAndroidTest` requiere un emulador o dispositivo activo. La compatibilidad mínima debe comprobarse en Android API 21 y, si es posible, también en una API moderna.

## Dependencia de integración

Esta implementación parte temporalmente de `feature/HU01-catalogo-albumes`, porque el PR #36 aún no se encontraba integrado en `develop`. Antes de integrar HU02, se debe actualizar la rama sobre el `develop` que ya contenga HU01.
