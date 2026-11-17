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
import com.whatsapprepeater.core.model.DynamicVariablesEngine
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.VariableResolutionResult
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

  val resolvedModePreview: MessageMode
    get() = when (val mode = draft.toMessageMode()) {
      is MessageMode.MultipleMessages -> {
        val (resolved, _) = DynamicVariablesEngine.validateBatch(mode.messages, draft.selectedContact)
        MessageMode.MultipleMessages(resolved)
      }
      is MessageMode.RepeatSingleMessage -> {
        val text = when (val res = DynamicVariablesEngine.resolveOrValidate(mode.message, draft.selectedContact)) {
          is VariableResolutionResult.Resolved -> res.text
          is VariableResolutionResult.Unresolved -> mode.message
        }
        MessageMode.RepeatSingleMessage(text, mode.repeatCount)
      }
    }

  val variableValidationError: String?
    get() = when (val mode = draft.toMessageMode()) {
      is MessageMode.MultipleMessages ->
        DynamicVariablesEngine.validateBatch(mode.messages, draft.selectedContact).second?.reason
      is MessageMode.RepeatSingleMessage ->
        (DynamicVariablesEngine.resolveOrValidate(mode.message, draft.selectedContact) as? VariableResolutionResult.Unresolved)?.reason
    }

  val canStartAutomation: Boolean
    get() = draft.selectedContact != null &&
      totalScheduledPreview > 0 &&
      draft.toIntervalRange().isValid &&
      variableValidationError == null &&
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
  private val whatsappInstalledFlag = MutableStateFlow(container.accessibilityDriver.isWhatsAppInstalled())

  val uiState: StateFlow<AutomationUiState> = combine(
    container.draft,
    container.automationEngine.state,
    container.settingsRepository.settings,
    container.accessibilityDriver.isServiceConnected,
    checkboxConfirmed,
    emergencyDialogState,
    whatsappInstalledFlag,
  ) { values ->
    AutomationUiState(
      draft = values[0] as AutomationDraft,
      engineState = values[1] as AutomationState,
      settings = values[2] as AppSettings,
      isServiceConnected = values[3] as Boolean,
      isWhatsAppInstalled = values[6] as Boolean,
      userCheckboxConfirmed = values[4] as Boolean,
      emergencyDialog = values[5] as EmergencyStopDialogState,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = AutomationUiState(),
  )

  fun refreshSystemStatus() {
    whatsappInstalledFlag.value = container.accessibilityDriver.isWhatsAppInstalled()
  }

  fun setUserCheckboxConfirmed(confirmed: Boolean) {
    checkboxConfirmed.value = confirmed
  }

  fun startAutomation() {
    val rawConfig = container.draft.value.toConfirmedConfig(confirmed = checkboxConfirmed.value) ?: return
    val resolvedConfig = resolveAndValidateConfig(rawConfig) ?: return
    container.markActiveScheduledTaskStarted()
    emergencyDialogState.value = EmergencyStopDialogState()
    container.automationEngine.start(resolvedConfig)
  }

  fun repeatSameConfiguration() {
    val rawConfig = container.draft.value.toConfirmedConfig(confirmed = true) ?: return
    val resolvedConfig = resolveAndValidateConfig(rawConfig) ?: return
    checkboxConfirmed.value = true
    emergencyDialogState.value = EmergencyStopDialogState()
    container.automationEngine.start(resolvedConfig)
  }

  private fun resolveAndValidateConfig(config: com.whatsapprepeater.core.model.AutomationConfig): com.whatsapprepeater.core.model.AutomationConfig? {
    val resolvedMode = when (val mode = config.mode) {
      is MessageMode.MultipleMessages -> {
        val (resolved, err) = DynamicVariablesEngine.validateBatch(mode.messages, config.contact)
        if (err != null) return null
        MessageMode.MultipleMessages(resolved)
      }
      is MessageMode.RepeatSingleMessage -> {
        when (val res = DynamicVariablesEngine.resolveOrValidate(mode.message, config.contact)) {
          is VariableResolutionResult.Resolved -> MessageMode.RepeatSingleMessage(res.text, mode.repeatCount)
          is VariableResolutionResult.Unresolved -> return null
        }
      }
    }
    return config.copy(mode = resolvedMode)
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
