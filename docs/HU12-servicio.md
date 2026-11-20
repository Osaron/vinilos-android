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
