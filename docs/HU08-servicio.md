# HU08 · Servicio para asociar una canción a un álbum (#33)

Contrato, DTO, Service Adapter y Repository para `POST /albums/{albumId}/tracks` de
[BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/track/track.controller.ts).

## Contrato verificado contra el backend

| Caso | Solicitud | Respuesta real |
|---|---|---|
| Guardado | `POST /albums/102/tracks` con `{"name":"Decisiones","duration":"5:05"}` | **200** `{"name":"Decisiones","duration":"5:05","album":{"id":102,…},"id":2}` |
| Álbum inexistente | `POST /albums/999999/tracks` | **404** `{"statusCode":404,"message":"The album with the given id was not found"}` |
| Campo faltante | `POST /albums/102/tracks` con `{"name":"x"}` | **400** `{"statusCode":400,"message":"ValidationError: \"duration\" is required"}` |
| Consulta | `GET /albums/{albumId}/tracks` | **200** lista de canciones del álbum, o 404 |

El backend guarda `duration` como texto. El formato `m:ss` lo valida el formulario de #32.

## Piezas

| Archivo | Responsabilidad |
|---|---|
| `model/NewTrack.kt` | Canción validada que envía la UI (`name`, `duration`). |
| `data/TrackService.kt` | `TrackService` (Retrofit), `AddTrackRequestDto`, `TrackResponseDto`, `TrackServiceAdapter` y `RemoteTrackAssociationRepository`. |
| `data/TrackAssociationRepository.kt` | Contrato `TrackAssociationRepository` y `TrackAssociationException.Reason`. |
| `VinilosApplication.trackAssociationRepository` | Punto de composición. Las pruebas de UI lo reemplazan antes del `launch`. |

El adapter recorta nombre y duración, rechaza `albumId <= 0` o campos vacíos sin enviar nada, envía
**una sola** solicitud (OkHttp con `retryOnConnectionFailure(false)`) y comprueba que la respuesta
traiga `id`, nombre y, si viene `album.id`, que sea el álbum pedido. Devuelve `Track` del dominio.

## Errores para #34

| `Reason` | Origen | Acción sugerida en la UI |
|---|---|---|
| `ALBUM_NOT_FOUND` | HTTP 404 | Conservar el borrador y pedir otro álbum. |
| `REJECTED` | HTTP 400 u otro 4xx | Conservar el borrador y mostrar error. |
| `SERVER` | HTTP 5xx | Conservar el borrador y permitir reintentar. |
| `NETWORK` | Sin respuesta, tiempo agotado o conexión cortada | Resultado incierto: llamar `getTracks(albumId)` antes de ofrecer un nuevo envío. |
| `INVALID_RESPONSE` | 200 sin `id`, sin nombre o con otro álbum | Tratar como error y recargar el álbum. |

## Doble de prueba

- **Pruebas JVM:** `app/src/test/.../data/FakeTrackAssociationRepository.kt` registra cada
  `albumId` y canción, guarda por álbum, y permite `failNext(reason)` y `holdNext()` (deja la
  solicitud pendiente para probar el doble toque).
- **Espresso:** como es una `fun interface`, basta un lambda:

```kotlin
(ApplicationProvider.getApplicationContext<VinilosApplication>()).trackAssociationRepository =
    TrackAssociationRepository { albumId, track -> Track(1, track.name, track.duration) }
```

## Validación

- `TrackServiceAdapterTest` (12 casos, MockWebServer): ruta con `albumId`, cuerpo exacto, respuesta,
  otro álbum, sin `id`, JSON inesperado, 404, 400 sin reintento, 5xx, conexión cortada sin reenvío,
  validación previa y consulta de canciones.
- `FakeTrackAssociationRepositoryTest` (4 casos).
- `TrackBackendRealTest` (3 casos) contra BackVynils `7178f83` en Docker con Postgres 14: la canción
  queda en el álbum elegido y no en otro, aparece en `GET /albums/{id}`, el 404 se traduce a
  `ALBUM_NOT_FOUND` y el backend responde 400 sin `duration`. Se omite si no existe `BACKVYNILS_URL`.
  En CI corre en el workflow **HU08 Backend real** (`.github/workflows/tracks-real.yml`), y en local
  con `BACKVYNILS_URL=http://localhost:3000/ scripts/test-tracks-real.sh`.

`minSdk` sigue en 21. No se cambió ninguna dependencia.
