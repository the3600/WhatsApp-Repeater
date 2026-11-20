package com.whatsapprepeater.core.data.backup

import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TemplateBundleDto(
  val format: String = FORMAT_ID,
  val schemaVersion: Int = 1,
  val exportedAtMillis: Long,
  val includesContactData: Boolean = false,
  val templates: List<TemplateItemDto>,
) {
  companion object {
    const val FORMAT_ID = "whatsapp-repeater-templates-v1"
  }
}

@Serializable
data class TemplateItemDto(
  val title: String,
  val isMultipleMode: Boolean,
  val messages: List<String> = emptyList(),
  val singleMessage: String = "",
  val repeatCount: Int = 1,
  val category: String = "General",
  val tags: List<String> = emptyList(),
  val isFavorite: Boolean = false,
  val isPinned: Boolean = false,
)

/**
 * Exporta e importa colecciones de plantillas en formato JSON validado y libre de datos
 * privados de contactos (sin nombres de destinatarios ni números telefónicos).
 */
object TemplateBundleCodec {
  private const val MAX_IMPORT_CHARS = 2_000_000
  private const val MAX_IMPORT_ITEMS = 500

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun exportJson(
    templates: List<SavedMessageList>,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val dto = TemplateBundleDto(
      exportedAtMillis = nowMillis,
      includesContactData = false,
      templates = templates.map { item ->
        TemplateItemDto(
          title = item.title.trim().take(60),
          isMultipleMode = item.isMultipleMode,
          messages = if (item.isMultipleMode) item.messages.map { it.take(SafetyLimits.MAX_MESSAGE_LENGTH) } else emptyList(),
          singleMessage = if (item.isMultipleMode) "" else item.singleMessage.take(SafetyLimits.MAX_MESSAGE_LENGTH),
          repeatCount = item.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT),
          category = item.category.trim().ifBlank { "General" }.take(32),
          tags = item.tags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinct(),
          isFavorite = item.isFavorite,
          isPinned = item.isPinned,
        )
      },
    )
    return json.encodeToString(dto)
  }

  fun importJson(
    rawJson: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<SavedMessageList> {
    require(rawJson.isNotBlank()) { "El archivo JSON está vacío." }
    require(rawJson.length <= MAX_IMPORT_CHARS) { "El archivo supera el límite seguro de 2 MB." }

    val bundle = try {
      json.decodeFromString<TemplateBundleDto>(rawJson)
    } catch (_: Exception) {
      throw IllegalArgumentException("El archivo no tiene una estructura JSON válida de plantillas.")
    }

    require(bundle.format == TemplateBundleDto.FORMAT_ID) {
      "El archivo no corresponde a una exportación de plantillas de WhatsApp Repeater."
    }
    require(bundle.templates.size <= MAX_IMPORT_ITEMS) {
      "El archivo excede el máximo permitido de $MAX_IMPORT_ITEMS plantillas."
    }

    val validTemplates = bundle.templates.mapNotNull { item ->
      val cleanTitle = item.title.trim().take(60)
      if (cleanTitle.isEmpty()) return@mapNotNull null
      val cleanMessages = item.messages
        .map { it.trim().take(SafetyLimits.MAX_MESSAGE_LENGTH) }
        .filter { it.isNotEmpty() }
        .take(SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT)
      val cleanSingle = item.singleMessage.trim().take(SafetyLimits.MAX_MESSAGE_LENGTH)
      if (item.isMultipleMode && cleanMessages.isEmpty()) return@mapNotNull null
      if (!item.isMultipleMode && cleanSingle.isEmpty()) return@mapNotNull null

      SavedMessageList(
        id = 0L,
        title = cleanTitle,
        isMultipleMode = item.isMultipleMode,
        messages = if (item.isMultipleMode) cleanMessages else emptyList(),
        singleMessage = if (item.isMultipleMode) "" else cleanSingle,
        repeatCount = item.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT),
        updatedAtMillis = nowMillis,
        category = item.category.trim().ifBlank { "General" }.take(32),
        tags = item.tags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinct(),
        isFavorite = item.isFavorite,
        isPinned = item.isPinned,
      )
    }

    require(validTemplates.isNotEmpty()) {
      "El archivo no contiene plantillas válidas para importar."
    }
    return validTemplates
  }
}
