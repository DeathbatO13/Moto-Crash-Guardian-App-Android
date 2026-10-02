# App Android — Moto Crash Guardian

Aplicacion Android nativa para motociclistas. Se conecta por BLE al dispositivo Moto Crash Guardian, valida eventos candidatos, permite cancelar falsas alarmas y despacha SMS/llamada desde el telefono. La emergencia funciona localmente y no depende del backend ni de internet.

> **Prototipo academico.** No es un sistema eCall certificado, no reemplaza a los servicios de emergencia y no garantiza detectar todos los accidentes ni enviar alertas. En Colombia, ante una emergencia, comunicate con la linea 123.

## Estado actual

El modulo compila una actividad Compose (`MainActivity` -> `ui/home/HomeScreen` con `ui/theme/MotoCrashGuardianTheme`), la clase `GuardianApp` (Hilt) y dependencias base para Compose, Hilt, Nordic BLE, Room, DataStore, WorkManager y Retrofit. La estructura de paquetes esta creada y protegida por pruebas; todavia no estan implementados el protocolo BLE, el servicio persistente, la deteccion, el flujo de emergencia ni las pantallas funcionales.

## Stack

- Kotlin 2.2.x, Java 21 y Gradle Wrapper.
- Jetpack Compose + Material 3 (sin AppCompat ni Material Components), Navigation Compose y Hilt.
- Pruebas: JUnit 5, JUnit Vintage (Robolectric/Compose UI Test), coroutines-test, Turbine y MockK.
- Android API 26 minimo; compile/target API 36.
- Nordic Android BLE Library, CompanionDeviceManager y Foreground Service.
- Proto DataStore, Room, WorkManager, Retrofit/OkHttp y Fused Location Provider.

## Requisitos locales

- Windows 10/11 o entorno compatible con Android Gradle Plugin.
- JDK 21 y Android SDK (Platform 36.1, Build-Tools y Platform-Tools); lo mas simple es instalar Android Studio, que crea `local.properties` con `sdk.dir`. Sin Android Studio: `ANDROID_HOME` apuntando al SDK.
- Gradle se ejecuta mediante el wrapper incluido; no es necesario instalarlo globalmente.

## Compilar y probar (PowerShell)

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Para instalar en un emulador/dispositivo conectado:

```powershell
.\gradlew.bat installDebug
```

La compilacion no requiere el dispositivo BLE. Las pruebas de integracion con hardware, SIM y permisos reales requieren un telefono Android compatible.

## Variantes y release

| Variante | `applicationId` | `BuildConfig.BASE_URL` | Red |
|---|---|---|---|
| `debug` | `com.motocrashguardian.debug` | `http://10.0.2.2:8080/` (backend local desde el emulador) | HTTP solo a `10.0.2.2`/`localhost` |
| `release` | `com.motocrashguardian` | `mcg.apiBaseUrl` de `gradle.properties` (backend en Render) | Solo HTTPS |

Version (`mcg.versionCode`, `mcg.versionName`) y URL del backend viven en `gradle.properties`. Para el APK firmado de entrega copia `keystore.properties.example` como `keystore.properties` (ignorado por Git) y ejecuta:

```powershell
.\gradlew.bat assembleRelease
```

Para la sustentacion (19-oct-2026) la app se entrega como **APK firmado instalado directamente**, sin Google Play. Guia de firma, CI, instalacion y cronograma: [`docs/DISTRIBUCION_APK.md`](docs/DISTRIBUCION_APK.md). Borrador de politica de privacidad (base para el texto de consentimiento): [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md). Referencia para una publicacion futura en Play: [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md).

## Estructura

```text
app/src/main/java/com/motocrashguardian/   # Aplicacion Android (paquetes abajo)
app/src/main/res/                          # Recursos y manifest
app/src/debug/res/                         # Overrides de debug (network security config)
app/src/test/                              # Pruebas JVM (JUnit 5 + Robolectric/Compose via Vintage)
app/src/androidTest/                       # Pruebas instrumentadas
.github/workflows/                         # CI (lint/tests) y APK firmado en GitHub Release (tag vX.Y.Z)
docs/                                      # Distribucion APK (y referencia de Play)
```

### Paquetes y convenciones

| Paquete | Responsabilidad | Android permitido |
|---|---|---|
| `ble/protocol` | UUIDs, parsers/encoders little-endian, vectores | **No** |
| `ble` | `GuardianBleManager`, CDM, `DeviceRepository` | Si |
| `detection` | `ConfirmationEngine`, `GuardianStateMachine` | **No** |
| `core/model` | Modelos de dominio y enums compartidos | **No** |
| `core/time`, `core/util` | Reloj inyectable y utilidades | Evitar |
| `data/settings`, `data/incidents` | Proto DataStore, Room, repositorios | Si |
| `data/remote`, `data/sync` | Retrofit/OkHttp y WorkManager (Fase 7) | Si |
| `emergency` | Normalizacion, SMS, llamada, `DispatchOrchestrator` | Logica pura separada de los dispatchers |
| `service` | `GuardianService` (FGS) | Si |
| `di` | Modulos Hilt | Si |
| `ui/<pantalla>`, `ui/theme` | Compose: pantalla + ViewModel por carpeta | Si |

- `ArchitectureConventionsTest` falla si `ble/protocol`, `detection` o `core/model` importan `android.*`, `androidx.*`, Dagger/Hilt o `javax.inject`, y si el `package` no coincide con la carpeta.
- Logica pura con JUnit 5 (`org.junit.jupiter`); pruebas con Robolectric o Compose UI Test con JUnit 4 (`org.junit`) y `@RunWith(RobolectricTestRunner::class)`. Ambas corren con `testDebugUnitTest`.
- Coroutines: inyectar dispatchers/reloj y probar con `kotlinx-coroutines-test` y Turbine; fakes antes que MockK cuando sea practico.

La arquitectura objetivo completa esta en [`../docs/05-app-architecture.md`](../docs/05-app-architecture.md). Las especificaciones observables estan en [`../specs/features/`](../specs/features/).

## Roadmap

Ver [`ROADMAP.md`](ROADMAP.md) para tareas ordenadas, dependencias y gates de aceptacion. La prioridad es completar la ruta local `BLE -> confirmacion -> cuenta regresiva -> SMS -> llamada`; sincronizacion remota y funciones P1 van despues.

## Configuracion y privacidad

No guardes keystores de release, tokens, telefonos ni coordenadas en el repositorio o logs. `local.properties`, `keystore.properties`, `*.jks`/`*.keystore`, credenciales y los artefactos de build estan ignorados por Git. La app desactiva los backups de Android (`allowBackup=false` y exclusion total en `data_extraction_rules.xml`). La politica funcional de permisos, retencion, logs y consentimiento se define en [`../docs/08-security-privacy.md`](../docs/08-security-privacy.md).
