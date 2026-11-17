package com.whatsapprepeater.app.ui.insights

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.DiagnosticActionType
import com.whatsapprepeater.core.model.DiagnosticItem
import com.whatsapprepeater.core.model.DiagnosticStatus
import com.whatsapprepeater.core.model.DynamicVariablesEngine
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.LocalAnalyticsSummary
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.TemplateUsageStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InsightsViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val _diagnostics = MutableStateFlow<List<DiagnosticItem>>(emptyList())
  val diagnostics: StateFlow<List<DiagnosticItem>> = _diagnostics.asStateFlow()

  private val _isXiaomiHyperOsDevice = MutableStateFlow(isXiaomiOrPocoDevice())
  val isXiaomiHyperOsDevice: StateFlow<Boolean> = _isXiaomiHyperOsDevice.asStateFlow()

  private val _isCheckingConnectivity = MutableStateFlow(false)
  val isCheckingConnectivity: StateFlow<Boolean> = _isCheckingConnectivity.asStateFlow()

  private var lastConnectivityStatus: DiagnosticItem? = null

  val analytics: StateFlow<LocalAnalyticsSummary> = combine(
    container.historyRepository.observeRecentExecutions(limit = 200),
    container.autoReplyRepository.observeHistory(limit = 200),
    container.savedConfigRepository.observeSavedMessageLists(),
    container.scheduledTaskRepository.observeTasks(),
  ) { motor1History, motor2History, templates, schedules ->
    LocalAnalyticsSummary(
      totalMotor1Runs = motor1History.size,
      completedMotor1Runs = motor1History.count { it.finalStatus == ExecutionFinalStatus.COMPLETED },
      interruptedMotor1Runs = motor1History.count { it.finalStatus == ExecutionFinalStatus.STOPPED_BY_USER },
      errorMotor1Runs = motor1History.count {
        it.finalStatus == ExecutionFinalStatus.FAILED_ERROR ||
          it.finalStatus == ExecutionFinalStatus.FAILED_SAFETY_CHECK
      },
      totalMessagesScheduled = motor1History.sumOf { it.scheduledCount },
      totalMessagesConfirmed = motor1History.sumOf { it.confirmedCount },
      totalMessagesCancelled = motor1History.sumOf { it.cancelledCount },
      totalMotor2Sent = motor2History.count { it.status == AutoReplyLogStatus.SENT },
      totalMotor2Skipped = motor2History.count {
        it.status != AutoReplyLogStatus.SENT && it.status != AutoReplyLogStatus.FAILED_SEND
      },
      totalMotor2Failed = motor2History.count { it.status == AutoReplyLogStatus.FAILED_SEND },
      pendingSchedules = schedules.count { it.enabled && it.status == ScheduledTaskStatus.PENDING },
      completedSchedules = schedules.count { it.status == ScheduledTaskStatus.COMPLETED },
      missedOrFailedSchedules = schedules.count {
        it.status == ScheduledTaskStatus.FAILED || it.status == ScheduledTaskStatus.EXPIRED
      },
      topTemplates = templates
        .filter { it.useCount > 0 }
        .sortedByDescending { it.useCount }
        .take(5)
        .map { TemplateUsageStat(title = it.title, category = it.category, useCount = it.useCount) },
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = LocalAnalyticsSummary(),
  )

  fun runDiagnostics(context: Context) {
    val items = mutableListOf<DiagnosticItem>()

    val a11yConnected = container.accessibilityDriver.isServiceConnected.value
    val a11yEnabled = AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(context)
    items.add(
      when {
        a11yConnected -> DiagnosticItem(
          id = "a11y",
          title = "Servicio de Accesibilidad (Motor 1)",
          detail = "Conectado y operativo.",
          recommendation = null,
          status = DiagnosticStatus.OK,
        )
        a11yEnabled -> DiagnosticItem(
          id = "a11y",
          title = "Servicio de Accesibilidad (Motor 1)",
          detail = "Habilitado en Ajustes, pero el enlace aún no está activo.",
          recommendation = "Desactiva y vuelve a activar el servicio si HyperOS lo suspendió tras actualizar.",
          status = DiagnosticStatus.WARNING,
          actionType = DiagnosticActionType.OPEN_ACCESSIBILITY,
          actionLabel = "Abrir Accesibilidad",
        )
        else -> DiagnosticItem(
          id = "a11y",
          title = "Servicio de Accesibilidad (Motor 1)",
          detail = "Desactivado en el sistema.",
          recommendation = "El servicio de accesibilidad está desactivado. Actívalo para utilizar el Motor 1.",
          status = DiagnosticStatus.ACTION_REQUIRED,
          actionType = DiagnosticActionType.OPEN_ACCESSIBILITY,
          actionLabel = "Activar servicio",
        )
      },
    )

    val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
      .contains(context.packageName)
    items.add(
      DiagnosticItem(
        id = "notif_listener",
        title = "Acceso a Notificaciones (Motor 2)",
        detail = if (listenerEnabled) "Concedido para WhatsApp y WhatsApp Business." else "Sin acceso a notificaciones.",
        recommendation = if (listenerEnabled) null else "Otorga acceso a notificaciones para usar respuestas automáticas (Motor 2).",
        status = if (listenerEnabled) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
        actionType = if (listenerEnabled) DiagnosticActionType.NONE else DiagnosticActionType.OPEN_NOTIFICATION_LISTENER,
        actionLabel = if (listenerEnabled) null else "Configurar acceso",
      ),
    )

    val postNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    items.add(
      DiagnosticItem(
        id = "post_notif",
        title = "Notificaciones de control y recordatorios",
        detail = if (postNotificationsGranted) "Permiso concedido." else "Bloqueadas por Android.",
        recommendation = if (postNotificationsGranted) null else "Permite notificaciones para ver el botón de parada de emergencia y recordatorios del Programador.",
        status = if (postNotificationsGranted) DiagnosticStatus.OK else DiagnosticStatus.ACTION_REQUIRED,
        actionType = if (postNotificationsGranted) DiagnosticActionType.NONE else DiagnosticActionType.REQUEST_POST_NOTIFICATIONS,
        actionLabel = if (postNotificationsGranted) null else "Permitir notificaciones",
      ),
    )

    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    items.add(
      DiagnosticItem(
        id = "contacts",
        title = "Permiso de Contactos",
        detail = if (contactsGranted) "Agenda sincronizable." else "No concedido (puedes usar ingreso manual o filtro básico).",
        recommendation = if (contactsGranted) null else "Concede acceso si deseas seleccionar contactos desde tu agenda o filtrar contactos guardados en Motor 2.",
        status = if (contactsGranted) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
        actionType = if (contactsGranted) DiagnosticActionType.NONE else DiagnosticActionType.REQUEST_CONTACTS,
        actionLabel = if (contactsGranted) null else "Conceder",
      ),
    )

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    items.add(
      DiagnosticItem(
        id = "battery",
        title = "Optimización de batería",
        detail = if (ignoringBattery) "Sin restricciones activas." else "El sistema restringe procesos en segundo plano.",
        recommendation = if (ignoringBattery) null else "En POCO / HyperOS configura 'Sin restricciones' e Inicio automático para evitar que el sistema cierre los servicios.",
        status = if (ignoringBattery) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
        actionType = if (ignoringBattery) DiagnosticActionType.NONE else DiagnosticActionType.OPEN_BATTERY_SETTINGS,
        actionLabel = if (ignoringBattery) null else "Ajustar batería",
      ),
    )

    val waInstalled = container.accessibilityDriver.isWhatsAppInstalled()
    items.add(
      DiagnosticItem(
        id = "whatsapp",
        title = "WhatsApp / WhatsApp Business",
        detail = if (waInstalled) "Aplicación objetivo detectada." else "No se detectó WhatsApp ni WhatsApp Business instalado.",
        recommendation = if (waInstalled) null else "Instala WhatsApp o WhatsApp Business para ejecutar automatizaciones.",
        status = if (waInstalled) DiagnosticStatus.OK else DiagnosticStatus.ACTION_REQUIRED,
      ),
    )

    val canInstallUpdates = container.updateManager.canRequestPackageInstalls()
    items.add(
      DiagnosticItem(
        id = "updater",
        title = "Actualizador local y permisos de instalación",
        detail = if (canInstallUpdates) {
          "Instalación de paquetes verificados habilitada."
        } else {
          "Falta permiso 'Instalar aplicaciones desconocidas'."
        },
        recommendation = if (canInstallUpdates) null else "Autoriza esta fuente para aplicar actualizaciones firmadas desde GitHub Releases.",
        status = if (canInstallUpdates) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
        actionType = if (canInstallUpdates) DiagnosticActionType.NONE else DiagnosticActionType.OPEN_UNKNOWN_SOURCES,
        actionLabel = if (canInstallUpdates) null else "Autorizar fuente",
      ),
    )

    val freeMb = runCatching {
      val stat = StatFs(context.filesDir.absolutePath)
      (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
    }.getOrDefault(0L)
    val dbKb = container.getDatabaseSizeBytes() / 1024L
    val lowStorage = freeMb in 1..149
    items.add(
      DiagnosticItem(
        id = "storage",
        title = "Almacenamiento e integridad local",
        detail = "Espacio libre: ${freeMb} MB • Base Room: ${dbKb} KB.",
        recommendation = if (lowStorage) "Libera al menos 150 MB para evitar fallos de caché o descarga de actualizaciones." else null,
        status = if (lowStorage) DiagnosticStatus.WARNING else DiagnosticStatus.OK,
      ),
    )

    val draft = container.draft.value
    val activeMsgs = if (draft.isMultipleMode) draft.multipleMessages else listOfNotNull(draft.singleMessage.takeIf { it.isNotBlank() })
    val varIssue = if (activeMsgs.isNotEmpty()) {
      DynamicVariablesEngine.validateBatch(activeMsgs, draft.selectedContact).second?.reason
    } else {
      null
    }
    val intervalIssue = draft.toIntervalRange().validate()
    val configIssue = varIssue ?: intervalIssue
    items.add(
      DiagnosticItem(
        id = "config",
        title = "Integridad de configuración activa",
        detail = configIssue ?: "Borrador activo e intervalos dentro de rangos válidos.",
        recommendation = configIssue,
        status = if (configIssue == null) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
        actionType = if (configIssue == null) DiagnosticActionType.NONE else DiagnosticActionType.OPEN_EDITOR,
        actionLabel = if (configIssue == null) null else "Revisar borrador",
      ),
    )

    lastConnectivityStatus?.let { items.add(it) }

    _diagnostics.value = items
  }

  fun checkUpdaterConnectivity(context: Context) {
    if (_isCheckingConnectivity.value) return
    viewModelScope.launch {
      _isCheckingConnectivity.value = true
      try {
        val settings = container.settingsRepository.settings.first()
        val result = container.updateManager.probeUpdaterAvailability(
          owner = settings.githubRepoOwner,
          repo = settings.githubRepoName,
        )
        lastConnectivityStatus = result.fold(
          onSuccess = { msg ->
            DiagnosticItem(
              id = "updater_network",
              title = "Conectividad hacia GitHub Releases",
              detail = msg,
              recommendation = if (msg.contains("404")) "Verifica owner/repo público en Actualizaciones." else null,
              status = if (msg.contains("HTTP 2")) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
              actionType = DiagnosticActionType.OPEN_UPDATES,
              actionLabel = "Abrir Actualizador",
            )
          },
          onFailure = { err ->
            DiagnosticItem(
              id = "updater_network",
              title = "Conectividad hacia GitHub Releases",
              detail = "Sin conexión o error de red: ${err.message ?: "No disponible"}",
              recommendation = "Conéctate a Wi-Fi o datos móviles únicamente cuando desees buscar o descargar actualizaciones.",
              status = DiagnosticStatus.WARNING,
              actionType = DiagnosticActionType.OPEN_UPDATES,
              actionLabel = "Ir a Actualizaciones",
            )
          },
        )
        runDiagnostics(context)
      } finally {
        _isCheckingConnectivity.value = false
      }
    }
  }

  private fun isXiaomiOrPocoDevice(): Boolean {
    val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return maker.contains("xiaomi") || maker.contains("poco") || maker.contains("redmi")
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InsightsViewModel(container) as T
      }
  }
}
