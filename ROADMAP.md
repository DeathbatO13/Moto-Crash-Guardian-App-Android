# Roadmap Android — Moto Crash Guardian

Este roadmap convierte `docs/` y `specs/features/` en trabajo ejecutable para `App Android/`. Las prioridades siguen el MVP: primero la ruta de emergencia local; la sincronizacion con el backend nunca debe bloquearla.

**Estado de partida:** el modulo ya usa Kotlin, Jetpack Compose, Hilt y Java 21; `minSdk 26`, `compileSdk/targetSdk 36`. La aplicacion muestra una pantalla de inicio minima; la base de pruebas (JUnit 5 + Robolectric/Compose) y las convenciones de paquetes estan definidas. El firmware no existe todavia en este workspace, por lo que las pruebas Android de protocolo deben empezar con vectores y fakes, y la integracion fisica queda como gate compartido.

## Fase 0 — Base verificable (Completada)

- [x] Ejecutar `testDebugUnitTest` y `assembleDebug` con el Gradle Wrapper en Windows (bloqueo superado mediante configuracion de `local.properties` con Android SDK API 36/36.1 local).
- [x] Alinear dependencias y runner con `docs/05-app-architecture.md` y `docs/09-testing-strategy.md`: JUnit 5 (BOM 5.13.4 + `junit-platform-launcher`, obligatorio en Gradle 9), JUnit Vintage para Robolectric/Compose UI Test en JVM, coroutines-test, Turbine y MockK; retiradas AppCompat, ConstraintLayout, Material Components, `activity_main.xml` y la prueba de ejemplo (el tema de ventana usa `android:Theme.Material.*`).
- [x] Configurar variantes debug/release y `BASE_URL`; permitir cleartext solo para `10.0.2.2` en debug y exigir HTTPS en release (`BuildConfigTest` verificado en verde).
- [x] Definir estructura y convenciones de paquetes (README, seccion *Paquetes y convenciones*; `di/` agregado, tema y home en `ui/`); `ArchitectureConventionsTest` impide que `ble/protocol`, `detection` y `core/model` dependan de Android/Hilt.
- [x] Anadir CI para `lint`, pruebas unitarias y `assembleDebug` (`.github/workflows/android-ci.yml`).
- [x] Configurar firma de release fuera del repo, R8 y workflow de APK firmado con GitHub Release (`docs/DISTRIBUCION_APK.md`).
- [ ] Generar `mcg-release.jks` (respaldada fuera del repo) e instalar un APK release en telefono real antes del 9-oct para detectar problemas de R8.
- [x] Decidir distribucion para la sustentacion (19-oct-2026): APK firmado por sideload, sin Google Play; las restricciones de politica de Play sobre `SEND_SMS` quedan para una publicacion futura (`docs/PLAY_STORE.md`).

**Salida:** build reproducible y una base de pruebas que corre sin dispositivo BLE.

## Fase 1 — Dominio local y persistencia

- [x] Crear modelos Kotlin puros de dominio para `Telemetry`, `DeviceEvent`, `DetectionConfig`, `AppSettings`, `Incident`, `Trace` y estados/enums compartidos según `docs/03-bluetooth-spec.md` y `docs/04-data-model.md`.
- [x] Implementar Proto DataStore (`settings.pb`) y `SettingsRepository`: valores por defecto, rangos, contactos, consentimiento, estado del viaje y cambios pendientes; generar protobuf lite y enlazar el store con Hilt.
- [x] Implementar Room (`incidents`, `incident_traces`), DAOs y repositorio local; exportar el esquema v1 y enlazar la base singleton con Hilt.
- [x] Probar round-trip de incidentes/trazas, reapertura de la base, deduplicacion por `bootCount:eventId`, cascada y politicas de retencion con Robolectric.
- [x] Agregar migracion Room v1→v2 para el deadline de cuenta regresiva y probar su preservacion junto con la reanudacion despues de recrear proceso.

**Salida:** preferencias e incidentes locales son la fuente de verdad y sobreviven a reinicios.

## Fase 2 — Protocolo BLE y deteccion

- [ ] Implementar UUIDs, enums, parsers y encoders little-endian para `DEVICE_INFO`, `TELEMETRY`, `EVENT`, `CONFIG`, `CONTROL_POINT`, `GPS` y `TRACE`, validando longitud y version de protocolo.
- [ ] Copiar los vectores hexadecimales de `specs/features/telemetry-streaming.md` sin alterarlos y probar resultados, bytes malformados, version futura y wrap de `seq`.
- [x] Implementar `ConfirmationEngine` puro con reglas R1-R7, ventana de telemetria, reloj inyectable, velocidad fiable, modo demo y fallo hacia avisar con cobertura menor al 50%.
- [x] Implementar `GuardianStateMachine` con entradas secuenciales, persistencia antes de efectos, un incidente activo, eventos repetidos y reanudacion de cuenta regresiva.
- [x] Cubrir los casos obligatorios de `docs/09-testing-strategy.md` y los criterios de `specs/features/crash-detection.md` con pruebas deterministas.

**Salida:** los contratos de bytes y las decisiones criticas se validan sin Android ni hardware.

La maquina de estados ya está implementada y probada de forma local; falta conectarla al `GuardianService` cuando se implemente T-1.05. El protocolo BLE (T-1.02) sigue siendo independiente y pendiente.

## Fase 3 — Descubrimiento, conexion y viaje

- [ ] Implementar emparejamiento CDM filtrado por el UUID de servicio BLE y persistir `pairedDeviceAddress` / `DeviceInfo`.
- [ ] Implementar `GuardianBleManager` con Nordic BLE Library y `DeviceRepository`: MTU 247, descubrimiento/validacion GATT, cifrado, suscripciones, comandos correlacionados, timeout y estados incompatibles.
- [ ] Implementar deduplicacion y `ACK_EVENT` incluso para duplicados; guardar el evento en Room antes del ACK y tratar eventos de mas de 5 minutos como `STALE_EVENT`.
- [ ] Implementar `GuardianService` como FGS connected-device/location, notificacion persistente, reconexion con backoff, resuscripcion y re-ARM tras reconectar/recrear proceso.
- [ ] Implementar iniciar/terminar viaje, `ARM`/`DISARM`, estado degradado y telemetria de inicio, sin admitir armado sin los requisitos definidos.
- [ ] Verificar en telefono real pantalla apagada durante 30 minutos, cierre desde recientes y reconexion en menos de 10 segundos (NFR-004/005).

**Salida:** viaje activo con enlace BLE persistente y estado visible; cumplir FEAT-01, FEAT-02 y FEAT-05.

## Fase 4 — Onboarding, permisos y configuracion

- [ ] Crear flujo Compose de bienvenida/descargo, consentimiento y asistente de permisos; guardar `consentAcceptedAt` y reevaluar permisos en `ON_RESUME` y al iniciar viaje.
- [ ] Implementar contactos principal/secundario, selector de agenda, validacion y normalizacion E.164 (`+57` por defecto), nombre del motociclista y SMS de prueba.
- [ ] Implementar TARE y sliders de configuracion con rangos de `docs/04-data-model.md`; persistir local primero y diferir escritura BLE hasta reconexion.
- [ ] Resolver `BUSY`, configuracion invalida y offsets inusuales con mensajes recuperables; restaurar defaults sin cambiar offsets.

**Salida:** configuracion usable y persistente; cumplir FEAT-04 y FEAT-09.

## Fase 5 — Alerta y despacho local (ruta P0)

- [x] Crear el componente visual aislado de S-20 a partir del boceto; mostrar solo nombres y precisión de ubicación, con acciones accesibles desacopladas del despacho real.
- [ ] Implementar `AlertActivity` y notificacion de alta prioridad/full-screen intent, permisos Android 14+, pantalla bloqueada y fallback heads-up.
- [ ] Implementar cuenta basada en deadline, alarma y vibracion, cancelacion con pulsacion sostenida de 1 s, envio inmediato de ayuda y reanudacion tras muerte del proceso.
- [ ] Implementar `LocationAcquirer`: iniciar al comenzar cuenta, prioridad GPS del telefono/NEO-6M/ultima ubicacion, frescura y timeouts definidos.
- [x] Implementar `PhoneNumberNormalizer` y `GsmMessageBuilder` como logica pura; asegurar GSM-7, normalizacion de tildes y plantillas de simulacro.
- [x] Implementar `SmsDispatcher` con multipart, intents internos, resultados y un reintento; `CallDispatcher` usa `TelecomManager.placeCall` con permiso y simulacro verificables.
- [ ] Implementar `DispatchOrchestrator`, estados parciales/fallidos y resultado/acciones de recuperacion. No hacer llamadas de red al backend en esta ruta.
- [ ] Probar manualmente en telefono con SIM: pantalla bloqueada, cancelacion, ubicacion no disponible, sin internet y SMS/llamada reales solo a contactos de prueba.

**Salida:** evento confirmado -> countdown -> SMS -> llamada opera offline; cumplir FEAT-06/07/08 y NFR-001/002/003.

## Fase 6 — Simulacro, historial y caja negra

- [ ] Implementar simulacro BLE con `TRIGGER_TEST_EVENT` y fallback local cuando no hay dispositivo; llamadas opcionales y SMS siempre marcados `[PRUEBA]`.
- [ ] Implementar modo demo temporal (10 s, reglas relajadas, indicador visible) sin modificar los ajustes persistidos ni la configuracion del firmware.
- [ ] Descargar trazas por chunks, reintentar hasta dos veces, validar longitud y guardar en Room; no competir con countdown/dispatch.
- [ ] Crear historial/detalle Compose y grafica Canvas de -3 a +3 s con instante del disparo, umbral y saturacion.
- [ ] Aplicar retencion de 100 incidentes y 90 dias de trazas; exportacion de diagnostico sin PII.

**Salida:** simulacro repetible e historial local verificable; cumplir FEAT-10/11.

## Fase 7 — Sincronizacion, seguridad y entrega

- [ ] Integrar Retrofit/OkHttp y API v1 solo despues de estabilizar el contrato del Backend; guardar token con Android Keystore AES-GCM y excluir datos sensibles de backups.
- [ ] Implementar WorkManager para registro, ajustes/contactos, incidentes y trazas; orden, idempotencia, reintentos, 401 y errores permanentes segun `specs/features/data-synchronization.md`.
- [ ] Implementar ajustes, privacidad/borrado local-remoto y diagnostico BLE; asegurar que logs no contengan nombres, telefonos, coordenadas ni tokens.
- [ ] Completar pruebas Compose, matriz API 26/31/33/34/36 y fabricantes; medir consumo, robustez de FGS y accesibilidad NFR-014.
- [ ] Ejecutar pruebas de campo: 9/10 caidas simuladas y 0 countdowns en 50 km; registrar evidencia en la bitacora del proyecto.
- [ ] Generar el APK release firmado de entrega (`v1.0.0`) siguiendo el cronograma de `docs/DISTRIBUCION_APK.md` §4 y ensayar dos veces el guion de demo.

**Salida MVP:** P0 aprobado, API sincronizando en segundo plano, APK instalable y evidencia de NFR-004/006/007.

## Dependencias externas y gates

- [ ] Confirmar firmware BLE v1 y usar los mismos vectores de prueba en ambos lados antes de la integracion fisica.
- [ ] Confirmar telefonos/SIM de prueba, cableado y dispositivo real; hasta entonces usar fakes y pruebas de contrato.
- [ ] Confirmar URL/entorno del Backend (deploy en Render el 13-oct) y contrato OpenAPI antes de habilitar sincronizacion; recompilar el APK con `mcg.apiBaseUrl` real.

## Referencias

- `../docs/05-app-architecture.md`, `../docs/06-ui-specification.md`, `../docs/07-error-handling.md`
- `../docs/08-security-privacy.md`, `../docs/09-testing-strategy.md`
- `../docs/03-bluetooth-spec.md`, `../docs/04-data-model.md`
- `../specs/features/README.md`, `../specs/tasks/backlog.md`
