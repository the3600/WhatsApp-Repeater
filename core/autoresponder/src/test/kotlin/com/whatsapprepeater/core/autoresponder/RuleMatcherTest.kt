package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.RuleMatchType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleMatcherTest {

  @Test
  fun `supports exact, single word, phrase, all words, regex and case sensitivity`() {
    assertTrue(RuleMatcher.matches("Precio", "precio", RuleMatchType.EXACT, caseSensitive = false))
    assertFalse(RuleMatcher.matches("Precio", "precio", RuleMatchType.EXACT, caseSensitive = true))
    assertFalse(RuleMatcher.matches("Hola precio", "precio", RuleMatchType.EXACT, caseSensitive = false))

    assertTrue(RuleMatcher.matches("Quiero saber el precio hoy", "precio", RuleMatchType.CONTAINS_WORD))
    assertFalse(RuleMatcher.matches("Preciosos zapatos", "precio", RuleMatchType.CONTAINS_WORD))

    assertTrue(RuleMatcher.matches("Hola buenas tardes, info", "buenas tardes", RuleMatchType.CONTAINS_PHRASE))
    assertFalse(RuleMatcher.matches("Hola buenas noches", "buenas tardes", RuleMatchType.CONTAINS_PHRASE))

    assertTrue(RuleMatcher.matches("Necesito soporte técnico urgente", "soporte urgente", RuleMatchType.CONTAINS_ALL_WORDS))
    assertFalse(RuleMatcher.matches("Necesito soporte hoy", "soporte urgente", RuleMatchType.CONTAINS_ALL_WORDS))

    assertTrue(RuleMatcher.matches("Pedido #12345 listo", "pedido\\s+#\\d{4,}", RuleMatchType.REGEX, caseSensitive = false))
    assertFalse(RuleMatcher.matches("Pedido #12 listo", "pedido\\s+#\\d{4,}", RuleMatchType.REGEX, caseSensitive = false))

    assertNull(RuleMatcher.validatePattern("^hola\\s+\\d+$", RuleMatchType.REGEX))
    assertNotNull(RuleMatcher.validatePattern("[sin cerrar", RuleMatchType.REGEX))
  }

  @Test
  fun `resolves template variables with safe fallbacks`() {
    val zone = ZoneId.of("UTC")
    val ts = ZonedDateTime.of(2026, 11, 15, 14, 30, 0, 0, zone).toInstant().toEpochMilli()

    val resolved = TemplateVariableResolver.resolve(
      template = "Hola {nombre}, recibimos '{mensaje}' el {fecha} a las {hora}.",
      senderOrChatName = "Carlos",
      incomingMessage = "Precio del servicio",
      timestampMillis = ts,
      zoneId = zone,
    )
    assertTrue(resolved.contains("Hola Carlos"))
    assertTrue(resolved.contains("Precio del servicio"))
    assertTrue(resolved.contains("15/11/2026"))
    assertTrue(resolved.contains("14:30"))

    val fallback = TemplateVariableResolver.resolve(
      template = "Hola {nombre}",
      senderOrChatName = "  ",
      incomingMessage = null,
      timestampMillis = ts,
      zoneId = zone,
    )
    assertTrue(fallback == "Hola contacto")
  }
}

