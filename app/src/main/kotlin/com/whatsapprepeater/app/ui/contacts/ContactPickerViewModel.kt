package com.whatsapprepeater.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactPickerUiState(
  val hasContactsPermission: Boolean = false,
  val isLoading: Boolean = false,
  val searchQuery: String = "",
  val filteredContacts: List<ContactInfo> = emptyList(),
  val selectedContact: ContactInfo? = null,
  val countryCode: String = "+57",
  val manualNumberInput: String = "",
  val manualContactName: String = "",
  val manualValidationError: String? = null,
  val loadError: String? = null,
) {
  val fullManualE164: String
    get() {
      val cleanDigits = manualNumberInput.filter { it.isDigit() }
      return "$countryCode$cleanDigits"
    }

  val isManualNumberValid: Boolean
    get() = E164_REGEX.matches(fullManualE164)

  companion object {
    val E164_REGEX = Regex("^\\+[1-9]\\d{6,14}$")
  }
}

class ContactPickerViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val permissionGranted = MutableStateFlow(false)
  private val isLoading = MutableStateFlow(false)
  private val searchQuery = MutableStateFlow("")
  private val allContacts = MutableStateFlow<List<ContactInfo>>(emptyList())
  private val countryCode = MutableStateFlow("+57")
  private val manualNumberInput = MutableStateFlow("")
  private val manualContactName = MutableStateFlow("")
  private val loadError = MutableStateFlow<String?>(null)

  val uiState: StateFlow<ContactPickerUiState> = combine(
    permissionGranted,
    isLoading,
    searchQuery,
    allContacts,
    container.draft,
    countryCode,
    manualNumberInput,
    manualContactName,
    loadError,
  ) { values ->
    val granted = values[0] as Boolean
    val loading = values[1] as Boolean
    val query = values[2] as String
    @Suppress("UNCHECKED_CAST")
    val contacts = values[3] as List<ContactInfo>
    val draft = values[4] as com.whatsapprepeater.app.AutomationDraft
    val cc = values[5] as String
    val manualNum = values[6] as String
    val manualName = values[7] as String
    val error = values[8] as String?

    val trimmed = query.trim().lowercase()
    val filtered = if (trimmed.isEmpty()) {
      contacts
    } else {
      val digitNeedle = trimmed.filter { it.isDigit() }
      contacts.filter {
        it.name.lowercase().contains(trimmed) ||
          it.phoneNumber.lowercase().contains(trimmed) ||
          (digitNeedle.isNotEmpty() && it.normalizedDigits.contains(digitNeedle))
      }
    }

    val fullE164 = "$cc${manualNum.filter { it.isDigit() }}"
    val manualErr = if (manualNum.isNotBlank() && !ContactPickerUiState.E164_REGEX.matches(fullE164)) {
      "Ingresa un número válido con código de país (7 a 15 dígitos)."
    } else {
      null
    }

    ContactPickerUiState(
      hasContactsPermission = granted,
      isLoading = loading,
      searchQuery = query,
      filteredContacts = filtered,
      selectedContact = draft.selectedContact,
      countryCode = cc,
      manualNumberInput = manualNum,
      manualContactName = manualName,
      manualValidationError = manualErr,
      loadError = error,
    )
  }.flowOn(Dispatchers.Default)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ContactPickerUiState(),
    )

  fun onPermissionStateChanged(granted: Boolean) {
    permissionGranted.value = granted
    if (granted && allContacts.value.isEmpty()) {
      loadContacts()
    }
  }

  fun loadContacts() {
    if (!permissionGranted.value) return
    viewModelScope.launch {
      isLoading.value = true
      loadError.value = null
      try {
        allContacts.value = container.contactsRepository.loadPhoneContacts()
      } catch (e: Exception) {
        loadError.value = e.message ?: "No se pudieron leer los contactos."
      } finally {
        isLoading.value = false
      }
    }
  }

  fun onSearchQueryChange(query: String) {
    searchQuery.value = query
  }

  fun selectSingleContact(contact: ContactInfo) {
    container.updateDraft { it.copy(selectedContact = contact) }
  }

  fun onCountryCodeSelected(code: String) {
    countryCode.value = code
  }

  fun onManualNumberChanged(raw: String) {
    val trimmed = raw.trim()
    if (trimmed.startsWith("+") && trimmed.length >= 4) {
      val knownPrefixes = listOf("+57", "+34", "+52", "+54", "+51", "+56", "+1")
      val matched = knownPrefixes.firstOrNull { trimmed.startsWith(it) }
      if (matched != null) {
        countryCode.value = matched
        manualNumberInput.value = trimmed.removePrefix(matched).filter { it.isDigit() }.take(14)
        return
      }
    }
    manualNumberInput.value = raw.filter { it.isDigit() }.take(15)
  }

  fun onManualNameChanged(name: String) {
    manualContactName.value = name.take(60)
  }

  fun selectManualNumberAsRecipient(): ContactInfo? {
    val current = uiState.value
    if (!current.isManualNumberValid) return null
    val e164 = current.fullManualE164
    val displayName = current.manualContactName.trim().ifBlank { e164 }
    val contact = ContactInfo(
      id = "manual_$e164",
      name = displayName,
      phoneNumber = e164,
    )
    container.updateDraft { it.copy(selectedContact = contact) }
    return contact
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ContactPickerViewModel(container) as T
      }
  }
}
