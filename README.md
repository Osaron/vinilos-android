# Vinilos – Aplicación móvil Android (Kotlin)

Versión móvil de la aplicación **Vinilos**, desarrollada para *The Software Design Company (TSDC)* en el curso **Ingeniería de Software para Aplicaciones Móviles** de la Maestría en Ingeniería de Software (MISO) – Universidad de los Andes.

La app replica la funcionalidad de la versión web de Vinilos (catálogo de álbumes, artistas y coleccionistas) consumiendo la API REST [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils).

## Equipo – ChatGP Team

| Integrante | Usuario GitHub |
|---|---|
| Hernán Enrique Gordillo Garzón | [@h-gordillog](https://github.com/h-gordillog) |
| Tibisay Paola Tarazona Uribe | [@Tibisaytarazona](https://github.com/Tibisaytarazona) |
| Oscar Andrés Rondón Estupiñán | [@Osaron](https://github.com/Osaron) |
| Donaldo Andrés Gándara Correa | [@gandaraco](https://github.com/gandaraco) |

## Alcance del MVP

| Id | Historia de usuario | Sprint |
|---|---|---|
| HU01 | Consultar catálogo de álbumes | 1 |
| HU02 | Consultar la información detallada de un álbum | 1 |
| HU03 | Consultar el listado de artistas | 2 |
| HU04 | Consultar la información detallada de un artista | 2 |
| HU07 | Crear un álbum | 2 |
| HU05 | Consultar listado de coleccionistas | 3 |
| HU06 | Consultar la información detallada de un coleccionista | 3 |
| HU08 | Asociar tracks con un álbum | 3 |

### Historias adicionales (ampliación del MVP)

Acordadas por el equipo el 14 de noviembre de 2026 para el Sprint 3. Amplían el MVP sin reemplazar sus 8 historias. Detalle, criterios y tareas en el [backlog de la wiki](https://github.com/Osaron/vinilos-android/wiki/Backlog-de-producto).

| Id | Historia de usuario | Sprint | Responsable |
|---|---|---|---|
| HU09 | Comentar un álbum | 3 | [@Osaron](https://github.com/Osaron) |
| HU10 | Agregar artistas favoritos | 3 | [@Osaron](https://github.com/Osaron) |
| HU11 | Agregar un álbum a una colección | 3 | [@Tibisaytarazona](https://github.com/Tibisaytarazona) |
| HU12 | Agregar músicos a una banda | 3 | [@Tibisaytarazona](https://github.com/Tibisaytarazona) |
| HU13 | Crear premios | 3 | [@h-gordillog](https://github.com/h-gordillog) |
| HU14 | Asignar un premio a un músico o una banda | 3 | [@gandaraco](https://github.com/gandaraco) |
| HU15 | Agregar un álbum a la discografía | 3 | [@Osaron](https://github.com/Osaron) |

**Estado actual (Iteración 3 · W8).** La versión 0.4.0 integra las ocho historias del MVP (HU01 a HU08) y las ampliaciones HU09, HU10 y HU12. Compatible con Android 5.0 (API 21) o superior. El [Sprint 3 en la wiki](https://github.com/Osaron/vinilos-android/wiki/Sprint-3) reúne el diseño y la evidencia de pruebas.

## Descargar el APK

| Versión | Contenido | APK |
|---|---|---|
| [v0.4.1-w7](https://github.com/Osaron/vinilos-android/releases/tag/v0.4.1-w7) · Avance | Accesibilidad, nuevas pruebas de calidad e ícono de la app | [APK de avance](https://github.com/Osaron/vinilos-android/releases/download/v0.4.1-w7/vinilos-v0.4.1-w7-debug.apk) |
| **[v0.4.0-w8](https://github.com/Osaron/vinilos-android/releases/tag/v0.4.0-w8)** (vigente) | Cierre de la Iteración 3 | [vinilos-v0.4.0-w8-debug.apk](https://github.com/Osaron/vinilos-android/releases/download/v0.4.0-w8/vinilos-v0.4.0-w8-debug.apk) |
| [v0.3.1-w6-dispatchers](https://github.com/Osaron/vinilos-android/releases/tag/v0.3.1-w6-dispatchers) | Cierre de la Iteración 2 y corrutinas de detalles | [vinilos-v0.3.1-w6-dispatchers-debug.apk](https://github.com/Osaron/vinilos-android/releases/download/v0.3.1-w6-dispatchers/vinilos-v0.3.1-w6-dispatchers-debug.apk) |
| [v0.3.1-w6](https://github.com/Osaron/vinilos-android/releases/tag/v0.3.1-w6) | Cierre anterior de la Iteración 2 | [vinilos-v0.3.1-w6-debug.apk](https://github.com/Osaron/vinilos-android/releases/download/v0.3.1-w6/vinilos-v0.3.1-w6-debug.apk) |
| [v0.3.0-w6](https://github.com/Osaron/vinilos-android/releases/tag/v0.3.0-w6) | Corte inicial W6 con HU08 parcial | [vinilos-v0.3.0-w6-debug.apk](https://github.com/Osaron/vinilos-android/releases/download/v0.3.0-w6/vinilos-v0.3.0-w6-debug.apk) |
| [v0.2.1-w5](https://github.com/Osaron/vinilos-android/releases/tag/v0.2.1-w5) | Avance de la Iteración 2 | [vinilos-v0.2.1-w5-debug.apk](https://github.com/Osaron/vinilos-android/releases/download/v0.2.1-w5/vinilos-v0.2.1-w5-debug.apk) |
| [v0.1.0](https://github.com/Osaron/vinilos-android/releases/tag/v0.1.0) | Cierre de la Iteración 1 | [Assets de la release](https://github.com/Osaron/vinilos-android/releases/tag/v0.1.0) |

Cada release publica el SHA-256 del APK y los detalles de las verificaciones y de la ejecución de CI asociada. Para comprobar la descarga: `sha256sum vinilos-v0.4.0-w8-debug.apk` (en Windows: `certutil -hashfile vinilos-v0.4.0-w8-debug.apk SHA256`).

Para instalarlo, abre el archivo en el teléfono y permite "instalar apps de origen desconocido", o ejecuta `adb install -r vinilos-v0.4.0-w8-debug.apk`. Requiere Android 5.0 (API 21) o superior. El APK apunta a `http://10.0.2.2:3000/`, que es BackVynils en el computador visto desde el emulador. En un teléfono físico, compila con `-PVINILOS_API_BASE_URL=http://<IP-del-computador>:3000/` (ver el paso 5).

## Tecnologías

| Componente | Versión |
|---|---|
| Lenguaje | Kotlin 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| Gradle (wrapper incluido) | 8.9 |
| JDK | 17 |
| Android SDK | compileSdk 35 · targetSdk 34 · **minSdk 21 (Android 5.0 Lollipop)** |
| Arquitectura | MVVM + Repository + Service Adapter |
| Red | Retrofit 2 + Gson · imágenes con Coil |
| Pruebas | JUnit 4, kotlinx-coroutines-test, MockWebServer, Espresso |
| Backend | BackVynils (NestJS + PostgreSQL) |

## Construir y ejecutar la app localmente

### 1. Requisitos previos

- **Git**.
- **Android Studio** compatible con AGP 8.7.3 (Ladybug o posterior), con el **Android SDK 35** instalado.
- **JDK 17**. Android Studio trae uno integrado; si compilas desde la terminal, configura `JAVA_HOME` hacia un JDK 17.
- **Docker Desktop** para ejecutar el backend. También se puede usar Node.js y PostgreSQL instalados localmente.
- Un **emulador** (Device Manager de Android Studio) o un **dispositivo físico** con Android 5.0 o superior y la depuración USB activada.

### 2. Clonar el repositorio

```bash
git clone https://github.com/Osaron/vinilos-android.git
cd vinilos-android
git checkout develop   # rama de integración; main contiene los releases
```

### 3. Levantar el backend (BackVynils)

La app necesita la API REST corriendo. Con Docker:

```bash
git clone https://github.com/TheSoftwareDesignLab/BackVynils.git
cd BackVynils
docker compose up --build   # API en http://localhost:3000 y PostgreSQL en el puerto 5432
```

Verifica que responda abriendo `http://localhost:3000/albums` en el navegador. Si la lista está vacía, el repositorio de BackVynils incluye datos de ejemplo en `sql/data.sql`, que se pueden cargar en la base `vinyls`. Para otras formas de ejecución, consulta el repositorio de [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils).

### 4. Abrir el proyecto en Android Studio

1. *File → Open* y selecciona la carpeta raíz `vinilos-android`.
2. Espera a que termine la sincronización de Gradle (*Sync Project with Gradle Files*). Si Android Studio pide instalar el SDK 35 o las build tools, acepta.

### 5. Configurar la URL del backend (opcional)

Por defecto la app usa `http://10.0.2.2:3000/`, que es la dirección del computador vista desde el **emulador**. No hay que cambiar nada si el backend corre en el mismo computador.

Para usar otra instancia, pasa la propiedad `VINILOS_API_BASE_URL`, que debe terminar en `/`:

```bash
./gradlew assembleDebug -PVINILOS_API_BASE_URL=http://192.168.1.50:3000/
```

En un **dispositivo físico** usa la IP del computador en la red local (el teléfono y el computador deben estar en la misma red). HTTP solo está habilitado en la variante debug para desarrollo local; la variante release requiere HTTPS. El repositorio no incluye claves ni una instancia pública del backend.

### 6. Ejecutar la app

- **Desde Android Studio:** selecciona el emulador o dispositivo y pulsa *Run 'app'* (▶).
- **Desde la terminal:** en Windows usa `gradlew.bat` en lugar de `./gradlew`. Debe estar configurado `ANDROID_HOME` o el archivo `local.properties` con `sdk.dir`.

```bash
./gradlew installDebug   # compila e instala en el emulador o dispositivo conectado
```

### 7. Generar e instalar el APK

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo en un dispositivo conectado:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También puedes copiar el APK al teléfono e instalarlo permitiendo "instalar apps de origen desconocido". El APK de cada entrega se publica en los [releases del repositorio](https://github.com/Osaron/vinilos-android/releases). La vigente es [v0.4.0-w8](https://github.com/Osaron/vinilos-android/releases/tag/v0.4.0-w8) (ver [Descargar el APK](#descargar-el-apk)).

### 8. Ejecutar las pruebas

```bash
./gradlew testDebugUnitTest            # pruebas unitarias y de integración (no requieren emulador)
./gradlew connectedDebugAndroidTest    # pruebas E2E con Espresso (requieren emulador o dispositivo)
./gradlew lintDebug                    # análisis estático de Android
```

Los reportes quedan en `app/build/reports/`. La estrategia de pruebas, el inventario de escenarios y los resultados de cada sprint están en la [wiki](https://github.com/Osaron/vinilos-android/wiki), en la página *Estrategia de pruebas* de cada sprint.

### Solución de problemas

| Problema | Solución |
|---|---|
| Error de versión de Java al sincronizar | En *Settings → Build Tools → Gradle → Gradle JDK*, selecciona un JDK 17. |
| `SDK location not found` | Crea `local.properties` en la raíz con `sdk.dir=<ruta al Android SDK>`, o define `ANDROID_HOME`. |
| La app muestra error de conexión | Verifica que el backend responda en `http://localhost:3000/albums` y que la URL configurada termine en `/`. |
| En un dispositivo físico no carga el catálogo | Usa la IP local del computador en lugar de `10.0.2.2`. |

## Flujo de trabajo (GitFlow)

| Rama | Propósito |
|---|---|
| `main` | Versiones liberadas (releases) |
| `develop` | Desarrollo e integración (rama por defecto) |
| `feature/HUxx-descripcion` | Implementación de cada funcionalidad, creada desde `develop` |
| `release/x.y.z` | Preparación de una entrega (ver [docs/corte-sprint3.md](docs/corte-sprint3.md)) |

Reglas de integración:

1. Cada funcionalidad se desarrolla en su rama `feature/` y se integra a `develop` mediante **pull request**.
2. Todo PR requiere **revisión de código** y **1 aprobación** de otro integrante, tras ejecutar las pruebas localmente.
3. La integración continua (GitHub Actions) ejecuta en cada PR las pruebas unitarias, lint y la compilación de la app y de las pruebas instrumentadas.
4. La integración se hace con **squash and merge**.
5. El paso a `main` requiere ejecutar todas las pruebas en diferentes versiones de dispositivos y APIs de Android, y la versión se etiqueta como release.

## Documentación

Toda la documentación del proyecto (backlog, diseño UX/UI, arquitectura, estrategia y artefactos de prueba, reglas de trabajo, actas y retrospectivas) está en la [wiki del repositorio](https://github.com/Osaron/vinilos-android/wiki), organizada por sprint: Inception, Sprint 1, Sprint 2 y Sprint 3.

Documentación técnica por historia en el repositorio:

- **HU07 – Crear un álbum (Sprint 2, en integración):** contrato POST /albums acordado con el backend, componentes (Fragment → ViewModel → Repository → Service Adapter → Retrofit), diagrama de secuencia del flujo Coleccionista y pruebas en [docs/HU07-creacion.md](docs/HU07-creacion.md).

### Diseño UX/UI (Figma)

- Archivo de diseño (editable): https://www.figma.com/design/MkdUukCRycnXvpfRPQj8Tp/Vinilos---Wireframes-UX-UI?node-id=0-1
- Prototipo navegable: ver link en la wiki / mensaje del equipo.

Incluye la pantalla inicial de selección de rol ("Visitante" / "Coleccionista") y el flujo completo de Visitante (HU01–HU06, sin el botón "+" de crear álbum ni "Agregar canción"). Capturas en [`docs/wireframes/flujo-visitante-y-selector-rol/`](docs/wireframes/flujo-visitante-y-selector-rol/).
