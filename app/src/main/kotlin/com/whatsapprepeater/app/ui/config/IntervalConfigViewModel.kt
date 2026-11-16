package com.whatsapprepeater.app.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.SafetyLimits
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IntervalConfigUiState(
  val minSeconds: Int = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
  val maxSeconds: Int = SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS,
  val scheduledCount: Int = 1,
  val saveAsDefault: Boolean = true,
  val preferWhatsAppBusiness: Boolean = false,
  val validationError: String? = null,
) {
  val isValid: Boolean
    get() = validationError == null

  val variabilitySeconds: Double
    get() = (maxSeconds - minSeconds).coerceAtLeast(0) / 2.0

  val estimatedTotalSeconds: Int
    get() {
      val intervalsCount = (scheduledCount - 1).coerceAtLeast(0)
      val avgInterval = (minSeconds + maxSeconds) / 2.0
      return (intervalsCount * avgInterval + scheduledCount * 3.0).toInt()
    }
}

class IntervalConfigViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val minFlow = MutableStateFlow(container.draft.value.minIntervalSeconds)
  private val maxFlow = MutableStateFlow(container.draft.value.maxIntervalSeconds)
  private val saveAsDefaultFlow = MutableStateFlow(true)
  private val preferBusinessFlow = MutableStateFlow(container.draft.value.preferWhatsAppBusiness)

  val uiState: StateFlow<IntervalConfigUiState> = combine(
    minFlow,
    maxFlow,
    saveAsDefaultFlow,
    preferBusinessFlow,
    container.draft,
  ) { minSec, maxSec, saveDefault, preferBiz, draft ->
    val scheduled = if (draft.isMultipleMode) {
      draft.multipleMessages.count { it.isNotBlank() }.coerceAtLeast(1)
    } else {
      draft.repeatCount.coerceAtLeast(1)
    }
    val err = IntervalRange(minSec, maxSec).validate()
    IntervalConfigUiState(
      minSeconds = minSec,
      maxSeconds = maxSec,
      scheduledCount = scheduled,
      saveAsDefault = saveDefault,
      preferWhatsAppBusiness = preferBiz,
      validationError = err,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = IntervalConfigUiState(),
  )

  fun adjustMin(delta: Int) {
    val updated = (minFlow.value + delta).coerceIn(1, SafetyLimits.MAX_INTERVAL_SECONDS)
    minFlow.value = updated
    syncDraftIfValid()
  }

  fun adjustMax(delta: Int) {
    val updated = (maxFlow.value + delta).coerceIn(1, SafetyLimits.MAX_INTERVAL_SECONDS)
    maxFlow.value = updated
    syncDraftIfValid()
  }

  fun applyPreset(minSec: Int, maxSec: Int) {
    minFlow.value = minSec
    maxFlow.value = maxSec
    syncDraftIfValid()
  }

  fun setSaveAsDefault(enabled: Boolean) {
    saveAsDefaultFlow.value = enabled
  }

  fun setPreferWhatsAppBusiness(enabled: Boolean) {
    preferBusinessFlow.value = enabled
    container.updateDraft { it.copy(preferWhatsAppBusiness = enabled) }
  }

  fun confirmAndPersist(): Boolean {
    val range = IntervalRange(minFlow.value, maxFlow.value)
    if (!range.isValid) return false

    container.updateDraft {
      it.copy(
        minIntervalSeconds = range.minSeconds,
        maxIntervalSeconds = range.maxSeconds,
        preferWhatsAppBusiness = preferBusinessFlow.value,
      )
    }

    viewModelScope.launch {
      withContext(NonCancellable) {
        if (saveAsDefaultFlow.value) {
          container.settingsRepository.updateDefaultIntervals(range.minSeconds, range.maxSeconds)
        }
        container.settingsRepository.updatePreferWhatsAppBusiness(preferBusinessFlow.value)
      }
    }
    return true
  }

  private fun syncDraftIfValid() {
    val range = IntervalRange(minFlow.value, maxFlow.value)
    if (range.isValid) {
      container.updateDraft {
        it.copy(
          minIntervalSeconds = range.minSeconds,
          maxIntervalSeconds = range.maxSeconds,
        )
      }
    }
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = IntervalConfigViewModel(container) as T
      }
  }
}

