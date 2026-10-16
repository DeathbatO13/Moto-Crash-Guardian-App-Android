package com.motocrashguardian.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motocrashguardian.BuildConfig
import com.motocrashguardian.ui.components.SafeRideLogo

/**
 * S-01 Bienvenida (docs/06 §4; boceto Stitch AI adaptado al tema M3).
 * Ilustración moto+dispositivo, título, 3 viñetas, tarjeta de advertencia
 * de prototipo, casilla "Entiendo" y **Continuar** (deshabilitado hasta
 * marcar la casilla).
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var understood by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Indicadores superiores
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "PROYECTO ACADÉMICO / DEMO",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Ilustración: la moto con el dispositivo
        DeviceIllustration()

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Título y subtítulo
        SafeRideLogo(height = 56.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sistema de telemetría y detección automática de caídas " +
                "e impactos para motociclistas con sensor ESP32 y " +
                "procesamiento inercial local.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Tres viñetas: qué hace
        FeatureCard(
            icon = Icons.Filled.Explore,
            title = "Monitoreo en tiempo real",
            body = "Mide aceleración, inclinación (pitch/roll) y fuerzas G " +
                "a 200 Hz en segundo plano durante tus trayectos."
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Filled.AddAlert,
            title = "Protocolo de alerta automática",
            body = "Si sufres una caída o impacto severo, se inicia una " +
                "cuenta regresiva audible antes de activar ayuda."
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Filled.ContactPhone,
            title = "Notificación a tus contactos",
            body = "Envía SMS con tus coordenadas GPS directamente a tus " +
                "contactos de emergencia."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Tarjeta de advertencia de prototipo (docs/06 §4, S-01)
        PrototypeWarningCard()

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Casilla de aceptación (fila clickeable = área táctil
        // amplia para guantes, docs/06 §1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { understood = !understood }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = understood,
                onCheckedChange = { understood = it }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Entiendo que es un prototipo académico y asumo la " +
                    "responsabilidad de prueba en ruta.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7. Botón principal
        Button(
            onClick = onContinue,
            enabled = understood,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = "COMENZAR CONFIGURACIÓN")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (understood) {
                "Listo para emparejar el sensor ESP32 y configurar contactos SOS."
            } else {
                "Marca la casilla de responsabilidad para habilitar el acceso."
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (understood) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Moto con el dispositivo: anillos concéntricos, insignia ESP32 y sensores. */
@Composable
private fun DeviceIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anillos concéntricos (estáticos en v1; el pulso animado es v2)
        Box(
            modifier = Modifier
                .size(190.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    CircleShape
                )
        )
        // Caja central: la moto
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.TwoWheeler,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        // Insignia del dispositivo
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ESP32",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        // Sensores del prototipo
        SensorTag(
            modifier = Modifier.align(Alignment.BottomStart),
            icon = Icons.Filled.Sensors,
            text = "MPU-6050",
            tint = MaterialTheme.colorScheme.primary
        )
        SensorTag(
            modifier = Modifier.align(Alignment.BottomEnd),
            icon = Icons.Filled.MyLocation,
            text = "GNSS",
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SensorTag(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrototypeWarningCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AVISO IMPORTANTE DE PROTOTIPO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Este sistema es un prototipo de apoyo. No reemplaza a " +
                    "los servicios de emergencia ni garantiza la detección " +
                    "de todos los accidentes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
                    .copy(alpha = 0.8f)
            )
        }
    }
}
