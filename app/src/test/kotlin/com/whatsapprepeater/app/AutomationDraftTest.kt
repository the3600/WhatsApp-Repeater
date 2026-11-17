package com.whatsapprepeater.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.whatsapprepeater.app.ui.automation.AutomationUiState
import com.whatsapprepeater.app.ui.config.IntervalConfigUiState
import com.whatsapprepeater.app.ui.contacts.ContactPickerUiState
import com.whatsapprepeater.app.ui.messages.MessageEditorUiState
import com.whatsapprepeater.app.ui.messages.MessageEditorViewModel
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.SafetyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDraftTest {

  @Test
  fun `builds confirmed config only when contact is present and roundtrips persistence`() {
    val emptyDraft = AutomationDraft()
    assertNull(emptyDraft.toConfirmedConfig(confirmed = true))

    val draft = AutomationDraft(
      selectedContact = ContactInfo("1", "Laura", "+57 320 111 2222"),
      isMultipleMode = false,
      singleMessage = "Hola Laura",
      repeatCount = 3,
      minIntervalSeconds = 15,
      maxIntervalSeconds = 30,
      preferWhatsAppBusiness = true,
    )

    val config = draft.toConfirmedConfig(confirmed = true)!!
    assertTrue(config.userConfirmed)
    assertEquals(AutomationConfig.WHATSAPP_BUSINESS_PACKAGE, config.targetPackage)
    assertTrue(config.mode is MessageMode.RepeatSingleMessage)
    assertFalse(draft.toConfirmedConfig(confirmed = false)!!.userConfirmed)

    val restored = AutomationDraft.fromPersisted(draft.toPersisted())
    assertEquals("Laura", restored.selectedContact?.name)
    assertEquals("+57 320 111 2222", restored.selectedContact?.phoneNumber)
    assertEquals("Hola Laura", restored.singleMessage)
    assertEquals(3, restored.repeatCount)
    assertEquals(15, restored.minIntervalSeconds)
    assertEquals(30, restored.maxIntervalSeconds)
    assertTrue(restored.preferWhatsAppBusiness)
  }

  @Test
  fun `validates international E164 manual phone numbers in ContactPickerUiState`() {
    val valid = ContactPickerUiState(countryCode = "+57", manualNumberInput = "3001112222")
    assertEquals("+573001112222", valid.fullManualE164)
    assertTrue(valid.isManualNumberValid)

    val tooShort = ContactPickerUiState(countryCode = "+34", manualNumberInput = "123")
    assertFalse(tooShort.isManualNumberValid)

    val leadingZeroCode = ContactPickerUiState(countryCode = "+01", manualNumberInput = "3001112222")
    assertFalse(leadingZeroCode.isManualNumberValid)
  }

  @Test
  fun `validates interval ranges and calculates variability in IntervalConfigUiState`() {
    val valid = IntervalConfigUiState(minSeconds = 15, maxSeconds = 35, scheduledCount = 5)
    assertTrue(valid.isValid)
    assertEquals(10.0, valid.variabilitySeconds, 0.01)
    assertTrue(valid.estimatedTotalSeconds > 0)

    val invalidOrder = IntervalConfigUiState(
      minSeconds = 40,
      maxSeconds = 20,
      validationError = "El intervalo mínimo no puede ser mayor que el máximo.",
    )
    assertFalse(invalidOrder.isValid)
  }

  @Test
  fun `enforces message editor mode validation rules`() {
    val emptyMultiple = MessageEditorUiState(isMultipleMode = true, multipleMessages = emptyList())
    assertFalse(emptyMultiple.canProceedMultiple)

    val validMultiple = MessageEditorUiState(isMultipleMode = true, multipleMessages = listOf("Hola"))
    assertTrue(validMultiple.canProceedMultiple)

    val overLimitRepeat = MessageEditorUiState(
      isMultipleMode = false,
      singleMessage = "Hola",
      repeatCount = SafetyLimits.DEFAULT_MAX_REPEAT_COUNT + 1,
      maxRepeatAllowed = SafetyLimits.DEFAULT_MAX_REPEAT_COUNT,
    )
    assertFalse(overLimitRepeat.canProceedRepeat)
  }

  @Test
  fun `automation ui state enforces confirmation service connection and non running guards`() {
    val validDraft = AutomationDraft(
      selectedContact = ContactInfo("1", "Laura", "+573001112222"),
      isMultipleMode = false,
      singleMessage = "Hola",
      repeatCount = 2,
      minIntervalSeconds = 10,
      maxIntervalSeconds = 20,
    )

    val ready = AutomationUiState(
      draft = validDraft,
      isServiceConnected = true,
      isWhatsAppInstalled = true,
      userCheckboxConfirmed = true,
    )
    assertTrue(ready.canStartAutomation)
    assertFalse(ready.copy(userCheckboxConfirmed = false).canStartAutomation)
    assertFalse(ready.copy(isServiceConnected = false).canStartAutomation)
    assertFalse(ready.copy(isWhatsAppInstalled = false).canStartAutomation)
    assertFalse(ready.copy(engineState = AutomationState(status = AutomationStatus.WRITING_MESSAGE)).canStartAutomation)
  }

  @Test
  fun `formats whatsapp markdown markers cleanly without dangling delimiters`() {
    assertEquals("*texto*", MessageEditorViewModel.formatMessageSnippet("", "*"))
    assertEquals("*Hola*", MessageEditorViewModel.formatMessageSnippet("Hola", "*"))
    assertEquals("Hola *texto*", MessageEditorViewModel.formatMessageSnippet("Hola ", "*"))
    assertEquals("*Hola* *texto*", MessageEditorViewModel.formatMessageSnippet("*Hola*", "*"))
  }

  @Test
  fun `editor preserves text selection when applying markdown and detects urls`() {
    val field = TextFieldValue("Hola mundo bonito", selection = TextRange(5, 10))
    val bolded = MessageEditorViewModel.applyMarkdownToSelection(field, "*")
    assertEquals("Hola *mundo* bonito", bolded.text)
    // Selection stays anchored on "mundo" inside the markers
    assertEquals("mundo", bolded.text.substring(bolded.selection.start, bolded.selection.end))

    val inserted = MessageEditorViewModel.insertTextAtCursor(
      TextFieldValue("Hola ", TextRange(5)),
      "{nombre}",
    )
    assertEquals("Hola {nombre}", inserted.text)
    assertEquals(TextRange(13), inserted.selection)

    val urls = MessageEditorViewModel.detectUrls("Visita https://example.com/promo hoy")
    assertEquals(listOf("https://example.com/promo"), urls)
  }

  @Test
  fun `confirmation blocks start when dynamic variable cannot be resolved`() {
    val draftWithUnknownVar = AutomationDraft(
      selectedContact = ContactInfo("1", "Laura", "+573001112222"),
      isMultipleMode = false,
      singleMessage = "Hola {variable_falsa}",
      repeatCount = 2,
      minIntervalSeconds = 10,
      maxIntervalSeconds = 20,
    )
    val state = AutomationUiState(
      draft = draftWithUnknownVar,
      isServiceConnected = true,
      isWhatsAppInstalled = true,
      userCheckboxConfirmed = true,
    )
    assertFalse(state.canStartAutomation)
    assertTrue(state.variableValidationError!!.contains("{variable_falsa}"))
  }
}
