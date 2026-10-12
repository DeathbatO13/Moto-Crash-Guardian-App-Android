package com.motocrashguardian.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * S-02 Consentimiento de datos (docs/06 §4; boceto Stitch AI
 * adaptado al tema M3). Qué se recoge, para qué, dónde,
 * derecho a borrar y casilla de consentimiento que habilita
 * **Acepto**.
 */
@Composable
fun ConsentScreen(
    onBack: () -> Unit = {},
    onAccept: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var consented by rememberSaveable { mutableStateOf(false) }
    var showDeclineDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Barra superior: volver + indicador de paso
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Volver")
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "PASO 2 DE 4 · PRIVACIDAD",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Policy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LEY 1581 DE 2012 · HABEAS DATA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Consentimiento de datos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Moto Crash Guardian procesa tu telemetría en el " +
                "dispositivo y el ESP32. Tus incidentes y ajustes se " +
                "sincronizan con nuestro servidor (Render, EE. UU.) " +
                "para tu historial.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Tarjeta de privacidad destacada
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Procesamiento inercial local",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Los datos del MPU-6050 se analizan en " +
                            "tiempo real en la app y el ESP32; no se " +
                            "envían a internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Qué recopilamos (docs/06 §4, S-02)
        Text(
            text = "¿QUÉ RECOPILAMOS?",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        ConsentRow(
            icon = Icons.Filled.Person,
            title = "Tu nombre",
            body = "Para identificarte en el historial y los mensajes de SOS."
        )
        ConsentRow(
            icon = Icons.Filled.ContactPhone,
            title = "Dos contactos de emergencia",
            body = "A quienes avisamos con tu ubicación si no respondes " +
                "la cuenta regresiva."
        )
        ConsentRow(
            icon = Icons.Filled.MyLocation,
            title = "Ubicación",
            body = "Solo durante incidentes, para adjuntar coordenadas " +
                "al mensaje de SOS."
        )
        ConsentRow(
            icon = Icons.Filled.Sensors,
            title = "Datos del sensor",
            body = "Acelerómetro e giroscopio del MPU-6050, para " +
                "detectar la caída."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Para qué: avisar a tus contactos, guardar tu " +
                "historial y mejorar la detección.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Dónde: en tu teléfono y en nuestro servidor " +
                "(Render, EE. UU.). Derecho a borrar desde Ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Marco legal + casilla de consentimiento
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Marco legal de habeas data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "En cumplimiento de la Ley Estatutaria 1581 " +
                        "de 2012 y el Decreto 1377 de 2013 de Colombia, " +
                        "tus contactos y registros cinemáticos quedan " +
                        "almacenados en la memoria interna del " +
                        "dispositivo. No hay transmisión a entidades " +
                        "publicitarias.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { consented = !consented }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = consented,
                        onCheckedChange = { consented = it }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Autorizo el tratamiento de mis datos " +
                            "únicamente para las funciones de protección " +
                            "y auxilio vial de Moto Crash Guardian.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Acciones
        Button(
            onClick = onAccept,
            enabled = consented,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = "ACEPTO")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { showDeclineDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "No acepto")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Puedes revocar o editar estos permisos cuando " +
                    "desees desde Ajustes > Aplicaciones de Android.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // "No acepto": sin contactos la app no puede funcionar
    // (docs/06 §4, S-02)
    if (showDeclineDialog) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = { Text(text = "Sin consentimiento") },
            text = {
                Text(
                    text = "Sin tus datos de contacto la app no puede " +
                        "funcionar: no habría a quién avisar en una " +
                        "emergencia. Puedes cambiar de opinión en " +
                        "cualquier momento."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeclineDialog = false }) {
                    Text(text = "Entendido")
                }
            }
        )
    }
}

@Composable
private fun ConsentRow(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
