package com.whatsapprepeater.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.MessageTraceStatus
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryFilter {
  ALL,
  COMPLETED,
  FAILED,
}

data class HistoryUiState(
  val allRecords: List<ExecutionRecord> = emptyList(),
  val filteredRecords: List<ExecutionRecord> = emptyList(),
  val filter: HistoryFilter = HistoryFilter.ALL,
  val isAccessibilityReady: Boolean = false,
  val activeAutomationState: AutomationState = AutomationState(),
)

class HistoryViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val filterFlow = MutableStateFlow(HistoryFilter.ALL)

  val uiState: StateFlow<HistoryUiState> = combine(
    container.historyRepository.observeRecentExecutions(limit = 100),
    filterFlow,
    container.accessibilityDriver.isServiceConnected,
    container.automationEngine.state,
  ) { records, filter, serviceConnected, engineState ->
    val filtered = when (filter) {
      HistoryFilter.ALL -> records
      HistoryFilter.COMPLETED -> records.filter { it.finalStatus == ExecutionFinalStatus.COMPLETED }
      HistoryFilter.FAILED -> records.filter { it.finalStatus != ExecutionFinalStatus.COMPLETED }
    }
    HistoryUiState(
      allRecords = records,
      filteredRecords = filtered,
      filter = filter,
      isAccessibilityReady = serviceConnected,
      activeAutomationState = engineState,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HistoryUiState(),
  )

  fun setFilter(filter: HistoryFilter) {
    filterFlow.value = filter
  }

  fun observeRecord(id: Long): Flow<ExecutionRecord?> =
    container.historyRepository.observeExecutionById(id)

  fun deleteRecord(id: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.historyRepository.deleteExecution(id)
      }
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.historyRepository.clearHistory()
      }
    }
  }

  fun prepareRetryPending(record: ExecutionRecord) {
    container.automationEngine.resetToIdle()
    val pendingMessages = record.messageTraces
      .filter { it.status != MessageTraceStatus.CONFIRMED }
      .map { it.messagePreview }
      .filter { it.isNotBlank() }

    container.updateDraft { draft ->
      val contact = ContactInfo(
        id = "history_${record.id}",
        name = record.contactName,
        phoneNumber = record.contactPhone,
      )
      if (pendingMessages.isNotEmpty()) {
        draft.copy(
          selectedContact = contact,
          isMultipleMode = true,
          multipleMessages = pendingMessages,
        )
      } else {
        draft.copy(selectedContact = contact)
      }
    }
  }

  fun saveBatchAsTemplate(record: ExecutionRecord) {
    val messages = record.messageTraces.map { it.messagePreview }.filter { it.isNotBlank() }
    if (messages.isEmpty()) return
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.saveMessageList(
          SavedMessageList(
            title = "Lote ${record.contactName}".take(40),
            isMultipleMode = true,
            messages = messages,
            singleMessage = "",
            repeatCount = 1,
            updatedAtMillis = System.currentTimeMillis(),
          ),
        )
      }
    }
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(container) as T
      }
  }
}

