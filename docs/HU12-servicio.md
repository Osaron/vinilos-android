# HU12 Asociación de músicos a una banda

El servicio de HU12 usa `POST /bands/{bandId}/musicians/{musicianId}`, sin cuerpo, y recibe HTTP 200 con el músico y su banda. BackVynils responde 404 si cualquiera de los dos identificadores no existe. La implementación devuelve un modelo Artist de tipo MUSICIAN y comprueba que el músico y la banda confirmados coincidan con los solicitados.

`GET /bands/{bandId}/musicians/` permite recuperar los integrantes para excluirlos del selector y verificar un resultado incierto antes de reenviar. El backend guarda una única referencia a banda en cada músico. La asociación a una banda diferente cambia la anterior. El flujo debe comunicar esa acción cuando corresponda.

El repositorio se compone en VinilosApplication. Sus métodos suspendidos propagan HttpException para conservar el código de rechazo e IOException para un fallo de transporte. La pantalla debe conservar la selección y comprobar el estado del servidor tras una respuesta perdida. El cliente está configurado sin reintentos automáticos.

La implementación y las pruebas del selector, la autorización por rol, la prevención de doble envío y el refresco del detalle se siguen en #93–#95. El diseño y su revisión se siguen en #91.

Fuente del contrato: [controlador](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/bandmusician/bandmusician.controller.ts) y [servicio](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/bandmusician/bandmusician.service.ts).

Pruebas con MockWebServer en BandMusicianServiceAdapterTest: ruta y ausencia de cuerpo, lectura de integrantes, identificadores inválidos, confirmaciones inconsistentes, HTTP 400/404/500 y fallo de red con una solicitud.

Comando para ejecutarlas:

```bash
./gradlew testDebugUnitTest --tests '*BandMusicianServiceAdapterTest'
```

## Selector de músico

MusicianSelectionFragment recibe bandId y devuelve el resultado hu12MusicianSelected con bandId y musicianId. Volver cancela la selección antes del envío. Agregar músico consulta los integrantes y guarda la asociación si aún no existe. Una respuesta confirmada devuelve el resultado con bandId y musicianId al detalle.

MusicianSelectionViewModel combina el catálogo de HU03 con los integrantes de la banda. Excluye bandas e integrantes existentes, distingue músicos por ID y conserva la selección en SavedStateHandle. Si una recarga descubre que el músico elegido ya pertenece a la banda, limpia esa selección.

La rama integra la selección de Coleccionista del PR #111. En el detalle de banda, la acción se muestra cuando hay un coleccionista activo y publica el resultado al regresar para actualizar los integrantes. El guardado de #94 desactiva los controles durante la solicitud, conserva la selección ante un fallo y verifica los integrantes tras un error de transporte antes de ofrecer un reintento. BandMusicianFlowTest cubre la acción oculta para Visitante, selección conservada al recrear, asociación a la banda correcta, refresco del detalle, exclusión posterior del integrante y cancelación sin enviar. El diálogo de confirmación advierte sobre el cambio de banda anterior.
