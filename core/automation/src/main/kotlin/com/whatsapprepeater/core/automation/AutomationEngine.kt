package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.MessageExecutionTrace
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.MessageTraceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor principal de automatización.
 * Ejecuta el flujo completo de forma transparente, verificando el destinatario antes de
 * escribir o enviar, validando razonablemente que cada mensaje se despache y deteniéndose
 * ante cualquier error, CAPTCHA o cambio inesperado de interfaz.
 */
class AutomationEngine(
  private val driver: AccessibilityDriver,
  private val navigator: WhatsAppNavigator = WhatsAppNavigator(),
  private val queueManager: MessageQueueManager = MessageQueueManager(),
  private val intervalGenerator: RandomIntervalGenerator = RandomIntervalGenerator(),
  private val onExecutionFinished: suspend (ExecutionRecord) -> Unit = {},
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
  private val delayProvider: suspend (Long) -> Unit = { delay(it) },
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
  private val _state = MutableStateFlow(AutomationState())
  val state: StateFlow<AutomationState> = _state.asStateFlow()

  private val pauseRequested = MutableStateFlow(false)
  @Volatile
  private var stopRequested = false
  private var activeJob: Job? = null
  private var lastStatusBeforePause: AutomationStatus = AutomationStatus.WAITING_INTERVAL

  @Synchronized
  fun start(config: AutomationConfig) {
    if (activeJob?.isActive == true) return

    if (!config.userConfirmed) {
      _state.value = AutomationState(
        status = AutomationStatus.ERROR,
        contact = config.contact,
        errorCount = 1,
        statusDetail = "Confirmación requerida",
        errorMessage = "El usuario debe confirmar explícitamente la operación antes de iniciar.",
      )
      return
    }

    val intervalError = config.intervalRange.validate()
    if (intervalError != null) {
      _state.value = AutomationState(
        status = AutomationStatus.ERROR,
        contact = config.contact,
        errorCount = 1,
        statusDetail = "Intervalo inválido",
        errorMessage = intervalError,
      )
      return
    }

    if (!driver.isServiceConnected.value) {
      _state.value = AutomationState(
        status = AutomationStatus.ERROR,
        contact = config.contact,
        errorCount = 1,
        statusDetail = "Servicio de accesibilidad inactivo",
        errorMessage = "Activa manualmente el servicio de accesibilidad de WhatsApp Repeater antes de iniciar.",
      )
      return
    }

    val loadedMessages = try {
      queueManager.load(config.mode)
    } catch (e: IllegalArgumentException) {
      _state.value = AutomationState(
        status = AutomationStatus.ERROR,
        contact = config.contact,
        errorCount = 1,
        statusDetail = "Configuración inválida",
        errorMessage = e.message ?: "Revisa los mensajes configurados.",
      )
      return
    }

    val now = clockMillis()
    val batchId = "WR-${((now % 90_000L) + 10_000L)}"
    val rangeText = "${config.intervalRange.minSeconds}s - ${config.intervalRange.maxSeconds}s"

    pauseRequested.value = false
    stopRequested = false
    _state.value = AutomationState(
      status = AutomationStatus.OPENING_WHATSAPP,
      contact = config.contact,
      totalScheduled = loadedMessages.size,
      sentConfirmed = 0,
      errorCount = 0,
      currentMessageIndex = 0,
      currentMessagePreview = loadedMessages.firstOrNull(),
      statusDetail = "Abriendo WhatsApp para ${config.contact.name}...",
      batchId = batchId,
      activeIntervalRangeText = rangeText,
      startedAtMillis = now,
    )

    activeJob = scope.launch {
      runAutomationLoop(config, batchId, now)
    }
  }

  fun pause() {
    if (_state.value.isRunning) {
      pauseRequested.value = true
      lastStatusBeforePause = _state.value.status
      _state.update {
        it.copy(
          status = AutomationStatus.PAUSED,
          statusDetail = "Pausado por el usuario",
        )
      }
    }
  }

  fun resume() {
    if (_state.value.isPaused) {
      pauseRequested.value = false
      _state.update {
        it.copy(
          status = lastStatusBeforePause,
          statusDetail = "Reanudando automatización...",
        )
      }
    }
  }

  fun stop() {
    pauseRequested.value = false
    stopRequested = true
    val current = _state.value
    if (activeJob?.isActive == true) {
      activeJob?.cancel(CancellationException("Detención de emergencia activada por el usuario"))
      _state.update {
        it.copy(
          status = AutomationStatus.STOPPED,
          remainingIntervalSeconds = 0,
          statusDetail = "Detenido manualmente por el usuario.",
          finishedAtMillis = clockMillis(),
        )
      }
    } else if (current.isPaused || current.isRunning) {
      _state.update {
        it.copy(
          status = AutomationStatus.STOPPED,
          remainingIntervalSeconds = 0,
          statusDetail = "Detenido manualmente por el usuario.",
          finishedAtMillis = clockMillis(),
        )
      }
    }
  }

  fun resetToIdle() {
    if (activeJob?.isActive != true) {
      queueManager.clear()
      _state.value = AutomationState()
    }
  }

  private suspend fun runAutomationLoop(
    config: AutomationConfig,
    batchId: String,
    startedAtMillis: Long,
  ) {
    var errorCount = 0
    var finalStatus = ExecutionFinalStatus.COMPLETED
    var finalDetail = "Todos los mensajes fueron enviados y verificados."
    val traces = mutableListOf<MessageExecutionTrace>()
    val intervalSamples = mutableListOf<Int>()
    val allMessages = queueManager.allMessages()

    val modeLabel = when (config.mode) {
      is MessageMode.RepeatSingleMessage -> "Repetición de mensaje único"
      is MessageMode.MultipleMessages -> "Secuencia de mensajes múltiples"
    }

    try {
      // Paso 6-7: Abrir WhatsApp directamente hacia la conversación del contacto seleccionado.
      val opened = driver.openWhatsAppChat(config.contact, config.targetPackage)
      if (!opened) {
        driver.openWhatsAppHome(config.targetPackage)
      }
      delayProvider(1_500L)
      ensureNotStopped()

      val reachedConversation = ensureConversationOpened(config)
      if (!reachedConversation) {
        errorCount++
        finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
        finalDetail = _state.value.errorMessage ?: "No se pudo abrir ni verificar la conversación del contacto."
        return
      }

      // Pasos 9-14: Iterar sobre la cola secuencial de mensajes.
      while (!queueManager.isFinished) {
        ensureNotStopped()
        waitWhilePaused()

        val message = queueManager.currentMessage() ?: break
        val currentIdx = queueManager.currentIndex + 1

        // Paso 8: Reverificar que el destinatario sigue coincidiendo antes de cada envío.
        val openScreen = verifyActiveConversation(config)
        if (openScreen == null) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
          finalDetail = _state.value.errorMessage ?: "El destinatario no coincide o la conversación cambió."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        val inputNode = openScreen.inputNode
        if (inputNode == null) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_ERROR
          failWith("No se encontró el campo de escritura en WhatsApp.")
          finalDetail = "Campo de escritura no encontrado."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        // Paso 9: Escribir mensaje.
        updateStatus(
          status = AutomationStatus.WRITING_MESSAGE,
          detail = "Escribiendo mensaje $currentIdx de ${queueManager.totalCount}...",
          preview = message,
        )
        val typed = driver.setText(inputNode.nodeId, message)
        if (!typed) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_ERROR
          failWith("No se pudo escribir el texto en el campo de mensaje.")
          finalDetail = "Error al escribir el mensaje."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        // Paso 10: Esperar confirmación visual de que el texto está escrito y el botón Enviar habilitado.
        updateStatus(
          status = AutomationStatus.WAITING_SEND_CONFIRMATION,
          detail = "Verificando botón Enviar para mensaje $currentIdx/${queueManager.totalCount}...",
          preview = message,
        )
        val readyScreen = waitForSendButtonReady(config, message)
        if (readyScreen?.sendButtonNode == null) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
          failWith("No se confirmó el botón de envío habilitado con el texto listo.")
          finalDetail = "Botón de envío no confirmado."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        waitWhilePaused()
        ensureNotStopped()

        // Paso 11: Enviar mensaje y verificar razonablemente que la caja de texto se vació.
        updateStatus(
          status = AutomationStatus.SENDING_MESSAGE,
          detail = "Enviando mensaje $currentIdx de ${queueManager.totalCount}...",
          preview = message,
        )
        val clicked = driver.clickNode(readyScreen.sendButtonNode.nodeId)
        if (!clicked) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_ERROR
          failWith("No se pudo pulsar el botón de envío de WhatsApp.")
          finalDetail = "Fallo al pulsar Enviar."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        val dispatched = verifyMessageDispatched(config, message)
        if (!dispatched) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
          failWith("Se pulsó Enviar pero no pudo verificarse que el mensaje saliera del campo de texto. Se detiene por seguridad.")
          finalDetail = "No se pudo verificar el despacho en la interfaz de WhatsApp."
          traces.add(
            MessageExecutionTrace(
              index = currentIdx,
              messagePreview = message.take(80),
              status = MessageTraceStatus.FAILED,
              timestampMillis = clockMillis(),
            ),
          )
          return
        }

        traces.add(
          MessageExecutionTrace(
            index = currentIdx,
            messagePreview = message.take(80),
            status = MessageTraceStatus.CONFIRMED,
            timestampMillis = clockMillis(),
          ),
        )
        queueManager.markCurrentSent()

        _state.update {
          it.copy(
            sentConfirmed = queueManager.sentCount,
            currentMessageIndex = queueManager.currentIndex,
            currentMessagePreview = queueManager.currentMessage(),
          )
        }

        // Paso 12-13: Esperar intervalo aleatorio si quedan mensajes pendientes.
        if (!queueManager.isFinished) {
          ensureNotStopped()
          val waitSeconds = intervalGenerator.nextIntervalSeconds(config.intervalRange)
          intervalSamples.add(waitSeconds)
          waitIntervalWithCountdown(waitSeconds)
        }
      }

      // Paso 14: Finalizar con éxito.
      val completedAt = clockMillis()
      _state.update {
        it.copy(
          status = AutomationStatus.COMPLETED,
          remainingIntervalSeconds = 0,
          statusDetail = "Completado: ${queueManager.sentCount} de ${queueManager.totalCount} mensajes confirmados.",
          errorMessage = null,
          finishedAtMillis = completedAt,
        )
      }
      finalStatus = ExecutionFinalStatus.COMPLETED
      finalDetail = "Completado con éxito."
    } catch (c: CancellationException) {
      finalStatus = ExecutionFinalStatus.STOPPED_BY_USER
      finalDetail = "Detención de emergencia activada por el usuario."
      _state.update {
        it.copy(
          status = AutomationStatus.STOPPED,
          remainingIntervalSeconds = 0,
          statusDetail = "Detenido manualmente por el usuario.",
          finishedAtMillis = clockMillis(),
        )
      }
      throw c
    } catch (e: Exception) {
      errorCount++
      finalStatus = ExecutionFinalStatus.FAILED_ERROR
      finalDetail = e.message ?: "Error inesperado durante la automatización."
      failWith(finalDetail)
    } finally {
      // Marcar los mensajes restantes en cola como CANCELLED si hubo interrupción/error.
      val recordedIndices = traces.map { it.index }.toSet()
      val nowEnd = clockMillis()
      for (i in allMessages.indices) {
        val oneBased = i + 1
        if (oneBased !in recordedIndices) {
          traces.add(
            MessageExecutionTrace(
              index = oneBased,
              messagePreview = allMessages[i].take(80),
              status = MessageTraceStatus.CANCELLED,
              timestampMillis = nowEnd,
            ),
          )
        }
      }

      val duration = (nowEnd - startedAtMillis).coerceAtLeast(0L)
      val avgInterval = if (intervalSamples.isNotEmpty()) intervalSamples.average() else 0.0
      val snapshotState = _state.value

      driver.clearNodeCache()

      withContext(NonCancellable) {
        onExecutionFinished(
          ExecutionRecord(
            batchCode = batchId,
            timestampMillis = nowEnd,
            contactName = config.contact.name,
            contactPhone = config.contact.phoneNumber,
            scheduledCount = queueManager.totalCount,
            confirmedCount = queueManager.sentCount,
            errorCount = errorCount.coerceAtLeast(snapshotState.errorCount),
            finalStatus = finalStatus,
            statusDetail = finalDetail,
            durationMillis = duration,
            averageIntervalSeconds = avgInterval,
            modeLabel = modeLabel,
            messageTraces = traces.sortedBy { it.index },
          ),
        )
      }
    }
  }

  private suspend fun verifyMessageDispatched(
    config: AutomationConfig,
    sentText: String,
  ): Boolean {
    repeat(4) {
      delayProvider(400L)
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      if (screen is WhatsAppScreen.UnexpectedOrError &&
        (screen.isSecurityOrCaptcha || screen.reason.contains("WhatsApp mostró"))
      ) {
        return false
      }
      if (screen is WhatsAppScreen.OpenConversation) {
        if (verifyRecipientAndUpdate(screen, config.contact) == null) {
          return false
        }
        val stillHasText = navigator.isMessageReadyInInput(screen.inputNode, sentText)
        if (!stillHasText) {
          return true
        }
      }
    }
    return false
  }

  private suspend fun ensureConversationOpened(config: AutomationConfig): Boolean {
    updateStatus(
      status = AutomationStatus.NAVIGATING_TO_CHAT,
      detail = "Buscando conversación de ${config.contact.name}...",
    )

    var searchAttempted = false
    repeat(14) {
      ensureNotStopped()
      waitWhilePaused()
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      when (screen) {
        is WhatsAppScreen.OpenConversation -> {
          return verifyRecipientAndUpdate(screen, config.contact) != null
        }

        is WhatsAppScreen.HomeChatList -> {
          val directRow = navigator.findMatchingChatRow(screen.chatRows, config.contact)
          if (directRow != null) {
            driver.clickNode(directRow.nodeId)
          } else if (!searchAttempted && screen.searchButtonNode != null) {
            searchAttempted = true
            driver.clickNode(screen.searchButtonNode.nodeId)
          }
        }

        is WhatsAppScreen.SearchMode -> {
          val matchingResult = navigator.findMatchingChatRow(screen.resultRows, config.contact)
          if (matchingResult != null) {
            driver.clickNode(matchingResult.nodeId)
          } else if (screen.searchInputNode != null && screen.searchInputNode.text.isNullOrBlank()) {
            val query = config.contact.name.ifBlank { config.contact.phoneNumber }
            driver.setText(screen.searchInputNode.nodeId, query)
          }
        }

        is WhatsAppScreen.UnexpectedOrError -> {
          if (screen.isSecurityOrCaptcha) {
            failWith("${screen.reason} La automatización se detiene por seguridad.")
            return false
          }
          if (screen.reason.contains("WhatsApp mostró")) {
            failWith(screen.reason)
            return false
          }
        }

        is WhatsAppScreen.ExternalApp -> {
          // Aún abriendo WhatsApp; esperar brevemente en los primeros intentos.
        }
      }
      delayProvider(700L)
    }

    failWith("No se pudo abrir la conversación de ${config.contact.name} dentro del tiempo límite.")
    return false
  }

  private suspend fun verifyActiveConversation(config: AutomationConfig): WhatsAppScreen.OpenConversation? {
    updateStatus(
      status = AutomationStatus.VERIFYING_RECIPIENT,
      detail = "Verificando destinatario en pantalla...",
    )

    val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
    return when (screen) {
      is WhatsAppScreen.OpenConversation -> verifyRecipientAndUpdate(screen, config.contact)
      is WhatsAppScreen.UnexpectedOrError -> {
        failWith(screen.reason)
        null
      }
      is WhatsAppScreen.ExternalApp -> {
        failWith("Se abandonó WhatsApp (${screen.packageName ?: "pantalla externa"}). Ejecución detenida.")
        null
      }
      else -> {
        failWith("La conversación ya no está abierta en primer plano. Ejecución detenida.")
        null
      }
    }
  }

  private fun verifyRecipientAndUpdate(
    screen: WhatsAppScreen.OpenConversation,
    contact: ContactInfo,
  ): WhatsAppScreen.OpenConversation? {
    return when (val result = navigator.verifyRecipientMatches(screen, contact)) {
      is RecipientVerificationResult.Verified -> screen
      is RecipientVerificationResult.Mismatch -> {
        failWith(result.reason)
        null
      }
    }
  }

  private suspend fun waitForSendButtonReady(
    config: AutomationConfig,
    expectedText: String,
  ): WhatsAppScreen.OpenConversation? {
    repeat(6) {
      ensureNotStopped()
      waitWhilePaused()
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      if (screen is WhatsAppScreen.UnexpectedOrError &&
        (screen.isSecurityOrCaptcha || screen.reason.contains("WhatsApp mostró"))
      ) {
        failWith(screen.reason)
        return null
      }
      if (screen is WhatsAppScreen.OpenConversation) {
        val verified = verifyRecipientAndUpdate(screen, config.contact) ?: return null
        val textReady = navigator.isMessageReadyInInput(verified.inputNode, expectedText)
        if (textReady && verified.sendButtonNode != null && verified.sendButtonNode.isEnabled) {
          return verified
        }
      }
      delayProvider(400L)
    }
    return null
  }

  private suspend fun waitIntervalWithCountdown(totalSeconds: Int) {
    for (remaining in totalSeconds downTo 1) {
      ensureNotStopped()
      waitWhilePaused()
      _state.update {
        it.copy(
          status = AutomationStatus.WAITING_INTERVAL,
          remainingIntervalSeconds = remaining,
          statusDetail = "Esperando intervalo aleatorio: ${remaining}s restantes...",
        )
      }
      delayProvider(1_000L)
      ensureNotStopped()
    }
    _state.update { it.copy(remainingIntervalSeconds = 0) }
  }

  private suspend fun waitWhilePaused() {
    while (pauseRequested.value) {
      ensureNotStopped()
      delayProvider(250L)
    }
  }

  private suspend fun ensureNotStopped() {
    if (stopRequested) {
      throw CancellationException("Detención de emergencia activada por el usuario")
    }
    currentCoroutineContext().ensureActive()
  }

  private fun updateStatus(
    status: AutomationStatus,
    detail: String,
    preview: String? = _state.value.currentMessagePreview,
  ) {
    _state.update {
      it.copy(
        status = status,
        statusDetail = detail,
        currentMessagePreview = preview,
      )
    }
  }

  private fun failWith(message: String) {
    _state.update {
      it.copy(
        status = AutomationStatus.ERROR,
        errorCount = it.errorCount + 1,
        remainingIntervalSeconds = 0,
        statusDetail = "Detenido por seguridad / error",
        errorMessage = message,
        finishedAtMillis = clockMillis(),
      )
    }
  }
}
