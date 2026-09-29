# App Android — Moto Crash Guardian

Aplicacion Android nativa para motociclistas. Se conecta por BLE al dispositivo Moto Crash Guardian, valida eventos candidatos, permite cancelar falsas alarmas y despacha SMS/llamada desde el telefono. La emergencia funciona localmente y no depende del backend ni de internet.

> **Prototipo academico.** No es un sistema eCall certificado, no reemplaza a los servicios de emergencia y no garantiza detectar todos los accidentes ni enviar alertas. En Colombia, ante una emergencia, comunicate con la linea 123.

## Estado actual

El modulo compila una actividad Compose de bienvenida, tiene la clase `GuardianApp` y dependencias base para Compose, Hilt, Nordic BLE, Room, DataStore, WorkManager y Retrofit. Todavia no estan implementados el protocolo BLE, el servicio persistente, la deteccion, el flujo de emergencia ni las pantallas funcionales. La prueba existente es solo la prueba de ejemplo de Android.

## Stack

- Kotlin 2.2.x, Java 21 y Gradle Wrapper.
- Jetpack Compose + Material 3, Navigation Compose y Hilt.
- Android API 26 minimo; compile/target API 36.
- Nordic Android BLE Library, CompanionDeviceManager y Foreground Service.
- Proto DataStore, Room, WorkManager, Retrofit/OkHttp y Fused Location Provider.

## Requisitos locales

- Windows 10/11 o entorno compatible con Android Gradle Plugin.
- JDK 21 y Android SDK para API 36; configurar `local.properties` con la ruta local del SDK.
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

## Estructura

```text
app/src/main/java/com/motocrashguardian/   # Aplicacion Android
app/src/main/res/                          # Recursos y manifest
app/src/test/                              # Pruebas JVM
app/src/androidTest/                       # Pruebas instrumentadas
```

La estructura de paquetes objetivo y responsabilidades estan en [`../docs/05-app-architecture.md`](../docs/05-app-architecture.md). Las especificaciones observables estan en [`../specs/features/`](../specs/features/).

## Roadmap

Ver [`ROADMAP.md`](ROADMAP.md) para tareas ordenadas, dependencias y gates de aceptacion. La prioridad es completar la ruta local `BLE -> confirmacion -> cuenta regresiva -> SMS -> llamada`; sincronizacion remota y funciones P1 van despues.

## Configuracion y privacidad

No guardes keystores de release, tokens, telefonos ni coordenadas en el repositorio o logs. El archivo `local.properties` y los artefactos de build estan ignorados por Git. La politica funcional de permisos, retencion, logs y consentimiento se define en [`../docs/08-security-privacy.md`](../docs/08-security-privacy.md).
