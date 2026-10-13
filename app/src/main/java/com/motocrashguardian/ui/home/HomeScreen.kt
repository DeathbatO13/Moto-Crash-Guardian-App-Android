package com.motocrashguardian.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.core.model.Telemetry
import com.motocrashguardian.ui.theme.AlertRed
import com.motocrashguardian.ui.theme.DarkSurface
import com.motocrashguardian.ui.theme.DarkSurfaceVariant
import com.motocrashguardian.ui.theme.PrimaryBlue
import com.motocrashguardian.ui.theme.SecondaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPrivacy: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedTab by remember { mutableIntStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Moto Crash Guardian",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "DTPE",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPrivacy) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = "Privacidad y datos"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Shield, contentDescription = null) },
                    label = { Text("Inicio") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.ContactPhone, contentDescription = null) },
                    label = { Text("Contactos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Tune, contentDescription = null) },
                    label = { Text("Calibración") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (selectedTab) {
                1 -> {
                    // Pestaña Contactos
                    StatusBanner(
                        icon = Icons.Default.CheckCircle,
                        message = "Contactos guardados",
                        subMessage = "Sincronizados con el dispositivo",
                        color = Color(0xFF2E7D32)
                    )

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = PrimaryBlue
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "PROTOCOLO DE ASISTENCIA CO",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Contactos de emergencia",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Se notificará automáticamente mediante SMS y llamada con tus coordenadas GPS si se detecta un impacto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ContactCard(
                        name = settings.primaryContact?.name ?: "Sin configurar",
                        phone = settings.primaryContact?.phoneE164 ?: "--",
                        isPrimary = true
                    )

                    ContactCard(
                        name = settings.secondaryContact?.name ?: "Sin configurar",
                        phone = settings.secondaryContact?.phoneE164 ?: "--",
                        isPrimary = false
                    )

                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContactPage, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir de la agenda")
                    }

                    Text(
                        text = "Editar información",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    EditInfoForm(
                        settings = settings,
                        onSave = { name, pName, pPhone, sName, sPhone ->
                            viewModel.updateProfile(name, pName, pPhone, sName, sPhone)
                        }
                    )
                    
                    LinkVerificationSection(onSendTestSms = { viewModel.triggerTest() })
                }
                0 -> {
                    // Pestaña Inicio
                    ProtectionToggle(
                        isActive = connectionState == DeviceConnectionState.READY,
                        onToggle = { viewModel.toggleProtection() }
                    )
                    TelemetryCard(telemetry)
                }
                2 -> {
                    // Pestaña Calibración
                    CalibrationSection(
                        connectionState = connectionState,
                        onTare = { viewModel.tare() }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusBanner(icon: ImageVector, message: String, subMessage: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(text = subMessage, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ContactCard(name: String, phone: String, isPrimary: Boolean) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = PrimaryBlue,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPrimary) Icons.Default.Place else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (isPrimary) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    Text(text = phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = if (isPrimary) AlertRed else DarkSurfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPrimary) Icons.Default.SmsFailed else Icons.Default.Sms,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isPrimary) " SMS + LLAMADA" else " SMS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            if (isPrimary) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PriorityHigh, contentDescription = null, tint = Color(0xFF6BFF8F), modifier = Modifier.size(12.dp))
                    Text(
                        text = " Prioridad Alta - Primer Receptor SOS",
                        fontSize = 11.sp,
                        color = Color(0xFF6BFF8F),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(text = "Mín. 00:00s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EditInfoForm(settings: com.motocrashguardian.core.model.AppSettings, onSave: (String, String, String, String, String) -> Unit) {
    var name by remember(settings.riderName) { mutableStateOf(settings.riderName) }
    var pName by remember(settings.primaryContact?.name) { mutableStateOf(settings.primaryContact?.name ?: "") }
    var pPhone by remember(settings.primaryContact?.phoneE164) { mutableStateOf(settings.primaryContact?.phoneE164 ?: "") }
    var sName by remember(settings.secondaryContact?.name) { mutableStateOf(settings.secondaryContact?.name ?: "") }
    var sPhone by remember(settings.secondaryContact?.phoneE164) { mutableStateOf(settings.secondaryContact?.phoneE164 ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditField(label = "TU NOMBRE", value = name, onValueChange = { name = it }, icon = Icons.Default.Badge)
        EditField(label = "CONTACTO 1", value = pName, onValueChange = { pName = it }, icon = Icons.Default.PersonOutline)
        EditField(label = "TELÉFONO 1", value = pPhone, onValueChange = { pPhone = it }, icon = Icons.Default.PhoneIphone)
        EditField(label = "CONTACTO 2", value = sName, onValueChange = { sName = it }, icon = Icons.Default.PersonOutline)
        EditField(label = "TELÉFONO 2", value = sPhone, onValueChange = { sPhone = it }, icon = Icons.Default.Dialpad)

        Surface(
            color = Color(0xFF553800).copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF553800))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFDDB8), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Usa código de país (+57).",
                    fontSize = 11.sp,
                    color = Color(0xFFFFDDB8)
                )
            }
        }

        Button(
            onClick = { onSave(name, pName, pPhone, sName, sPhone) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = "Guardar contactos")
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = DarkSurface,
                focusedContainerColor = DarkSurface,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = PrimaryBlue
            )
        )
    }
}

@Composable
private fun LinkVerificationSection(onSendTestSms: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Construction, contentDescription = null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = "Verificación de enlace", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Envía un mensaje de prueba para asegurar que el sistema funciona correctamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onSendTestSms,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = "Generar simulacro de alerta")
        }
        
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = "ESTADO DEL HARDWARE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Sensor listo para detección", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProtectionToggle(isActive: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (isActive) Color(0xFF1B5E20).copy(alpha = 0.2f) else DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF6BFF8F) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "PROTECCIÓN ACTIVA" else "PROTECCIÓN DESACTIVADA",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isActive) "Monitoreando telemetría..." else "Presiona para iniciar viaje",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isActive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun TelemetryCard(telemetry: Telemetry?) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TelemetryItem(Icons.Default.Height, "Pitch", "${(telemetry?.pitchCdeg ?: 0) / 100f}°")
            TelemetryItem(Icons.AutoMirrored.Filled.RotateRight, "Roll", "${(telemetry?.rollCdeg ?: 0) / 100f}°")
            TelemetryItem(Icons.Default.BatteryFull, "Batería", "${telemetry?.batteryPercent ?: "--"}%")
        }
    }
}

@Composable
private fun TelemetryItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun CalibrationSection(connectionState: DeviceConnectionState, onTare: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Calibración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Asegúrate de que la moto esté nivelada antes de calibrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onTare,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = connectionState == DeviceConnectionState.READY,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Straighten, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = "FIJAR NIVEL NEUTRO (TARE)")
        }
    }
}
