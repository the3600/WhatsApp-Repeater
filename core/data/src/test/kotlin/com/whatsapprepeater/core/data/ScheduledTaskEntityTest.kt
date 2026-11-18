package com.whatsapprepeater.core.data

import com.whatsapprepeater.core.data.local.ScheduledTaskEntity
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerLogic
import com.whatsapprepeater.core.model.SchedulerRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledTaskEntityTest {

  @Test
  fun `persists and restores scheduled task including custom weekdays and failure reason`() {
    val trigger = ZonedDateTime.of(2026, 11, 20, 9, 15, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val domain = ScheduledTask(
      id = 42L,
      title = "Seguimiento Viernes",
      contactId = "c42",
      contactName = "Mi Princesa hermosa",
      contactPhone = "+525576294320",
      isMultipleMode = true,
      messages = listOf("Buenos días", "¿Cómo vas?"),
      singleMessage = "",
      repeatCount = 1,
      minIntervalSeconds = 12,
      maxIntervalSeconds = 25,
      preferWhatsAppBusiness = true,
      triggerAtMillis = trigger,
      recurrence = SchedulerRecurrence.CUSTOM_DAYS,
      customWeekDays = listOf(1, 5),
      expirationMillis = trigger + 14L * 86_400_000L,
      enabled = true,
      notifyBeforeMinutes = 15,
      autoExecuteWhenReady = true,
      status = ScheduledTaskStatus.FAILED,
      lastExecutedAtMillis = trigger - 86_400_000L,
      lastPunctualityDeltaSeconds = 4L,
      lastFailureReason = "El teléfono estaba bloqueado.",
      createdAtMillis = trigger - 100_000L,
      updatedAtMillis = trigger,
    )

    val entity = ScheduledTaskEntity.fromDomain(domain)
    val restored = entity.toDomain()

    assertEquals(domain.id, restored.id)
    assertEquals(domain.title, restored.title)
    assertEquals(domain.messages, restored.messages)
    assertEquals(SchedulerRecurrence.CUSTOM_DAYS, restored.recurrence)
    assertEquals(listOf(1, 5), restored.customWeekDays)
    assertEquals(true, restored.autoExecuteWhenReady)
    assertEquals(ScheduledTaskStatus.FAILED, restored.status)
    assertEquals("El teléfono estaba bloqueado.", restored.lastFailureReason)
    assertEquals(domain.expirationMillis, restored.expirationMillis)
  }

  @Test
  fun `simulates device reboot evaluation separating overdue one-shot vs upcoming recurring schedule`() {
    val zone = ZoneId.of("UTC")
    val pastDue = ZonedDateTime.of(2026, 11, 16, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
    val afterRebootNow = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    val oneShotPast = ScheduledTask(
      id = 1L,
      title = "Aviso único",
      contactId = "1",
      contactName = "Ana",
      contactPhone = "+573001112222",
      isMultipleMode = false,
      messages = emptyList(),
      singleMessage = "Hola",
      repeatCount = 1,
      minIntervalSeconds = 15,
      maxIntervalSeconds = 30,
      preferWhatsAppBusiness = false,
      triggerAtMillis = pastDue,
      recurrence = SchedulerRecurrence.NONE,
      enabled = true,
    )

    val recurringPast = oneShotPast.copy(id = 2L, recurrence = SchedulerRecurrence.DAILY)

    val nextOneShot = SchedulerLogic.computeNextTriggerMillis(oneShotPast, afterRebootNow, zone)
    val nextRecurring = SchedulerLogic.computeNextTriggerMillis(recurringPast, afterRebootNow, zone)

    assertEquals(null, nextOneShot)
    assertFalse(oneShotPast.triggerAtMillis > afterRebootNow)
    assertTrue(nextRecurring != null && nextRecurring > afterRebootNow)
  }
}
