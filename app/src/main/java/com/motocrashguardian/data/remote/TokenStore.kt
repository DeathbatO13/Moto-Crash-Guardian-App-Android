package com.motocrashguardian.data.remote

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log

/** Sesion de la instalacion con el backend: id y token Bearer. */
interface AuthSession {
    fun installationId(): String?
    fun token(): String?
    fun save(installationId: String, token: String)
    fun clear()
}

/** Cifrado simetrico de secretos; en produccion lo respalda Android Keystore (AES-GCM). */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * Guarda el token cifrado. Las preferencias estan excluidas de las copias de seguridad, y la
 * clave de Keystore no sale del dispositivo, asi que un token restaurado en otro equipo no se
 * puede descifrar: se descarta y la app se vuelve a registrar.
 */
class TokenStore(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher
) : AuthSession {

    override fun installationId(): String? = preferences.getString(KeyInstallationId, null)

    override fun token(): String? {
        val stored = preferences.getString(KeyToken, null) ?: return null
        return try {
            String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (error: Exception) {
            Log.w(Tag, "El token guardado no se puede descifrar; se descarta.")
            clear()
            null
        }
    }

    override fun save(installationId: String, token: String) {
        val encrypted = Base64.encodeToString(
            cipher.encrypt(token.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        preferences.edit()
            .putString(KeyInstallationId, installationId)
            .putString(KeyToken, encrypted)
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(KeyInstallationId).remove(KeyToken).apply()
    }

    private companion object {
        const val Tag = "TokenStore"
        const val KeyInstallationId = "installation_id"
        const val KeyToken = "token_encrypted"
    }
}
