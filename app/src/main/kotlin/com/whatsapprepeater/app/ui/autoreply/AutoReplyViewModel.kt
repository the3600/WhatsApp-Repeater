package com.whatsapprepeater.app.ui.autoreply

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.autoreply.AutoReplyPermissionsHelper
import com.whatsapprepeater.app.autoreply.AutoReplyRuntimeState
import com.whatsapprepeater.core.autoresponder.RuleMatcher
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.RuleMatchType
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
  val phoneContacts: List<ContactInfo> = emptyList(),
)

class AutoReplyViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val notifAccessFlag = MutableStateFlow(false)
  private val batteryOptFlag = MutableStateFlow(false)
  private val contactsPermissionFlag = MutableStateFlow(false)
  private val phoneContactsFlow = MutableStateFlow<List<ContactInfo>>(emptyList())

  val uiState: StateFlow<AutoReplyUiState> = combine(
    container.autoReplySettingsRepository.settings,
    container.autoReplyRepository.observeRules(),
    container.autoReplyRepository.observeHistory(limit = 100),
    container.autoReplyCoordinator.runtimeState,
    notifAccessFlag,
    batteryOptFlag,
    container.accessibilityDriver.isServiceConnected,
    container.automationEngine.state,
    contactsPermissionFlag,
    phoneContactsFlow,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    val engineState = values[7] as com.whatsapprepeater.core.model.AutomationState
    @Suppress("UNCHECKED_CAST")
    AutoReplyUiState(
      settings = values[0] as AutoReplySettings,
      rules = values[1] as List<AutoReplyRule>,
      history = values[2] as List<AutoReplyLogEntry>,
      runtime = values[3] as AutoReplyRuntimeState,
      isNotificationAccessEnabled = values[4] as Boolean,
      isIgnoringBatteryOptimizations = values[5] as Boolean,
      isAccessibilityReady = values[6] as Boolean,
      isAutomationRunning = engineState.isRunning || engineState.isPaused,
      hasContactsPermission = values[8] as Boolean,
      phoneContacts = values[9] as List<ContactInfo>,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = AutoReplyUiState(),
  )

  fun refreshSystemStatus(context: Context) {
    notifAccessFlag.value = AutoReplyPermissionsHelper.isNotificationAccessEnabled(context)
    batteryOptFlag.value = AutoReplyPermissionsHelper.isIgnoringBatteryOptimizations(context)
    val granted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    contactsPermissionFlag.value = granted
    if (granted && phoneContactsFlow.value.isEmpty()) {
      loadContacts()
    }
  }

  fun onContactsPermissionResult(granted: Boolean) {
    contactsPermissionFlag.value = granted
    if (granted) {
      loadContacts()
    }
  }

  private fun loadContacts() {
    viewModelScope.launch {
      phoneContactsFlow.value = runCatching {
        container.contactsRepository.loadPhoneContacts()
      }.getOrDefault(emptyList())
    }
  }

  fun setMasterEnabled(enabled: Boolean) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
          it.copy(masterEnabled = enabled)
        }
      }
    }
  }

  fun emergencyStop() {
    container.autoReplyCoordinator.emergencyStop()
  }

  fun updateTargetApp(targetApp: AutoReplyTargetApp) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings { it.copy(targetApp = targetApp) }
      }
    }
  }

  fun updateLoopProtection(enabled: Boolean) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings { it.copy(preventLoopsEnabled = enabled) }
      }
    }
  }

  fun updateGlobalLimits(minIntervalSec: Int, maxReplies: Int) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
          it.copy(
            minIntervalSeconds = minIntervalSec.coerceIn(0, 3_600),
            maxRepliesPerConversation = maxReplies.coerceIn(1, 100),
          )
        }
      }
    }
  }

  fun updateGlobalSchedule(schedule: AutoReplySchedule) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings { it.copy(globalSchedule = schedule) }
      }
    }
  }

  fun updateWelcomeConfig(enabled: Boolean, message: String, cooldownMinutes: Int) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
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
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
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
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
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
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplySettingsRepository.updateSettings {
          it.copy(
            saveHistoryEnabled = saveHistory,
            saveIncomingPreviewEnabled = if (saveHistory) saveIncomingPreview else false,
          )
        }
      }
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplyRepository.clearHistory()
      }
    }
  }

  fun saveRule(rule: AutoReplyRule) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplyRepository.saveRule(
          rule.copy(updatedAtMillis = System.currentTimeMillis()),
        )
      }
    }
  }

  fun setRuleEnabled(ruleId: Long, enabled: Boolean) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplyRepository.setRuleEnabled(ruleId, enabled)
      }
    }
  }

  fun deleteRule(ruleId: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.autoReplyRepository.deleteRule(ruleId)
      }
    }
  }

  suspend fun getRuleOnce(ruleId: Long): AutoReplyRule? =
    container.autoReplyRepository.getRuleById(ruleId)

  fun validateRulePattern(pattern: String, matchType: RuleMatchType): String? =
    RuleMatcher.validatePattern(pattern, matchType)

  fun testPatternMatch(
    sampleIncoming: String,
    pattern: String,
    matchType: RuleMatchType,
    caseSensitive: Boolean,
  ): Boolean = RuleMatcher.matches(sampleIncoming, pattern, matchType, caseSensitive)

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AutoReplyViewModel(container) as T
      }
  }
}

