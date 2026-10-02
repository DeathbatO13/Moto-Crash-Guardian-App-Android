# Politica de privacidad — Moto Crash Guardian (BORRADOR)

> Borrador tecnico para revision del equipo y, si aplica, de un asesor legal. Completa los campos `<...>`, verifica cada punto contra la implementacion final y usalo como base del texto de consentimiento de la app. Para la sustentacion (APK sin Play) no es obligatorio publicarlo en una URL; si la app se publica en Play, debe estar en una URL publica y coincidir con *Seguridad de los datos*. Debe ser coherente con `../docs/08-security-privacy.md`.

**Ultima actualizacion:** `<fecha>`
**Responsable del tratamiento:** `<nombre del equipo / institucion>` — contacto: `<correo-de-soporte>`

## 1. Alcance

Esta politica describe como la app Android Moto Crash Guardian y su servicio de respaldo tratan tus datos. Moto Crash Guardian es un prototipo academico y no es un servicio de emergencia certificado.

## 2. Datos que tratamos

| Dato | Para que | Donde se guarda |
|---|---|---|
| Tu nombre | Incluirlo en el SMS de emergencia | Telefono; respaldo en el servidor si activas la sincronizacion |
| Nombre y telefono de hasta 2 contactos de emergencia | Enviarles el SMS y llamar al contacto principal | Telefono; respaldo en el servidor |
| Ubicacion precisa | Incluirla en el SMS cuando se detecta una posible caida y registrar el incidente | Telefono; incidentes en el servidor |
| Datos del sensor alrededor del evento ("caja negra") | Mostrar el historial y analizar la deteccion | Telefono; respaldo en el servidor |
| Identificador aleatorio de instalacion | Asociar tus datos de respaldo sin crear una cuenta | Servidor (solo un hash del token de acceso) |

No recopilamos tu correo, contrasenas, contactos de la agenda (solo los que eliges), contenido de otros mensajes ni datos para publicidad.

## 3. Uso de permisos

- **Bluetooth**: conectar con el dispositivo instalado en la moto.
- **Ubicacion**: solo mientras hay un viaje activo o una alerta en curso; no se usa en segundo plano fuera del viaje.
- **SMS y llamadas**: exclusivamente para enviar la alerta y llamar a los contactos que configuraste. La app no lee tus mensajes ni tu registro de llamadas.
- **Notificaciones y pantalla completa**: mostrar la cuenta regresiva aunque el telefono este bloqueado.

## 4. Con quien se comparten

- **Tus contactos de emergencia** reciben tu nombre y ubicacion por SMS cuando la alerta no se cancela.
- **Proveedor de infraestructura**: el respaldo se aloja en Render (region Virginia, Estados Unidos). Esto implica una transferencia internacional de datos.
- No vendemos ni cedemos datos a terceros para publicidad o analitica.

## 5. Conservacion

- En el telefono: hasta 100 incidentes y trazas de hasta 90 dias.
- En el servidor: `<plazo definido por el equipo>` o hasta que solicites el borrado.

## 6. Tus derechos

Conforme a la Ley 1581 de 2012 (Colombia) puedes conocer, actualizar, rectificar y suprimir tus datos, y revocar la autorizacion. Desde la app puedes borrar tus datos locales y del servidor en *Ajustes → Privacidad → Borrar mis datos*. Tambien puedes escribir a `<correo-de-soporte>`.

## 7. Seguridad

Las comunicaciones con el servidor usan HTTPS. El token de acceso se guarda cifrado con Android Keystore en el telefono y el servidor solo almacena su hash. Los datos de la app se excluyen de las copias de seguridad de Android. Ningun sistema es 100 % seguro.

## 8. Menores de edad

La app esta dirigida a personas mayores de 18 anos que conducen motocicleta.

## 9. Cambios

Publicaremos cualquier cambio en esta pagina con su fecha de actualizacion.
