package com.whatsapprepeater.app.ui.home

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
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.app.autoreply.AutoReplyRuntimeState
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.DynamicVariablesEngine
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.IntelligenceSnapshot
import com.whatsapprepeater.core.model.LocalIntelligenceEngine
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SmartRecommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val isServiceConnected: Boolean = false,
  val isServiceEnabledInSettings: Boolean = false,
  val isWhatsAppInstalled: Boolean = true,
  val recentExecutions: List<ExecutionRecord> = emptyList(),
  val activeAutomationState: AutomationState = AutomationState(),
  val settings: AppSettings = AppSettings(),
  val savedDraft: AutomationDraft = AutomationDraft(),
  val autoReplySettings: AutoReplySettings = AutoReplySettings(),
  val autoReplyRuntime: AutoReplyRuntimeState = AutoReplyRuntimeState(),
  val nextScheduledTask: ScheduledTask? = null,
  val activeSchedulesCount: Int = 0,
  val pausedSchedulesCount: Int = 0,
  val failedSchedulesCount: Int = 0,
  val completedExecutionsCount: Int = 0,
  val failedExecutionsCount: Int = 0,
  val savedTemplatesCount: Int = 0,
  val canInstallUpdates: Boolean = false,
  val smartRecommendations: List<SmartRecommendation> = emptyList(),
) {
  val isAccessibilityReady: Boolean
    get() = isServiceConnected || isServiceEnabledInSettings

  val hasSavedDraft: Boolean
    get() = savedDraft.selectedContact != null &&
      ((savedDraft.isMultipleMode && savedDraft.multipleMessages.isNotEmpty()) ||
        (!savedDraft.isMultipleMode && savedDraft.singleMessage.isNotBlank()))

  val draftPendingMessagesCount: Int
    get() = if (savedDraft.isMultipleMode) {
      savedDraft.multipleMessages.count { it.isNotBlank() }
    } else if (savedDraft.singleMessage.isNotBlank()) {
      savedDraft.repeatCount
    } else {
      0
    }

  val pendingErrorsToReviewCount: Int
    get() = failedSchedulesCount + failedExecutionsCount + (if (!isAccessibilityReady) 1 else 0) + (if (!isWhatsAppInstalled) 1 else 0)

  val attentionAlertsCount: Int
    get() {
      var count = 0
      if (!isAccessibilityReady) count++
      if (!isWhatsAppInstalled) count++
      if (failedSchedulesCount > 0) count++
      if (hasSavedDraft) {
        val msgs = if (savedDraft.isMultipleMode) savedDraft.multipleMessages else listOf(savedDraft.singleMessage)
        if (DynamicVariablesEngine.validateBatch(msgs, savedDraft.selectedContact).second != null) count++
      }
      return count
    }
}

class HomeViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val settingsEnabledFlag = MutableStateFlow(false)
  private val whatsappInstalledFlag = MutableStateFlow(container.accessibilityDriver.isWhatsAppInstalled())
  private val canInstallUpdatesFlag = MutableStateFlow(container.updateManager.canRequestPackageInstalls())
  private val ignoringBatteryFlag = MutableStateFlow(true)
  private val exactAlarmsFlag = MutableStateFlow(container.schedulerAlarmHelper.canScheduleExactAlarms())
  private val postNotificationsFlag = MutableStateFlow(true)

  private val envPermissionsFlow = combine(
    ignoringBatteryFlag,
    exactAlarmsFlag,
    postNotificationsFlag,
  ) { battery, exactAlarms, postNotifs ->
    listOf(battery, exactAlarms, postNotifs)
  }

  private val systemStatusFlow = combine(
    container.accessibilityDriver.isServiceConnected,
    settingsEnabledFlag,
    whatsappInstalledFlag,
    canInstallUpdatesFlag,
    envPermissionsFlow,
  ) { a11yConn, a11ySettings, waInstalled, canInstall, env ->
    listOf(a11yConn, a11ySettings, waInstalled, canInstall, env[0], env[1], env[2])
  }

  private val controlDataFlow = combine(
    container.autoReplySettingsRepository.settings,
    container.autoReplyCoordinator.runtimeState,
    container.scheduledTaskRepository.observeTasks(),
    container.savedConfigRepository.observeSavedMessageLists(),
  ) { autoSettings, autoRuntime, tasks, templates ->
    listOf(autoSettings, autoRuntime, tasks, templates)
  }

  val uiState: StateFlow<HomeUiState> = combine(
    systemStatusFlow,
    container.historyRepository.observeRecentExecutions(limit = 15),
    container.automationEngine.state,
    container.settingsRepository.settings,
    container.draft,
    controlDataFlow,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    val sys = values[0] as List<Boolean>
    @Suppress("UNCHECKED_CAST")
    val history = values[1] as List<ExecutionRecord>
    val activeState = values[2] as AutomationState
    val settings = values[3] as AppSettings
    val draft = values[4] as AutomationDraft
    @Suppress("UNCHECKED_CAST")
    val ctrl = values[5] as List<Any>
    val autoSettings = ctrl[0] as AutoReplySettings
    val autoRuntime = ctrl[1] as AutoReplyRuntimeState
    @Suppress("UNCHECKED_CAST")
    val tasks = ctrl[2] as List<ScheduledTask>
    @Suppress("UNCHECKED_CAST")
    val templates = ctrl[3] as List<com.whatsapprepeater.core.model.SavedMessageList>

    val activeSchedules = tasks.filter {
      it.enabled &&
        it.status != ScheduledTaskStatus.EXPIRED &&
        it.status != ScheduledTaskStatus.FAILED &&
        it.status != ScheduledTaskStatus.PAUSED &&
        it.status != ScheduledTaskStatus.COMPLETED
    }
    val nextTask = activeSchedules.minByOrNull { it.triggerAtMillis }
    val pausedCount = tasks.count {
      it.status == ScheduledTaskStatus.PAUSED ||
        (!it.enabled && it.status != ScheduledTaskStatus.COMPLETED && it.status != ScheduledTaskStatus.FAILED && it.status != ScheduledTaskStatus.EXPIRED)
    }
    val failedOrExpired = tasks.count {
      it.status == ScheduledTaskStatus.FAILED || it.status == ScheduledTaskStatus.EXPIRED
    }
    val completedExecs = history.count { it.finalStatus == com.whatsapprepeater.core.model.ExecutionFinalStatus.COMPLETED }
    val failedExecs = history.count {
      it.finalStatus == com.whatsapprepeater.core.model.ExecutionFinalStatus.FAILED_ERROR ||
        it.finalStatus == com.whatsapprepeater.core.model.ExecutionFinalStatus.FAILED_SAFETY_CHECK
    }

    val draftMessages = if (draft.isMultipleMode) {
      draft.multipleMessages
    } else {
      listOfNotNull(draft.singleMessage.takeIf { it.isNotBlank() })
    }
    val smartRecommendations = LocalIntelligenceEngine.analyze(
      IntelligenceSnapshot(
        isAccessibilityReady = sys[0] || sys[1],
        isWhatsAppInstalled = sys[2],
        isIgnoringBatteryOptimizations = sys[4],
        canScheduleExactAlarms = sys[5],
        postNotificationsGranted = sys[6],
        vacationPauseEnabled = settings.vacationPauseEnabled,
        activeSchedules = activeSchedules,
        recentExecutions = history,
        savedTemplates = templates,
        draftContact = draft.selectedContact,
        draftMessages = draftMessages,
        draftMinIntervalSeconds = draft.minIntervalSeconds,
        isHyperOsOrXiaomi = isXiaomiOrPoco(),
      ),
    )

    HomeUiState(
      isServiceConnected = sys[0],
      isServiceEnabledInSettings = sys[1],
      isWhatsAppInstalled = sys[2],
      canInstallUpdates = sys[3],
      recentExecutions = history,
      activeAutomationState = activeState,
      settings = settings,
      savedDraft = draft,
      autoReplySettings = autoSettings,
      autoReplyRuntime = autoRuntime,
      nextScheduledTask = nextTask,
      activeSchedulesCount = activeSchedules.size,
      pausedSchedulesCount = pausedCount,
      failedSchedulesCount = failedOrExpired,
      completedExecutionsCount = completedExecs,
      failedExecutionsCount = failedExecs,
      savedTemplatesCount = templates.size,
      smartRecommendations = smartRecommendations,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HomeUiState(),
  )

  fun refreshSystemStatus(isEnabledInSystemSettings: Boolean) {
    settingsEnabledFlag.value = isEnabledInSystemSettings
    whatsappInstalledFlag.value = container.accessibilityDriver.isWhatsAppInstalled()
    canInstallUpdatesFlag.value = container.updateManager.canRequestPackageInstalls()
    exactAlarmsFlag.value = container.schedulerAlarmHelper.canScheduleExactAlarms()
  }

  fun refreshSystemStatus(context: Context) {
    val appCtx = context.applicationContext
    refreshSystemStatus(AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(appCtx))
    val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    ignoringBatteryFlag.value = pm?.isIgnoringBatteryOptimizations(appCtx.packageName) ?: true
    postNotificationsFlag.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
  }

  private fun isXiaomiOrPoco(): Boolean {
    val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return maker.contains("xiaomi") || maker.contains("poco") || maker.contains("redmi")
  }

  fun prepareNewAutomation(isMultipleMode: Boolean = true) {
    val currentSettings = uiState.value.settings
    container.automationEngine.resetToIdle()
    container.resetDraftForNewAutomation(
      defaultMin = currentSettings.defaultMinIntervalSeconds,
      defaultMax = currentSettings.defaultMaxIntervalSeconds,
      preferBusiness = currentSettings.preferWhatsAppBusiness,
    )
    container.updateDraft { it.copy(isMultipleMode = isMultipleMode) }
  }

  fun clearHistory() {
    viewModelScope.launch {
      container.historyRepository.clearHistory()
    }
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
      }
  }
}
