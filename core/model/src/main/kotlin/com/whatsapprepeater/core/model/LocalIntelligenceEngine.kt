package com.whatsapprepeater.core.model

import kotlin.math.abs

enum class RecommendationSeverity(val priorityOrder: Int) {
  CRITICAL(0),
  WARNING(1),
  INFO(2),
}

enum class RecommendationAction {
  OPEN_ACCESSIBILITY,
  OPEN_SCHEDULER,
  OPEN_BATTERY,
  OPEN_EXACT_ALARMS,
  OPEN_NOTIFICATIONS,
  OPEN_EDITOR,
  OPEN_SECURITY_CENTER,
  OPEN_DIAGNOSTICS,
  NONE,
}

data class SmartRecommendation(
  val id: String,
  val title: String,
  val message: String,
  val severity: RecommendationSeverity,
  val action: RecommendationAction,
  val actionLabel: String? = null,
)

data class IntelligenceSnapshot(
  val isAccessibilityReady: Boolean,
  val isWhatsAppInstalled: Boolean,
  val isIgnoringBatteryOptimizations: Boolean,
  val canScheduleExactAlarms: Boolean,
  val postNotificationsGranted: Boolean,
  val vacationPauseEnabled: Boolean,
  val activeSchedules: List<ScheduledTask>,
  val recentExecutions: List<ExecutionRecord>,
  val savedTemplates: List<SavedMessageList>,
  val draftContact: ContactInfo? = null,
  val draftMessages: List<String> = emptyList(),
  val draftMinIntervalSeconds: Int = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
  val isHyperOsOrXiaomi: Boolean = false,
)

/**
 * Motor determinista 100% local (sin red ni servicios externos) que analiza el estado real de
 * permisos, programaciones, borrador e historial para prevenir errores antes de que ocurran.
 */
object LocalIntelligenceEngine {

  private const val OVERLAP_WINDOW_MS = 3L * 60L * 1000L // 3 minutos
  private const val DUPLICATE_WINDOW_MS = 30L * 60L * 1000L // 30 minutos

  fun analyze(snapshot: IntelligenceSnapshot): List<SmartRecommendation> {
    val recs = mutableListOf<SmartRecommendation>()
    val activeSchedules = snapshot.activeSchedules.filter {
      it.enabled &&
        it.status != ScheduledTaskStatus.COMPLETED &&
        it.status != ScheduledTaskStatus.FAILED &&
        it.status != ScheduledTaskStatus.EXPIRED &&
        it.status != ScheduledTaskStatus.PAUSED
    }.sortedBy { it.triggerAtMillis }

    // 1. WhatsApp no instalado
    if (!snapshot.isWhatsAppInstalled) {
      recs.add(
        SmartRecommendation(
          id = "whatsapp_missing",
          title = "WhatsApp no detectado",
          message = "No se encontró WhatsApp ni WhatsApp Business instalado en este dispositivo.",
          severity = RecommendationSeverity.CRITICAL,
          action = RecommendationAction.OPEN_DIAGNOSTICS,
          actionLabel = "Ver diagnóstico",
        ),
      )
    }

    // 2. Programaciones activas sin Accesibilidad activa
    if (activeSchedules.isNotEmpty() && !snapshot.isAccessibilityReady) {
      recs.add(
        SmartRecommendation(
          id = "schedules_without_accessibility",
          title = "Programaciones bloqueadas sin Accesibilidad",
          message = "Tienes ${activeSchedules.size} envío(s) programado(s) activo(s), pero el Servicio de Accesibilidad está inactivo y no podrán ejecutarse.",
          severity = RecommendationSeverity.CRITICAL,
          action = RecommendationAction.OPEN_ACCESSIBILITY,
          actionLabel = "Activar Accesibilidad",
        ),
      )
    }

    // 3. Variables dinámicas sin resolver en borrador
    val nonBlankDraftMsgs = snapshot.draftMessages.filter { it.isNotBlank() }
    if (nonBlankDraftMsgs.isNotEmpty()) {
      val validationError = DynamicVariablesEngine.validateBatch(nonBlankDraftMsgs, snapshot.draftContact).second
      if (validationError != null) {
        recs.add(
          SmartRecommendation(
            id = "draft_unresolved_variables",
            title = "Variables sin resolver en tu borrador",
            message = validationError.reason,
            severity = RecommendationSeverity.WARNING,
            action = RecommendationAction.OPEN_EDITOR,
            actionLabel = "Revisar mensajes",
          ),
        )
      }
    }

    // 4. Posible duplicado accidental (mismo teléfono + mismo contenido en <= 30 min)
    var duplicatePair: Pair<ScheduledTask, ScheduledTask>? = null
    var overlapPair: Pair<ScheduledTask, ScheduledTask>? = null
    for (i in 0 until activeSchedules.size) {
      for (j in i + 1 until activeSchedules.size) {
        val a = activeSchedules[i]
        val b = activeSchedules[j]
        val diff = abs(b.triggerAtMillis - a.triggerAtMillis)
        if (duplicatePair == null && diff <= DUPLICATE_WINDOW_MS && isSameContentAndRecipient(a, b)) {
          duplicatePair = a to b
        } else if (overlapPair == null && diff <= OVERLAP_WINDOW_MS) {
          overlapPair = a to b
        }
      }
    }

    duplicatePair?.let { (a, b) ->
      recs.add(
        SmartRecommendation(
          id = "duplicate_schedules",
          title = "Posible programación duplicada",
          message = "\"${a.title}\" y \"${b.title}\" apuntan al mismo destinatario con el mismo contenido en menos de 30 minutos.",
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_SCHEDULER,
          actionLabel = "Revisar duplicados",
        ),
      )
    }

    overlapPair?.let { (a, b) ->
      recs.add(
        SmartRecommendation(
          id = "overlapping_schedules",
          title = "Horarios demasiado cercanos",
          message = "\"${a.title}\" y \"${b.title}\" están separados por menos de 3 minutos; podrían competir al abrir WhatsApp.",
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_SCHEDULER,
          actionLabel = "Ajustar horarios",
        ),
      )
    }

    // 5. Fallo reciente de verificación de destinatario
    val recentSafetyFailure = snapshot.recentExecutions
      .take(3)
      .firstOrNull { it.finalStatus == ExecutionFinalStatus.FAILED_SAFETY_CHECK }
    if (recentSafetyFailure != null) {
      recs.add(
        SmartRecommendation(
          id = "recent_safety_failure",
          title = "Verificación de chat detenida recientemente",
          message = "El envío a ${recentSafetyFailure.contactName} se detuvo por seguridad al no coincidir la cabecera del chat. Revisa que el nombre o número coincida con WhatsApp.",
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_DIAGNOSTICS,
          actionLabel = "Abrir diagnóstico",
        ),
      )
    }

    // 6. Restricción de batería (crítico en HyperOS / POCO o con programaciones)
    if (!snapshot.isIgnoringBatteryOptimizations && (activeSchedules.isNotEmpty() || snapshot.isHyperOsOrXiaomi)) {
      recs.add(
        SmartRecommendation(
          id = "battery_optimization_risk",
          title = if (snapshot.isHyperOsOrXiaomi) {
            "HyperOS / POCO restringe la batería"
          } else {
            "Ahorro de batería activo"
          },
          message = if (snapshot.isHyperOsOrXiaomi) {
            "Configura 'Sin restricciones' e 'Inicio automático' para evitar que HyperOS detenga Accesibilidad o retrase tus programaciones."
          } else {
            "La optimización de batería puede retrasar alarmas o desconectar el servicio en segundo plano."
          },
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_BATTERY,
          actionLabel = "Ajustar batería",
        ),
      )
    }

    // 7. Alarmas exactas no autorizadas teniendo programaciones
    if (activeSchedules.isNotEmpty() && !snapshot.canScheduleExactAlarms) {
      recs.add(
        SmartRecommendation(
          id = "exact_alarms_missing",
          title = "Alarmas exactas desactivadas",
          message = "Sin el permiso de alarmas exactas, Android puede retrasar tus ${activeSchedules.size} programación(es) activas.",
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_EXACT_ALARMS,
          actionLabel = "Autorizar alarmas",
        ),
      )
    }

    // 8. Notificaciones bloqueadas teniendo programaciones
    if (activeSchedules.isNotEmpty() && !snapshot.postNotificationsGranted) {
      recs.add(
        SmartRecommendation(
          id = "notifications_blocked",
          title = "Notificaciones de control bloqueadas",
          message = "Permite notificaciones para ver la cuenta regresiva en vivo y los botones rápidos de pausa o cancelación.",
          severity = RecommendationSeverity.WARNING,
          action = RecommendationAction.OPEN_NOTIFICATIONS,
          actionLabel = "Permitir avisos",
        ),
      )
    }

    // 9. Modo vacaciones pausando programaciones activas
    if (snapshot.vacationPauseEnabled && activeSchedules.isNotEmpty()) {
      recs.add(
        SmartRecommendation(
          id = "vacation_pause_active",
          title = "Modo vacaciones activo",
          message = "Hay ${activeSchedules.size} programación(es) retenidas mientras el modo vacaciones permanezca activado.",
          severity = RecommendationSeverity.INFO,
          action = RecommendationAction.OPEN_SCHEDULER,
          actionLabel = "Ir al Programador",
        ),
      )
    }

    // 10. Intervalo demasiado corto para lotes grandes
    if (nonBlankDraftMsgs.size >= 5 && snapshot.draftMinIntervalSeconds < 10) {
      recs.add(
        SmartRecommendation(
          id = "aggressive_interval_tip",
          title = "Intervalo corto para un lote de ${nonBlankDraftMsgs.size} mensajes",
          message = "Tu pausa mínima actual (${snapshot.draftMinIntervalSeconds}s) es ajustada. Usar 12s–30s reduce fallos de renderizado en WhatsApp.",
          severity = RecommendationSeverity.INFO,
          action = RecommendationAction.OPEN_EDITOR,
          actionLabel = "Ajustar borrador",
        ),
      )
    }

    // 11. Sugerencia de biblioteca si el usuario ya ejecutó varios lotes pero no guarda plantillas
    if (snapshot.savedTemplates.isEmpty() && snapshot.recentExecutions.size >= 2) {
      recs.add(
        SmartRecommendation(
          id = "library_reuse_tip",
          title = "Ahorra tiempo con la Biblioteca",
          message = "Guarda tus mensajes frecuentes como plantillas para reutilizarlos con un toque en el editor y en el Programador.",
          severity = RecommendationSeverity.INFO,
          action = RecommendationAction.NONE,
        ),
      )
    }

    return recs.sortedBy { it.severity.priorityOrder }
  }

  /**
   * Agrupa errores reales desde historial de Motor 1, Motor 2 y Programador para las analíticas locales.
   */
  fun groupFrequentErrors(
    motor1History: List<ExecutionRecord>,
    motor2History: List<AutoReplyLogEntry>,
    schedules: List<ScheduledTask>,
    limit: Int = 5,
  ): List<FrequentErrorStat> {
    val rawReasons = mutableListOf<String>()

    motor1History.forEach { rec ->
      if (rec.finalStatus == ExecutionFinalStatus.FAILED_ERROR || rec.finalStatus == ExecutionFinalStatus.FAILED_SAFETY_CHECK) {
        val label = rec.statusDetail.trim().take(90).ifBlank {
          if (rec.finalStatus == ExecutionFinalStatus.FAILED_SAFETY_CHECK) {
            "Verificación de destinatario detenida por seguridad"
          } else {
            "Error en ejecución de Motor 1"
          }
        }
        rawReasons.add(label)
      }
    }

    motor2History.forEach { log ->
      if (log.status == AutoReplyLogStatus.FAILED_SEND) {
        val label = log.detail.trim().take(90).ifBlank { "Error al enviar autorespuesta (Motor 2)" }
        rawReasons.add(label)
      }
    }

    schedules.forEach { task ->
      if (task.status == ScheduledTaskStatus.FAILED || task.status == ScheduledTaskStatus.EXPIRED) {
        val label = task.lastFailureReason.trim().take(90).takeIf { it.isNotBlank() }
          ?: if (task.status == ScheduledTaskStatus.EXPIRED) {
            "Programación vencida por fecha límite"
          } else {
            "Programación fallida al disparar"
          }
        rawReasons.add(label)
      }
    }

    return rawReasons
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .take(limit)
      .map { FrequentErrorStat(reason = it.key, count = it.value) }
  }

  private fun isSameContentAndRecipient(a: ScheduledTask, b: ScheduledTask): Boolean {
    val phoneA = a.contactPhone.filter { it.isDigit() }
    val phoneB = b.contactPhone.filter { it.isDigit() }
    val sameRecipient = (phoneA.isNotBlank() && phoneA == phoneB) ||
      a.contactName.trim().equals(b.contactName.trim(), ignoreCase = true)
    if (!sameRecipient) return false
    return a.isMultipleMode == b.isMultipleMode &&
      a.singleMessage.trim().equals(b.singleMessage.trim(), ignoreCase = true) &&
      a.messages.map { it.trim() } == b.messages.map { it.trim() }
  }
}
