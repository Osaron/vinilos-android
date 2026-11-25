# HU13 · Catálogo y creación de premios

Rama `feature/HU13-crear-premio`, basada en develop `f5cf482` que ya incluye HU14 (#143). Diseño: [Flow 12](https://www.figma.com/proto/MkdUukCRycnXvpfRPQj8Tp/Vinilos---Wireframes-UX-UI?node-id=154-2445&starting-point-node-id=154-2445&scaling=scale-down).

## Recorrido

Desde Álbumes, **Ver premios** abre el catálogo con nombre, organización y descripción. **Crear premio** reutiliza el selector de coleccionista si no hay uno activo. El formulario pide los tres campos, valida contenido después de `trim`, conserva el borrador al recrear la pantalla y protege el envío en ViewModel, además del botón.

Un HTTP 200 confirmado retorna al catálogo y repite GET para mostrar el premio. Si ese GET falla, se conserva la confirmación y Reintentar repite solo la lectura. Ante un rechazo 4xx confirmado (excepto 408), se conservan los campos y se permite corregir o reintentar explícitamente.

Una excepción de red, HTTP 408/5xx o una confirmación 2xx inválida se trata como **resultado incierto**: no se ofrece otro POST. Ver catálogo abre una pantalla de revisión y Volver al borrador conserva el formulario original. La misma combinación de textos no permite identificar de forma inequívoca una creación, pues el backend admite duplicados. Descartar el borrador requiere una decisión explícita, con advertencia si la escritura podría haberse aplicado.

## Contrato y arquitectura

- Se comparte `Prize`/`PrizeDto` con HU14; `description` es opcional en el modelo de lectura para conservar compatibilidad con usos anteriores. El ID sigue siendo la identidad.
- `PrizeService` → `PrizeServiceAdapter` → `RemotePrizeRepository`; dependencias sustituibles en `VinilosApplication`.
- GET `/prizes`; POST `/prizes`, cuerpo exacto `{name, description, organization}`. No se envía coleccionista, ID ni asociaciones. El rol de coleccionista es una decisión de UI basada en el patrón del equipo, no autorización del servidor.
- Retrofit suspend para HTTP, sin reintentos automáticos de escritura en OkHttp. La validación de una respuesta consistente es parte del Service Adapter.
- ViewModels conservan estado, cargan fuera de callbacks de vistas y propagan cancelación. La marca de escritura se guarda en `SavedStateHandle` antes del POST: restaurar una escritura interrumpida no la repite.
- Fragments recogen estado con `repeatOnLifecycle`; se liberan adaptador y diálogo al destruir la vista. No se fuerza una transacción anidada al regresar de la selección de coleccionista.
- HU14 sigue obteniendo premios desde GET al abrir su selector; no se acopla a las vistas del formulario HU13. La prueba integrada completa se realiza en fase 3/4.

## Diseño y accesibilidad

Roboto del sistema, grises, campos delineados, botones con al menos 48 dp, etiquetas `labelFor`, mensajes por campo, anuncio del primer error, carga/resultado mediante live region y formulario desplazable. Se reutilizan estilos AppCompat y controles del proyecto, sin nuevas dependencias.

El shell actual de la app usa accesos por botones y navegación de Fragments: se conserva ese patrón en lugar de introducir una navegación inferior global ni dibujar falsos indicadores del sistema. Esta es una diferencia preexistente respecto al shell ilustrado en Figma; los botones Volver nativos también siguen el patrón de la app. La comparación visual del formulario se documenta con capturas; no se afirma igualdad de píxel de todo el shell.

## Verificación

Pruebas JVM nuevas: `PrizeCreationTest` (13) y `PrizeServiceAdapterTest` (6), incluyendo doble toque concurrente, campos blancos, JSON de tres campos, restauración y prohibición de reenvío incierto. La prueba de lectura de HU14 se actualiza para comprobar que también conserva la descripción; sus rutas de asignación no cambian.

Pruebas de humo Android: `PrizeCreationFlowTest`: validación y recreación, creación confirmada, revisión del catálogo conservando borrador y elección de coleccionista. Usan repositorios controlados, no demuestran una creación HTTP real desde UI.

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PVINILOS_API_BASE_URL=http://10.0.2.2:3001/
```

La URL 3001 apunta al backend aislado de validación. La versión de entrega se prepara en la fase de release, no a partir de este APK de desarrollo. Pendientes: matriz funcional completa, HTTP real desde UI/HU14, Scanner/TalkBack, texto ampliado, CI API 21/29/34, revisión y PR.
