package com.motocrashguardian.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.motocrashguardian.ui.theme.appColors
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale


/**
 * Aceleracion alrededor del disparo (de -3 a +3 s): linea de magnitud, instante del disparo,
 * umbral de impacto y las muestras en las que el sensor llego a su limite.
 */
@Composable
fun TraceChart(
    trace: DecodedTrace,
    impactThresholdG: Float,
    modifier: Modifier = Modifier
) {
    val windowed = trace.window()
    val maxG = chartMaxG(windowed.points, impactThresholdG)
    val lineColor = MaterialTheme.colorScheme.primary
    val triggerColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = MaterialTheme.appColors.chartThreshold
    val saturationColor = MaterialTheme.appColors.chartSaturation
    val description = chartDescription(windowed, impactThresholdG)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = description }
        ) {
            val width = size.width
            val height = size.height

            // Rejilla horizontal.
            for (step in 1..3) {
                val y = height * step / 4f
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
            }

            // Umbral de impacto.
            val thresholdY = thresholdY(impactThresholdG, maxG, height)
            drawLine(
                color = thresholdColor,
                start = Offset(0f, thresholdY),
                end = Offset(width, thresholdY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )

            // Instante del disparo (t = 0).
            val triggerX = width * (0f - DecodedTrace.WindowStartSeconds) /
                (DecodedTrace.WindowEndSeconds - DecodedTrace.WindowStartSeconds)
            drawLine(triggerColor, Offset(triggerX, 0f), Offset(triggerX, height), strokeWidth = 3f)

            val offsets = mapToCanvas(
                windowed.points,
                DecodedTrace.WindowStartSeconds,
                DecodedTrace.WindowEndSeconds,
                maxG,
                width,
                height
            )
            if (offsets.size >= 2) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, lineColor, style = Stroke(width = 4f))
            }
            windowed.points.zip(offsets).filter { it.first.saturated }.forEach { (_, point) ->
                drawCircle(saturationColor, radius = 7f, center = point)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel("−3 s")
            AxisLabel("Disparo")
            AxisLabel("+3 s")
        }
        Legend(trace = windowed, impactThresholdG = impactThresholdG)
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Legend(trace: DecodedTrace, impactThresholdG: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LegendLine(
            MaterialTheme.colorScheme.primary,
            "Aceleración (pico ${formatG(trace.peakAccelG)})"
        )
        LegendLine(MaterialTheme.appColors.chartThreshold, "Umbral de impacto actual (${formatG(impactThresholdG)})")
        if (trace.saturatedCount > 0) {
            LegendLine(
                MaterialTheme.appColors.chartSaturation,
                "Sensor saturado en ${trace.saturatedCount} muestra(s): el pico real pudo ser mayor"
            )
        }
    }
}

@Composable
private fun LegendLine(color: Color, text: String) {
    Row {
        Text("● ", color = color, style = MaterialTheme.typography.bodySmall)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatG(g: Float): String = String.format(Locale.forLanguageTag("es-CO"), "%.1f g", g)

/** Resumen textual de la grafica, para accesibilidad (TalkBack) y pruebas. */
internal fun chartDescription(trace: DecodedTrace, impactThresholdG: Float): String =
    "Gráfica de aceleración de −3 a +3 segundos. Pico de ${formatG(trace.peakAccelG)}, " +
        "umbral de ${formatG(impactThresholdG)}, " +
        "${trace.saturatedCount} muestra(s) con el sensor saturado."
