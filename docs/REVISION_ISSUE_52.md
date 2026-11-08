# Revisión de desempeño — Issue #52

**Fecha:** 8 de noviembre de 2026
**Rama/commit del código revisado:** `feature/HU04-detalle-artista`, `a75198e`
**Alcance:** aplicación Android; detalle de músico/banda HU04 y rutas de catálogo de artistas/coleccionistas existentes.
**Dispositivo disponible:** emulador `Vinilos_API_35`, Android 15 / API 35, `sdk_gphone64_arm64`, ABI `arm64-v8a`. No había un teléfono físico conectado; las mediciones de UI y memoria aquí son diagnósticas del emulador.

## Revisión de corrutinas, hilo principal y ciclo de vida

- Las solicitudes de los repositorios pasan por Retrofit `suspend`; no se encontraron `runBlocking`, `GlobalScope` ni operaciones directas de red, disco o JSON en la capa de vista.
- `ArtistDetailViewModel` usa `viewModelScope`, evita una segunda consulta mientras hay otra activa y vuelve a propagar `CancellationException`.
- `ArtistDetailFragment` recoge `StateFlow` dentro de `viewLifecycleOwner.repeatOnLifecycle(STARTED)`. El binding es local a `onViewCreated`; el ViewModel no conserva `View`, binding ni `Context`.
- Coil recibe las URL de imágenes de manera asíncrona y entrega placeholder/error local; no se guardan bitmaps en el dominio ni en el ViewModel.
- El catálogo de artistas y el de coleccionistas usan los mismos límites de ciclo de vida para observar estado. Los recorridos de catálogo existentes también están incluidos en los tests instrumentados del proyecto.
- Se añadió `salir_del_detalle_durante_la_carga_cancela_la_consulta`: mantiene una respuesta pendiente, vuelve al catálogo y comprueba que se ejecutó la cancelación del trabajo del ViewModel.
- La navegación hace `executePendingTransactions()` para aplicar el detalle antes de procesar otra selección. La prueba de doble selección confirma una sola entrada y una sola consulta. La medición de interacción no aísla el costo de esa llamada frente al tiempo de Espresso, por lo que no hay evidencia suficiente para cambiarla por ahora.

## Recorrido repetido y memoria

Se ejecutó `ArtistDetailPerformanceTest` con un repositorio determinista en memoria, sin red ni URL de imágenes, y un detalle con dos álbumes. Se midió desde la selección de la banda hasta que el título del detalle quedó visible. Entre muestras se solicitaron recolecciones de basura y se midió PSS (`Debug.getPss`) y heap Java.

| Muestra | PSS | Heap Java |
|---|---:|---:|
| Antes de navegar | 79 130 KB | 4 300 KB |
| Después de 10 ciclos HU03 → HU04 → Atrás | 84 770 KB (+5 640 KB) | 4 511 KB (+211 KB) |
| Después de 20 ciclos | 85 490 KB (+6 360 KB desde el inicio; +720 KB entre ciclos 10 y 20) | 4 495 KB (+195 KB desde el inicio) |

El PSS muestra un aumento inicial seguido de crecimiento pequeño en el segundo bloque de diez ciclos. El heap Java quedó prácticamente estable entre las muestras 10 y 20 (−16 KB). Son mediciones puntuales de un emulador; el PSS incluye memoria nativa y cachés del proceso. No demuestran una fuga y no motivan una corrección sin una traza de memoria adicional.

Mediana de tiempo desde la acción Espresso de selección hasta detalle visible:

| Ciclos | Mediana | Condiciones |
|---|---:|---|
| 1–10 | 299 ms | Repositorio determinista inmediato, dos filas de álbum, sin red/imágenes |
| 11–20 | 296 ms | Mismo recorrido y proceso |

La medición incluye la acción y espera de Espresso; no equivale a tiempo puro de render ni a una medición de jank.

## Backend y estados lentos o fallidos

Se hicieron diez solicitudes por endpoint desde el host al BackVynils local, leyendo el cuerpo y verificando HTTP 200:

| Endpoint | Mínimo | Mediana | Máximo |
|---|---:|---:|---:|
| `GET /musicians/100` | 2,3 ms | 3,3 ms | 37,5 ms |
| `GET /bands/101` | 2,5 ms | 3,3 ms | 3,8 ms |

Estas cifras son de loopback en el host, no de la ruta emulador-a-host ni de un dispositivo físico. El máximo del endpoint del músico fue un valor atípico aislado; no se atribuye a la UI.

La prueba instrumentada de carga suspende una respuesta por seis segundos y confirma que el indicador permanece visible, luego comprueba el detalle tras completar la respuesta. El reintento tras un error y el caso de backend detenido/restaurado también quedaron cubiertos en las pruebas y la evidencia de HU04.

## Procedimiento reproducible

```text
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ArtistDetailPerformanceTest
adb logcat -d -s HU04_PERFORMANCE:I
```

El log registra `cycles=20`, mediana de apertura para cada bloque de diez, PSS antes/después y heap Java. La prueba no impone umbrales de tiempo o memoria para evitar fallos dependientes del AVD.

## Resultado y limitaciones

- No se encontró una operación de red bloqueante ni una referencia de vista retenida en HU04.
- En el recorrido controlado, el tiempo de detalle visible no aumentó del primer al segundo bloque, y la memoria Java se mantuvo estable después del calentamiento inicial.
- No se cambió código de producción por una sospecha sin medición atribuible.
- Falta repetir la medición de memoria y respuesta en un teléfono físico y perfilar con Android Studio si el equipo observa crecimiento o jank.
- La pantalla de detalle de coleccionista no existe en el alcance actual; este documento cubre navegación de artistas en HU04 y las pantallas de catálogo existentes.
