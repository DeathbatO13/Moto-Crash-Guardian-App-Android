package com.motocrashguardian.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motocrashguardian.R
import com.motocrashguardian.core.model.IncidentStatus

private val ScreenBackground = Color(0xFF121316)

@Composable
fun DispatchingScreen(modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Text(
            text = stringResource(R.string.alert_dispatching),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ResultScreen(
    status: IncidentStatus,
    isSimulation: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenteredColumn(modifier) {
        if (isSimulation) {
            Text(
                text = stringResource(R.string.alert_simulation),
                color = Color(0xFFFFDDB8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(resultTitle(status)),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(resultDetail(status)),
            color = Color(0xFFBDC8D1),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.alert_result_close))
        }
    }
}

private fun resultTitle(status: IncidentStatus): Int = when (status) {
    IncidentStatus.DISPATCHED -> R.string.alert_result_dispatched_title
    IncidentStatus.DISPATCH_PARTIAL -> R.string.alert_result_partial_title
    else -> R.string.alert_result_failed_title
}

private fun resultDetail(status: IncidentStatus): Int = when (status) {
    IncidentStatus.DISPATCHED -> R.string.alert_result_dispatched_detail
    IncidentStatus.DISPATCH_PARTIAL -> R.string.alert_result_partial_detail
    else -> R.string.alert_result_failed_detail
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        content()
    }
}
