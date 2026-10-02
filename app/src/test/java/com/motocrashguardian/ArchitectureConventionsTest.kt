package com.motocrashguardian

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guardia de las convenciones de docs/05-app-architecture.md: el protocolo BLE, la deteccion y
 * los modelos de dominio son Kotlin puro para probarse en JVM sin Android ni hardware.
 */
class ArchitectureConventionsTest {

    private val sourceRoot = File("src/main/java/com/motocrashguardian")

    private val purePackages = listOf("ble/protocol", "detection", "core/model")
    private val forbiddenImports = listOf("import android.", "import androidx.", "import dagger.", "import javax.inject.")

    @Test
    fun `paquetes puros no dependen de Android ni de Hilt`() {
        assertTrue(sourceRoot.isDirectory, "No se encontro ${sourceRoot.absolutePath}")
        val violations = purePackages
            .flatMap { kotlinFiles(File(sourceRoot, it)) }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { line.trim().startsWith(it) } }
                    .map { "${file.relativeTo(sourceRoot)}: ${it.trim()}" }
            }
        assertTrue(violations.isEmpty(), "Imports prohibidos:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `el package de cada archivo coincide con su carpeta`() {
        val violations = kotlinFiles(sourceRoot).mapNotNull { file ->
            val expected = "com.motocrashguardian" +
                file.parentFile.relativeTo(sourceRoot).invariantSeparatorsPath
                    .takeIf { it.isNotEmpty() }?.let { "." + it.replace('/', '.') }.orEmpty()
            val declared = file.useLines { lines ->
                lines.firstOrNull { it.startsWith("package ") }?.removePrefix("package ")?.trim()
            }
            if (declared != expected) "${file.relativeTo(sourceRoot)}: $declared (esperado $expected)" else null
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
