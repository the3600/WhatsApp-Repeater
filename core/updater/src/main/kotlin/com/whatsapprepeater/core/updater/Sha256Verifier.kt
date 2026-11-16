package com.whatsapprepeater.core.updater

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Calcula y verifica resúmenes SHA-256 para validar la integridad de APKs descargados.
 */
object Sha256Verifier {

  private val sha256HexRegex = Regex("\\b([a-fA-F0-9]{64})\\b")

  fun computeSha256(file: File): String =
    file.inputStream().use { computeSha256(it) }

  fun computeSha256(inputStream: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    var read = inputStream.read(buffer)
    while (read > 0) {
      digest.update(buffer, 0, read)
      read = inputStream.read(buffer)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun verifyHexMatch(actualHex: String, expectedHex: String): Boolean {
    val a = actualHex.trim().lowercase()
    val b = expectedHex.trim().lowercase()
    if (a.length != 64 || b.length != 64) return false
    var diff = 0
    for (i in 0 until 64) {
      diff = diff or (a[i].code xor b[i].code)
    }
    return diff == 0
  }

  fun extractSha256FromText(content: String?): String? {
    if (content.isNullOrBlank()) return null
    return sha256HexRegex.find(content)?.groupValues?.getOrNull(1)?.lowercase()
  }
}

