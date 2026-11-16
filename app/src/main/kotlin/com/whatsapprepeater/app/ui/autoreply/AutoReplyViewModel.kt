package com.whatsapprepeater.app.ui.autoreply

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.autoreply.AutoReplyPermissionsHelper
import com.whatsapprepeater.app.autoreply.AutoReplyRuntimeState
import com.whatsapprepeater.core.autoresponder.RuleMatcher
import com.whatsapprepeater.core.data.repository.AutoReplyRepository
import com.whatsapprepeater.core.data.repository.AutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.ContactsRepository
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.RuleMatchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AutoReplyUiState(
  val settings: AutoReplySettings = AutoReplySettings(),
  val rules: List<AutoReplyRule> = emptyList(),
  val history: List<AutoReplyLogEntry> = emptyList(),
  val runtime: AutoReplyRuntimeState = AutoReplyRuntimeState(),
  val isNotificationAccessEnabled: Boolean = false,
  val isIgnoringBatteryOptimizations: Boolean = false,
  val isAccessibilityReady: Boolean = false,
  val isAutomationRunning: Boolean = false,
  val hasContactsPermission: Boolean = false,
  val hasPostNotificationsPermission: Boolean = true,
  val phoneContacts: List<ContactInfo> = emptyList(),
  val feedbackMessage: String? = null,
  val isErrorFeedback: Boolean = false,
)

private data class PersistedAutoReplySnapshot(
  val settings: AutoReplySettings,
  val rules: List<AutoReplyRule>,
  val history: List<AutoReplyLogEntry>,
  val runtime: AutoReplyRuntimeState,
)

private data class SystemStatusSnapshot(
  val notifAccess: Boolean,
  val batteryOpt: Boolean,
  val accessibilityReady: Boolean,
  val automationState: AutomationState,
  val contactsPermission: Boolean,
  val postNotificationsPermission: Boolean,
  val phoneContacts: List<ContactInfo>,
)

class AutoReplyViewModel internal constructor(
  private val autoReplyRepository: AutoReplyRepository,
  private val autoReplySettingsRepository: AutoReplySettingsRepository,
  private val contactsRepository: ContactsRepository,
  accessibilityConnectedFlow: StateFlow<Boolean>,
  automationStateFlow: StateFlow<AutomationState>,
  runtimeStateFlow: StateFlow<AutoReplyRuntimeState>,
  private val onEmergencyStop: () -> Unit,
) : ViewModel() {

  constructor(container: AppContainer) : this(
    autoReplyRepository = container.autoReplyRepository,
    autoReplySettingsRepository = container.autoReplySettingsRepository,
    contactsRepository = container.contactsRepository,
    accessibilityConnectedFlow = container.accessibilityDriver.isServiceConnected,
    automationStateFlow = container.automationEngine.state,
    runtimeStateFlow = container.autoReplyCoordinator.runtimeState,
    onEmergencyStop = container.autoReplyCoordinator::emergencyStop,
  )

  private val notifAccessFlag = MutableStateFlow(false)
  private val batteryOptFlag = MutableStateFlow(false)
  private val contactsPermissionFlag = MutableStateFlow(false)
  private val postNotificationsFlag = MutableStateFlow(true)
  private val phoneContactsFlow = MutableStateFlow<List<ContactInfo>>(emptyList())
  private val feedbackMessageFlow = MutableStateFlow<String?>(null)
  private val isErrorFeedbackFlow = MutableStateFlow(false)

  private val persistedFlow = combine(
    autoReplySettingsRepository.settings,
    autoReplyRepository.observeRules(),
    autoReplyRepository.observeHistory(limit = 100),
    runtimeStateFlow,
  ) { settings, rules, history, runtime ->
    PersistedAutoReplySnapshot(
      settings = settings,
      rules = rules,
      history = history,
      runtime = runtime,
    )
  }

  private val systemPart1Flow = combine(
    notifAccessFlag,
    batteryOptFlag,
    accessibilityConnectedFlow,
    automationStateFlow,
  ) { notif, battery, accessibility, automation ->
    Quad(notif, battery, accessibility, automation)
  }

  private val systemStatusFlow = combine(
    systemPart1Flow,
    contactsPermissionFlag,
    postNotificationsFlag,
    phoneContactsFlow,
  ) { part1, contactsPerm, postNotifPerm, contacts ->
    SystemStatusSnapshot(
      notifAccess = part1.first,
      batteryOpt = part1.second,
      accessibilityReady = part1.third,
      automationState = part1.fourth,
      contactsPermission = contactsPerm,
      postNotificationsPermission = postNotifPerm,
      phoneContacts = contacts,
    )
  }

  val uiState: StateFlow<AutoReplyUiState> = combine(
    persistedFlow,
    systemStatusFlow,
    feedbackMessageFlow,
    isErrorFeedbackFlow,
  ) { persisted, sys, feedback, isError ->
    AutoReplyUiState(
      settings = persisted.settings,
      rules = persisted.rules,
      history = persisted.history,
      runtime = persisted.runtime,
      isNotificationAccessEnabled = sys.notifAccess,
      isIgnoringBatteryOptimizations = sys.batteryOpt,
      isAccessibilityReady = sys.accessibilityReady,
      isAutomationRunning = sys.automationState.isRunning || sys.automationState.isPaused,
      hasContactsPermission = sys.contactsPermission,
      hasPostNotificationsPermission = sys.postNotificationsPermission,
      phoneContacts = sys.phoneContacts,
      feedbackMessage = feedback,
      isErrorFeedback = isError,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = AutoReplyUiState(),
  )

  fun refreshSystemStatus(context: Context) {
    notifAccessFlag.value = AutoReplyPermissionsHelper.isNotificationAccessEnabled(context)
    batteryOptFlag.value = AutoReplyPermissionsHelper.isIgnoringBatteryOptimizations(context)
    val contactsGranted = runCatching {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
      ) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    contactsPermissionFlag.value = contactsGranted

    postNotificationsFlag.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      runCatching {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
      }.getOrDefault(false)

    if (contactsGranted && phoneContactsFlow.value.isEmpty()) {
      loadContacts()
    }
  }

  fun onContactsPermissionResult(granted: Boolean) {
    contactsPermissionFlag.value = granted
    if (granted) {
      loadContacts()
    }
  }

  fun onPostNotificationsPermissionResult(granted: Boolean) {
    postNotificationsFlag.value = granted
  }

  fun clearFeedback() {
    feedbackMessageFlow.value = null
    isErrorFeedbackFlow.value = false
  }

  private fun loadContacts() {
    launchSafe(errorPrefix = "No se pudieron cargar los contactos") {
      phoneContactsFlow.value = contactsRepository.loadPhoneContacts()
    }
  }

  fun setMasterEnabled(enabled: Boolean) {
    launchSafe(errorPrefix = "No se pudo cambiar el estado del Motor 2") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(masterEnabled = enabled)
        }
      }
    }
  }

  fun emergencyStop() {
    runCatching { onEmergencyStop() }
      .onFailure { err ->
        runCatching { Log.e(TAG, "Error en parada de emergencia", err) }
        isErrorFeedbackFlow.value = true
        feedbackMessageFlow.value = err.message ?: "No se pudo completar la parada de emergencia."
      }
  }

  fun updateTargetApp(targetApp: AutoReplyTargetApp) {
    launchSafe(errorPrefix = "No se pudo guardar la app objetivo") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings { it.copy(targetApp = targetApp) }
      }
    }
  }

  fun updateLoopProtection(enabled: Boolean) {
    launchSafe(errorPrefix = "No se pudo actualizar la protección antibucle") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings { it.copy(preventLoopsEnabled = enabled) }
      }
    }
  }

  fun updateGlobalLimits(minIntervalSec: Int, maxReplies: Int) {
    launchSafe(errorPrefix = "No se pudieron guardar los límites") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(
            minIntervalSeconds = minIntervalSec.coerceIn(0, 3_600),
            maxRepliesPerConversation = maxReplies.coerceIn(1, 100),
          )
        }
      }
    }
  }

  fun updateGlobalSchedule(schedule: AutoReplySchedule) {
    launchSafe(errorPrefix = "No se pudo guardar el horario global") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings { it.copy(globalSchedule = schedule) }
      }
    }
  }

  fun updateWelcomeConfig(enabled: Boolean, message: String, cooldownMinutes: Int) {
    launchSafe(errorPrefix = "No se pudo guardar el mensaje de bienvenida") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(
            welcomeEnabled = enabled,
            welcomeMessage = message.take(1000),
            welcomeCooldownMinutes = cooldownMinutes.coerceIn(1, 10_080),
          )
        }
      }
    }
  }

  fun updateAwayConfig(enabled: Boolean, message: String, schedule: AutoReplySchedule) {
    launchSafe(errorPrefix = "No se pudo guardar el mensaje de ausencia") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(
            awayEnabled = enabled,
            awayMessage = message.take(1000),
            awaySchedule = schedule,
          )
        }
      }
    }
  }

  fun updateContactFilters(
    mode: AutoReplyContactFilterMode,
    allowGroups: Boolean,
    selected: List<String>,
    excluded: List<String>,
  ) {
    launchSafe(errorPrefix = "No se pudieron guardar los filtros de contactos") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(
            globalContactFilterMode = mode,
            allowGroupsByDefault = allowGroups,
            selectedContactNamesOrPhones = selected.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.distinct(),
            excludedIdentifiers = excluded.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.distinct(),
          )
        }
      }
    }
  }

  fun updateHistoryPrivacy(saveHistory: Boolean, saveIncomingPreview: Boolean) {
    launchSafe(errorPrefix = "No se pudo actualizar la privacidad del historial") {
      withContext(NonCancellable) {
        autoReplySettingsRepository.updateSettings {
          it.copy(
            saveHistoryEnabled = saveHistory,
            saveIncomingPreviewEnabled = if (saveHistory) saveIncomingPreview else false,
          )
        }
      }
    }
  }

  fun clearHistory() {
    launchSafe(errorPrefix = "No se pudo borrar el historial") {
      withContext(NonCancellable) {
        autoReplyRepository.clearHistory()
      }
    }
  }

  fun saveRule(rule: AutoReplyRule) {
    launchSafe(errorPrefix = "No se pudo guardar la regla") {
      withContext(NonCancellable) {
        autoReplyRepository.saveRule(
          rule.copy(updatedAtMillis = System.currentTimeMillis()),
        )
      }
    }
  }

  fun setRuleEnabled(ruleId: Long, enabled: Boolean) {
    launchSafe(errorPrefix = "No se pudo cambiar el estado de la regla") {
      withContext(NonCancellable) {
        autoReplyRepository.setRuleEnabled(ruleId, enabled)
      }
    }
  }

  fun deleteRule(ruleId: Long) {
    launchSafe(errorPrefix = "No se pudo eliminar la regla") {
      withContext(NonCancellable) {
        autoReplyRepository.deleteRule(ruleId)
      }
    }
  }

  suspend fun getRuleOnce(ruleId: Long): AutoReplyRule? =
    runCatching { autoReplyRepository.getRuleById(ruleId) }
      .onFailure { err ->
        if (err is CancellationException) throw err
        runCatching { Log.e(TAG, "Error consultando regla $ruleId", err) }
        isErrorFeedbackFlow.value = true
        feedbackMessageFlow.value = "No se pudo cargar la regla: ${err.message ?: err.javaClass.simpleName}"
      }
      .getOrNull()

  fun validateRulePattern(pattern: String, matchType: RuleMatchType): String? =
    RuleMatcher.validatePattern(pattern, matchType)

  fun testPatternMatch(
    sampleIncoming: String,
    pattern: String,
    matchType: RuleMatchType,
    caseSensitive: Boolean,
  ): Boolean = RuleMatcher.matches(sampleIncoming, pattern, matchType, caseSensitive)

  private fun launchSafe(
    errorPrefix: String,
    block: suspend () -> Unit,
  ) {
    viewModelScope.launch {
      try {
        block()
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        runCatching { Log.e(TAG, errorPrefix, e) }
        isErrorFeedbackFlow.value = true
        feedbackMessageFlow.value = "$errorPrefix: ${e.message ?: e.javaClass.simpleName}"
      }
    }
  }

  private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
  )

  companion object {
    private const val TAG = "AutoReplyViewModel"

    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AutoReplyViewModel(container) as T
      }
  }
}
