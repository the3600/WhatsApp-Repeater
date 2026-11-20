package com.whatsapprepeater.app.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ActivityCategoryFilter(val label: String) {
  ALL("Todo"),
  SCHEDULES("Programaciones"),
  CONFIRMED("Confirmados"),
  FAILED("Fallidos"),
  INTERRUPTED("Interrumpidos"),
  PERMISSIONS("Permisos"),
}

enum class ActivityDateFilter(val label: String) {
  ALL("Todas las fechas"),
  TODAY("Hoy"),
  LAST_7_DAYS("7 días"),
  LAST_30_DAYS("30 días"),
}

enum class ActivityEntryKind {
  SCHEDULE_UPCOMING,
  SCHEDULE_PAUSED,
  SCHEDULE_FAILED,
  EXECUTION_CONFIRMED,
  EXECUTION_FAILED,
  EXECUTION_INTERRUPTED,
  AUTOREPLY_SENT,
  AUTOREPLY_FAILED,
  PERMISSION_ALERT,
}

data class ActivityEntry(
  val id: String,
  val kind: ActivityEntryKind,
  val title: String,
  val recipientName: String,
  val recipientPhone: String,
  val summary: String,
  val detail: String,
  val timestampMillis: Long,
  val badgeText: String,
  val executionRecordId: Long? = null,
  val scheduledTaskId: Long? = null,
)

data class ActivityCenterUiState(
  val entries: List<ActivityEntry> = emptyList(),
  val categoryFilter: ActivityCategoryFilter = ActivityCategoryFilter.ALL,
  val dateFilter: ActivityDateFilter = ActivityDateFilter.ALL,
  val recipientQuery: String = "",
  val totalConfirmedMessages: Int = 0,
  val totalFailedItems: Int = 0,
  val totalUpcomingSchedules: Int = 0,
  val totalPermissionAlerts: Int = 0,
)

class ActivityCenterViewModel(
  private val container: AppContainer,
  private val appContext: Context,
) : ViewModel() {

  private val categoryFilter = MutableStateFlow(ActivityCategoryFilter.ALL)
  private val dateFilter = MutableStateFlow(ActivityDateFilter.ALL)
  private val recipientQuery = MutableStateFlow("")
  private val permissionAlerts = MutableStateFlow<List<ActivityEntry>>(emptyList())

  private val dataFlow = combine(
    container.scheduledTaskRepository.observeTasks(),
    container.historyRepository.observeRecentExecutions(limit = 200),
    container.autoReplyRepository.observeHistory(limit = 100),
    permissionAlerts,
  ) { tasks, executions, autoLogs, permAlerts ->
    ActivityRawInputs(tasks, executions, autoLogs, permAlerts)
  }

  val uiState: StateFlow<ActivityCenterUiState> = combine(
    dataFlow,
    categoryFilter,
    dateFilter,
    recipientQuery,
  ) { raw, catFilter, dtFilter, query ->
    val now = System.currentTimeMillis()
    val allEntries = buildEntries(raw)

    val minTime = when (dtFilter) {
      ActivityDateFilter.ALL -> 0L
      ActivityDateFilter.TODAY -> now - 86_400_000L
      ActivityDateFilter.LAST_7_DAYS -> now - 7L * 86_400_000L
      ActivityDateFilter.LAST_30_DAYS -> now - 30L * 86_400_000L
    }

    val q = query.trim().lowercase()
    val filtered = allEntries.filter { entry ->
      val matchesCategory = when (catFilter) {
        ActivityCategoryFilter.ALL -> true
        ActivityCategoryFilter.SCHEDULES -> entry.kind in setOf(
          ActivityEntryKind.SCHEDULE_UPCOMING,
          ActivityEntryKind.SCHEDULE_PAUSED,
          ActivityEntryKind.SCHEDULE_FAILED,
        )
        ActivityCategoryFilter.CONFIRMED -> entry.kind in setOf(
          ActivityEntryKind.EXECUTION_CONFIRMED,
          ActivityEntryKind.AUTOREPLY_SENT,
        )
        ActivityCategoryFilter.FAILED -> entry.kind in setOf(
          ActivityEntryKind.SCHEDULE_FAILED,
          ActivityEntryKind.EXECUTION_FAILED,
          ActivityEntryKind.AUTOREPLY_FAILED,
        )
        ActivityCategoryFilter.INTERRUPTED -> entry.kind == ActivityEntryKind.EXECUTION_INTERRUPTED
        ActivityCategoryFilter.PERMISSIONS -> entry.kind == ActivityEntryKind.PERMISSION_ALERT
      }
      val matchesDate = dtFilter == ActivityDateFilter.ALL ||
        entry.kind == ActivityEntryKind.PERMISSION_ALERT ||
        entry.kind == ActivityEntryKind.SCHEDULE_UPCOMING ||
        entry.timestampMillis >= minTime
      val matchesRecipient = q.isEmpty() ||
        entry.recipientName.lowercase().contains(q) ||
        entry.recipientPhone.lowercase().contains(q) ||
        entry.title.lowercase().contains(q)
      matchesCategory && matchesDate && matchesRecipient
    }

    ActivityCenterUiState(
      entries = filtered,
      categoryFilter = catFilter,
      dateFilter = dtFilter,
      recipientQuery = query,
      totalConfirmedMessages = raw.executions.sumOf { it.confirmedCount },
      totalFailedItems = allEntries.count {
        it.kind in setOf(
          ActivityEntryKind.SCHEDULE_FAILED,
          ActivityEntryKind.EXECUTION_FAILED,
          ActivityEntryKind.AUTOREPLY_FAILED,
        )
      },
      totalUpcomingSchedules = raw.tasks.count {
        it.enabled &&
          it.status != ScheduledTaskStatus.PAUSED &&
          it.status != ScheduledTaskStatus.COMPLETED &&
          it.status != ScheduledTaskStatus.FAILED &&
          it.status != ScheduledTaskStatus.EXPIRED
      },
      totalPermissionAlerts = raw.permissionAlerts.size,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = ActivityCenterUiState(),
  )

  private data class ActivityRawInputs(
    val tasks: List<ScheduledTask>,
    val executions: List<ExecutionRecord>,
    val autoLogs: List<AutoReplyLogEntry>,
    val permissionAlerts: List<ActivityEntry>,
  )

  init {
    refreshPermissionAlerts()
  }

  fun refreshPermissionAlerts() {
    val now = System.currentTimeMillis()
    val alerts = mutableListOf<ActivityEntry>()

    val a11yReady = container.accessibilityDriver.isServiceConnected.value ||
      AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(appContext)
    if (!a11yReady) {
      alerts.add(
        ActivityEntry(
          id = "perm_a11y",
          kind = ActivityEntryKind.PERMISSION_ALERT,
          title = "Servicio de Accesibilidad inactivo",
          recipientName = "Sistema Android",
          recipientPhone = "",
          summary = "Requerido para enviar mensajes en WhatsApp.",
          detail = "Activa WhatsApp Repeater en Ajustes de Accesibilidad antes de iniciar envíos.",
          timestampMillis = now,
          badgeText = "Permiso requerido",
        ),
      )
    }

    val notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    if (!notifGranted) {
      alerts.add(
        ActivityEntry(
          id = "perm_notif",
          kind = ActivityEntryKind.PERMISSION_ALERT,
          title = "Notificaciones desactivadas",
          recipientName = "Sistema Android",
          recipientPhone = "",
          summary = "No se mostrará la cuenta regresiva ni la alerta de parada de emergencia.",
          detail = "Concede el permiso de notificaciones en Diagnóstico o Ajustes de la app.",
          timestampMillis = now,
          badgeText = "Aviso",
        ),
      )
    }

    if (!container.schedulerAlarmHelper.canScheduleExactAlarms()) {
      alerts.add(
        ActivityEntry(
          id = "perm_exact_alarm",
          kind = ActivityEntryKind.PERMISSION_ALERT,
          title = "Alarmas exactas sin autorizar",
          recipientName = "Programador",
          recipientPhone = "",
          summary = "Android puede retrasar las programaciones hasta varios minutos.",
          detail = "Autoriza 'Alarmas y recordatorios' en el Programador o Diagnóstico para puntualidad exacta.",
          timestampMillis = now,
          badgeText = "Recomendado",
        ),
      )
    }

    val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: false
    if (!ignoringBattery) {
      alerts.add(
        ActivityEntry(
          id = "perm_battery",
          kind = ActivityEntryKind.PERMISSION_ALERT,
          title = "Ahorro de batería activo",
          recipientName = "HyperOS / Android",
          recipientPhone = "",
          summary = "El sistema puede suspender servicios en segundo plano.",
          detail = "Configura 'Sin restricciones' en Info de la app → Batería.",
          timestampMillis = now,
          badgeText = "Batería",
        ),
      )
    }

    permissionAlerts.value = alerts
  }

  fun setCategoryFilter(filter: ActivityCategoryFilter) {
    categoryFilter.value = filter
  }

  fun setDateFilter(filter: ActivityDateFilter) {
    dateFilter.value = filter
  }

  fun onRecipientQueryChanged(query: String) {
    recipientQuery.value = query
  }

  private fun buildEntries(raw: ActivityRawInputs): List<ActivityEntry> {
    val result = mutableListOf<ActivityEntry>()
    result.addAll(raw.permissionAlerts)

    raw.tasks.forEach { task ->
      val kind = when {
        task.status == ScheduledTaskStatus.FAILED || task.status == ScheduledTaskStatus.EXPIRED ->
          ActivityEntryKind.SCHEDULE_FAILED
        task.status == ScheduledTaskStatus.PAUSED || task.status == ScheduledTaskStatus.SKIPPED_VACATION || !task.enabled ->
          ActivityEntryKind.SCHEDULE_PAUSED
        else -> ActivityEntryKind.SCHEDULE_UPCOMING
      }
      val typeLabel = if (task.isMultipleMode) "Secuencia (${task.totalMessagesCount} msg)" else "Único (${task.repeatCount}x)"
      result.add(
        ActivityEntry(
          id = "sched_${task.id}",
          kind = kind,
          title = "Programación: ${task.title}",
          recipientName = task.contactName,
          recipientPhone = task.contactPhone,
          summary = "$typeLabel • ${task.recurrence.label}",
          detail = task.lastFailureReason.ifBlank { task.primaryPreview.ifBlank { "Programación configurada" } },
          timestampMillis = task.triggerAtMillis,
          badgeText = task.status.label,
          scheduledTaskId = task.id,
        ),
      )
    }

    raw.executions.forEach { rec ->
      val (kind, badge) = when (rec.finalStatus) {
        ExecutionFinalStatus.COMPLETED -> ActivityEntryKind.EXECUTION_CONFIRMED to "Confirmado"
        ExecutionFinalStatus.STOPPED_BY_USER -> ActivityEntryKind.EXECUTION_INTERRUPTED to "Interrumpido"
        ExecutionFinalStatus.FAILED_SAFETY_CHECK, ExecutionFinalStatus.FAILED_ERROR ->
          ActivityEntryKind.EXECUTION_FAILED to "Fallido"
      }
      result.add(
        ActivityEntry(
          id = "exec_${rec.id}",
          kind = kind,
          title = rec.modeLabel.ifBlank { "Envío Motor 1" },
          recipientName = rec.contactName,
          recipientPhone = rec.contactPhone,
          summary = "${rec.confirmedCount}/${rec.scheduledCount} confirmados • Errores: ${rec.errorCount}",
          detail = rec.statusDetail,
          timestampMillis = rec.timestampMillis,
          badgeText = badge,
          executionRecordId = rec.id,
        ),
      )
    }

    raw.autoLogs.forEach { log ->
      if (log.status == AutoReplyLogStatus.SENT || log.status == AutoReplyLogStatus.FAILED_SEND) {
        val sent = log.status == AutoReplyLogStatus.SENT
        result.add(
          ActivityEntry(
            id = "auto_${log.id}",
            kind = if (sent) ActivityEntryKind.AUTOREPLY_SENT else ActivityEntryKind.AUTOREPLY_FAILED,
            title = "Autorespuesta: ${log.matchedRuleName}",
            recipientName = log.chatIdentifier,
            recipientPhone = "",
            summary = if (sent) "Enviada por RemoteInput" else "Fallo de respuesta automática",
            detail = log.detail,
            timestampMillis = log.timestampMillis,
            badgeText = if (sent) "Respondida" else "Error Motor 2",
          ),
        )
      }
    }

    return result.sortedWith(
      compareBy<ActivityEntry> {
        when (it.kind) {
          ActivityEntryKind.PERMISSION_ALERT -> 0
          ActivityEntryKind.SCHEDULE_UPCOMING -> 1
          else -> 2
        }
      }.thenByDescending { it.timestampMillis },
    )
  }

  companion object {
    fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          ActivityCenterViewModel(container, context.applicationContext) as T
      }
  }
}
