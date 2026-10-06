package com.motocrashguardian.ui.alert

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motocrashguardian.R
import kotlinx.coroutines.withTimeoutOrNull

private val AlertBackground = Color(0xFF121316)
private val AlertSurface = Color(0xFF1B1B1F)
private val AlertSurfaceHigh = Color(0xFF292A2D)
private val AlertSurfaceTrack = Color(0xFF343538)
private val AlertRed = Color(0xFFFFB4AB)
private val AlertRedContainer = Color(0xFF5A1018)
private val AlertActionRed = Color(0xFFBA1A1A)
private val AlertGreen = Color(0xFF6BFF8F)
private val AlertCyan = Color(0xFF8ED5FF)
private val AlertText = Color(0xFFE3E2E6)
private val AlertMuted = Color(0xFFBDC8D1)

private const val HoldToCancelDurationMillis = 1_000L

@Composable
fun CountdownScreen(
    remainingSeconds: Int,
    totalSeconds: Int,
    emergencyContactNames: List<String>,
    locationAccuracyMeters: Int?,
    isSimulation: Boolean,
    onCancelConfirmed: () -> Unit,
    onSendHelpNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalSeconds.coerceAtLeast(1)
    val safeRemaining = remainingSeconds.coerceIn(0, safeTotal)
    val progress = safeRemaining.toFloat() / safeTotal

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AlertBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSimulation) {
            SimulationBanner()
            Spacer(Modifier.height(12.dp))
        }

        AlertStatusBanner()
        Spacer(Modifier.height(18.dp))
        WarningHeader()
        Spacer(Modifier.height(12.dp))
        CountdownDial(
            remainingSeconds = safeRemaining,
            progress = progress
        )
        Spacer(Modifier.height(16.dp))
        EmergencyContactNotice(emergencyContactNames)
        Spacer(Modifier.height(16.dp))
        HoldToCancelButton(onConfirmed = onCancelConfirmed)
        Spacer(Modifier.height(12.dp))
        SendHelpButton(onClick = onSendHelpNow)
        Spacer(Modifier.height(16.dp))
        LocationFooter(locationAccuracyMeters)
    }
}

@Composable
private fun SimulationBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF553800))
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.alert_simulation),
            color = Color(0xFFFFDDB8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun AlertStatusBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AlertRedContainer.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(AlertRed, CircleShape)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.alert_status_active),
                color = Color(0xFFFFDAD6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Text(
            text = stringResource(R.string.alert_header_emergency),
            color = AlertRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun WarningHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(10.dp, CircleShape)
                .background(AlertRedContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = AlertRed,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.alert_possible_accident),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.2.sp
        )
        Text(
            text = stringResource(R.string.alert_are_you_ok),
            color = AlertRed,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CountdownDial(
    remainingSeconds: Int,
    progress: Float
) {
    val countdownDescription = stringResource(
        R.string.alert_countdown_content_description,
        remainingSeconds
    )
    Box(
        modifier = Modifier
            .size(232.dp)
            .semantics {
                contentDescription = countdownDescription
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            drawArc(
                color = AlertSurfaceTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = AlertRed,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingSeconds.toString(),
                color = Color.White,
                fontSize = 64.sp,
                lineHeight = 68.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.alert_seconds_to_cancel),
                color = AlertMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmergencyContactNotice(contactNames: List<String>) {
    val recipients = contactNames
        .filter(String::isNotBlank)
        .distinct()
        .joinToString()
        .ifBlank { stringResource(R.string.alert_emergency_contacts_fallback) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AlertSurface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.alert_emergency_contact_heading),
            color = AlertCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.alert_sos_recipient_message, recipients),
            color = AlertText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HoldToCancelButton(onConfirmed: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    val cancelActionDescription = stringResource(R.string.alert_cancel_action_description)
    val holdProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(durationMillis = HoldToCancelDurationMillis.toInt()),
        label = "holdToCancelProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AlertSurfaceHigh)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onLongClick(label = cancelActionDescription) {
                    onConfirmed()
                    true
                }
            }
            .pointerInput(onConfirmed) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isHolding = true
                    val heldLongEnough = withTimeoutOrNull(HoldToCancelDurationMillis) {
                        waitForUpOrCancellation()
                        false
                    } ?: true
                    isHolding = false
                    if (heldLongEnough) onConfirmed()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(holdProgress)
                .fillMaxSize()
                .align(Alignment.CenterStart)
                .background(AlertGreen.copy(alpha = 0.28f))
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.alert_hold_to_cancel),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.alert_hold_helper),
                color = AlertMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SendHelpButton(onClick: () -> Unit) {
    val sendHelpLabel = stringResource(R.string.alert_send_help_now)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AlertActionRed)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                semanticsOnClick(label = sendHelpLabel) {
                    onClick()
                    true
                }
            }
            .pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (waitForUpOrCancellation() != null) onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = sendHelpLabel,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun LocationFooter(locationAccuracyMeters: Int?) {
    val safeAccuracy = locationAccuracyMeters?.takeIf { it >= 0 }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (safeAccuracy == null) {
                stringResource(R.string.alert_location_unavailable)
            } else {
                stringResource(R.string.alert_location_available, safeAccuracy)
            },
            color = AlertMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.alert_dispatch_uses_location),
            color = AlertMuted.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun CountdownScreenPreview() {
    CountdownScreen(
        remainingSeconds = 20,
        totalSeconds = 20,
        emergencyContactNames = listOf("Contacto principal"),
        locationAccuracyMeters = 8,
        isSimulation = false,
        onCancelConfirmed = {},
        onSendHelpNow = {}
    )
}
