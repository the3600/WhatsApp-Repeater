package com.whatsapprepeater.core.model

enum class DiagnosticStatus {
  OK,
  WARNING,
  ACTION_REQUIRED,
}

enum class DiagnosticActionType {
  NONE,
  OPEN_ACCESSIBILITY,
  OPEN_NOTIFICATION_LISTENER,
  REQUEST_POST_NOTIFICATIONS,
  REQUEST_CONTACTS,
  OPEN_BATTERY_SETTINGS,
  OPEN_UNKNOWN_SOURCES,
  OPEN_UPDATES,
  OPEN_EDITOR,
}

data class DiagnosticItem(
  val id: String,
  val title: String,
  val detail: String,
  val recommendation: String?,
  val status: DiagnosticStatus,
  val actionType: DiagnosticActionType = DiagnosticActionType.NONE,
  val actionLabel: String? = null,
)

data class TemplateUsageStat(
  val title: String,
  val category: String,
  val useCount: Int,
)

/**
 * Estadísticas estrictamente locales y agregadas; nunca almacena contenido de mensajes.
 */
data class LocalAnalyticsSummary(
  val totalMotor1Runs: Int = 0,
  val completedMotor1Runs: Int = 0,
  val interruptedMotor1Runs: Int = 0,
  val errorMotor1Runs: Int = 0,
  val totalMessagesScheduled: Int = 0,
  val totalMessagesConfirmed: Int = 0,
  val totalMessagesCancelled: Int = 0,
  val totalMotor2Sent: Int = 0,
  val totalMotor2Skipped: Int = 0,
  val totalMotor2Failed: Int = 0,
  val pendingSchedules: Int = 0,
  val completedSchedules: Int = 0,
  val missedOrFailedSchedules: Int = 0,
  val topTemplates: List<TemplateUsageStat> = emptyList(),
) {
  val deliveryRatePercent: Int
    get() = if (totalMessagesScheduled <= 0) {
      100
    } else {
      ((totalMessagesConfirmed * 100) / totalMessagesScheduled).coerceIn(0, 100)
    }
}
