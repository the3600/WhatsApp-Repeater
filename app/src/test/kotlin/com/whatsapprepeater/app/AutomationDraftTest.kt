package com.whatsapprepeater.app

import com.whatsapprepeater.app.ui.config.IntervalConfigUiState
import com.whatsapprepeater.app.ui.contacts.ContactPickerUiState
import com.whatsapprepeater.app.ui.messages.MessageEditorUiState
import com.whatsapprepeater.core.model.AutomationConfig
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
}
