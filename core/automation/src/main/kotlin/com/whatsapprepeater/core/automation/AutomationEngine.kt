package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.DynamicVariablesEngine
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
 * Ejecuta el flujo completo de forma transparente: abre la conversación, verifica estrictamente
 * al destinatario antes de escribir o pulsar Enviar, confirma el despacho real del mensaje y se
 * detiene ante cualquier error, desconexión de accesibilidad, CAPTCHA o cambio inesperado de UI.
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
  private val stateLock = Any()
  @Volatile
  private var stopRequested = false
  @Volatile
  private var isLoopActive = false
  private var activeJob: Job? = null
  private var lastStatusBeforePause: AutomationStatus = AutomationStatus.WAITING_INTERVAL

  fun start(config: AutomationConfig) {
    synchronized(stateLock) {
      if (isLoopActive || activeJob?.isActive == true) return

      if (config.contact.phoneNumber.isBlank() && config.contact.name.isBlank()) {
        _state.value = AutomationState(
          status = AutomationStatus.ERROR,
          contact = config.contact,
          errorCount = 1,
          diagnosticStage = "Validación previa",
          statusDetail = "Destinatario inválido",
          errorMessage = "Selecciona un contacto o ingresa un número válido antes de iniciar.",
        )
        return
      }

      if (!config.userConfirmed) {
        _state.value = AutomationState(
          status = AutomationStatus.ERROR,
          contact = config.contact,
          errorCount = 1,
          diagnosticStage = "Confirmación del usuario",
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
          diagnosticStage = "Validación de intervalo",
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
          diagnosticStage = "Servicio de Accesibilidad",
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
          diagnosticStage = "Cola de mensajes",
          statusDetail = "Configuración inválida",
          errorMessage = e.message ?: "Revisa los mensajes configurados.",
        )
        return
      }

      val unresolvedTokens = loadedMessages
        .flatMap { DynamicVariablesEngine.findPlaceholders(it) }
        .distinct()
      if (unresolvedTokens.isNotEmpty()) {
        queueManager.clear()
        _state.value = AutomationState(
          status = AutomationStatus.ERROR,
          contact = config.contact,
          errorCount = 1,
          diagnosticStage = "Variables dinámicas",
          statusDetail = "Variables sin resolver",
          errorMessage = "Se detectaron variables sin resolver (${unresolvedTokens.joinToString(", ")}). Cancela y corrígelas antes de enviar.",
        )
        return
      }

      val now = clockMillis()
      val batchId = "WR-${((now % 90_000L) + 10_000L)}"
      val rangeText = "${config.intervalRange.minSeconds}s - ${config.intervalRange.maxSeconds}s"

      pauseRequested.value = false
      stopRequested = false
      isLoopActive = true
      _state.value = AutomationState(
        status = AutomationStatus.OPENING_WHATSAPP,
        contact = config.contact,
        totalScheduled = loadedMessages.size,
        sentConfirmed = 0,
        errorCount = 0,
        currentMessageIndex = 0,
        currentMessagePreview = loadedMessages.firstOrNull(),
        statusDetail = "Abriendo WhatsApp para ${config.contact.name}...",
        diagnosticStage = "Apertura de conversación",
        batchId = batchId,
        activeIntervalRangeText = rangeText,
        startedAtMillis = now,
      )

      activeJob = scope.launch {
        runAutomationLoop(config, batchId, now, loadedMessages.toList())
      }
    }
  }

  fun pause() {
    synchronized(stateLock) {
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
  }

  fun resume() {
    synchronized(stateLock) {
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
  }

  fun stop() {
    synchronized(stateLock) {
      pauseRequested.value = false
      stopRequested = true
      val current = _state.value
      val job = activeJob
      if (job?.isActive == true) {
        job.cancel(CancellationException("Detención de emergencia activada por el usuario"))
        _state.update {
          it.copy(
            status = AutomationStatus.STOPPED,
            remainingIntervalSeconds = 0,
            statusDetail = "Detenido manualmente por el usuario.",
            finishedAtMillis = clockMillis(),
          )
        }
      } else if (current.isPaused || current.isRunning || isLoopActive) {
        isLoopActive = false
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
  }

  fun resetToIdle() {
    synchronized(stateLock) {
      if (!isLoopActive && activeJob?.isActive != true) {
        queueManager.clear()
        _state.value = AutomationState()
      }
    }
  }

  private suspend fun runAutomationLoop(
    config: AutomationConfig,
    batchId: String,
    startedAtMillis: Long,
    initialMessages: List<String>,
  ) {
    var errorCount = 0
    var confirmedLocalCount = 0
    val scheduledTotal = initialMessages.size
    var finalStatus = ExecutionFinalStatus.COMPLETED
    var finalDetail = "Todos los mensajes fueron enviados y verificados."
    val traces = mutableListOf<MessageExecutionTrace>()
    val intervalSamples = mutableListOf<Int>()
    val allMessages = initialMessages

    val modeLabel = when (config.mode) {
      is MessageMode.RepeatSingleMessage -> "Repetición de mensaje único"
      is MessageMode.MultipleMessages -> "Secuencia de mensajes múltiples"
    }

    try {
      ensureServiceAndNotStopped()
      val opened = driver.openWhatsAppChat(config.contact, config.targetPackage)
      if (!opened) {
        driver.openWhatsAppHome(config.targetPackage)
      }
      delayProvider(1_500L)
      ensureServiceAndNotStopped()

      val reachedConversation = ensureConversationOpened(config)
      if (!reachedConversation) {
        errorCount++
        finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
        finalDetail = _state.value.errorMessage ?: "No se pudo abrir ni verificar la conversación del contacto."
        return
      }

      while (!queueManager.isFinished) {
        ensureServiceAndNotStopped()
        waitWhilePaused()

        val message = queueManager.currentMessage() ?: break
        val currentIdx = queueManager.currentIndex + 1

        // 1. Verificar que seguimos dentro de la conversación del contacto correcto.
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
          failWith(
            message = "No se encontró el campo de escritura en WhatsApp. Verifica que la conversación esté abierta y sin bloqueos.",
            stage = "Campo de escritura",
          )
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

        // 2. Escribir mensaje en el campo de entrada (con 1 reintento seguro ante refresco de nodo).
        updateStatus(
          status = AutomationStatus.WRITING_MESSAGE,
          detail = "Escribiendo mensaje $currentIdx de ${queueManager.totalCount}...",
          stage = "Inserción de mensaje",
          preview = message,
        )
        var typed = driver.setText(inputNode.nodeId, message)
        if (!typed) {
          delayProvider(250L)
          val refreshed = verifyActiveConversation(config)
          val freshInput = refreshed?.inputNode
          if (freshInput != null) {
            typed = driver.setText(freshInput.nodeId, message)
          }
        }
        if (!typed) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_ERROR
          failWith(
            message = "No se pudo escribir el texto en el campo de mensaje de WhatsApp.",
            stage = "Inserción de mensaje",
          )
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

        // 3. Esperar confirmación visual del texto y del botón Enviar habilitado.
        updateStatus(
          status = AutomationStatus.WAITING_SEND_CONFIRMATION,
          detail = "Verificando botón Enviar para mensaje $currentIdx/${queueManager.totalCount}...",
          stage = "Botón Enviar",
          preview = message,
        )
        val readyOutcome = waitForSendButtonReady(config, message)
        val readyScreen = readyOutcome.screen
        if (readyScreen?.sendButtonNode == null) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_SAFETY_CHECK
          val reason = readyOutcome.failureReason
            ?: "No se confirmó el botón Enviar habilitado con el texto listo en WhatsApp."
          failWith(message = reason, stage = "Botón Enviar")
          finalDetail = reason
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
        ensureServiceAndNotStopped()

        // 4. Pulsar Enviar y confirmar que el mensaje fue despachado (nunca marcar enviado sin verificar).
        updateStatus(
          status = AutomationStatus.SENDING_MESSAGE,
          detail = "Enviando mensaje $currentIdx de ${queueManager.totalCount}...",
          stage = "Pulsación y despacho",
          preview = message,
        )
        var clicked = driver.clickNode(readyScreen.sendButtonNode.nodeId)
        if (!clicked) {
          delayProvider(200L)
          val refreshedReady = waitForSendButtonReady(config, message).screen
          if (refreshedReady?.sendButtonNode != null) {
            clicked = driver.clickNode(refreshedReady.sendButtonNode.nodeId)
          }
        }
        if (!clicked) {
          errorCount++
          finalStatus = ExecutionFinalStatus.FAILED_ERROR
          failWith(
            message = "No se pudo pulsar el botón Enviar de WhatsApp.",
            stage = "Pulsación de Enviar",
          )
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
          failWith(
            message = "Se pulsó Enviar pero no pudo confirmarse que el mensaje saliera de la caja de texto en WhatsApp. Se detiene por seguridad sin registrarlo como enviado.",
            stage = "Confirmación de despacho",
          )
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
        confirmedLocalCount++
        queueManager.markCurrentSent()

        _state.update {
          it.copy(
            sentConfirmed = confirmedLocalCount,
            currentMessageIndex = queueManager.currentIndex,
            currentMessagePreview = queueManager.currentMessage(),
          )
        }

        // 5. Esperar intervalo aleatorio si quedan mensajes pendientes.
        if (!queueManager.isFinished) {
          ensureServiceAndNotStopped()
          val waitSeconds = intervalGenerator.nextIntervalSeconds(config.intervalRange)
          intervalSamples.add(waitSeconds)
          waitIntervalWithCountdown(waitSeconds)
        }
      }

      // 6. Finalización exitosa confirmada.
      val completedAt = clockMillis()
      _state.update {
        it.copy(
          status = AutomationStatus.COMPLETED,
          remainingIntervalSeconds = 0,
          statusDetail = "Completado: ${queueManager.sentCount} de ${queueManager.totalCount} mensajes confirmados.",
          diagnosticStage = "Completado",
          errorMessage = null,
          observedHeaderSummary = "",
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
          diagnosticStage = "Detención manual",
          finishedAtMillis = clockMillis(),
        )
      }
      throw c
    } catch (s: ServiceDisconnectedException) {
      errorCount++
      finalStatus = ExecutionFinalStatus.FAILED_ERROR
      finalDetail = s.message ?: "El servicio de accesibilidad fue detenido por Android o HyperOS."
      failWith(message = finalDetail, stage = "Servicio de Accesibilidad")
    } catch (e: Exception) {
      errorCount++
      finalStatus = ExecutionFinalStatus.FAILED_ERROR
      finalDetail = e.message ?: "Error inesperado durante la automatización."
      failWith(message = finalDetail, stage = "Error de ejecución")
    } finally {
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

      synchronized(stateLock) {
        if (activeJob === kotlin.coroutines.coroutineContext[Job]) {
          activeJob = null
        }
        isLoopActive = false
      }

      withContext(NonCancellable) {
        onExecutionFinished(
          ExecutionRecord(
            batchCode = batchId,
            timestampMillis = nowEnd,
            contactName = config.contact.name,
            contactPhone = config.contact.phoneNumber,
            scheduledCount = scheduledTotal,
            confirmedCount = confirmedLocalCount,
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
    repeat(8) {
      delayProvider(350L)
      if (!driver.isServiceConnected.value) {
        throw ServiceDisconnectedException(
          "El servicio de Accesibilidad de WhatsApp Repeater fue desconectado por Android o HyperOS durante el despacho.",
        )
      }
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      if (screen is WhatsAppScreen.UnexpectedOrError &&
        (screen.isSecurityOrCaptcha || screen.reason.contains("WhatsApp mostró"))
      ) {
        return false
      }
      if (screen is WhatsAppScreen.OpenConversation) {
        if (verifyRecipientAndUpdate(screen, config.contact, updateFailState = false) == null) {
          return false
        }
        val cleared = navigator.isComposerClearedAfterSend(screen.inputNode, sentText)
        val resetToMicOrNoSend = screen.sendButtonNode == null || screen.micButtonPresent
        if (cleared && resetToMicOrNoSend) {
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
      stage = "Apertura de conversación",
    )

    var searchAttempted = false
    repeat(16) {
      ensureServiceAndNotStopped()
      waitWhilePaused()
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      when (screen) {
        is WhatsAppScreen.OpenConversation -> {
          return verifyRecipientAndUpdate(screen, config.contact, updateFailState = true) != null
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
            failWith(
              message = "${screen.reason} La automatización se detiene por seguridad.",
              stage = "Bloqueo / Seguridad WhatsApp",
            )
            return false
          }
          if (screen.reason.contains("WhatsApp mostró")) {
            failWith(
              message = screen.reason,
              stage = "Diálogo de WhatsApp",
            )
            return false
          }
        }

        is WhatsAppScreen.ExternalApp -> {
          // Aún abriendo WhatsApp; esperar brevemente en los primeros intentos.
        }
      }
      delayProvider(700L)
    }

    failWith(
      message = "No se pudo abrir ni confirmar la conversación de ${config.contact.name} (${config.contact.phoneNumber}) dentro del tiempo límite.",
      stage = "Apertura de conversación",
    )
    return false
  }

  private suspend fun verifyActiveConversation(config: AutomationConfig): WhatsAppScreen.OpenConversation? {
    updateStatus(
      status = AutomationStatus.VERIFYING_RECIPIENT,
      detail = "Verificando destinatario en pantalla...",
      stage = "Verificación de destinatario",
    )

    val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
    return when (screen) {
      is WhatsAppScreen.OpenConversation -> verifyRecipientAndUpdate(screen, config.contact, updateFailState = true)
      is WhatsAppScreen.UnexpectedOrError -> {
        failWith(message = screen.reason, stage = "Pantalla de WhatsApp")
        null
      }
      is WhatsAppScreen.ExternalApp -> {
        failWith(
          message = "Se abandonó WhatsApp (${screen.packageName ?: "pantalla externa"}). Ejecución detenida por seguridad.",
          stage = "Cambio de pantalla",
        )
        null
      }
      else -> {
        failWith(
          message = "La conversación ya no está abierta en primer plano. Ejecución detenida.",
          stage = "Conversación cerrada",
        )
        null
      }
    }
  }

  private fun verifyRecipientAndUpdate(
    screen: WhatsAppScreen.OpenConversation,
    contact: ContactInfo,
    updateFailState: Boolean,
  ): WhatsAppScreen.OpenConversation? {
    return when (val result = navigator.verifyRecipientMatches(screen, contact)) {
      is RecipientVerificationResult.Verified -> screen
      is RecipientVerificationResult.Mismatch -> {
        if (updateFailState) {
          failWith(
            message = result.reason,
            stage = "Verificación de destinatario",
            observedHeader = result.observedHeaders.joinToString(" | "),
          )
        }
        null
      }
    }
  }

  private data class SendButtonReadyOutcome(
    val screen: WhatsAppScreen.OpenConversation? = null,
    val failureReason: String? = null,
  )

  private suspend fun waitForSendButtonReady(
    config: AutomationConfig,
    expectedText: String,
  ): SendButtonReadyOutcome {
    var lastTextReady = false
    var lastMicPresent = false
    repeat(8) {
      ensureServiceAndNotStopped()
      waitWhilePaused()
      val screen = navigator.inspectScreen(driver.captureScreenSnapshot())
      if (screen is WhatsAppScreen.UnexpectedOrError &&
        (screen.isSecurityOrCaptcha || screen.reason.contains("WhatsApp mostró"))
      ) {
        return SendButtonReadyOutcome(failureReason = screen.reason)
      }
      if (screen is WhatsAppScreen.OpenConversation) {
        val verified = verifyRecipientAndUpdate(screen, config.contact, updateFailState = true)
          ?: return SendButtonReadyOutcome(failureReason = _state.value.errorMessage)
        lastTextReady = navigator.isMessageReadyInInput(verified.inputNode, expectedText)
        lastMicPresent = verified.micButtonPresent
        if (lastTextReady && verified.sendButtonNode != null && verified.sendButtonNode.isEnabled) {
          return SendButtonReadyOutcome(screen = verified)
        }
      }
      delayProvider(350L)
    }
    val diagnosis = when {
      !lastTextReady -> "El mensaje escrito no permaneció confirmado dentro del campo de texto."
      lastMicPresent -> "El texto está en la caja pero WhatsApp siguió mostrando el botón de nota de voz en lugar de Enviar."
      else -> "No se confirmó el botón Enviar habilitado dentro de WhatsApp."
    }
    return SendButtonReadyOutcome(failureReason = diagnosis)
  }

  private suspend fun waitIntervalWithCountdown(totalSeconds: Int) {
    for (remaining in totalSeconds downTo 1) {
      ensureServiceAndNotStopped()
      waitWhilePaused()
      _state.update {
        it.copy(
          status = AutomationStatus.WAITING_INTERVAL,
          remainingIntervalSeconds = remaining,
          diagnosticStage = "Intervalo entre mensajes",
          statusDetail = "Esperando intervalo aleatorio: ${remaining}s restantes...",
        )
      }
      delayProvider(1_000L)
      ensureServiceAndNotStopped()
    }
    _state.update { it.copy(remainingIntervalSeconds = 0) }
  }

  private suspend fun waitWhilePaused() {
    while (pauseRequested.value) {
      ensureServiceAndNotStopped()
      delayProvider(250L)
    }
  }

  private suspend fun ensureServiceAndNotStopped() {
    if (stopRequested) {
      throw CancellationException("Detención de emergencia activada por el usuario")
    }
    if (!driver.isServiceConnected.value) {
      throw ServiceDisconnectedException(
        "El servicio de Accesibilidad de WhatsApp Repeater fue desconectado por Android o HyperOS durante la operación.",
      )
    }
    currentCoroutineContext().ensureActive()
  }

  private fun updateStatus(
    status: AutomationStatus,
    detail: String,
    stage: String = _state.value.diagnosticStage,
    preview: String? = _state.value.currentMessagePreview,
  ) {
    _state.update {
      it.copy(
        status = status,
        statusDetail = detail,
        diagnosticStage = stage,
        currentMessagePreview = preview,
      )
    }
  }

  private fun failWith(
    message: String,
    stage: String = "Seguridad / Error",
    observedHeader: String = "",
  ) {
    _state.update {
      it.copy(
        status = AutomationStatus.ERROR,
        errorCount = it.errorCount + 1,
        remainingIntervalSeconds = 0,
        statusDetail = "Detenido en: $stage",
        diagnosticStage = stage,
        errorMessage = message,
        observedHeaderSummary = observedHeader,
        finishedAtMillis = clockMillis(),
      )
    }
  }

  private class ServiceDisconnectedException(message: String) : Exception(message)
}
