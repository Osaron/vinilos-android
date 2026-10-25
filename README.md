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

| Id | Historia de usuario |
|---|---|
| HU01 | Consultar catálogo de álbumes |
| HU02 | Consultar la información detallada de un álbum |
| HU03 | Consultar el listado de artistas |
| HU04 | Consultar la información detallada de un artista |
| HU05 | Consultar listado de coleccionistas |
| HU06 | Consultar la información detallada de un coleccionista |
| HU07 | Crear un álbum |
| HU08 | Asociar tracks con un álbum |

## Tecnologías

- Kotlin + Android SDK (Android Studio)
- API REST: BackVynils (NestJS)
- Pruebas: Espresso / JUnit

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
3. La integración se hace con **squash and merge**.
4. El paso a `main` requiere ejecutar todas las pruebas en diferentes versiones de dispositivos y APIs de Android.

## Ejecución local

Proyecto inicial de HU01 (Visitante). [Alcance, integración con HU02 y pruebas](docs/HU01-avance.md).

Requisitos previos:

- Android Studio compatible con AGP 8.7.3, JDK 17, Android SDK 35 y un emulador configurado
- Docker Desktop (o Node.js LTS) para ejecutar el backend BackVynils
- Git

Pasos generales:

1. Clonar este repositorio, seleccionar la rama del avance y abrir la raíz en Android Studio.
2. Levantar el backend siguiendo las instrucciones de [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils).
3. Sincronizar Gradle. La URL de desarrollo predeterminada es `http://10.0.2.2:3000/` (backend del PC desde el emulador). Configurar otra URL mediante `-PVINILOS_API_BASE_URL=https://servidor/`; siempre debe terminar en `/`. En un dispositivo físico usar una dirección accesible desde ese dispositivo. No se incluyen claves ni una instancia pública de backend.
4. Ejecutar la app en un emulador o dispositivo físico.

Desde terminal (SDK instalado y `ANDROID_HOME` o `local.properties` configurado):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest  # requiere emulador o dispositivo conectado
# Otra instancia de backend:
./gradlew assembleDebug -PVINILOS_API_BASE_URL=https://servidor/
```

Windows: usar `gradlew.bat`. El APK debug se genera en
`app/build/outputs/apk/debug/app-debug.apk`; instalar con `adb install -r` sobre ese archivo.
La configuración mínima es Android 5.0 (API 21); su ejecución debe comprobarse en dispositivo.
HTTP solo está habilitado en debug para desarrollo local; la distribución release requiere HTTPS.

HU01 muestra el catálogo obtenido de `GET /albums`, búsqueda y estados de carga/vacío/error.
Al seleccionar entrega el ID para HU02; el detalle todavía muestra un aviso temporal.
No se implementan aún los flujos de otras historias ni se presenta este avance como release final.

## Documentación

Toda la documentación del proyecto (backlog, diseño UX/UI, reglas de trabajo, actas y artefactos de cada sprint) está en la [wiki del repositorio](https://github.com/Osaron/vinilos-android/wiki), organizada por sprint: Inception, Sprint 1, Sprint 2 y Sprint 3.
