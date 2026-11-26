# Cierre W8 — borrador para wiki y reunión

Corte: 26 de noviembre de 2026. No constituye acta de una reunión realizada ni certifica la rúbrica oficial.

## Conciliación de integración

| HU | Responsable | PR | Estado comprobado | Pendiente |
|---|---|---|---|---|
| HU11 | Tibisay | [145](https://github.com/Osaron/vinilos-android/pull/145) | Abierto, borrador | CI, revisión, UI, Figma, APK |
| HU13 | Hernán | [144](https://github.com/Osaron/vinilos-android/pull/144) | Fusionado | Conciliar issue 138 y evidencias |
| HU14 | Donaldo | [143](https://github.com/Osaron/vinilos-android/pull/143) | Fusionado | Corrección [146](https://github.com/Osaron/vinilos-android/pull/146), issue 139 |
| HU15 | Óscar | [142](https://github.com/Osaron/vinilos-android/pull/142) | Fusionado | Conciliar evidencias del issue 141 |

## Borrador de acta W8

Fecha, hora, asistentes y captura: pendientes de confirmar tras reunión real.
Agenda propuesta: revisar integración HU11/13/14/15, corrección HU14, defecto TalkBack #121, pruebas de regresión, perfilamiento, APK y entrega.
Acuerdos, decisiones, responsables de compromisos nuevos y fecha de seguimiento: completar durante la reunión. No registrar asistencia ni acuerdos anticipados.

## Lista de cierre provisional

- [ ] Contrastar requisitos y ponderaciones con rúbrica oficial Coursera.
- [ ] Vincular diseño, criterios, PR y pruebas por HU en wiki.
- [ ] Conciliar columnas del proyecto con integración y aceptación reales.
- [ ] Validar TalkBack, fuente ampliada, orientación y evidencias pendientes.
- [ ] Consolidar perfilamiento, exploración sistemática y aleatoria.
- [ ] Integrar PR pendientes tras revisión independiente y checks.
- [ ] Publicar APK desde rama de release acordada; registrar versión, SHA-256 y prueba de instalación.
- [ ] Confirmar acta y captura de reunión real.
- [ ] Verificar enlaces accesibles y entrega.

## CI

Android ya usa gradle/actions/setup-gradle@v4. Se propone concurrency por workflow, evento y PR/ref para cancelar ejecuciones obsoletas sin cruzar ramas o eventos. Se preservan los triggers, nombres de jobs y matriz API 21/29/34. No se introduce paths-ignore hasta verificar reglas de protección: un workflow omitido puede dejar checks requeridos pendientes. No serializar emuladores detrás de build sin medir impacto. Revisar duplicación push/PR y políticas de checks con el equipo antes de modificar triggers.
