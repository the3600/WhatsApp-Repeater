package com.whatsapprepeater.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SchedulerRecurrence(val label: String) {
  NONE("Una sola vez"),
  DAILY("Diaria"),
  WORKDAYS("Lunes a viernes"),
  WEEKLY("Semanal"),
}

enum class ScheduledTaskStatus(val label: String) {
  PENDING("Pendiente"),
  NOTIFIED("Recordatorio emitido"),
  COMPLETED("Ejecutada"),
  FAILED("Fallida / omitida"),
  SKIPPED_VACATION("Pausada por vacaciones"),
  EXPIRED("Expirada"),
}

/**
 * Programación local de envío con recurrencia, fecha de expiración y control de puntualidad.
 */
data class ScheduledTask(
  val id: Long = 0L,
  val title: String,
  val contactId: String,
  val contactName: String,
  val contactPhone: String,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val triggerAtMillis: Long,
  val recurrence: SchedulerRecurrence = SchedulerRecurrence.NONE,
  val expirationMillis: Long? = null,
  val enabled: Boolean = true,
  val notifyBeforeMinutes: Int = 5,
  val status: ScheduledTaskStatus = ScheduledTaskStatus.PENDING,
  val lastExecutedAtMillis: Long = 0L,
  val lastPunctualityDeltaSeconds: Long = 0L,
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
) {
  val totalMessagesCount: Int
    get() = if (isMultipleMode) messages.count { it.isNotBlank() } else repeatCount

  val primaryPreview: String
    get() = if (isMultipleMode) messages.firstOrNull().orEmpty() else singleMessage

  fun isExpiredAt(nowMillis: Long): Boolean =
    expirationMillis != null && nowMillis > expirationMillis
}

object SchedulerLogic {

  private const val DUPLICATE_WINDOW_MILLIS = 5 * 60_000L

  fun findDuplicateConflict(
    candidate: ScheduledTask,
    existing: List<ScheduledTask>,
  ): ScheduledTask? {
    val candidateDigits = candidate.contactPhone.filter { it.isDigit() }
    val candidatePayload = normalizePayload(candidate)
    return existing.firstOrNull { item ->
      if (item.id == candidate.id || !item.enabled) return@firstOrNull false
      if (item.status == ScheduledTaskStatus.EXPIRED || item.status == ScheduledTaskStatus.COMPLETED) {
        return@firstOrNull false
      }
      val sameContact = item.contactPhone.filter { it.isDigit() } == candidateDigits &&
        candidateDigits.isNotEmpty()
      val timeClose = kotlin.math.abs(item.triggerAtMillis - candidate.triggerAtMillis) <= DUPLICATE_WINDOW_MILLIS
      sameContact && timeClose && normalizePayload(item) == candidatePayload
    }
  }

  fun computeNextTriggerMillis(
    task: ScheduledTask,
    fromMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Long? {
    if (task.recurrence == SchedulerRecurrence.NONE) return null
    var candidateZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(task.triggerAtMillis), zoneId)
    val floorZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zoneId)
    var safety = 0
    while (!candidateZdt.isAfter(floorZdt) && safety < 400) {
      safety++
      candidateZdt = when (task.recurrence) {
        SchedulerRecurrence.NONE -> return null
        SchedulerRecurrence.DAILY -> candidateZdt.plusDays(1)
        SchedulerRecurrence.WEEKLY -> candidateZdt.plusWeeks(1)
        SchedulerRecurrence.WORKDAYS -> {
          var next = candidateZdt.plusDays(1)
          while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
          }
          next
        }
      }
    }
    val nextMillis = candidateZdt.toInstant().toEpochMilli()
    return if (task.expirationMillis != null && nextMillis > task.expirationMillis) null else nextMillis
  }

  private fun normalizePayload(task: ScheduledTask): String {
    return if (task.isMultipleMode) {
      "M:" + task.messages.joinToString("\n") { it.trim() }
    } else {
      "S:${task.repeatCount}:" + task.singleMessage.trim()
    }
  }
}
