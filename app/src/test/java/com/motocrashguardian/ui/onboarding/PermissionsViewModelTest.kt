package com.motocrashguardian.ui.onboarding

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Lógica del asistente de permisos (docs/06 §4, S-03):
 * solo los P0 bloquean Continuar; la batería es P1.
 */
@RunWith(RobolectricTestRunner::class)
class PermissionsViewModelTest {

    private val context: Application =
        RuntimeEnvironment.getApplication()

    private val viewModel = PermissionsViewModel(context)

    @Test
    fun sinPermisosElArranqueEstaDenegado() {
        assertFalse(viewModel.allRequiredGranted.value)
        assertFalse(viewModel.batteryUnrestricted.value)
    }

    @Test
    fun concederLosP0HabilitaElArranque() {
        viewModel.onPermissionsResult(todosConcedidos())
        assertTrue(viewModel.allRequiredGranted.value)
    }

    @Test
    fun revocarUnP0VuelveADeshabilitar() {
        viewModel.onPermissionsResult(todosConcedidos())
        viewModel.onPermissionsResult(
            mapOf(Manifest.permission.SEND_SMS to false)
        )
        assertFalse(viewModel.allRequiredGranted.value)
    }

    @Test
    fun laBateriaNoBloqueaElArranque() {
        // P1: se recomienda, pero Continuar no la exige.
        viewModel.onPermissionsResult(todosConcedidos())
        assertFalse(viewModel.batteryUnrestricted.value)
        assertTrue(viewModel.allRequiredGranted.value)
    }

    @Test
    fun laFilaDeAjustesApareceSoloTrasDenegacionPermanente() {
        // Nunca pedido: no es denegación permanente.
        assertFalse(
            viewModel.isPermanentlyDenied(
                Manifest.permission.SEND_SMS,
                activity = null
            )
        )
        // Pedido y denegado (con actividad que no pide
        // rationale = "no preguntar de nuevo").
        viewModel.onPermissionsResult(
            mapOf(Manifest.permission.SEND_SMS to false)
        )
        assertTrue(
            viewModel.isPermanentlyDenied(
                Manifest.permission.SEND_SMS,
                activity = DenyingActivity()
            )
        )
    }

    private fun todosConcedidos(): Map<String, Boolean> = mapOf(
        Manifest.permission.POST_NOTIFICATIONS to true,
        Manifest.permission.BLUETOOTH_SCAN to true,
        Manifest.permission.BLUETOOTH_CONNECT to true,
        Manifest.permission.SEND_SMS to true,
        Manifest.permission.CALL_PHONE to true
    )
}

/** Actividad que reporta "no mostrar rationale" (= denegación permanente). */
private class DenyingActivity : android.app.Activity() {
    override fun shouldShowRequestPermissionRationale(
        permission: String
    ): Boolean = false
}
