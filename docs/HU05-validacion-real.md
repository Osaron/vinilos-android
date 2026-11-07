# HU05 — validación real y preparación de revisión manual

## Integración automatizada

`CollectorBackendRealTest` obtiene el catálogo directamente de GET /collectors y
lo contrasta con la pantalla: cantidad, nombres y correo o texto de respaldo.
Selecciona cada fila y verifica su collectorId; prueba búsqueda y conservación al
rotar. Usa el repositorio real compuesto por la aplicación, sin dobles de prueba.

El workflow `HU05 Backend real` levanta PostgreSQL 14 y BackVynils fijado al commit
7178f83662022904dc9e88b8a2292568954b56cf. Los datos de ejemplo se cargan solo en la
base efímera del runner. Ese SQL contiene DELETE y no se debe ejecutar en una base
compartida. La prueba no modifica coleccionistas.

El argumento `backendReal=true` activa el escenario; sin él queda omitido en la
regresión normal. `scripts/test-collectors-real.sh` ejecuta la prueba y extrae
capturas antes de apagar el emulador, conservando el código de salida de Gradle.
El artefacto `hu05-backend-real` reúne reporte, log y capturas. Su existencia no
acredita revisión visual: hay que inspeccionarlas.

## Revisión manual preparada (no ejecutada)

| Caso | Comprobar | Evidencia a registrar |
|---|---|---|
| Listado | Nombres y correos legibles; icono y espacios correctos | Captura y versión/API |
| Nombre/correo largo | Sin superposición, fila crece o texto legible | Captura con datos de prueba |
| Fuente ampliada | Búsqueda, selección y regreso utilizables | Escala de fuente y captura |
| TalkBack | Nombre de cada tarjeta, orden de foco y botones entendibles | Dispositivo y observaciones |
| Rotación | Conserva búsqueda y selección, sin duplicar llamadas | Pasos y resultado |
| Sin conexión | Mensaje claro y reintento al recuperar red | Error observado y recuperación |
| Rendimiento | Carga, scroll y memoria con el protocolo del equipo | Mediciones físicas reales |

No inventar asistencia, resultados, capturas ni mediciones. Registrar defectos en
issues existentes o nuevos si son distintos, con pasos, resultado esperado/obtenido,
SHA, API/dispositivo y evidencia. No cerrar HU05 solo por pasar este escenario:
faltan HU06, revisión visual/accesibilidad y perfilamiento físico según el plan.

## Ejecución local futura

Con BackVynils local activo y datos de prueba, el emulador usa http://10.0.2.2:3000/:

```bash
bash scripts/test-collectors-real.sh
```

La prueba se ejecuta en API 34 en CI; la regresión controlada conserva API 21/29/34.
No se extrapola esta validación a tres dispositivos físicos.
