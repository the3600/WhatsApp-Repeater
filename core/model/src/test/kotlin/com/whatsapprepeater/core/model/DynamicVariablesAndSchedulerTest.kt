package com.whatsapprepeater.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DynamicVariablesAndSchedulerTest {

  private val zone = ZoneId.of("UTC")
  private val mondayTenAm = ZonedDateTime.of(2026, 11, 16, 10, 30, 0, 0, zone).toInstant().toEpochMilli()

  @Test
  fun `resolves valid dynamic variables accurately without inventing data`() {
    val contact = ContactInfo(id = "1", name = "Laura Gómez", phoneNumber = "+57 300 111 2222")
    val result = DynamicVariablesEngine.resolveOrValidate(
      template = "Hola {nombre} ({numero}), hoy {dia} {fecha} a las {hora}.",
      contact = contact,
      nowMillis = mondayTenAm,
      zoneId = zone,
    )
    assertTrue(result is VariableResolutionResult.Resolved)
    val text = (result as VariableResolutionResult.Resolved).text
    assertEquals("Hola Laura Gómez (+57 300 111 2222), hoy Lunes 16/11/2026 a las 10:30.", text)
  }

  @Test
  fun `blocks unresolved or unknown variables and missing contact name instead of fabricating`() {
    val phoneOnlyContact = ContactInfo(id = "m1", name = "+573001112222", phoneNumber = "+573001112222")
    val missingName = DynamicVariablesEngine.resolveOrValidate(
      template = "Buenos días {nombre}",
      contact = phoneOnlyContact,
      nowMillis = mondayTenAm,
      zoneId = zone,
    )
    assertTrue(missingName is VariableResolutionResult.Unresolved)
    assertEquals(listOf("{nombre}"), (missingName as VariableResolutionResult.Unresolved).missingOrInvalidTokens)

    val unknownVar = DynamicVariablesEngine.resolveOrValidate(
      template = "Tu código es {desconocido}",
      contact = ContactInfo("1", "Ana", "+573001112222"),
      nowMillis = mondayTenAm,
      zoneId = zone,
    )
    assertTrue(unknownVar is VariableResolutionResult.Unresolved)
    assertEquals(listOf("{desconocido}"), (unknownVar as VariableResolutionResult.Unresolved).missingOrInvalidTokens)
  }

  @Test
  fun `detects duplicate schedule conflicts and skips weekend for workdays recurrence`() {
    val fridayFourPm = ZonedDateTime.of(2026, 11, 20, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
    val task = ScheduledTask(
      id = 1L,
      title = "Cobro",
      contactId = "1",
      contactName = "Carlos",
      contactPhone = "+57 300 222 3333",
      isMultipleMode = false,
      messages = emptyList(),
      singleMessage = "Recordatorio mensual",
      repeatCount = 1,
      minIntervalSeconds = 15,
      maxIntervalSeconds = 30,
      preferWhatsAppBusiness = false,
      triggerAtMillis = fridayFourPm,
      recurrence = SchedulerRecurrence.WORKDAYS,
    )

    val duplicate = task.copy(id = 0L, triggerAtMillis = fridayFourPm + 120_000L)
    assertNotNull(SchedulerLogic.findDuplicateConflict(duplicate, listOf(task)))

    val differentContact = duplicate.copy(contactPhone = "+57 311 999 8888")
    assertNull(SchedulerLogic.findDuplicateConflict(differentContact, listOf(task)))

    val nextMillis = SchedulerLogic.computeNextTriggerMillis(task, fridayFourPm, zone)!!
    val nextZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextMillis), zone)
    assertEquals(java.time.DayOfWeek.MONDAY, nextZdt.dayOfWeek)
    assertEquals(16, nextZdt.hour)

    val expiringTask = task.copy(expirationMillis = fridayFourPm + 3_600_000L)
    assertTrue(expiringTask.isExpiredAt(fridayFourPm + 7_200_000L))
    assertNull(SchedulerLogic.computeNextTriggerMillis(expiringTask, fridayFourPm, zone))
  }

  @Test
  fun `aligns and advances custom weekday recurrence and ignores paused tasks in duplicate check`() {
    // Lunes 16 nov 2026 -> custom days Miércoles(3) y Viernes(5)
    val customTask = ScheduledTask(
      id = 10L,
      title = "Rutina específica",
      contactId = "c1",
      contactName = "Ana",
      contactPhone = "+525576294320",
      isMultipleMode = true,
      messages = listOf("Paso 1", "Paso 2"),
      singleMessage = "",
      repeatCount = 1,
      minIntervalSeconds = 10,
      maxIntervalSeconds = 20,
      preferWhatsAppBusiness = false,
      triggerAtMillis = mondayTenAm,
      recurrence = SchedulerRecurrence.CUSTOM_DAYS,
      customWeekDays = listOf(3, 5),
    )

    val aligned = SchedulerLogic.normalizeWeekDaysForFirstTrigger(
      startMillis = mondayTenAm,
      recurrence = SchedulerRecurrence.CUSTOM_DAYS,
      customWeekDays = listOf(3, 5),
      zoneId = zone,
    )
    val alignedZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(aligned), zone)
    assertEquals(java.time.DayOfWeek.WEDNESDAY, alignedZdt.dayOfWeek)

    val nextAfterWed = SchedulerLogic.computeNextTriggerMillis(
      task = customTask.copy(triggerAtMillis = aligned),
      fromMillis = aligned,
      zoneId = zone,
    )!!
    val nextZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextAfterWed), zone)
    assertEquals(java.time.DayOfWeek.FRIDAY, nextZdt.dayOfWeek)

    val pausedExisting = customTask.copy(id = 11L, status = ScheduledTaskStatus.PAUSED, enabled = false)
    assertNull(SchedulerLogic.findDuplicateConflict(customTask.copy(id = 0L), listOf(pausedExisting)))
  }

  @Test
  fun `formats schedule countdown accurately and derives consolidated notification state without duplicates`() {
    assertEquals("00:24:35", ScheduleCountdownFormatter.formatRemaining((24 * 60 + 35) * 1000L))
    assertEquals("1d 02:05:09", ScheduleCountdownFormatter.formatRemaining((86_400L + 2 * 3_600L + 5 * 60L + 9L) * 1000L))
    assertEquals("00:00:00", ScheduleCountdownFormatter.formatRemaining(-5_000L))

    val now = mondayTenAm
    val laterTask = ScheduledTask(
      id = 1L,
      title = "Tarde",
      contactId = "1",
      contactName = "Ana",
      contactPhone = "+525576294320",
      isMultipleMode = false,
      messages = emptyList(),
      singleMessage = "Hola",
      repeatCount = 2,
      minIntervalSeconds = 10,
      maxIntervalSeconds = 20,
      preferWhatsAppBusiness = false,
      triggerAtMillis = now + 7_200_000L,
      enabled = true,
      status = ScheduledTaskStatus.PENDING,
    )
    val soonerTask = laterTask.copy(id = 2L, title = "Pronto", triggerAtMillis = now + 1_475_000L)
    val pausedTask = laterTask.copy(id = 3L, title = "Pausada", enabled = false, status = ScheduledTaskStatus.PAUSED)

    // Prioritizes nearest active schedule and consolidates counts in one state
    val activeState = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = listOf(laterTask, soonerTask, pausedTask),
      vacationPauseEnabled = false,
      nowMillis = now,
    )
    assertEquals(ScheduleNotificationMode.ACTIVE, activeState.mode)
    assertEquals(2L, activeState.primaryTask?.id)
    assertEquals(2, activeState.activeCount)
    assertEquals(1, activeState.pausedCount)
    assertEquals("00:24:35", ScheduleCountdownFormatter.formatRemaining(activeState.remainingMillis))

    // Vacation mode pauses active notifications clearly
    val vacationState = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = listOf(soonerTask),
      vacationPauseEnabled = true,
      nowMillis = now,
    )
    assertEquals(ScheduleNotificationMode.VACATION_PAUSED, vacationState.mode)

    // Paused only
    val pausedOnly = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = listOf(pausedTask),
      vacationPauseEnabled = false,
      nowMillis = now,
    )
    assertEquals(ScheduleNotificationMode.PAUSED, pausedOnly.mode)

    // Failed only
    val failedTask = laterTask.copy(id = 4L, enabled = false, status = ScheduledTaskStatus.FAILED, lastFailureReason = "Pantalla bloqueada")
    val failedOnly = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = listOf(failedTask),
      vacationPauseEnabled = false,
      nowMillis = now,
    )
    assertEquals(ScheduleNotificationMode.FAILED, failedOnly.mode)
    assertEquals(4L, failedOnly.primaryTask?.id)

    // Empty / completed only -> NONE
    val completedOnly = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = listOf(laterTask.copy(enabled = false, status = ScheduledTaskStatus.COMPLETED)),
      vacationPauseEnabled = false,
      nowMillis = now,
    )
    assertEquals(ScheduleNotificationMode.NONE, completedOnly.mode)
  }
}
