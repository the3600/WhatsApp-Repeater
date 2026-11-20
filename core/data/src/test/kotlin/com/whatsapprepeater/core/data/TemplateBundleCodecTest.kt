package com.whatsapprepeater.core.data

import com.whatsapprepeater.core.data.backup.TemplateBundleCodec
import com.whatsapprepeater.core.model.SavedMessageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateBundleCodecTest {

  @Test
  fun `exports and imports templates cleanly without private contact fields`() {
    val templates = listOf(
      SavedMessageList(
        id = 1L,
        title = "Bienvenida clientes",
        isMultipleMode = false,
        messages = emptyList(),
        singleMessage = "Hola, gracias por escribirnos.",
        repeatCount = 2,
        updatedAtMillis = 1_700_000_000_000L,
        category = "Ventas",
        tags = listOf("cliente", "saludo"),
        isFavorite = true,
        isPinned = false,
      ),
      SavedMessageList(
        id = 2L,
        title = "Secuencia cierre",
        isMultipleMode = true,
        messages = listOf("Paso 1: confirmar datos", "Paso 2: enviar comprobante"),
        singleMessage = "",
        repeatCount = 1,
        updatedAtMillis = 1_700_000_001_000L,
        category = "Soporte",
        tags = listOf("cierre"),
        isFavorite = false,
        isPinned = true,
      ),
    )

    val exported = TemplateBundleCodec.exportJson(templates, nowMillis = 1_800_000_000_000L)
    assertTrue(exported.contains("\"includesContactData\": false"))
    assertFalse(exported.contains("contactPhone"))
    assertFalse(exported.contains("contactName"))

    val restored = TemplateBundleCodec.importJson(exported, nowMillis = 1_800_000_005_000L)
    assertEquals(2, restored.size)
    assertEquals("Bienvenida clientes", restored[0].title)
    assertEquals("Ventas", restored[0].category)
    assertEquals(true, restored[0].isFavorite)
    assertEquals(2, restored[1].messages.size)
    assertEquals(true, restored[1].isPinned)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects invalid format identifier on template import`() {
    TemplateBundleCodec.importJson("""{"format":"otro-formato","exportedAtMillis":1,"templates":[]}""")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects oversized template json payload safely`() {
    TemplateBundleCodec.importJson("a".repeat(2_000_001))
  }
}
