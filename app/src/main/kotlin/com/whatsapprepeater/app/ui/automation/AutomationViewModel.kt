package com.whatsapprepeater.app.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.app.ui.components.EmergencyStopDialogState
import com.whatsapprepeater.app.ui.components.EmergencyStopStage
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.MessageMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationUiState(
  val draft: AutomationDraft = AutomationDraft(),
  val engineState: AutomationState = AutomationState(),
  val settings: AppSettings = AppSettings(),
  val isServiceConnected: Boolean = false,
  val isWhatsAppInstalled: Boolean = true,
  val userCheckboxConfirmed: Boolean = false,
  val emergencyDialog: EmergencyStopDialogState = EmergencyStopDialogState(),
) {
  val totalScheduledPreview: Int
    get() = when (val mode = draft.toMessageMode()) {
      is MessageMode.MultipleMessages -> mode.messages.count { it.isNotBlank() }
      is MessageMode.RepeatSingleMessage -> if (mode.message.isBlank()) 0 else mode.repeatCount
    }

  val canStartAutomation: Boolean
    get() = draft.selectedContact != null &&
      totalScheduledPreview > 0 &&
      draft.toIntervalRange().isValid &&
      isServiceConnected &&
      isWhatsAppInstalled &&
      userCheckboxConfirmed &&
      !engineState.isRunning &&
      !engineState.isPaused
}

class AutomationViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val checkboxConfirmed = MutableStateFlow(false)
  private val emergencyDialogState = MutableStateFlow(EmergencyStopDialogState())

  val uiState: StateFlow<AutomationUiState> = combine(
    container.draft,
    container.automationEngine.state,
    container.settingsRepository.settings,
    container.accessibilityDriver.isServiceConnected,
    checkboxConfirmed,
    emergencyDialogState,
  ) { values ->
    AutomationUiState(
      draft = values[0] as AutomationDraft,
      engineState = values[1] as AutomationState,
      settings = values[2] as AppSettings,
      isServiceConnected = values[3] as Boolean,
      isWhatsAppInstalled = container.accessibilityDriver.isWhatsAppInstalled(),
      userCheckboxConfirmed = values[4] as Boolean,
      emergencyDialog = values[5] as EmergencyStopDialogState,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = AutomationUiState(),
  )

  fun setUserCheckboxConfirmed(confirmed: Boolean) {
    checkboxConfirmed.value = confirmed
  }

  fun startAutomation() {
    val config = container.draft.value.toConfirmedConfig(confirmed = checkboxConfirmed.value) ?: return
    emergencyDialogState.value = EmergencyStopDialogState()
    container.automationEngine.start(config)
  }

  fun repeatSameConfiguration() {
    val config = container.draft.value.toConfirmedConfig(confirmed = true) ?: return
    checkboxConfirmed.value = true
    emergencyDialogState.value = EmergencyStopDialogState()
    container.automationEngine.start(config)
  }

  fun pauseAutomation() {
    container.automationEngine.pause()
  }

  fun resumeAutomation() {
    container.automationEngine.resume()
  }

  fun requestEmergencyStopDialog() {
    val current = container.automationEngine.state.value
    emergencyDialogState.value = EmergencyStopDialogState(
      isVisible = true,
      stage = EmergencyStopStage.CONFIRMATION,
      recipient = current.contact?.let { "${it.name} (${it.phoneNumber})" } ?: "Contacto",
      sentCount = current.sentConfirmed,
      totalCount = current.totalScheduled,
    )
  }

  fun dismissEmergencyStopDialog() {
    if (emergencyDialogState.value.stage != EmergencyStopStage.STOPPING) {
      emergencyDialogState.value = EmergencyStopDialogState()
    }
  }

  fun confirmEmergencyStop() {
    val snapshot = emergencyDialogState.value
    emergencyDialogState.value = snapshot.copy(stage = EmergencyStopStage.STOPPING)
    viewModelScope.launch {
      container.automationEngine.stop()
      delay(350L)
      val latest = container.automationEngine.state.value
      emergencyDialogState.value = snapshot.copy(
        isVisible = true,
        stage = EmergencyStopStage.STOPPED,
        sentCount = latest.sentConfirmed,
        totalCount = latest.totalScheduled,
      )
    }
  }

  fun stopAutomationImmediately() {
    container.automationEngine.stop()
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AutomationViewModel(container) as T
      }
  }
}
