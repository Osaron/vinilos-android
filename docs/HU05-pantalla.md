# HU05 — pantalla del listado de coleccionistas

Acceso desde el catálogo mediante «Coleccionistas», al lado de «Ver artistas» en
una fila desplazable horizontalmente. No se añade altura fija adicional al catálogo.
La navegación usa el back stack y conserva los flujos de álbumes, artistas y creación.

`CollectorCatalogFragment` observa `CollectorCatalogViewModel`, usando el repositorio
real compuesto en `VinilosApplication`. Muestra lista vertical, nombre, correo (o
«Miembro de la comunidad Vinilos»), contador, búsqueda por nombre y estados de carga,
vacío, sin coincidencias, error y reintento. El avatar es un icono local porque el
contrato del coleccionista no incluye foto. Las filas no tienen altura de texto fija.

Referencia: docs/wireframes/flujo-visitante-y-selector-rol/Coleccionistas.png.
Se conserva la estructura de tarjetas; este avance no muestra cantidades de álbumes
ni favoritos del wireframe. Tampoco implementa todavía la navegación global inferior.

## Contrato de selección para HU06

Resultado de fragmento `collector_selected` con `collectorId` (Int) y
`collectorName` (String). El ID se conserva en el ViewModel y no se infiere del
nombre o posición. El detalle HU06 queda fuera de este PR; por ahora se confirma
visualmente la selección. No se declara un acuerdo de integración ya confirmado.
Búsqueda y selección sobreviven a rotación mediante `by viewModels`; no se promete
persistencia tras muerte del proceso.

## Pruebas y pendientes

Cuatro escenarios Espresso con repositorios controlados: búsqueda/ID/rotación/regreso,
catálogo vacío, búsqueda sin coincidencias y recuperación de error. No sustituyen
backend real ni revisión visual. Las pruebas de servicio/ViewModel están en PR #65.

XML analizado y revisión de formato local. Gradle local continúa bloqueado por la
descarga sin red; consultar CI del PR para resultados de compilación, unitarias,
Lint y Espresso API 21/29/34.

Pendiente para cerrar HU05: revisión e integración, recorrido con backend real,
comprobación visual, TalkBack/fuente ampliada y perfilamiento físico. Relacionado
con #22, #24, #25 y #55.
