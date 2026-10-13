package com.motocrashguardian.data.remote

import kotlinx.serialization.json.Json

/**
 * Configuracion JSON del contrato con el backend: ignora campos nuevos del servidor y omite
 * los `null` (el backend trata un campo ausente igual que uno nulo).
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
