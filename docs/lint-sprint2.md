# Revisión de Lint – Sprint 2 (corte W5)

Base: `lintDebug` del CI sobre `develop` con HU03 y HU07 integradas (PR #61, ejecución
[42378211786](https://github.com/Osaron/vinilos-android/actions/runs/42378211786)).
Resultado inicial: **0 errores, 27 advertencias**.

## Corregidas

| Regla | Archivos | Cambio | Justificación |
|---|---|---|---|
| `LabelFor` (4) | `fragment_create_album.xml` | Se quitan los `hint` duplicados; cada campo conserva su etiqueta visible con `labelFor`. El formato esperado pasa a la etiqueta: "URL de la portada (http:// o https://)", "Fecha de lanzamiento (dd/mm/aaaa)" | TalkBack leía etiqueta y hint a la vez. Una etiqueta permanente también es mejor que un hint, que desaparece al escribir |
| `DataExtractionRules` (1) | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` | Reglas que excluyen todo de la copia en la nube y de la transferencia entre dispositivos | `allowBackup="false"` no aplica en Android 12+; con las reglas, la app sigue sin respaldar datos en todas las versiones |

## Conservadas a propósito (anotadas en el XML)

| Regla | Archivos | Anotación | Justificación |
|---|---|---|---|
| `Overdraw` (4) | `fragment_album_catalog`, `fragment_album_detail`, `fragment_artist_catalog`, `fragment_create_album` | `tools:ignore="Overdraw"` | El fondo explícito `#FAFAFA` corrige el fondo negro (#43) y transparente (#44) observados en Android 5.0 / API 21. Quitarlo reintroduce esos defectos; el costo es una capa de dibujo por pantalla |
| `UnusedAttribute` – `accessibilityHeading` (3) | Catálogo, detalle y formulario | `tools:targetApi="p"` | Marca encabezados para TalkBack desde API 28; en versiones anteriores se ignora sin efectos |
| `UnusedAttribute` – `foreground`, `clipToOutline` (2) | `item_album.xml` | `tools:targetApi="s"` | Efecto de pulsación y esquinas recortadas en APIs nuevas; en API 21–22 la tarjeta funciona igual sin ellos |
| `UnusedAttribute` – `usesCleartextTraffic` (1) | `src/debug/AndroidManifest.xml` | `tools:targetApi="m"` | Solo en debug, para el backend local por HTTP. En API 21–22 HTTP ya está permitido por defecto |

## Pospuestas al Sprint 3

| Regla | Detalle | Justificación |
|---|---|---|
| `GradleDependency` (11) | Versiones nuevas de AndroidX, Lifecycle y AndroidX Test | Actualizar dependencias antes del corte W5 obliga a repetir la regresión en API 21, 29 y 34 y en dispositivos físicos. Se planifica al inicio del Sprint 3, con una rama propia |
| `OldTargetApi` (1) | `targetSdk = 34` | Subirlo cambia comportamientos en tiempo de ejecución (por ejemplo, edge-to-edge). Requiere su propia validación |

Resultado esperado tras este cambio: 0 errores y 12 advertencias, solo las pospuestas.
