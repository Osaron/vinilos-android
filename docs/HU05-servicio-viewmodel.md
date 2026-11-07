# HU05 — servicio y ViewModel del listado de coleccionistas

Adelanto del Sprint 2. Relacionado con #23, #24, #25 y la historia #55.

## Contrato

Se consultaron `src/collector/collector.controller.ts` y `collector.entity.ts` de
TheSoftwareDesignLab/BackVynils (master): GET /collectors devuelve una lista con
id, name, telephone y email, además de relaciones que este listado no utiliza.
No se presupone foto, país, género ni una cuenta autenticada.

`CollectorService` → `CollectorServiceAdapter` → `CollectorRepository` separa los
DTO del modelo. ID positivo y nombre no vacío son obligatorios; email/teléfono
nulos se normalizan a cadenas vacías. Un fallo HTTP/red se propaga, no se presenta
como catálogo vacío.

`CollectorCatalogViewModel` ofrece carga, contenido, error y reintento; búsqueda por
nombre sin distinguir mayúsculas/tildes y selección por ID. Evita consultas duplicadas
en curso y conserva búsqueda/selección en la misma instancia del ViewModel. El futuro
fragmento deberá usar `by viewModels` para conservarlo al rotar. No se promete
persistencia tras muerte del proceso. Para HU06 se propone transmitir collectorId;
no se declara un acuerdo ya confirmado con el responsable del detalle.

## Pruebas y límites

Cinco pruebas de servicio con MockWebServer y cinco pruebas del ViewModel verifican
contrato, identidad inválida, datos vacíos/opcionales, errores, recuperación, búsqueda
y selección. No se presentan como pruebas con backend real ni Espresso.

Este PR agrega solo archivos nuevos para evitar conflictos con HU07 y su release.
Faltan diseño final, composición del repositorio, pantalla, navegación al detalle,
Espresso, backend real, revisión visual y perfilamiento. No cierra HU05.

La ejecución local de Gradle está bloqueada por la descarga de su distribución;
consultar los checks del PR para los resultados reales.
