# Cierre del Sprint 1 — HU01 y HU02

Revisión de requisitos: 31 de octubre de 2026 (Colombia). Fecha límite confirmada en Coursera: 1 de noviembre de 2026, 23:59 GMT-5.

## Alcance y trazabilidad

La release prepara el código de `develop` 700475e para `main`. Incluye catálogo, búsqueda, detalle con tracks, regreso al catálogo, estados de carga/error y correcciones para Android 5. No incorpora HU de Sprint 2 o Sprint 3.

El PR #42 presentaba conflictos por el historial posterior al squash de la release W3 (#45). Esta rama parte de `main` d3495fd y conserva las versiones de `develop` del README, AlbumAdapter y fragment_album_detail.xml. Las mejoras funcionales son BUG-01 (artista ausente) y fondo claro del detalle en API 21; no se elimina ninguna funcionalidad exclusiva de main. Se añaden únicamente aclaraciones documentales sobre el estado final y las pruebas reales.

## Validación contra la rúbrica

Fuente: [instrucciones y criterios de la iteración 1](https://www.coursera.org/learn/ingenieria-de-software-para-aplicaciones-moviles/supplement/KVcUl/llego-la-hora-de-la-iteracion-1-de-desarrollo), referenciados por Entrega de la iteración 1 W4.

| Dimensión | Puntos | Evidencia / condición de cierre |
|---|---:|---|
| Prácticas ágiles | 15 | Repositorio accesible; APK y release sobre main; contribuciones GitFlow; reuniones W3/W4; retrospectiva del Inception. |
| Documentación | 5 | Wiki con arquitectura y estrategia de pruebas. |
| Diseño y construcción | 40 | Diagramas de clases, paquetes y componentes coherentes con el código; MVVM, Repository y Service Adapter; consumo REST; instrucciones de construcción local. |
| Calidad | 40 | Estrategia y artefactos, HU implementadas, compatibilidad desde Android L, escenarios automatizados por HU y resultados/defectos en el repositorio. |

La rúbrica permite acta, bitácora o video como evidencia de reunión. No exige un video de demostración de la app en estas instrucciones. La reunión W4 es una actividad aparte y requiere su enlace de evidencia. El formulario de entrega W4 solicita URL aunque su ventana de instrucciones indica revisión directa del repositorio: comprobar ambos estados antes del cierre.

## Documentación del proyecto

- [Índice de Sprint 1](https://github.com/Osaron/vinilos-android/wiki/Sprint-1)
- [Arquitectura: clases, paquetes y componentes](https://github.com/Osaron/vinilos-android/wiki/Sprint-1-%E2%80%90-Dise%C3%B1o-arquitect%C3%B3nico)
- [Estrategia de pruebas](https://github.com/Osaron/vinilos-android/wiki/Sprint-1-%E2%80%90-Estrategia-de-pruebas)
- [Escenarios, resultados y defectos](https://github.com/Osaron/vinilos-android/wiki/Sprint-1-%E2%80%90-Artefactos-de-prueba)
- [Retrospectiva del Inception](https://github.com/Osaron/vinilos-android/wiki/Inception-%E2%80%90-Retrospectiva): documento existente; confirmar entrega de la actividad W3 en Coursera.
- [Actas](https://github.com/Osaron/vinilos-android/wiki/Actas-de-reuni%C3%B3n): añadir evidencia real de W4 después de la reunión.

## Pruebas y límites de la evidencia

El PR #47 documenta 10 unitarias aprobadas, 15 instrumentadas con repositorio controlado y 2 casos con backend real en API 34. Las ejecuciones 40771012330 y 40772138244 corresponden a 96ff0c6; no se atribuyen al commit final de esta release. Las pruebas reales son opt-in; dos casos omitidos en CI no significan que se hayan ejecutado contra el backend.

La CI del PR de release debe ejecutar unitarias, lint, construcción de APK e instrumentadas en API 21, 29 y 34. Registrar URL y SHA de sus resultados antes de publicar. No se declara ejecución local en entornos sin SDK/emulador.

## Pendientes antes de entregar

- [ ] Otro integrante revisa y aprueba el PR de release.
- [ ] Checks del head de release y matriz Android en verde.
- [ ] Integración en main y verificación del SHA resultante.
- [ ] Tag/release final con APK construido desde ese SHA; no reutilizar el APK W3 como si fuera el final.
- [ ] Verificar instalación y configuración del backend del APK distribuido.
- [ ] Acta W4 completada con participantes, horario y acuerdos reales, más captura si está disponible; enlace registrado en Coursera.
- [ ] Confirmar entrega de Retrospectiva Inception W3 y estado de Entrega W4.

## Guía para la reunión de cierre del 1 de noviembre

Documento de preparación: no constituye acta ni evidencia de asistencia.

1. Confirmar la aprobación del PR, checks y versión que se libera.
2. Revisar catálogo, búsqueda, detalle y retorno, y los resultados de pruebas.
3. Confirmar responsables y evidencias pendientes conforme al reparto ya acordado por el equipo.
4. Registrar bloqueos y acuerdos, con responsable y fecha real de cumplimiento.
5. Añadir el enlace de la release/APK y comprobar los enlaces de Coursera.

Al finalizar, completar en el acta: hora de inicio/fin, asistentes efectivos, temas tratados, decisiones, tareas con responsable, enlaces revisados y evidencia de la reunión. No dar por asistente ni por aprobado a nadie antes de la reunión.
