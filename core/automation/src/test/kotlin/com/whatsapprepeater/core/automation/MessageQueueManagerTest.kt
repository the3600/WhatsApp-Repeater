package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.SafetyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageQueueManagerTest {

  @Test
  fun `loads multiple messages in exact order and tracks progress`() {
    val manager = MessageQueueManager()
    val loaded = manager.load(MessageMode.MultipleMessages(listOf("Hola", "  ", "Segundo", "Tercero")))

    assertEquals(listOf("Hola", "Segundo", "Tercero"), loaded)
    assertEquals(3, manager.totalCount)
    assertEquals(0, manager.sentCount)
    assertEquals(3, manager.pendingCount)
    assertEquals("Hola", manager.currentMessage())

    manager.markCurrentSent()
    assertEquals(1, manager.sentCount)
    assertEquals(2, manager.pendingCount)
    assertEquals("Segundo", manager.currentMessage())

    manager.markCurrentSent()
    manager.markCurrentSent()
    assertTrue(manager.isFinished)
    assertNull(manager.currentMessage())
    assertEquals(0, manager.pendingCount)
  }

  @Test
  fun `loads repeated single message with exact repeat count`() {
    val manager = MessageQueueManager()
    val loaded = manager.load(MessageMode.RepeatSingleMessage(message = "Recordatorio", repeatCount = 4))

    assertEquals(4, loaded.size)
    assertTrue(loaded.all { it == "Recordatorio" })
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects repeat count above safety limit`() {
    val manager = MessageQueueManager()
    manager.load(
      MessageMode.RepeatSingleMessage(
        message = "Hola",
        repeatCount = SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT + 1,
      ),
    )
  }
}

