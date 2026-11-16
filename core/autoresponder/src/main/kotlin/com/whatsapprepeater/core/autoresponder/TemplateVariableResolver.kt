package com.whatsapprepeater.core.autoresponder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reemplaza variables locales cuando existe información disponible, usando
 * valores seguros si algún dato no aparece en la notificación.
 */
object TemplateVariableResolver {

  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

  fun resolve(
    template: String,
    senderOrChatName: String?,
    incomingMessage: String?,
    timestampMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    val safeName = senderOrChatName
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: "contacto"

    val safeIncoming = incomingMessage
      ?.trim()
      ?.take(200)
      .orEmpty()

    val zoned = Instant.ofEpochMilli(timestampMillis).atZone(zoneId)
    val dateStr = dateFormatter.format(zoned)
    val timeStr = timeFormatter.format(zoned)

    return template
      .replace("{nombre}", safeName, ignoreCase = true)
      .replace("{mensaje}", safeIncoming, ignoreCase = true)
      .replace("{fecha}", dateStr, ignoreCase = true)
      .replace("{hora}", timeStr, ignoreCase = true)
      .trim()
  }
}

