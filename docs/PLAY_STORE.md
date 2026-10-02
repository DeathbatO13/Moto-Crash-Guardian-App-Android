# Publicacion en Google Play — Moto Crash Guardian

> **Fuera del alcance de la sustentacion (19-oct-2026).** Para la entrega se distribuye el APK firmado directamente: ver [`DISTRIBUCION_APK.md`](DISTRIBUCION_APK.md). Este documento queda como referencia si el proyecto se publica despues. Diferencias con la configuracion actual del repo:
>
> - `android-release.yml` hoy genera un **APK** y un GitHub Release. Para Play hay que cambiarlo a `bundleRelease`, usar un environment `play-store` y agregar el paso de subida (`r0adkll/upload-google-play`, track `internal`, `status: draft`).
> - La llave `mcg-release.jks` usada para los APK puede registrarse como *upload key* al activar Play App Signing. Para no romper las actualizaciones de quienes ya instalaron el APK, sube esa misma llave como *app signing key*.

Guia para firmar, construir y publicar la app en Google Play, y lista de declaraciones de politica que exige el manifiesto actual. Leela completa antes de crear la ficha: varias decisiones (package name, llave de subida, permisos SMS) son dificiles o imposibles de revertir.

> **Estado:** la app aun muestra una pantalla de bienvenida. Google rechaza apps sin funcionalidad minima, asi que **produccion solo es viable despues de la Fase 5/6 del roadmap** (ruta de emergencia + simulacro sin dispositivo). Lo que si se puede hacer ya: crear la cuenta, reservar el package name con una version en *prueba interna* y validar el pipeline de firma y CI.

## 1. Resumen de la configuracion en el repo

| Elemento | Donde | Detalle |
|---|---|---|
| `applicationId` | `app/build.gradle.kts` | `com.motocrashguardian` (definitivo: Play no permite cambiarlo). Debug usa `.debug` para convivir en el mismo telefono. |
| Version | `gradle.properties` | `mcg.versionCode` / `mcg.versionName`; CI los sobrescribe (`100 + run_number`, nombre desde el tag). |
| URL del backend | `gradle.properties` → `BuildConfig.BASE_URL` | Release: `mcg.apiBaseUrl` (se valida que sea HTTPS). Debug: `http://10.0.2.2:8080/`. |
| Firma | `keystore.properties` o variables `MCG_UPLOAD_*` | Nunca en Git (`.gitignore`). Plantilla: `keystore.properties.example`. |
| Minificacion | `buildTypes.release` | R8 + shrink de recursos; reglas en `app/proguard-rules.pro`. |
| Red | `res/xml/network_security_config.xml` | Release solo HTTPS; `src/debug` permite HTTP a `10.0.2.2`/`localhost`. |
| Backups | Manifest + `res/xml/*rules.xml` | `allowBackup=false` y exclusion total en nube y transferencia entre equipos. |
| CI | `.github/workflows/android-ci.yml` | `lint`, `testDebugUnitTest`, `assembleDebug` en cada PR. |
| Release | `.github/workflows/android-release.yml` | Tag `vX.Y.Z` → AAB firmado + `mapping.txt` → track interno (borrador). |

## 2. Cuenta de desarrollador

1. Registrate en <https://play.google.com/console> (pago unico de US$25 y verificacion de identidad).
2. **Cuenta personal vs. organizacion**:
   - *Personal* (creada despues de nov-2023): antes de pedir acceso a produccion Google exige una **prueba cerrada con al menos 12 testers activos durante 14 dias seguidos**. Planifica esas dos semanas en el cronograma academico.
   - *Organizacion*: requiere numero D-U-N-S; no tiene ese requisito de prueba cerrada.
3. Verifica los requisitos vigentes en la consola al momento de registrarte; Google los ajusta con frecuencia.

## 3. Llave de subida y Play App Signing

Play firma el APK final con la *app signing key* que custodia Google. Tu solo generas y guardas la **upload key**.

```powershell
keytool -genkeypair -v -keystore C:\ruta\segura\mcg-upload.jks -alias mcg-upload -keyalg RSA -keysize 4096 -validity 10000
```

- Guarda el `.jks` y sus claves en un gestor de contrasenas y en un respaldo fuera del equipo. Si se pierde, se puede solicitar un reset de upload key en la consola, pero toma dias.
- Crea `keystore.properties` a partir de `keystore.properties.example`.
- En la primera subida acepta **Play App Signing** (obligatorio para apps nuevas).

## 4. Construir el Android App Bundle

```powershell
.\gradlew.bat testDebugUnitTest bundleRelease
```

Salida: `app/build/outputs/bundle/release/app-release.aab` y `app/build/outputs/mapping/release/mapping.txt` (subelo con cada version para desofuscar los crashes).

Antes de subir, instala el release en un telefono real para detectar reglas R8 faltantes:

```powershell
.\gradlew.bat installRelease
```

Si `keystore.properties` no existe, `bundleRelease` produce un bundle sin firmar que Play rechazara.

## 5. Crear la app en Play Console

1. **Crear app**: nombre "Moto Crash Guardian", idioma predeterminado espanol (Latinoamerica, `es-419`), tipo *App*, *Gratis*.
2. **Primera version (manual)**: Pruebas → Prueba interna → Crear version → subir el `.aab`. La API de Play no puede crear la app ni la primera version, por eso esta subida es manual; las siguientes las hace CI.
3. Completa **Contenido de la app** (seccion 7) y la **Ficha de Play Store** (borrador en [`STORE_LISTING.md`](STORE_LISTING.md)).

## 6. Pipeline de CI para releases

Configura en GitHub → Settings → Environments → `play-store`:

| Tipo | Nombre | Valor |
|---|---|---|
| Secret | `MCG_UPLOAD_KEYSTORE_BASE64` | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\ruta\segura\mcg-upload.jks"))` |
| Secret | `MCG_UPLOAD_STORE_PASSWORD` | Clave del keystore |
| Secret | `MCG_UPLOAD_KEY_ALIAS` | `mcg-upload` |
| Secret | `MCG_UPLOAD_KEY_PASSWORD` | Clave de la llave |
| Secret | `PLAY_SERVICE_ACCOUNT_JSON` | JSON de la cuenta de servicio (abajo) |
| Variable | `PLAY_UPLOAD_ENABLED` | `true` cuando la cuenta de servicio este lista |

Cuenta de servicio: Google Cloud → crear proyecto → habilitar *Google Play Android Developer API* → crear cuenta de servicio y llave JSON → Play Console → Usuarios y permisos → invitar el correo de la cuenta de servicio con permiso de *Publicar en pistas de prueba* solo para esta app.

Publicar una version:

```bash
git tag v0.1.0
git push origin v0.1.0
```

El workflow sube el AAB como **borrador** en el track interno; revisa y promueve desde la consola (interna → cerrada → produccion con despliegue escalonado).

Si el proyecto es un monorepo, mueve los workflows a la raiz, agrega `paths: ["App Android/**"]` y `defaults.run.working-directory: "App Android"`.

## 7. Contenido de la app y declaraciones de politica

Estas declaraciones salen directamente de `AndroidManifest.xml`. Sin ellas Play bloquea la publicacion, incluso en pruebas.

### 7.1 SMS — `SEND_SMS` (riesgo ALTO)

`SEND_SMS` es un permiso restringido: Play solo lo permite a la app de SMS predeterminada o a casos de uso de una **lista cerrada de excepciones**, con el *Formulario de declaracion de permisos*.

- Declara el uso central: "enviar automaticamente la ubicacion a contactos de emergencia elegidos por el usuario cuando el dispositivo complementario detecta una caida". Revisa en la politica vigente cual excepcion aplica (dispositivo complementario conectado / seguridad) y adjunta video.
- **La aprobacion no esta garantizada.** Plan B, a decidir antes de invertir en la ficha:
  1. Distribuir la version academica por **prueba interna/cerrada** o APK firmado, y tramitar la excepcion en paralelo.
  2. Crear un *product flavor* `play` sin `SEND_SMS` que abra la app de mensajes con `ACTION_SENDTO` (requiere un toque del usuario; degrada el despacho automatico y debe reflejarse en las specs).
- No agregues `READ_SMS`/`RECEIVE_SMS`: el flujo no los necesita y agravarian la revision.

### 7.2 Llamadas — `CALL_PHONE`

No es restringido por Play, pero es permiso *peligroso*: pidelo en contexto (configuracion de contactos) con explicacion previa.

### 7.3 Full-screen intent — `USE_FULL_SCREEN_INTENT` (riesgo MEDIO)

Desde Android 14 solo se concede por defecto a apps de **llamadas o alarmas**. Declara el uso en *Contenido de la app → Intents de pantalla completa* (la cuenta regresiva de emergencia funciona como alarma). Si Play no lo concede, la app debe verificar `NotificationManager.canUseFullScreenIntent()` y enviar al usuario a `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, con notificacion heads-up como fallback (ya previsto en la Fase 5).

### 7.4 Servicios en primer plano

Declara cada tipo en *Contenido de la app → Permisos de servicio en primer plano*, con descripcion y video corto:

| Tipo | Justificacion |
|---|---|
| `connectedDevice` | Mantener el enlace BLE con el dispositivo Moto Crash Guardian durante el viaje. |
| `location` | Obtener la ubicacion al iniciar la cuenta regresiva para incluirla en el SMS. |

Asegurate de declarar el `<service android:foregroundServiceType="connectedDevice|location">` cuando se implemente `GuardianService`.

### 7.5 Ubicacion

Solo ubicacion en primer plano (via FGS); **no** agregues `ACCESS_BACKGROUND_LOCATION`, que exige una revision adicional. Muestra una divulgacion destacada dentro de la app antes del dialogo del sistema (que datos, para que, con quien se comparten).

### 7.6 Bateria — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (riesgo MEDIO)

Play limita este permiso a casos donde la funcion principal se rompe sin la exencion. Recomendacion: **retirarlo del manifiesto** y, si hace falta, abrir `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no requiere permiso). CDM + FGS `connectedDevice` suelen bastar. Si se mantiene, justificalo en la declaracion.

### 7.7 Acceso a la app para revisores

La app depende de un dispositivo BLE que el revisor no tiene. En *Contenido de la app → Acceso a la app* explica como usar el **simulacro sin dispositivo** (Fase 6: fallback local, SMS marcados `[PRUEBA]`) y adjunta un video del flujo real. Sin esto Google suele rechazar por "no se pudo revisar".

### 7.8 Seguridad de los datos (Data safety)

Borrador segun el diseno actual; revisalo contra la implementacion final:

| Dato | Recopilado (sale del telefono) | Proposito | Opcional |
|---|---|---|---|
| Nombre del motociclista | Si (backend) | Funcionalidad de la app | No |
| Telefonos de contactos de emergencia | Si (backend) | Funcionalidad de la app | No |
| Ubicacion precisa | Si (incidentes en backend; SMS a contactos) | Funcionalidad de la app | No |
| Identificador de instalacion | Si (UUID + token) | Funcionalidad, seguridad | No |
| Trazas de sensores de incidentes | Si (backend) | Funcionalidad, analisis | Si |

- Datos cifrados en transito: **Si** (HTTPS obligatorio en release).
- El usuario puede solicitar el borrado: **Si** (`DELETE /api/v1/me` + borrado local).
- No se venden datos ni se usan para publicidad.
- El SMS a contactos lo inicia el propio flujo de emergencia configurado por el usuario; describelo en la politica de privacidad.

### 7.9 Otros formularios

- **Politica de privacidad**: URL publica obligatoria (permisos sensibles). Borrador en [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md); publicala por ejemplo con GitHub Pages.
- **Publico objetivo**: 18+ (motociclistas); no dirigida a ninos.
- **Clasificacion de contenido**: cuestionario IARC (sin contenido sensible).
- **Anuncios**: no contiene.
- **Apps de salud**: completa la declaracion indicando que no es un dispositivo medico.
- **Afirmaciones**: la ficha no debe prometer deteccion garantizada; incluye el descargo de prototipo academico y la linea 123 (ver `STORE_LISTING.md`).

## 8. Recursos graficos de la ficha

| Recurso | Especificacion |
|---|---|
| Icono | 512×512 PNG 32 bits |
| Grafico de funciones | 1024×500 JPG/PNG |
| Capturas de telefono | 2 a 8, relacion 16:9 o 9:16, lado minimo 320 px |
| Video (opcional) | URL de YouTube |

## 9. Checklist por version

- [ ] `versionCode` mayor que la ultima subida (CI lo garantiza con `100 + run_number`).
- [ ] `mcg.apiBaseUrl` apunta al backend de Render vigente (HTTPS).
- [ ] `testDebugUnitTest` y `lint` en verde; release instalado y probado en telefono real.
- [ ] Ruta de emergencia probada offline (sin backend ni internet).
- [ ] `mapping.txt` subido.
- [ ] Notas de la version en espanol.
- [ ] Declaraciones de la seccion 7 vigentes si cambiaron permisos.
