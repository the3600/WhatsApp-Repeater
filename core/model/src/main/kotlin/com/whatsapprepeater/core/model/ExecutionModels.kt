package com.whatsapprepeater.core.model

/**
 * Estado detallado del ciclo de vida de la automatización en tiempo real.
 */
enum class AutomationStatus {
  IDLE,
  CONFIRMING_START,
  OPENING_WHATSAPP,
  NAVIGATING_TO_CHAT,
  VERIFYING_RECIPIENT,
  WRITING_MESSAGE,
  WAITING_SEND_CONFIRMATION,
  SENDING_MESSAGE,
  WAITING_INTERVAL,
  PAUSED,
  COMPLETED,
  STOPPED,
  ERROR,
}

/**
 * Estado observable en la UI de ejecución.
 */
data class AutomationState(
  val status: AutomationStatus = AutomationStatus.IDLE,
  val contact: ContactInfo? = null,
  val totalScheduled: Int = 0,
  val sentConfirmed: Int = 0,
  val errorCount: Int = 0,
  val currentMessageIndex: Int = 0,
  val currentMessagePreview: String? = null,
  val remainingIntervalSeconds: Int = 0,
  val statusDetail: String = "Listo para iniciar",
  val errorMessage: String? = null,
  val batchId: String = "",
  val activeIntervalRangeText: String = "",
  val startedAtMillis: Long = 0L,
  val finishedAtMillis: Long = 0L,
) {
  val pendingCount: Int
    get() = (totalScheduled - sentConfirmed).coerceAtLeast(0)

  val progress: Float
    get() = if (totalScheduled <= 0) 0f else (sentConfirmed.toFloat() / totalScheduled.toFloat()).coerceIn(0f, 1f)

  val isRunning: Boolean
    get() = status in setOf(
      AutomationStatus.CONFIRMING_START,
      AutomationStatus.OPENING_WHATSAPP,
      AutomationStatus.NAVIGATING_TO_CHAT,
      AutomationStatus.VERIFYING_RECIPIENT,
      AutomationStatus.WRITING_MESSAGE,
      AutomationStatus.WAITING_SEND_CONFIRMATION,
      AutomationStatus.SENDING_MESSAGE,
      AutomationStatus.WAITING_INTERVAL,
    )

  val isPaused: Boolean
    get() = status == AutomationStatus.PAUSED

  val isTerminal: Boolean
    get() = status in setOf(
      AutomationStatus.COMPLETED,
      AutomationStatus.STOPPED,
      AutomationStatus.ERROR,
    )
}

enum class ExecutionFinalStatus {
  COMPLETED,
  STOPPED_BY_USER,
  FAILED_SAFETY_CHECK,
  FAILED_ERROR,
}

enum class MessageTraceStatus {
  CONFIRMED,
  CANCELLED,
  FAILED,
}

/**
 * Traza individual por mensaje dentro de un lote ejecutado.
 */
data class MessageExecutionTrace(
  val index: Int,
  val messagePreview: String,
  val status: MessageTraceStatus,
  val timestampMillis: Long,
)

/**
 * Registro histórico local (sin contraseñas, sesiones ni credenciales de WhatsApp).
 */
data class ExecutionRecord(
  val id: Long = 0L,
  val batchCode: String = "",
  val timestampMillis: Long,
  val contactName: String,
  val contactPhone: String,
  val scheduledCount: Int,
  val confirmedCount: Int,
  val errorCount: Int,
  val finalStatus: ExecutionFinalStatus,
  val statusDetail: String = "",
  val durationMillis: Long = 0L,
  val averageIntervalSeconds: Double = 0.0,
  val modeLabel: String = "",
  val messageTraces: List<MessageExecutionTrace> = emptyList(),
) {
  val cancelledCount: Int
    get() = (scheduledCount - confirmedCount).coerceAtLeast(0)
}

/**
 * Preferencias de usuario persistidas con DataStore.
 */
data class AppSettings(
  val defaultMinIntervalSeconds: Int = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
  val defaultMaxIntervalSeconds: Int = SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS,
  val maxRepeatCount: Int = SafetyLimits.DEFAULT_MAX_REPEAT_COUNT,
  val preferWhatsAppBusiness: Boolean = false,
  val hapticFeedbackEnabled: Boolean = true,
  val keepScreenOnDuringAutomation: Boolean = true,
  val githubRepoOwner: String = "the3600",
  val githubRepoName: String = "WhatsApp-Repeater",
)

/**
 * Lista o plantilla de mensajes guardada por el usuario y conservada entre actualizaciones.
 */
data class SavedMessageList(
  val id: Long = 0L,
  val title: String,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val updatedAtMillis: Long,
)

/**
 * Última configuración de automatización (contacto, mensajes e intervalos) persistida localmente.
 */
data class PersistedAutomationDraft(
  val contactId: String?,
  val contactName: String?,
  val contactPhone: String?,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val updatedAtMillis: Long,
)
