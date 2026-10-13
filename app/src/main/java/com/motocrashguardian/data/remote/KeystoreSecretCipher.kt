package com.motocrashguardian.data.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM con una clave no exportable de Android Keystore. Formato: IV (12 B) + cifrado. */
class KeystoreSecretCipher : SecretCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IvSize) { "Dato cifrado truncado" }
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TagBits, blob, 0, IvSize))
        return cipher.doFinal(blob, IvSize, blob.size - IvSize)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(Provider).apply { load(null) }
        (keyStore.getKey(Alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider).apply {
            init(
                KeyGenParameterSpec.Builder(
                    Alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val Provider = "AndroidKeyStore"
        const val Alias = "mcg_auth_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagBits = 128
    }
}
