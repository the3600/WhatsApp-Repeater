package com.whatsapprepeater.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntelligenceEngineTest {

  private fun sampleTask(
    id: Long,
    title: String,
    phone: String = "+573001112222",
    message: String = "Hola",
    triggerAtMillis: Long = 1_800_000_000_000L,
  ): ScheduledTask = ScheduledTask(
    id = id,
    title = title,
    contactId = "c-$id",
    contactName = "Ana",
    contactPhone = phone,
    isMultipleMode = false,
    messages = emptyList(),
    singleMessage = message,
    repeatCount = 1,
    minIntervalSeconds = 15,
    maxIntervalSeconds = 30,
    preferWhatsAppBusiness = false,
    triggerAtMillis = triggerAtMillis,
    enabled = true,
    status = ScheduledTaskStatus.PENDING,
  )

  @Test
  fun analyze_returnsEmptyList_whenSystemHealthyAndNoRisks() {
    val recs = LocalIntelligenceEngine.analyze(
      IntelligenceSnapshot(
        isAccessibilityReady = true,
        isWhatsAppInstalled = true,
        isIgnoringBatteryOptimizations = true,
        canScheduleExactAlarms = true,
        postNotificationsGranted = true,
        vacationPauseEnabled = false,
        activeSchedules = listOf(sampleTask(1L, "Recordatorio")),
        recentExecutions = emptyList(),
        savedTemplates = listOf(
          SavedMessageList(
            id = 1L,
            title = "Saludo",
            isMultipleMode = false,
            messages = emptyList(),
            singleMessage = "Hola",
            repeatCount = 1,
            updatedAtMillis = 1L,
          ),
        ),
      ),
    )
    assertTrue(recs.isEmpty())
  }

  @Test
  fun analyze_prioritizesCriticalIssuesBeforeWarningsAndInfo() {
    val base = 1_800_000_000_000L
    val recs = LocalIntelligenceEngine.analyze(
      IntelligenceSnapshot(
        isAccessibilityReady = false,
        isWhatsAppInstalled = false,
        isIgnoringBatteryOptimizations = false,
        canScheduleExactAlarms = false,
        postNotificationsGranted = false,
        vacationPauseEnabled = true,
        activeSchedules = listOf(
          sampleTask(1L, "Envío 1", triggerAtMillis = base),
          sampleTask(2L, "Envío 2", triggerAtMillis = base + 60_000L),
        ),
        recentExecutions = listOf(
          ExecutionRecord(
            id = 1L,
            timestampMillis = base - 10_000L,
            contactName = "Ana",
            contactPhone = "+573001112222",
            scheduledCount = 3,
            confirmedCount = 0,
            errorCount = 1,
            finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK,
            statusDetail = "Cabecera no coincide",
          ),
        ),
        savedTemplates = emptyList(),
        draftMessages = listOf("Hola {nombre}", "2", "3", "4", "5"),
        draftContact = null,
        draftMinIntervalSeconds = 5,
        isHyperOsOrXiaomi = true,
      ),
    )

    assertEquals(RecommendationSeverity.CRITICAL, recs.first().severity)
    val ids = recs.map { it.id }
    assertTrue("whatsapp_missing" in ids)
    assertTrue("schedules_without_accessibility" in ids)
    assertTrue("draft_unresolved_variables" in ids)
    assertTrue("duplicate_schedules" in ids)
    assertTrue("recent_safety_failure" in ids)
    assertTrue("battery_optimization_risk" in ids)
    assertTrue("exact_alarms_missing" in ids)
    assertTrue("notifications_blocked" in ids)
    assertTrue("vacation_pause_active" in ids)
    assertTrue("aggressive_interval_tip" in ids)

    // Verify strict priority ordering (CRITICAL <= WARNING <= INFO)
    val orders = recs.map { it.severity.priorityOrder }
    assertEquals(orders.sorted(), orders)
  }

  @Test
  fun analyze_detectsOverlappingSchedulesForDifferentContacts() {
    val base = 1_800_000_000_000L
    val recs = LocalIntelligenceEngine.analyze(
      IntelligenceSnapshot(
        isAccessibilityReady = true,
        isWhatsAppInstalled = true,
        isIgnoringBatteryOptimizations = true,
        canScheduleExactAlarms = true,
        postNotificationsGranted = true,
        vacationPauseEnabled = false,
        activeSchedules = listOf(
          sampleTask(1L, "Cliente A", phone = "+573001111111", message = "A", triggerAtMillis = base),
          sampleTask(2L, "Cliente B", phone = "+573002222222", message = "B", triggerAtMillis = base + 90_000L),
        ),
        recentExecutions = emptyList(),
        savedTemplates = emptyList(),
      ),
    )
    assertTrue(recs.any { it.id == "overlapping_schedules" })
  }

  @Test
  fun groupFrequentErrors_aggregatesAndSortsTopReasons() {
    val motor1 = listOf(
      ExecutionRecord(
        id = 1L,
        timestampMillis = 1L,
        contactName = "A",
        contactPhone = "1",
        scheduledCount = 2,
        confirmedCount = 0,
        errorCount = 1,
        finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK,
        statusDetail = "Cabecera diferente",
      ),
      ExecutionRecord(
        id = 2L,
        timestampMillis = 2L,
        contactName = "B",
        contactPhone = "2",
        scheduledCount = 2,
        confirmedCount = 0,
        errorCount = 1,
        finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK,
        statusDetail = "Cabecera diferente",
      ),
      ExecutionRecord(
        id = 3L,
        timestampMillis = 3L,
        contactName = "C",
        contactPhone = "3",
        scheduledCount = 2,
        confirmedCount = 2,
        errorCount = 0,
        finalStatus = ExecutionFinalStatus.COMPLETED,
        statusDetail = "OK",
      ),
    )
    val motor2 = listOf(
      AutoReplyLogEntry(
        id = 1L,
        timestampMillis = 1L,
        packageName = "com.whatsapp",
        chatIdentifier = "Ana",
        matchedRuleName = "R1",
        responseSent = "",
        incomingPreview = "",
        status = AutoReplyLogStatus.FAILED_SEND,
        detail = "RemoteInput expirado",
      ),
    )
    val schedules = listOf(
      sampleTask(1L, "T1").copy(
        status = ScheduledTaskStatus.FAILED,
        lastFailureReason = "Cabecera diferente",
      ),
    )

    val top = LocalIntelligenceEngine.groupFrequentErrors(motor1, motor2, schedules)
    assertEquals(2, top.size)
    assertEquals("Cabecera diferente", top[0].reason)
    assertEquals(3, top[0].count)
    assertEquals("RemoteInput expirado", top[1].reason)
    assertEquals(1, top[1].count)
  }

  @Test
  fun analyticsSummary_formatsEstimatedTimeSavedHonestly() {
    val zero = LocalAnalyticsSummary()
    assertEquals("0 min", zero.estimatedTimeSavedFormatted)

    val small = LocalAnalyticsSummary(totalMessagesConfirmed = 3, totalMotor2Sent = 0)
    assertEquals(36L, small.estimatedSecondsSaved)
    assertEquals("36 s", small.estimatedTimeSavedFormatted)

    val medium = LocalAnalyticsSummary(totalMessagesConfirmed = 10, totalMotor2Sent = 2)
    assertEquals(150L, medium.estimatedSecondsSaved)
    assertEquals("2 min 30 s", medium.estimatedTimeSavedFormatted)

    val large = LocalAnalyticsSummary(totalMessagesConfirmed = 300, totalMotor2Sent = 40)
    assertEquals(4200L, large.estimatedSecondsSaved)
    assertEquals("1 h 10 min", large.estimatedTimeSavedFormatted)
  }
}
