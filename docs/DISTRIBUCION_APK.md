# Distribucion por APK — Moto Crash Guardian

Para la sustentacion (lunes **19 de octubre de 2026**) la app se distribuye como **APK release firmado instalado directamente** en los telefonos de prueba. No se publica en Google Play; la guia de Play queda como referencia futura en [`PLAY_STORE.md`](PLAY_STORE.md).

Ventajas para el MVP academico: no hay revision de Google, no aplican las restricciones de politica sobre `SEND_SMS`, `USE_FULL_SCREEN_INTENT` ni servicios en primer plano, y no se necesita cuenta de desarrollador ni prueba cerrada de 14 dias.

## 1. Llave de firma (una sola vez)

Sin Play App Signing, **esta llave es la firma definitiva de la app**: Android solo permite actualizar un APK instalado con otro firmado por la misma llave. Si se pierde, hay que desinstalar la app (y perder sus datos locales) en cada telefono.

```powershell
keytool -genkeypair -v -keystore C:\ruta\segura\mcg-release.jks -alias mcg-release -keyalg RSA -keysize 4096 -validity 10000
```

1. Guarda el `.jks` y sus claves en un gestor de contrasenas y en un respaldo fuera del equipo.
2. Copia `keystore.properties.example` como `keystore.properties` (ignorado por Git) y completalo.
3. Nunca subas el `.jks` al repositorio ni lo compartas por chat.

## 2. Generar el APK

### Local

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
```

Salida: `app/build/outputs/apk/release/app-release.apk`. Guarda tambien `app/build/outputs/mapping/release/mapping.txt` de cada version entregada: sin el, los stack traces de R8 son ilegibles.

Para instalar encima de una version previa, sube `mcg.versionCode` en `gradle.properties` (o pasa `-Pmcg.versionCode=<n>`).

### CI (GitHub Actions)

Crea el environment `release` en GitHub → Settings → Environments con estos secretos:

| Secreto | Valor |
|---|---|
| `MCG_UPLOAD_KEYSTORE_BASE64` | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\ruta\segura\mcg-release.jks"))` |
| `MCG_UPLOAD_STORE_PASSWORD` | Clave del keystore |
| `MCG_UPLOAD_KEY_ALIAS` | `mcg-release` |
| `MCG_UPLOAD_KEY_PASSWORD` | Clave de la llave |

```bash
git tag v0.1.0
git push origin v0.1.0
```

`android-release.yml` corre las pruebas, firma el APK y crea un GitHub Release con `moto-crash-guardian-<version>.apk` y su `.sha256` (el `mapping.txt` queda como artefacto del workflow). El `versionCode` es `100 + numero de ejecucion`, siempre mayor que el de un build local con `mcg.versionCode=1`.

## 3. Instalar en los telefonos de prueba

**Opcion A — cable USB (recomendada para la demo):**

```powershell
adb install -r moto-crash-guardian-0.1.0.apk
```

`-r` reinstala conservando los datos. Requiere *Opciones de desarrollador → Depuracion USB*.

**Opcion B — enlace de descarga** (GitHub Release o Drive):

1. Abrir el enlace en el telefono y descargar el APK.
2. Permitir *Instalar apps desconocidas* para el navegador o gestor de archivos cuando Android lo pida.
3. Si Play Protect muestra una advertencia por ser una app desconocida, elegir *Mas detalles → Instalar de todas formas*. Es esperado en APKs fuera de la tienda.

Despues de instalar, conceder los permisos desde el asistente de la app. Verificar en *Ajustes → Apps → Moto Crash Guardian*:

- Notificaciones activadas y, en Android 14+, *Intents de pantalla completa* permitidos (la app debe comprobarlo con `NotificationManager.canUseFullScreenIntent()`).
- SMS, Telefono, Ubicacion y Dispositivos cercanos concedidos.
- Bateria sin restricciones (algunos fabricantes, como Xiaomi o Samsung, matan el servicio en segundo plano si no).

Las variantes `debug` (`com.motocrashguardian.debug`) y `release` (`com.motocrashguardian`) pueden convivir en el mismo telefono.

## 4. Cronograma hacia la sustentacion

| Fecha | Tarea |
|---|---|
| Hasta el vie 9 oct | Generar la llave de firma, instalar un APK release en un telefono real y corregir reglas R8 si algo falla (Hilt, Room, Nordic BLE, serializacion). |
| **Mar 13 oct** | Deploy del backend en Render (ver `Backend/docs/DEPLOY_RENDER.md`) y confirmar la URL publica. |
| Mie 14 oct | Actualizar `mcg.apiBaseUrl` con la URL real, generar el APK candidato (`v1.0.0-rc1`) e instalarlo en todos los telefonos de prueba. |
| Jue 15 – vie 16 oct | Prueba de extremo a extremo: viaje, simulacro, SMS/llamada a contactos de prueba, sincronizacion con Render y **ruta de emergencia con datos moviles y wifi apagados**. |
| Dom 18 oct | Congelar codigo, etiquetar `v1.0.0`, reinstalar si hubo cambios y ensayar dos veces el guion de demo. |
| **Lun 19 oct** | Unos minutos antes: despertar el backend (`/actuator/health`), cargar los telefonos y tener el APK en USB como respaldo. |

Si la URL de Render cambia despues del 14 de octubre, hay que recompilar e instalar de nuevo el APK: la URL va compilada en `BuildConfig.BASE_URL`.

## 5. Checklist del APK de entrega

- [ ] Firmado con `mcg-release.jks` (`apksigner verify --print-certs <apk>` muestra el certificado esperado).
- [ ] `mcg.apiBaseUrl` apunta al backend desplegado (HTTPS).
- [ ] `versionCode` mayor que el instalado en los telefonos.
- [ ] `testDebugUnitTest` y `lint` en verde; APK release probado en telefono real.
- [ ] Emergencia probada sin internet y con el backend dormido o caido.
- [ ] SMS y llamadas solo a contactos de prueba; simulacros marcados `[PRUEBA]`.
- [ ] `mapping.txt` y SHA-256 guardados junto al APK entregado.
- [ ] Descargo de prototipo academico y linea 123 visibles en la bienvenida de la app.
