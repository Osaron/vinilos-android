# HU08 Formulario de canción (#32)

El componente `TrackFormFragment` captura nombre y duración. El nombre se recorta y no puede quedar vacío; la duración acepta minutos y segundos de 00 a 59 (por ejemplo, `3:45`), según la propuesta de #30. La regla de duración debe acordarse con el equipo. Los errores se muestran junto a cada campo y el borrador y estado de errores sobreviven a la recreación de la pantalla.

## Integración pendiente

El flujo de #31 debe proporcionar la selección y conservar el ID del álbum. El componente de #34 debe escuchar el resultado de fragmento `valid_track_form`, con claves `track_name` y `track_duration`, y asociarlo al ID seleccionado. Ese resultado indica únicamente que el formulario es válido; no realiza ni confirma guardado. La integración debe evitar envíos duplicados, preservar el borrador en caso de error y gestionar éxito y refresco. No se ha conectado el formulario a navegación ni a servicio y no se declara HU08 completa.

## Validación

`testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` y `connectedDebugAndroidTest` con clase `co.edu.uniandes.vinilos.TrackFormTest` pasaron localmente en AVD API 35. La prueba de interfaz abre el fragmento directamente en un contenedor de prueba, comprueba errores y resultado validado, y recrea la actividad para verificar el borrador. No acredita el recorrido real ni la integración con los componentes del equipo.
