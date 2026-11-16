package com.whatsapprepeater.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.ExecutionRecord
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
) {
  val isAccessibilityReady: Boolean
    get() = isServiceConnected || isServiceEnabledInSettings

  val hasSavedDraft: Boolean
    get() = savedDraft.selectedContact != null &&
      ((savedDraft.isMultipleMode && savedDraft.multipleMessages.isNotEmpty()) ||
        (!savedDraft.isMultipleMode && savedDraft.singleMessage.isNotBlank()))
}

class HomeViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val settingsEnabledFlag = MutableStateFlow(false)
  private val whatsappInstalledFlag = MutableStateFlow(container.accessibilityDriver.isWhatsAppInstalled())

  val uiState: StateFlow<HomeUiState> = combine(
    container.accessibilityDriver.isServiceConnected,
    settingsEnabledFlag,
    whatsappInstalledFlag,
    container.historyRepository.observeRecentExecutions(limit = 15),
    container.automationEngine.state,
    container.settingsRepository.settings,
    container.draft,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    HomeUiState(
      isServiceConnected = values[0] as Boolean,
      isServiceEnabledInSettings = values[1] as Boolean,
      isWhatsAppInstalled = values[2] as Boolean,
      recentExecutions = values[3] as List<ExecutionRecord>,
      activeAutomationState = values[4] as AutomationState,
      settings = values[5] as AppSettings,
      savedDraft = values[6] as AutomationDraft,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HomeUiState(),
  )

  fun refreshSystemStatus(isEnabledInSystemSettings: Boolean) {
    settingsEnabledFlag.value = isEnabledInSystemSettings
    whatsappInstalledFlag.value = container.accessibilityDriver.isWhatsAppInstalled()
  }

  fun prepareNewAutomation() {
    val currentSettings = uiState.value.settings
    container.automationEngine.resetToIdle()
    container.resetDraftForNewAutomation(
      defaultMin = currentSettings.defaultMinIntervalSeconds,
      defaultMax = currentSettings.defaultMaxIntervalSeconds,
      preferBusiness = currentSettings.preferWhatsAppBusiness,
    )
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
