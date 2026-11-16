package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyDecision
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.IncomingNotificationEvent
import com.whatsapprepeater.core.model.RuleMatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoReplyEngineTest {

  private val zone = ZoneId.of("UTC")
  private var nowMillis = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, zone).toInstant().toEpochMilli() // Lunes 10:00

  private fun event(
    key: String = "k1",
    pkg: String = AutomationConfig.WHATSAPP_PACKAGE,
    title: String = "Ana Gómez",
    sender: String? = "Ana Gómez",
    text: String = "Hola, precio por favor",
    isGroup: Boolean = false,
    isSummary: Boolean = false,
    isSelf: Boolean = false,
    hasRemoteInput: Boolean = true,
  ) = IncomingNotificationEvent(
    notificationKey = key,
    packageName = pkg,
    conversationTitle = title,
    senderName = sender,
    messageText = text,
    isGroupConversation = isGroup,
    isGroupSummary = isSummary,
    isSelfMessage = isSelf,
    hasReplyAction = hasRemoteInput,
    timestampMillis = nowMillis,
  )

  @Test
  fun `ignores external packages, empty texts, summaries, self messages and missing remote input`() {
    val engine = AutoReplyEngine({ nowMillis }, zone)
    val settings = AutoReplySettings(masterEnabled = true, welcomeEnabled = true)

    assertNull(engine.evaluate(event(pkg = "com.instagram.android"), settings, emptyList()))
    assertNull(engine.evaluate(event(text = "   "), settings, emptyList()))
    assertNull(engine.evaluate(event(isSummary = true), settings, emptyList()))
    assertNull(engine.evaluate(event(isSelf = true), settings, emptyList()))

    val noInput = engine.evaluate(event(hasRemoteInput = false), settings, emptyList())
    assertTrue(noInput is AutoReplyDecision.Skip)
    assertEquals(AutoReplyLogStatus.SKIPPED_NO_REMOTE_INPUT, (noInput as AutoReplyDecision.Skip).status)
  }

  @Test
  fun `selects highest priority matching rule across WhatsApp and WhatsApp Business`() {
    val engine = AutoReplyEngine({ nowMillis }, zone)
    val settings = AutoReplySettings(masterEnabled = true)
    val lowRule = AutoReplyRule(
      id = 1L,
      name = "General",
      pattern = "precio",
      matchType = RuleMatchType.CONTAINS_WORD,
      responseTemplate = "Respuesta general",
      priority = 5,
      targetApp = AutoReplyTargetApp.BOTH,
    )
    val highRule = AutoReplyRule(
      id = 2L,
      name = "VIP Business",
      pattern = "precio",
      matchType = RuleMatchType.CONTAINS_WORD,
      responseTemplate = "Respuesta prioritaria para {nombre}",
      priority = 20,
      targetApp = AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY,
    )

    val waBiz = engine.evaluate(
      event(pkg = AutomationConfig.WHATSAPP_BUSINESS_PACKAGE),
      settings,
      listOf(lowRule, highRule),
    )
    assertTrue(waBiz is AutoReplyDecision.Reply)
    assertEquals("VIP Business", (waBiz as AutoReplyDecision.Reply).ruleName)
    assertEquals("Respuesta prioritaria para Ana Gómez", waBiz.responseText)

    val waStd = engine.evaluate(
      event(key = "k2", pkg = AutomationConfig.WHATSAPP_PACKAGE),
      settings,
      listOf(lowRule, highRule),
    )
    assertTrue(waStd is AutoReplyDecision.Reply)
    assertEquals("General", (waStd as AutoReplyDecision.Reply).ruleName)
  }

  @Test
  fun `prevents duplicate processing and autoresponder ping pong loops`() {
    val engine = AutoReplyEngine({ nowMillis }, zone)
    val settings = AutoReplySettings(masterEnabled = true, preventLoopsEnabled = true)
    val rule = AutoReplyRule(
      id = 1L,
      name = "Soporte",
      pattern = "soporte",
      responseTemplate = "Gracias por escribir a soporte",
      minIntervalSeconds = 0,
      maxRepliesPerConversation = 10,
    )

    val first = engine.evaluate(event(key = "same", text = "Necesito soporte"), settings, listOf(rule))
    assertTrue(first is AutoReplyDecision.Reply)
    engine.recordReplyDispatched(first as AutoReplyDecision.Reply)

    // Misma notificación repetida por Android -> ignorada (null)
    assertNull(engine.evaluate(event(key = "same", text = "Necesito soporte"), settings, listOf(rule)))

    // Eco de nuestra propia respuesta -> detenido por antibucle
    nowMillis += 2_000L
    val echo = engine.evaluate(
      event(key = "k2", text = "Gracias por escribir a soporte"),
      settings,
      listOf(rule),
    )
    assertTrue(echo is AutoReplyDecision.Skip)
    assertEquals(AutoReplyLogStatus.SKIPPED_LOOP_GUARD, (echo as AutoReplyDecision.Skip).status)
  }

  @Test
  fun `enforces cooldown and max replies per conversation`() {
    val engine = AutoReplyEngine({ nowMillis }, zone)
    val settings = AutoReplySettings(masterEnabled = true, preventLoopsEnabled = false)
    val rule = AutoReplyRule(
      id = 10L,
      name = "Info",
      pattern = "info",
      responseTemplate = "Info automática",
      minIntervalSeconds = 60,
      maxRepliesPerConversation = 2,
    )

    val r1 = engine.evaluate(event(key = "1", text = "info 1"), settings, listOf(rule)) as AutoReplyDecision.Reply
    engine.recordReplyDispatched(r1)

    nowMillis += 15_000L // Dentro de cooldown 60s
    val skipCooldown = engine.evaluate(event(key = "2", text = "info 2"), settings, listOf(rule))
    assertEquals(AutoReplyLogStatus.SKIPPED_COOLDOWN, (skipCooldown as AutoReplyDecision.Skip).status)

    nowMillis += 50_000L // Pasa cooldown 60s
    val r2 = engine.evaluate(event(key = "3", text = "info 3"), settings, listOf(rule)) as AutoReplyDecision.Reply
    engine.recordReplyDispatched(r2)

    nowMillis += 70_000L // Alcanza límite de 2
    val skipLimit = engine.evaluate(event(key = "4", text = "info 4"), settings, listOf(rule))
    assertEquals(AutoReplyLogStatus.SKIPPED_LIMIT, (skipLimit as AutoReplyDecision.Skip).status)
  }

  @Test
  fun `evaluates saved contacts, unknown numbers, exclusions, groups and overnight schedules`() {
    val engine = AutoReplyEngine({ nowMillis }, zone)
    val saved = SavedContactsLookup.fromContacts(
      permissionGranted = true,
      contacts = listOf(ContactInfo("1", "Ana Gómez", "+573001112222")),
    )
    val savedOnlyRule = AutoReplyRule(
      id = 1L,
      name = "Solo guardados",
      pattern = "hola",
      responseTemplate = "Hola guardado",
      contactFilterMode = AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY,
      excludedIdentifiers = listOf("Bloqueado"),
    )

    val allowed = engine.evaluate(event(title = "Ana Gómez", text = "hola"), AutoReplySettings(masterEnabled = true), listOf(savedOnlyRule), saved)
    assertTrue(allowed is AutoReplyDecision.Reply)

    val unknownBlocked = engine.evaluate(
      event(key = "u1", title = "+57 311 999 0000", sender = "+57 311 999 0000", text = "hola"),
      AutoReplySettings(masterEnabled = true),
      listOf(savedOnlyRule),
      saved,
    )
    assertEquals(AutoReplyLogStatus.SKIPPED_FILTER, (unknownBlocked as AutoReplyDecision.Skip).status)

    // Horario nocturno 20:00 -> 08:00 con ausencias (10:00 UTC debe omitir ausencia, 22:30 UTC debe activarla)
    val awaySettings = AutoReplySettings(
      masterEnabled = true,
      awayEnabled = true,
      awaySchedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 20 * 60, endMinutesOfDay = 8 * 60),
    )
    val daytime = engine.evaluate(event(key = "d1", text = "algo"), awaySettings, emptyList(), saved)
    assertEquals(AutoReplyLogStatus.SKIPPED_NO_MATCH, (daytime as AutoReplyDecision.Skip).status)

    nowMillis = ZonedDateTime.of(2026, 11, 16, 22, 30, 0, 0, zone).toInstant().toEpochMilli()
    val nightAway = engine.evaluate(event(key = "n1", text = "algo"), awaySettings, emptyList(), saved)
    assertTrue(nightAway is AutoReplyDecision.Reply)
    assertEquals("Mensaje de ausencia", (nightAway as AutoReplyDecision.Reply).ruleName)
  }
}

