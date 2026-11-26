# HU11 — Plan de pruebas y evidencias W8

Issue [#140](https://github.com/Osaron/vinilos-android/issues/140). Este plan propone escenarios; no certifica su ejecución ni sustituye los criterios oficiales de Coursera.

## Preparación

BackVynils aislado con datos de prueba, dos coleccionistas A/B y un álbum no asociado a A. Registrar versión APK, SHA del commit, API, dispositivo, fecha, resultado y enlace a evidencia. No usar datos de producción.

| ID | Escenario | Resultado esperado | Estado |
|---|---|---|---|
| HU11-01 | Coleccionista A agrega álbum disponible con precio 15 y estado activo | Regresa al detalle; GET confirma relación y datos | Pendiente prueba UI |
| HU11-02 | Consultar colección B antes y después | Colección B permanece sin cambios | Verificación backend previa documentada; UI pendiente |
| HU11-03 | Visitante y coleccionista distinto de A | No se ofrece edición de colección A | Pendiente |
| HU11-04 | Buscar álbum asociado previamente | No aparece entre disponibles | Pendiente |
| HU11-05 | Pulsar guardar repetidamente | Una sola asociación en uso secuencial | Pendiente |
| HU11-06 | Precio vacío, negativo o fuera del rango Int | Error de campo, sin POST | Pendiente UI |
| HU11-07 | Precio cero y estado inactivo | Se envían los valores correctos | Pendiente UI |
| HU11-08 | Interrumpir red antes de guardar | Mensaje de error y borrador recuperable | Pendiente |
| HU11-09 | Simular respuesta perdida después de persistir | Reconsulta antes de nuevo POST; sin duplicado secuencial | Pendiente |
| HU11-10 | Rotar con álbum, precio y estado seleccionados | Conserva borrador y selección | Pendiente |
| HU11-11 | Rotar durante envío | No duplicar asociación; resultado recuperable | Pendiente |
| HU11-12 | TalkBack: buscar, seleccionar, editar precio y guardar | Etiquetas comprensibles, foco útil, anuncios de estado | Pendiente |
| HU11-13 | Fuente 130% y pantalla pequeña | Controles legibles y alcanzables por desplazamiento | Pendiente |
| HU11-14 | Volver o cancelar sin confirmar | No se crea asociación | Pendiente |

## Evidencia disponible

- [Documentación del contrato](HU11-album-coleccion.md): registra comprobación previa de POST/GET en backend efímero.
- [CI Android del commit 3dcb1b4](https://github.com/Osaron/vinilos-android/actions/runs/45865991861): build, lint, unitarias e instrumentadas API 21/29/34 exitosas; no demuestra ejecución de cada escenario HU11.
- [Pruebas unitarias nuevas](../app/src/test/java/co/edu/uniandes/vinilos/data/CollectorAlbumRepositoryTest.kt): cinco casos de contrato y validación; [CI del commit e2e8d41](https://github.com/Osaron/vinilos-android/actions/runs/45878942239) en progreso al registrar este plan.

## Cierre

Adjuntar resultados reales por escenario, enlace al PR, revisión independiente, Figma y APK de entrega. La unicidad concurrente entre clientes requiere garantía del backend. No marcar como aprobada una prueba pendiente.
