package com.motocrashguardian.ui.theme

import androidx.compose.ui.graphics.Color

// --- Tema oscuro (paleta basada en la imagen de referencia) ---
internal val DarkBackground = Color(0xFF0F1115)
internal val DarkSurface = Color(0xFF1B1F23)
internal val DarkSurfaceVariant = Color(0xFF2C3136)
internal val DarkSurfaceHigh = Color(0xFF262B30)
internal val DarkPrimary = Color(0xFF4DB6E1)
internal val DarkPrimaryContainer = Color(0xFF0E4A63)
internal val DarkOnPrimaryContainer = Color(0xFFBDE6F7)
internal val DarkSecondaryContainer = Color(0xFF22404F)
internal val DarkOnSecondaryContainer = Color(0xFFD6EEF8)
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkTextPrimary = Color(0xFFE1E1E1)
internal val DarkTextSecondary = Color(0xFFB0B0B0)
internal val DarkOutline = Color(0xFF6B7580)
internal val DarkOutlineVariant = Color(0xFF383D42)

// --- Tema claro ---
internal val LightBackground = Color(0xFFF4F7FA)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFE3EAF0)
internal val LightSurfaceHigh = Color(0xFFEEF2F6)
internal val LightPrimary = Color(0xFF0B6E99)
internal val LightPrimaryContainer = Color(0xFFCDE8F5)
internal val LightOnPrimaryContainer = Color(0xFF003A52)
internal val LightSecondaryContainer = Color(0xFFD6E4F0)
internal val LightOnSecondaryContainer = Color(0xFF0B2A3F)
internal val LightError = Color(0xFFB3261E)
internal val LightTextPrimary = Color(0xFF11181F)
internal val LightTextSecondary = Color(0xFF4A5560)
internal val LightOutline = Color(0xFF6E7A85)
internal val LightOutlineVariant = Color(0xFFC5CED6)

/**
 * Colores de estado que Material 3 no trae (exito, advertencia, peligro). Cada tema define los
 * suyos para que el contraste se cumpla en ambos; se leen con `MaterialTheme.appColors`.
 */
data class AppColors(
    /** Texto o icono de exito sobre `surface` o `background`. */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Relleno solido (insignias) con texto/icono blanco encima. */
    val successSolid: Color,
    /** Texto o icono de advertencia sobre `surface` o `background`. */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Relleno solido de peligro con texto/icono blanco encima. */
    val dangerSolid: Color,
    /** Linea del umbral de impacto en la grafica de la traza. */
    val chartThreshold: Color,
    /** Muestras saturadas en la grafica de la traza. */
    val chartSaturation: Color
)

internal val DarkAppColors = AppColors(
    success = Color(0xFF6BFF8F),
    successContainer = Color(0xFF17301D),
    onSuccessContainer = Color(0xFFC9F7D3),
    successSolid = Color(0xFF2E7D32),
    warning = Color(0xFFFFB300),
    warningContainer = Color(0xFF553800),
    onWarningContainer = Color(0xFFFFDDB8),
    dangerSolid = Color(0xFFD32F2F),
    chartThreshold = Color(0xFFFFB300),
    chartSaturation = Color(0xFFFF5252)
)

internal val LightAppColors = AppColors(
    success = Color(0xFF1B6B2A),
    successContainer = Color(0xFFDFF3E3),
    onSuccessContainer = Color(0xFF0E3B17),
    successSolid = Color(0xFF2E7D32),
    warning = Color(0xFF8A5200),
    warningContainer = Color(0xFFFFEFD0),
    onWarningContainer = Color(0xFF4A2C00),
    dangerSolid = Color(0xFFD32F2F),
    chartThreshold = Color(0xFFA85F00),
    chartSaturation = Color(0xFFC62828)
)
