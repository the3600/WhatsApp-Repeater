package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.SafetyLimits

/**
 * Administra la cola secuencial de mensajes a enviar en una ejecución.
 */
class MessageQueueManager {
  private var messages: List<String> = emptyList()
  private var index: Int = 0
  private var confirmedCount: Int = 0

  val totalCount: Int
    get() = messages.size

  val sentCount: Int
    get() = confirmedCount

  val pendingCount: Int
    get() = (messages.size - confirmedCount).coerceAtLeast(0)

  val currentIndex: Int
    get() = index

  val isFinished: Boolean
    get() = index >= messages.size

  fun load(mode: MessageMode): List<String> {
    val expanded = when (mode) {
      is MessageMode.MultipleMessages -> {
        val cleaned = mode.messages
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .map { it.take(SafetyLimits.MAX_MESSAGE_LENGTH) }
        require(cleaned.isNotEmpty()) { "Debe agregar al menos un mensaje válido." }
        require(cleaned.size <= SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT) {
          "El límite máximo es de ${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT} mensajes por ejecución."
        }
        cleaned
      }

      is MessageMode.RepeatSingleMessage -> {
        val cleaned = mode.message.trim().take(SafetyLimits.MAX_MESSAGE_LENGTH)
        require(cleaned.isNotEmpty()) { "El mensaje no puede estar vacío." }
        require(mode.repeatCount in SafetyLimits.MIN_REPEAT_COUNT..SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT) {
          "Las repeticiones deben estar entre ${SafetyLimits.MIN_REPEAT_COUNT} y ${SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT}."
        }
        List(mode.repeatCount) { cleaned }
      }
    }
    messages = expanded
    index = 0
    confirmedCount = 0
    return messages
  }

  fun currentMessage(): String? = messages.getOrNull(index)

  fun markCurrentSent() {
    if (index < messages.size) {
      index++
      confirmedCount++
    }
  }

  fun allMessages(): List<String> = messages

  fun clear() {
    messages = emptyList()
    index = 0
    confirmedCount = 0
  }
}

