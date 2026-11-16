package com.whatsapprepeater.core.model

/**
 * Estrategia de coincidencia 100 % local para el motor de autorespuestas.
 */
enum class RuleMatchType {
  EXACT,
  CONTAINS_WORD,
  CONTAINS_PHRASE,
  CONTAINS_ALL_WORDS,
  REGEX,
}

/**
 * Aplicación objetivo para una regla o para el autorespondedor.
 */
enum class AutoReplyTargetApp {
  WHATSAPP_ONLY,
  WHATSAPP_BUSINESS_ONLY,
  BOTH,
}

/**
 * Filtro de remitente / conversación para aplicar autorespuestas.
 */
enum class AutoReplyContactFilterMode {
  ALL,
  SAVED_CONTACTS_ONLY,
  UNKNOWN_NUMBERS_ONLY,
  SELECTED_CONTACTS,
  GROUPS_ONLY,
}

/**
 * Franja horaria semanal (1 = Lunes .. 7 = Domingo). Soporta tramos nocturnos (ej. 22:00..07:00).
 */
data class AutoReplySchedule(
  val enabled: Boolean = false,
  val startMinutesOfDay: Int = 9 * 60,
  val endMinutesOfDay: Int = 18 * 60,
  val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
)

/**
 * Regla local de autorespuesta.
 */
data class AutoReplyRule(
  val id: Long = 0L,
  val name: String,
  val pattern: String,
  val matchType: RuleMatchType = RuleMatchType.CONTAINS_PHRASE,
  val caseSensitive: Boolean = false,
  val responseTemplate: String,
  val enabled: Boolean = true,
  val priority: Int = 10,
  val targetApp: AutoReplyTargetApp = AutoReplyTargetApp.BOTH,
  val contactFilterMode: AutoReplyContactFilterMode = AutoReplyContactFilterMode.ALL,
  val allowGroups: Boolean = false,
  val selectedContactNamesOrPhones: List<String> = emptyList(),
  val excludedIdentifiers: List<String> = emptyList(),
  val schedule: AutoReplySchedule = AutoReplySchedule(),
  val minIntervalSeconds: Int = 30,
  val maxRepliesPerConversation: Int = 5,
  val delaySeconds: Int = 0,
  val updatedAtMillis: Long = 0L,
)

/**
 * Configuración global del Motor 2 (Autorespuestas por notificaciones).
 */
data class AutoReplySettings(
  val masterEnabled: Boolean = false,
  val targetApp: AutoReplyTargetApp = AutoReplyTargetApp.BOTH,
  val globalContactFilterMode: AutoReplyContactFilterMode = AutoReplyContactFilterMode.ALL,
  val allowGroupsByDefault: Boolean = false,
  val selectedContactNamesOrPhones: List<String> = emptyList(),
  val excludedIdentifiers: List<String> = emptyList(),
  val globalSchedule: AutoReplySchedule = AutoReplySchedule(enabled = false),
  val welcomeEnabled: Boolean = false,
  val welcomeMessage: String = "Hola {nombre}, gracias por escribir. En un momento te respondo.",
  val welcomeCooldownMinutes: Int = 12 * 60,
  val awayEnabled: Boolean = false,
  val awayMessage: String = "En este momento no estoy disponible. Te responderé en cuanto pueda.",
  val awaySchedule: AutoReplySchedule = AutoReplySchedule(
    enabled = true,
    startMinutesOfDay = 20 * 60,
    endMinutesOfDay = 8 * 60,
  ),
  val minIntervalSeconds: Int = 20,
  val maxRepliesPerConversation: Int = 5,
  val preventLoopsEnabled: Boolean = true,
  val saveHistoryEnabled: Boolean = false,
  val saveIncomingPreviewEnabled: Boolean = false,
)

/**
 * Representación agnóstica de una notificación entrante de mensajería.
 */
data class IncomingNotificationEvent(
  val notificationKey: String,
  val packageName: String,
  val conversationTitle: String,
  val senderName: String?,
  val messageText: String,
  val isGroupConversation: Boolean,
  val isGroupSummary: Boolean,
  val isSelfMessage: Boolean,
  val hasReplyAction: Boolean,
  val timestampMillis: Long,
)

enum class AutoReplyLogStatus {
  SENT,
  SKIPPED_NO_REMOTE_INPUT,
  SKIPPED_FILTER,
  SKIPPED_SCHEDULE,
  SKIPPED_COOLDOWN,
  SKIPPED_LIMIT,
  SKIPPED_LOOP_GUARD,
  SKIPPED_NO_MATCH,
  FAILED_SEND,
}

data class AutoReplyLogEntry(
  val id: Long = 0L,
  val timestampMillis: Long,
  val packageName: String,
  val chatIdentifier: String,
  val matchedRuleName: String,
  val responseSent: String,
  val incomingPreview: String = "",
  val status: AutoReplyLogStatus,
  val detail: String = "",
)

sealed interface AutoReplyDecision {
  data class Reply(
    val ruleId: Long?,
    val ruleName: String,
    val responseText: String,
    val delaySeconds: Int,
    val conversationKey: String,
  ) : AutoReplyDecision

  data class Skip(
    val status: AutoReplyLogStatus,
    val reason: String,
    val conversationKey: String,
  ) : AutoReplyDecision
}

