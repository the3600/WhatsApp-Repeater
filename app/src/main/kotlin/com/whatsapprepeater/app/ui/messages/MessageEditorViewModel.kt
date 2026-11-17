package com.whatsapprepeater.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageEditorUiState(
  val isMultipleMode: Boolean = true,
  val multipleMessages: List<String> = emptyList(),
  val newMessageInput: String = "",
  val singleMessage: String = "",
  val repeatCount: Int = 1,
  val maxRepeatAllowed: Int = SafetyLimits.DEFAULT_MAX_REPEAT_COUNT,
  val savedLists: List<SavedMessageList> = emptyList(),
  val validationError: String? = null,
) {
  val canProceedMultiple: Boolean
    get() = multipleMessages.any { it.isNotBlank() }

  val canProceedRepeat: Boolean
    get() = singleMessage.isNotBlank() && repeatCount in SafetyLimits.MIN_REPEAT_COUNT..maxRepeatAllowed

  val canProceed: Boolean
    get() = if (isMultipleMode) canProceedMultiple else canProceedRepeat
}

class MessageEditorViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val newMessageInput = MutableStateFlow("")
  private val validationError = MutableStateFlow<String?>(null)

  val uiState: StateFlow<MessageEditorUiState> = combine(
    container.draft,
    container.settingsRepository.settings,
    container.savedConfigRepository.observeSavedMessageLists(),
    newMessageInput,
    validationError,
  ) { draft, settings, savedLists, newMsg, error ->
    MessageEditorUiState(
      isMultipleMode = draft.isMultipleMode,
      multipleMessages = draft.multipleMessages,
      newMessageInput = newMsg,
      singleMessage = draft.singleMessage,
      repeatCount = draft.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, settings.maxRepeatCount),
      maxRepeatAllowed = settings.maxRepeatCount,
      savedLists = savedLists,
      validationError = error,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = MessageEditorUiState(),
  )

  fun setMultipleMode(multiple: Boolean) {
    validationError.value = null
    container.updateDraft { it.copy(isMultipleMode = multiple) }
  }

  fun onNewMessageInputChanged(text: String) {
    validationError.value = null
    newMessageInput.value = text.take(SafetyLimits.MAX_MESSAGE_LENGTH)
  }

  fun wrapOrAppendFormatting(marker: String) {
    newMessageInput.value = formatMessageSnippet(newMessageInput.value, marker)
      .take(SafetyLimits.MAX_MESSAGE_LENGTH)
  }

  fun addMessageToList() {
    val cleaned = newMessageInput.value.trim()
    if (cleaned.isEmpty()) {
      validationError.value = "Escribe un mensaje antes de agregarlo."
      return
    }
    val currentList = container.draft.value.multipleMessages
    if (currentList.size >= SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT) {
      validationError.value = "Límite máximo de ${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT} mensajes."
      return
    }
    container.updateDraft { it.copy(multipleMessages = currentList + cleaned) }
    newMessageInput.value = ""
    validationError.value = null
  }

  fun editMessageAt(index: Int, updatedText: String) {
    val cleaned = updatedText.trim().take(SafetyLimits.MAX_MESSAGE_LENGTH)
    if (cleaned.isEmpty()) return
    container.updateDraft { draft ->
      if (index !in draft.multipleMessages.indices) return@updateDraft draft
      val mutable = draft.multipleMessages.toMutableList()
      mutable[index] = cleaned
      draft.copy(multipleMessages = mutable)
    }
  }

  fun removeMessageAt(index: Int) {
    container.updateDraft { draft ->
      if (index !in draft.multipleMessages.indices) return@updateDraft draft
      val mutable = draft.multipleMessages.toMutableList()
      mutable.removeAt(index)
      draft.copy(multipleMessages = mutable)
    }
  }

  fun clearAllMultipleMessages() {
    container.updateDraft { it.copy(multipleMessages = emptyList()) }
  }

  fun moveMessageUp(index: Int) {
    if (index <= 0) return
    container.updateDraft { draft ->
      if (index !in draft.multipleMessages.indices) return@updateDraft draft
      val mutable = draft.multipleMessages.toMutableList()
      val item = mutable.removeAt(index)
      mutable.add(index - 1, item)
      draft.copy(multipleMessages = mutable)
    }
  }

  fun moveMessageDown(index: Int) {
    if (index < 0 || index >= container.draft.value.multipleMessages.lastIndex) return
    container.updateDraft { draft ->
      if (index !in draft.multipleMessages.indices) return@updateDraft draft
      val mutable = draft.multipleMessages.toMutableList()
      val item = mutable.removeAt(index)
      mutable.add(index + 1, item)
      draft.copy(multipleMessages = mutable)
    }
  }

  fun onSingleMessageChanged(text: String) {
    validationError.value = null
    container.updateDraft { it.copy(singleMessage = text.take(SafetyLimits.MAX_MESSAGE_LENGTH)) }
  }

  fun onRepeatCountChanged(count: Int) {
    val maxAllowed = uiState.value.maxRepeatAllowed
    val clamped = count.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, maxAllowed)
    container.updateDraft { it.copy(repeatCount = clamped) }
  }

  fun selectRepeatPreset(preset: Int) {
    val safePreset = preset.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT)
    val currentMax = uiState.value.maxRepeatAllowed
    if (safePreset > currentMax) {
      container.updateDraft { it.copy(repeatCount = safePreset) }
      viewModelScope.launch {
        withContext(NonCancellable) {
          container.settingsRepository.updateMaxRepeatCount(safePreset)
        }
      }
    } else {
      container.updateDraft { it.copy(repeatCount = safePreset) }
    }
  }

  fun updateMaxRepeatAllowed(newMax: Int) {
    val safeMax = newMax.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT)
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.settingsRepository.updateMaxRepeatCount(safeMax)
      }
      container.updateDraft { draft ->
        draft.copy(repeatCount = draft.repeatCount.coerceAtMost(safeMax))
      }
    }
  }

  fun saveCurrentAsTemplate(title: String) {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return
    val current = container.draft.value
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.saveMessageList(
          SavedMessageList(
            title = cleanTitle,
            isMultipleMode = current.isMultipleMode,
            messages = current.multipleMessages,
            singleMessage = current.singleMessage,
            repeatCount = current.repeatCount,
            updatedAtMillis = System.currentTimeMillis(),
          ),
        )
      }
    }
  }

  fun loadSavedTemplate(template: SavedMessageList) {
    val targetLimit = uiState.value.maxRepeatAllowed.coerceAtLeast(template.repeatCount)
      .coerceAtMost(SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT)
    if (targetLimit > uiState.value.maxRepeatAllowed) {
      viewModelScope.launch {
        withContext(NonCancellable) {
          container.settingsRepository.updateMaxRepeatCount(targetLimit)
        }
      }
    }
    container.updateDraft {
      it.copy(
        isMultipleMode = template.isMultipleMode,
        multipleMessages = template.messages,
        singleMessage = template.singleMessage,
        repeatCount = template.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, targetLimit),
      )
    }
  }

  fun deleteSavedTemplate(id: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.deleteMessageList(id)
      }
    }
  }

  companion object {
    internal fun formatMessageSnippet(current: String, marker: String): String = when {
      current.isBlank() -> "${marker}texto$marker"
      current.endsWith(" ") || current.endsWith("\n") -> "$current${marker}texto$marker"
      !current.contains(marker) -> "$marker$current$marker"
      else -> "$current ${marker}texto$marker"
    }

    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MessageEditorViewModel(container) as T
      }
  }
}
