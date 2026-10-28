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

**Estado actual (Sprint 1, en curso):** HU01 muestra el catálogo obtenido de `GET /albums`, con búsqueda y estados de carga, vacío y error. Al seleccionar un álbum se entrega su ID para HU02; el detalle de álbum está en desarrollo. Los flujos de las demás historias aún no están implementados. Más detalle en [docs/HU01-avance.md](docs/HU01-avance.md).

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

También puedes copiar el APK al teléfono e instalarlo permitiendo "instalar apps de origen desconocido". El APK de cada entrega se publica en los releases del repositorio.

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
| `release/x.y.z` | Preparación de una entrega |

Reglas de integración:

1. Cada funcionalidad se desarrolla en su rama `feature/` y se integra a `develop` mediante **pull request**.
2. Todo PR requiere **revisión de código** y **1 aprobación** de otro integrante, tras ejecutar las pruebas localmente.
3. La integración continua (GitHub Actions) ejecuta en cada PR las pruebas unitarias, lint y la compilación de la app y de las pruebas instrumentadas.
4. La integración se hace con **squash and merge**.
5. El paso a `main` requiere ejecutar todas las pruebas en diferentes versiones de dispositivos y APIs de Android, y la versión se etiqueta como release.

## Documentación

Toda la documentación del proyecto (backlog, diseño UX/UI, arquitectura, estrategia y artefactos de prueba, reglas de trabajo, actas y retrospectivas) está en la [wiki del repositorio](https://github.com/Osaron/vinilos-android/wiki), organizada por sprint: Inception, Sprint 1, Sprint 2 y Sprint 3.
