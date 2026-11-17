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
}
