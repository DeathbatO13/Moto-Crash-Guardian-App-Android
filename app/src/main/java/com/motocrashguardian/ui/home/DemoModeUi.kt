package com.motocrashguardian.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motocrashguardian.emergency.DemoModeController
import com.motocrashguardian.emergency.DemoState
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration

private val DemoOrange = Color(0xFFFFB300)
private val DemoOnOrange = Color(0xFF3E2A00)

/** `mm:ss` del tiempo que queda; nunca negativo. */
fun formatRemaining(remaining: Duration): String {
    val seconds = remaining.seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Banner visible en toda la app mientras el demo esta activo, con el tiempo que le queda. */
@Composable
fun DemoBanner(
    state: DemoState,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC()
) {
    if (state !is DemoState.On) return
    var now by remember(state.expiresAt) { mutableStateOf(clock.instant()) }
    LaunchedEffect(state.expiresAt) {
        while (clock.instant().isBefore(state.expiresAt)) {
            now = clock.instant()
            delay(1_000)
        }
        now = clock.instant()
    }
    DemoBannerContent(
        remainingText = formatRemaining(Duration.between(now, state.expiresAt)),
        onDisable = onDisable,
        modifier = modifier
    )
}

@Composable
fun DemoBannerContent(
    remainingText: String,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DemoOrange,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MODO DEMO · $remainingText",
                    color = DemoOnOrange,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Las alertas son simulacros.",
                    color = DemoOnOrange,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onDisable) {
                Text("DESACTIVAR", color = DemoOnOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Tarjeta con el interruptor del modo demo. */
@Composable
fun DemoModeCard(
    state: DemoState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Modo demo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Para presentaciones: cuenta regresiva de 10 s y reglas más sensibles. " +
                        "Se desactiva solo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state is DemoState.On, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun DemoConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Activar el modo demo?") },
        text = {
            Text(
                "Durante ${DemoModeController.DefaultDuration.toMinutes()} minutos:\n" +
                    "• la cuenta regresiva dura 10 s\n" +
                    "• la detección es más sensible\n" +
                    "• toda alerta se trata como simulacro: los SMS salen marcados como [PRUEBA]\n\n" +
                    "No cambia tu configuración ni la del módulo, y se desactiva solo."
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("ACTIVAR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
