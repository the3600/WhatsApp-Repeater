package com.whatsapprepeater.app.ui.messages

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.DynamicVariablesEngine
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
import com.whatsapprepeater.core.model.VariableResolutionResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
  val selectedContact: ContactInfo? = null,
  val livePreviewText: String = "",
  val variableError: String? = null,
  val detectedLinksCount: Int = 0,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
) {
  val canProceedMultiple: Boolean
    get() = multipleMessages.any { it.isNotBlank() }

  val canProceedRepeat: Boolean
    get() = singleMessage.isNotBlank() &&
      repeatCount in SafetyLimits.MIN_REPEAT_COUNT..maxRepeatAllowed &&
      variableError == null

  val canProceed: Boolean
    get() = if (isMultipleMode) canProceedMultiple else canProceedRepeat
}

class MessageEditorViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val newMessageInput = MutableStateFlow("")
  private val _newMessageField = MutableStateFlow(TextFieldValue(""))
  val newMessageField: StateFlow<TextFieldValue> = _newMessageField.asStateFlow()

  private val _singleMessageField = MutableStateFlow(
    TextFieldValue(container.draft.value.singleMessage, TextRange(container.draft.value.singleMessage.length)),
  )
  val singleMessageField: StateFlow<TextFieldValue> = _singleMessageField.asStateFlow()

  private val validationError = MutableStateFlow<String?>(null)
  private val historyVersion = MutableStateFlow(0)

  private val undoStack = ArrayDeque<TextFieldValue>()
  private val redoStack = ArrayDeque<TextFieldValue>()
  private val singleUndoStack = ArrayDeque<TextFieldValue>()
  private val singleRedoStack = ArrayDeque<TextFieldValue>()

  init {
    viewModelScope.launch {
      val recovered = runCatching { container.settingsRepository.composerDraft.first() }.getOrDefault("")
      if (recovered.isNotEmpty() && _newMessageField.value.text.isEmpty()) {
        val clamped = recovered.take(SafetyLimits.MAX_MESSAGE_LENGTH)
        _newMessageField.value = TextFieldValue(clamped, TextRange(clamped.length))
        newMessageInput.value = clamped
      }
    }
    viewModelScope.launch {
      container.draft.collect { draft ->
        if (draft.singleMessage != _singleMessageField.value.text) {
          val clamped = draft.singleMessage.take(SafetyLimits.MAX_MESSAGE_LENGTH)
          _singleMessageField.value = TextFieldValue(clamped, TextRange(clamped.length))
        }
      }
    }
  }

  val uiState: StateFlow<MessageEditorUiState> = combine(
    container.draft,
    container.settingsRepository.settings,
    container.savedConfigRepository.observeSavedMessageLists(),
    newMessageInput,
    validationError,
    historyVersion,
  ) { values ->
    val draft = values[0] as com.whatsapprepeater.app.AutomationDraft
    val settings = values[1] as com.whatsapprepeater.core.model.AppSettings
    @Suppress("UNCHECKED_CAST")
    val savedLists = values[2] as List<SavedMessageList>
    val newMsg = values[3] as String
    val error = values[4] as String?

    val activeText = if (draft.isMultipleMode) newMsg else draft.singleMessage
    val resolution = DynamicVariablesEngine.resolveOrValidate(activeText, draft.selectedContact)
    val preview = when (resolution) {
      is VariableResolutionResult.Resolved -> resolution.text
      is VariableResolutionResult.Unresolved -> activeText
    }
    val batchVarError = if (draft.isMultipleMode) {
      DynamicVariablesEngine.validateBatch(draft.multipleMessages, draft.selectedContact).second?.reason
    } else {
      (resolution as? VariableResolutionResult.Unresolved)?.reason
    }
    val canUndoNow = if (draft.isMultipleMode) undoStack.isNotEmpty() else singleUndoStack.isNotEmpty()
    val canRedoNow = if (draft.isMultipleMode) redoStack.isNotEmpty() else singleRedoStack.isNotEmpty()

    MessageEditorUiState(
      isMultipleMode = draft.isMultipleMode,
      multipleMessages = draft.multipleMessages,
      newMessageInput = newMsg,
      singleMessage = draft.singleMessage,
      repeatCount = draft.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, settings.maxRepeatCount),
      maxRepeatAllowed = settings.maxRepeatCount,
      savedLists = savedLists,
      validationError = error,
      selectedContact = draft.selectedContact,
      livePreviewText = preview,
      variableError = batchVarError ?: (resolution as? VariableResolutionResult.Unresolved)?.reason,
      detectedLinksCount = detectUrls(activeText).size,
      canUndo = canUndoNow,
      canRedo = canRedoNow,
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

  fun onNewMessageFieldChanged(value: TextFieldValue) {
    validationError.value = null
    val cappedText = value.text.take(SafetyLimits.MAX_MESSAGE_LENGTH)
    val capped = if (cappedText.length != value.text.length) {
      val maxIdx = cappedText.length
      TextFieldValue(
        text = cappedText,
        selection = TextRange(value.selection.start.coerceAtMost(maxIdx), value.selection.end.coerceAtMost(maxIdx)),
      )
    } else {
      value
    }
    val prev = _newMessageField.value
    if (prev.text != capped.text) {
      pushHistory(undoStack, redoStack, prev)
    }
    _newMessageField.value = capped
    newMessageInput.value = capped.text
    historyVersion.value += 1
    if (prev.text != capped.text) {
      viewModelScope.launch {
        runCatching { container.settingsRepository.updateComposerDraft(capped.text) }
      }
    }
  }

  fun onNewMessageInputChanged(text: String) {
    val capped = text.take(SafetyLimits.MAX_MESSAGE_LENGTH)
    onNewMessageFieldChanged(TextFieldValue(capped, TextRange(capped.length)))
  }

  fun wrapOrAppendFormatting(marker: String) {
    val currentField = _newMessageField.value
    val formatted = applyMarkdownToSelection(currentField, marker)
    onNewMessageFieldChanged(formatted)
  }

  fun applySingleMessageFormatting(marker: String) {
    val currentField = _singleMessageField.value
    val formatted = applyMarkdownToSelection(currentField, marker)
    onSingleMessageFieldChanged(formatted)
  }

  fun insertSnippetInComposer(snippet: String) {
    val updated = insertTextAtCursor(_newMessageField.value, snippet)
    onNewMessageFieldChanged(updated)
  }

  fun insertSnippetInSingleMessage(snippet: String) {
    val updated = insertTextAtCursor(_singleMessageField.value, snippet)
    onSingleMessageFieldChanged(updated)
  }

  fun duplicateFragment(isMultiple: Boolean) {
    if (isMultiple) {
      onNewMessageFieldChanged(duplicateFragmentInField(_newMessageField.value))
    } else {
      onSingleMessageFieldChanged(duplicateFragmentInField(_singleMessageField.value))
    }
  }

  fun clearFormatting(isMultiple: Boolean) {
    if (isMultiple) {
      onNewMessageFieldChanged(stripMarkdownMarkers(_newMessageField.value))
    } else {
      onSingleMessageFieldChanged(stripMarkdownMarkers(_singleMessageField.value))
    }
  }

  fun undoActiveEditor(isMultiple: Boolean) {
    if (isMultiple) {
      if (undoStack.isEmpty()) return
      val current = _newMessageField.value
      redoStack.addLast(current)
      val restored = undoStack.removeLast()
      _newMessageField.value = restored
      newMessageInput.value = restored.text
    } else {
      if (singleUndoStack.isEmpty()) return
      val current = _singleMessageField.value
      singleRedoStack.addLast(current)
      val restored = singleUndoStack.removeLast()
      _singleMessageField.value = restored
      container.updateDraft { it.copy(singleMessage = restored.text) }
    }
    historyVersion.value += 1
  }

  fun redoActiveEditor(isMultiple: Boolean) {
    if (isMultiple) {
      if (redoStack.isEmpty()) return
      val current = _newMessageField.value
      undoStack.addLast(current)
      val restored = redoStack.removeLast()
      _newMessageField.value = restored
      newMessageInput.value = restored.text
    } else {
      if (singleRedoStack.isEmpty()) return
      val current = _singleMessageField.value
      singleUndoStack.addLast(current)
      val restored = singleRedoStack.removeLast()
      _singleMessageField.value = restored
      container.updateDraft { it.copy(singleMessage = restored.text) }
    }
    historyVersion.value += 1
  }

  fun searchAndReplace(find: String, replace: String, isMultiple: Boolean, applyToSavedList: Boolean): Int {
    if (find.isEmpty()) return 0
    var count = 0
    if (isMultiple) {
      val current = _newMessageField.value.text
      if (current.contains(find)) {
        val matches = current.split(find).size - 1
        count += matches
        val next = current.replace(find, replace).take(SafetyLimits.MAX_MESSAGE_LENGTH)
        onNewMessageFieldChanged(TextFieldValue(next, TextRange(next.length)))
      }
      if (applyToSavedList) {
        container.updateDraft { draft ->
          val updatedList = draft.multipleMessages.map { msg ->
            if (msg.contains(find)) {
              count += msg.split(find).size - 1
              msg.replace(find, replace).take(SafetyLimits.MAX_MESSAGE_LENGTH)
            } else {
              msg
            }
          }
          draft.copy(multipleMessages = updatedList)
        }
      }
    } else {
      val current = _singleMessageField.value.text
      if (current.contains(find)) {
        count += current.split(find).size - 1
        val next = current.replace(find, replace).take(SafetyLimits.MAX_MESSAGE_LENGTH)
        onSingleMessageFieldChanged(TextFieldValue(next, TextRange(next.length)))
      }
    }
    return count
  }

  fun duplicateMessageAt(index: Int) {
    container.updateDraft { draft ->
      if (index !in draft.multipleMessages.indices) return@updateDraft draft
      if (draft.multipleMessages.size >= SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT) {
        validationError.value = "Límite máximo de ${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT} mensajes."
        return@updateDraft draft
      }
      val mutable = draft.multipleMessages.toMutableList()
      mutable.add(index + 1, mutable[index])
      draft.copy(multipleMessages = mutable)
    }
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
    val varCheck = DynamicVariablesEngine.resolveOrValidate(cleaned, container.draft.value.selectedContact)
    if (varCheck is VariableResolutionResult.Unresolved) {
      validationError.value = varCheck.reason
      return
    }
    container.updateDraft { it.copy(multipleMessages = currentList + cleaned) }
    undoStack.clear()
    redoStack.clear()
    _newMessageField.value = TextFieldValue("")
    newMessageInput.value = ""
    validationError.value = null
    historyVersion.value += 1
    viewModelScope.launch {
      runCatching { container.settingsRepository.updateComposerDraft("") }
    }
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

  fun removeMessagesAt(indices: Set<Int>) {
    if (indices.isEmpty()) return
    container.updateDraft { draft ->
      val remaining = draft.multipleMessages.filterIndexed { idx, _ -> idx !in indices }
      draft.copy(multipleMessages = remaining)
    }
  }

  fun duplicateMessagesAt(indices: Set<Int>) {
    if (indices.isEmpty()) return
    container.updateDraft { draft ->
      val toCopy = draft.multipleMessages.filterIndexed { idx, _ -> idx in indices }
      if (toCopy.isEmpty()) return@updateDraft draft
      val available = (SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT - draft.multipleMessages.size).coerceAtLeast(0)
      if (available == 0) {
        validationError.value = "Límite máximo de ${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT} mensajes."
        return@updateDraft draft
      }
      draft.copy(multipleMessages = draft.multipleMessages + toCopy.take(available))
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

  fun onSingleMessageFieldChanged(value: TextFieldValue) {
    validationError.value = null
    val cappedText = value.text.take(SafetyLimits.MAX_MESSAGE_LENGTH)
    val capped = if (cappedText.length != value.text.length) {
      val maxIdx = cappedText.length
      TextFieldValue(
        text = cappedText,
        selection = TextRange(value.selection.start.coerceAtMost(maxIdx), value.selection.end.coerceAtMost(maxIdx)),
      )
    } else {
      value
    }
    val prev = _singleMessageField.value
    if (prev.text != capped.text) {
      pushHistory(singleUndoStack, singleRedoStack, prev)
    }
    _singleMessageField.value = capped
    container.updateDraft { it.copy(singleMessage = capped.text) }
    historyVersion.value += 1
  }

  fun onSingleMessageChanged(text: String) {
    val capped = text.take(SafetyLimits.MAX_MESSAGE_LENGTH)
    onSingleMessageFieldChanged(TextFieldValue(capped, TextRange(capped.length)))
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

  fun saveCurrentAsTemplate(
    title: String,
    category: String = "General",
    tags: List<String> = emptyList(),
    isFavorite: Boolean = false,
  ) {
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
            category = category.trim().ifBlank { "General" },
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            isFavorite = isFavorite,
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
    container.loadTemplateIntoDraft(template.copy(repeatCount = template.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, targetLimit)))
  }

  fun deleteSavedTemplate(id: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.deleteMessageList(id)
      }
    }
  }

  private fun pushHistory(
    undo: ArrayDeque<TextFieldValue>,
    redo: ArrayDeque<TextFieldValue>,
    snapshot: TextFieldValue,
  ) {
    if (undo.lastOrNull()?.text == snapshot.text) return
    undo.addLast(snapshot)
    while (undo.size > MAX_UNDO_HISTORY) {
      undo.removeFirst()
    }
    redo.clear()
  }

  companion object {
    private const val MAX_UNDO_HISTORY = 30
    private val urlRegex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun detectUrls(text: String): List<String> =
      urlRegex.findAll(text).map { it.value }.toList()

    fun applyMarkdownToSelection(field: TextFieldValue, marker: String): TextFieldValue {
      val text = field.text
      val selStart = field.selection.min.coerceIn(0, text.length)
      val selEnd = field.selection.max.coerceIn(selStart, text.length)
      return if (selStart == selEnd) {
        val inserted = "${marker}texto$marker"
        val updated = (text.substring(0, selStart) + inserted + text.substring(selEnd))
          .take(SafetyLimits.MAX_MESSAGE_LENGTH)
        val innerStart = (selStart + marker.length).coerceAtMost(updated.length)
        val innerEnd = (innerStart + 5).coerceAtMost(updated.length)
        TextFieldValue(updated, TextRange(innerStart, innerEnd))
      } else {
        val selected = text.substring(selStart, selEnd)
        val wrapped = "$marker$selected$marker"
        val updated = (text.substring(0, selStart) + wrapped + text.substring(selEnd))
          .take(SafetyLimits.MAX_MESSAGE_LENGTH)
        val newStart = (selStart + marker.length).coerceAtMost(updated.length)
        val newEnd = (selEnd + marker.length).coerceAtMost(updated.length)
        TextFieldValue(updated, TextRange(newStart, newEnd))
      }
    }

    fun insertTextAtCursor(field: TextFieldValue, snippet: String): TextFieldValue {
      val text = field.text
      val start = field.selection.min.coerceIn(0, text.length)
      val end = field.selection.max.coerceIn(start, text.length)
      val updated = (text.substring(0, start) + snippet + text.substring(end))
        .take(SafetyLimits.MAX_MESSAGE_LENGTH)
      val cursor = (start + snippet.length).coerceAtMost(updated.length)
      return TextFieldValue(updated, TextRange(cursor))
    }

    fun duplicateFragmentInField(field: TextFieldValue): TextFieldValue {
      val text = field.text
      if (text.isEmpty()) return field
      val start = field.selection.min.coerceIn(0, text.length)
      val end = field.selection.max.coerceIn(start, text.length)
      return if (start < end) {
        val fragment = text.substring(start, end)
        val updated = (text.substring(0, end) + fragment + text.substring(end))
          .take(SafetyLimits.MAX_MESSAGE_LENGTH)
        val newStart = end.coerceAtMost(updated.length)
        val newEnd = (end + fragment.length).coerceAtMost(updated.length)
        TextFieldValue(updated, TextRange(newStart, newEnd))
      } else {
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val currentLine = text.substring(lineStart, lineEnd)
        if (currentLine.isEmpty()) return field
        val updated = (text.substring(0, lineEnd) + "\n" + currentLine + text.substring(lineEnd))
          .take(SafetyLimits.MAX_MESSAGE_LENGTH)
        val cursor = (lineEnd + 1 + currentLine.length).coerceAtMost(updated.length)
        TextFieldValue(updated, TextRange(cursor))
      }
    }

    fun stripMarkdownMarkers(field: TextFieldValue): TextFieldValue {
      val text = field.text
      if (text.isEmpty()) return field
      val start = field.selection.min.coerceIn(0, text.length)
      val end = field.selection.max.coerceIn(start, text.length)
      fun cleanMarkers(src: String): String =
        src.replace("```", "").replace("*", "").replace("_", "").replace("~", "")
      return if (start < end) {
        val cleaned = cleanMarkers(text.substring(start, end))
        val updated = (text.substring(0, start) + cleaned + text.substring(end))
          .take(SafetyLimits.MAX_MESSAGE_LENGTH)
        TextFieldValue(updated, TextRange(start, (start + cleaned.length).coerceAtMost(updated.length)))
      } else {
        val cleaned = cleanMarkers(text).take(SafetyLimits.MAX_MESSAGE_LENGTH)
        TextFieldValue(cleaned, TextRange(cleaned.length))
      }
    }

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
