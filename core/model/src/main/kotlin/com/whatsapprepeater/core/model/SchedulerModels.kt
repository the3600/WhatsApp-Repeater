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
  CUSTOM_DAYS("Días específicos"),
}

enum class ScheduledTaskStatus(val label: String) {
  PENDING("Pendiente"),
  ACTIVE("Activa"),
  PAUSED("Pausada"),
  NOTIFIED("Lista para ejecutar"),
  COMPLETED("Completada"),
  FAILED("Fallida"),
  SKIPPED_VACATION("Pausada por vacaciones"),
  EXPIRED("Expirada"),
}

/**
 * Programación local de envío con recurrencia, días específicos, fecha de finalización
 * y ejecución real sujeta a las condiciones de Android e HyperOS.
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
  val customWeekDays: List<Int> = emptyList(),
  val expirationMillis: Long? = null,
  val enabled: Boolean = true,
  val notifyBeforeMinutes: Int = 5,
  val autoExecuteWhenReady: Boolean = true,
  val status: ScheduledTaskStatus = ScheduledTaskStatus.PENDING,
  val lastExecutedAtMillis: Long = 0L,
  val lastPunctualityDeltaSeconds: Long = 0L,
  val lastFailureReason: String = "",
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
      if (item.status == ScheduledTaskStatus.EXPIRED ||
        item.status == ScheduledTaskStatus.COMPLETED ||
        item.status == ScheduledTaskStatus.PAUSED
      ) {
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
    val allowedDays = task.customWeekDays.filter { it in 1..7 }.toSet()
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
        SchedulerRecurrence.CUSTOM_DAYS -> {
          if (allowedDays.isEmpty()) {
            candidateZdt.plusDays(1)
          } else {
            var next = candidateZdt.plusDays(1)
            var hops = 0
            while (next.dayOfWeek.value !in allowedDays && hops < 8) {
              next = next.plusDays(1)
              hops++
            }
            next
          }
        }
      }
    }
    val nextMillis = candidateZdt.toInstant().toEpochMilli()
    return if (task.expirationMillis != null && nextMillis > task.expirationMillis) null else nextMillis
  }

  fun normalizeWeekDaysForFirstTrigger(
    startMillis: Long,
    recurrence: SchedulerRecurrence,
    customWeekDays: List<Int>,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Long {
    if (recurrence != SchedulerRecurrence.CUSTOM_DAYS) return startMillis
    val allowed = customWeekDays.filter { it in 1..7 }.toSet()
    if (allowed.isEmpty()) return startMillis
    var zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zoneId)
    var hops = 0
    while (zdt.dayOfWeek.value !in allowed && hops < 8) {
      zdt = zdt.plusDays(1)
      hops++
    }
    return zdt.toInstant().toEpochMilli()
  }

  private fun normalizePayload(task: ScheduledTask): String {
    return if (task.isMultipleMode) {
      "M:" + task.messages.joinToString("\n") { it.trim() }
    } else {
      "S:${task.repeatCount}:" + task.singleMessage.trim()
    }
  }
}

enum class ScheduleNotificationMode {
  NONE,
  ACTIVE,
  PAUSED,
  VACATION_PAUSED,
  FAILED,
}

data class ActiveScheduleNotificationState(
  val mode: ScheduleNotificationMode,
  val primaryTask: ScheduledTask? = null,
  val activeCount: Int = 0,
  val pausedCount: Int = 0,
  val failedCount: Int = 0,
  val remainingMillis: Long = 0L,
)

object ScheduleCountdownFormatter {

  fun formatRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (days > 0L) {
      String.format(java.util.Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
      String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
  }

  /**
   * Deriva un único estado consolidado para la notificación persistente del Programador,
   * garantizando que nunca se dupliquen notificaciones y priorizando la tarea más próxima.
   */
  fun deriveNotificationState(
    tasks: List<ScheduledTask>,
    vacationPauseEnabled: Boolean,
    nowMillis: Long,
  ): ActiveScheduleNotificationState {
    val activeTasks = tasks.filter {
      it.enabled &&
        it.status != ScheduledTaskStatus.PAUSED &&
        it.status != ScheduledTaskStatus.COMPLETED &&
        it.status != ScheduledTaskStatus.EXPIRED &&
        it.status != ScheduledTaskStatus.FAILED
    }.sortedBy { it.triggerAtMillis }

    val pausedTasks = tasks.filter {
      it.status == ScheduledTaskStatus.PAUSED ||
        (!it.enabled &&
          it.status != ScheduledTaskStatus.COMPLETED &&
          it.status != ScheduledTaskStatus.FAILED &&
          it.status != ScheduledTaskStatus.EXPIRED)
    }.sortedBy { it.triggerAtMillis }

    val failedTasks = tasks.filter {
      it.status == ScheduledTaskStatus.FAILED
    }.sortedByDescending { it.updatedAtMillis }

    if (vacationPauseEnabled && activeTasks.isNotEmpty()) {
      val next = activeTasks.first()
      return ActiveScheduleNotificationState(
        mode = ScheduleNotificationMode.VACATION_PAUSED,
        primaryTask = next,
        activeCount = activeTasks.size,
        pausedCount = pausedTasks.size,
        failedCount = failedTasks.size,
        remainingMillis = (next.triggerAtMillis - nowMillis).coerceAtLeast(0L),
      )
    }

    if (activeTasks.isNotEmpty()) {
      val next = activeTasks.first()
      return ActiveScheduleNotificationState(
        mode = ScheduleNotificationMode.ACTIVE,
        primaryTask = next,
        activeCount = activeTasks.size,
        pausedCount = pausedTasks.size,
        failedCount = failedTasks.size,
        remainingMillis = (next.triggerAtMillis - nowMillis).coerceAtLeast(0L),
      )
    }

    if (pausedTasks.isNotEmpty()) {
      val next = pausedTasks.first()
      return ActiveScheduleNotificationState(
        mode = ScheduleNotificationMode.PAUSED,
        primaryTask = next,
        activeCount = 0,
        pausedCount = pausedTasks.size,
        failedCount = failedTasks.size,
        remainingMillis = (next.triggerAtMillis - nowMillis).coerceAtLeast(0L),
      )
    }

    if (failedTasks.isNotEmpty()) {
      val latestFailed = failedTasks.first()
      return ActiveScheduleNotificationState(
        mode = ScheduleNotificationMode.FAILED,
        primaryTask = latestFailed,
        activeCount = 0,
        pausedCount = 0,
        failedCount = failedTasks.size,
        remainingMillis = 0L,
      )
    }

    return ActiveScheduleNotificationState(mode = ScheduleNotificationMode.NONE)
  }
}
