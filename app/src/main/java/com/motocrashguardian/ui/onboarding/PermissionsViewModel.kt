package com.motocrashguardian.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * S-03 Asistente de permisos. 
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _granted = MutableStateFlow<Set<String>>(emptySet())
    val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    private val _asked = MutableStateFlow<Set<String>>(emptySet())
    private val _allRequiredGranted = MutableStateFlow(false)
    val allRequiredGranted: StateFlow<Boolean> = _allRequiredGranted.asStateFlow()

    private val _batteryUnrestricted = MutableStateFlow(false)
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _granted.value = allPossiblePermissions().filter { isGranted(it) }.toSet()
        _batteryUnrestricted.value = isBatteryUnrestricted()
        recomputeRequired()
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        _asked.value = _asked.value + grants.keys
        val current = _granted.value.toMutableSet()
        grants.forEach { (permission, granted) ->
            if (granted) current += permission else current -= permission
        }
        _granted.value = current
        recomputeRequired()
    }

    fun isPermanentlyDenied(permission: String, activity: Activity?): Boolean =
        permission in _asked.value &&
            permission !in _granted.value &&
            activity?.shouldShowRequestPermissionRationale(permission) == false

    val batterySettingsIntent: Intent
        get() = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    val appSettingsIntent: Intent
        get() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    val isAggressiveOem: Boolean = Build.MANUFACTURER.lowercase() in AGGRESSIVE_OEMS

    private fun recomputeRequired() {
        _allRequiredGranted.value = requiredPermissions().all { it in _granted.value }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isBatteryUnrestricted(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    /** Todos los permisos que la pantalla puede pedir o mostrar. */
    private fun allPossiblePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.CALL_PHONE)
    }

    /** Permisos críticos para poder continuar. */
    private fun requiredPermissions(): List<String> = allPossiblePermissions()

    companion object {
        private val AGGRESSIVE_OEMS = listOf("xiaomi", "oppo", "realme", "vivo", "huawei", "samsung")
    }
}
