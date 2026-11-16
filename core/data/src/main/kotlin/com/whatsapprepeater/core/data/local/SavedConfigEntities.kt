package com.whatsapprepeater.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val jsonCodec = Json { ignoreUnknownKeys = true }

@Entity(tableName = "saved_message_lists")
data class SavedMessageListEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val isMultipleMode: Boolean,
  val messagesJson: String,
  val singleMessage: String,
  val repeatCount: Int,
  val updatedAtMillis: Long,
) {
  fun toDomain(): SavedMessageList = SavedMessageList(
    id = id,
    title = title,
    isMultipleMode = isMultipleMode,
    messages = runCatching { jsonCodec.decodeFromString<List<String>>(messagesJson) }
      .getOrDefault(emptyList()),
    singleMessage = singleMessage,
    repeatCount = repeatCount,
    updatedAtMillis = updatedAtMillis,
  )

  companion object {
    fun fromDomain(model: SavedMessageList): SavedMessageListEntity = SavedMessageListEntity(
      id = model.id,
      title = model.title,
      isMultipleMode = model.isMultipleMode,
      messagesJson = jsonCodec.encodeToString(model.messages),
      singleMessage = model.singleMessage,
      repeatCount = model.repeatCount,
      updatedAtMillis = model.updatedAtMillis,
    )
  }
}

@Entity(tableName = "automation_config")
data class AutomationConfigEntity(
  @PrimaryKey val id: Int = SINGLETON_ID,
  val contactId: String?,
  val contactName: String?,
  val contactPhone: String?,
  val isMultipleMode: Boolean,
  val messagesJson: String,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val updatedAtMillis: Long,
) {
  fun toDomain(): PersistedAutomationDraft = PersistedAutomationDraft(
    contactId = contactId,
    contactName = contactName,
    contactPhone = contactPhone,
    isMultipleMode = isMultipleMode,
    messages = runCatching { jsonCodec.decodeFromString<List<String>>(messagesJson) }
      .getOrDefault(emptyList()),
    singleMessage = singleMessage,
    repeatCount = repeatCount,
    minIntervalSeconds = minIntervalSeconds,
    maxIntervalSeconds = maxIntervalSeconds,
    preferWhatsAppBusiness = preferWhatsAppBusiness,
    updatedAtMillis = updatedAtMillis,
  )

  companion object {
    const val SINGLETON_ID = 1

    fun fromDomain(model: PersistedAutomationDraft): AutomationConfigEntity = AutomationConfigEntity(
      id = SINGLETON_ID,
      contactId = model.contactId,
      contactName = model.contactName,
      contactPhone = model.contactPhone,
      isMultipleMode = model.isMultipleMode,
      messagesJson = jsonCodec.encodeToString(model.messages),
      singleMessage = model.singleMessage,
      repeatCount = model.repeatCount,
      minIntervalSeconds = model.minIntervalSeconds,
      maxIntervalSeconds = model.maxIntervalSeconds,
      preferWhatsAppBusiness = model.preferWhatsAppBusiness,
      updatedAtMillis = model.updatedAtMillis,
    )
  }
}

