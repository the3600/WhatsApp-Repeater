package com.whatsapprepeater.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class KeystoreVaultStatus(
  val isAvailable: Boolean,
  val isVerified: Boolean,
  val detail: String,
)

/**
 * Bóveda criptográfica local respaldada por Android Keystore (`AES/GCM/NoPadding`, 256 bits).
 * La clave secreta se genera y permanece dentro del almacén seguro del sistema Android;
 * jamás se exporta ni se guarda junto a los datos protegidos.
 */
class AndroidKeystoreVault(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
  fun verifyHealth(): KeystoreVaultStatus {
    return runCatching {
      val sample = "wr-keystore-health-check"
      val encrypted = encrypt(sample)
      val decrypted = decrypt(encrypted)
      val ok = decrypted == sample
      KeystoreVaultStatus(
        isAvailable = true,
        isVerified = ok,
        detail = if (ok) {
          "Android Keystore operativo (AES-256-GCM autenticado, clave no exportable)."
        } else {
          "Android Keystore respondió con discrepancia en autocomprobación."
        },
      )
    }.getOrElse { err ->
      KeystoreVaultStatus(
        isAvailable = false,
        isVerified = false,
        detail = "Android Keystore no disponible en este entorno: ${err.message ?: err.javaClass.simpleName}",
      )
    }
  }

  fun encrypt(plainText: String): String {
    val secretKey = getOrCreateKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val combined = ByteArray(1 + iv.size + cipherBytes.size)
    combined[0] = iv.size.toByte()
    System.arraycopy(iv, 0, combined, 1, iv.size)
    System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
    return Base64.getEncoder().encodeToString(combined)
  }

  fun decrypt(encodedCipherText: String): String {
    val combined = Base64.getDecoder().decode(encodedCipherText)
    require(combined.size > 13) { "Bloque cifrado inválido o truncado." }
    val ivLength = combined[0].toInt() and 0xFF
    require(ivLength in 12..16 && combined.size > 1 + ivLength) { "IV de cifrado inválido." }
    val iv = combined.copyOfRange(1, 1 + ivLength)
    val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)

    val secretKey = getOrCreateKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    if (existing != null) return existing

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(KEY_SIZE_BITS)
      .setRandomizedEncryptionRequired(true)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DEFAULT_KEY_ALIAS = "whatsapp_repeater_local_aes_gcm_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val GCM_TAG_BITS = 128
  }
}
