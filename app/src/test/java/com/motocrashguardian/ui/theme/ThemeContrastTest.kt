package com.motocrashguardian.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contraste WCAG 2.x de cada par texto/fondo (y icono/fondo) que usa la app, en los dos temas.
 * Texto normal: 4.5:1. Iconos, graficas y bordes de campos: 3:1.
 */
class ThemeContrastTest {

    private data class Pair(val name: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun pairs(s: ColorScheme, c: AppColors): List<Pair> {
        val text = 4.5
        val graphic = 3.0
        return listOf(
            Pair("onBackground/background", s.onBackground, s.background, text),
            Pair("onSurface/background", s.onSurface, s.background, text),
            Pair("onSurface/surface", s.onSurface, s.surface, text),
            Pair("onSurface/surfaceVariant", s.onSurface, s.surfaceVariant, text),
            Pair("onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh, text),
            Pair("onSurfaceVariant/background", s.onSurfaceVariant, s.background, text),
            Pair("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, text),
            Pair("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, text),
            Pair("onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh, text),
            Pair("onPrimary/primary", s.onPrimary, s.primary, text),
            Pair("primary/background", s.primary, s.background, text),
            Pair("primary/surface", s.primary, s.surface, text),
            Pair("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, text),
            Pair("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, text),
            Pair("onSurfaceVariant/background (barra de navegacion)", s.onSurfaceVariant, s.background, text),
            Pair("onError/error", s.onError, s.error, text),
            Pair("error/background", s.error, s.background, text),
            Pair("error/surface", s.error, s.surface, text),
            Pair("outline/background", s.outline, s.background, graphic),
            Pair("outline/surface", s.outline, s.surface, graphic),
            Pair("success/background", c.success, s.background, text),
            Pair("success/surface", c.success, s.surface, text),
            Pair("success/successContainer", c.success, c.successContainer, text),
            Pair("onSuccessContainer/successContainer", c.onSuccessContainer, c.successContainer, text),
            Pair("onSurface/successContainer", s.onSurface, c.successContainer, text),
            Pair("onSurfaceVariant/successContainer", s.onSurfaceVariant, c.successContainer, text),
            Pair("blanco/successSolid", Color.White, c.successSolid, text),
            Pair("blanco/dangerSolid", Color.White, c.dangerSolid, text),
            Pair("warning/background", c.warning, s.background, text),
            Pair("warning/surface", c.warning, s.surface, text),
            Pair("onWarningContainer/warningContainer", c.onWarningContainer, c.warningContainer, text),
            Pair("chartThreshold/surface", c.chartThreshold, s.surface, graphic),
            Pair("chartSaturation/surface", c.chartSaturation, s.surface, graphic)
        )
    }

    private fun check(theme: String, s: ColorScheme, c: AppColors) {
        val failures = pairs(s, c)
            .map { it to contrast(it.foreground, it.background) }
            .filter { (pair, ratio) -> ratio < pair.minimum }
            .map { (pair, ratio) -> "${pair.name}: %.2f < %.1f".format(ratio, pair.minimum) }
        assertTrue(failures.isEmpty(), "Contraste insuficiente en el tema $theme:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `el tema claro cumple el contraste en todos sus pares`() =
        check("claro", LightColorScheme, LightAppColors)

    @Test
    fun `el tema oscuro cumple el contraste en todos sus pares`() =
        check("oscuro", DarkColorScheme, DarkAppColors)

    @Test
    fun `los dos temas son distintos`() {
        assertTrue(LightColorScheme.background.luminance() > 0.5f)
        assertTrue(DarkColorScheme.background.luminance() < 0.1f)
    }
}
