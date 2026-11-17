package com.whatsapprepeater.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerRecurrence
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val schedulerJsonCodec = Json { ignoreUnknownKeys = true }

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val contactId: String,
  val contactName: String,
  val contactPhone: String,
  val isMultipleMode: Boolean,
  val messagesJson: String,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val triggerAtMillis: Long,
  val recurrence: String,
  val expirationMillis: Long?,
  val enabled: Boolean,
  val notifyBeforeMinutes: Int,
  val status: String,
  val lastExecutedAtMillis: Long,
  val lastPunctualityDeltaSeconds: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
) {
  fun toDomain(): ScheduledTask = ScheduledTask(
    id = id,
    title = title,
    contactId = contactId,
    contactName = contactName,
    contactPhone = contactPhone,
    isMultipleMode = isMultipleMode,
    messages = runCatching { schedulerJsonCodec.decodeFromString<List<String>>(messagesJson) }
      .getOrDefault(emptyList()),
    singleMessage = singleMessage,
    repeatCount = repeatCount,
    minIntervalSeconds = minIntervalSeconds,
    maxIntervalSeconds = maxIntervalSeconds,
    preferWhatsAppBusiness = preferWhatsAppBusiness,
    triggerAtMillis = triggerAtMillis,
    recurrence = runCatching { SchedulerRecurrence.valueOf(recurrence) }
      .getOrDefault(SchedulerRecurrence.NONE),
    expirationMillis = expirationMillis,
    enabled = enabled,
    notifyBeforeMinutes = notifyBeforeMinutes,
    status = runCatching { ScheduledTaskStatus.valueOf(status) }
      .getOrDefault(ScheduledTaskStatus.PENDING),
    lastExecutedAtMillis = lastExecutedAtMillis,
    lastPunctualityDeltaSeconds = lastPunctualityDeltaSeconds,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
  )

  companion object {
    fun fromDomain(model: ScheduledTask): ScheduledTaskEntity = ScheduledTaskEntity(
      id = model.id,
      title = model.title,
      contactId = model.contactId,
      contactName = model.contactName,
      contactPhone = model.contactPhone,
      isMultipleMode = model.isMultipleMode,
      messagesJson = schedulerJsonCodec.encodeToString(model.messages),
      singleMessage = model.singleMessage,
      repeatCount = model.repeatCount,
      minIntervalSeconds = model.minIntervalSeconds,
      maxIntervalSeconds = model.maxIntervalSeconds,
      preferWhatsAppBusiness = model.preferWhatsAppBusiness,
      triggerAtMillis = model.triggerAtMillis,
      recurrence = model.recurrence.name,
      expirationMillis = model.expirationMillis,
      enabled = model.enabled,
      notifyBeforeMinutes = model.notifyBeforeMinutes,
      status = model.status.name,
      lastExecutedAtMillis = model.lastExecutedAtMillis,
      lastPunctualityDeltaSeconds = model.lastPunctualityDeltaSeconds,
      createdAtMillis = model.createdAtMillis,
      updatedAtMillis = model.updatedAtMillis,
    )
  }
}
