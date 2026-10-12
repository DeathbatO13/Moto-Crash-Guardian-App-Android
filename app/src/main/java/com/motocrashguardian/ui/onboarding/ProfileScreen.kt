package com.motocrashguardian.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    
    var name by remember(settings.riderName) { mutableStateOf(settings.riderName) }
    var primaryName by remember(settings.primaryContact?.name) { mutableStateOf(settings.primaryContact?.name ?: "") }
    var primaryPhone by remember(settings.primaryContact?.phoneE164) { mutableStateOf(settings.primaryContact?.phoneE164 ?: "") }
    var secondaryName by remember(settings.secondaryContact?.name) { mutableStateOf(settings.secondaryContact?.name ?: "") }
    var secondaryPhone by remember(settings.secondaryContact?.phoneE164) { mutableStateOf(settings.secondaryContact?.phoneE164 ?: "") }

    val isFormValid = name.isNotBlank() && primaryName.isNotBlank() && primaryPhone.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tu Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configura tus datos básicos para la emergencia.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tu Nombre") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            HorizontalDivider()

            Text(
                text = "Contactos de Emergencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "A quién avisaremos primero:",
                style = MaterialTheme.typography.labelMedium
            )

            OutlinedTextField(
                value = primaryName,
                onValueChange = { primaryName = it },
                label = { Text("Nombre Contacto 1") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = primaryPhone,
                onValueChange = { primaryPhone = it },
                label = { Text("Teléfono (+57...)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Contacto opcional de respaldo:",
                style = MaterialTheme.typography.labelMedium
            )

            OutlinedTextField(
                value = secondaryName,
                onValueChange = { secondaryName = it },
                label = { Text("Nombre Contacto 2") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = secondaryPhone,
                onValueChange = { secondaryPhone = it },
                label = { Text("Teléfono (+57...)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.updateProfile(name, primaryName, primaryPhone, secondaryName, secondaryPhone)
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isFormValid
            ) {
                Text("CONTINUAR")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
