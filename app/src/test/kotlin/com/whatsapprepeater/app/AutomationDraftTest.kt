package com.whatsapprepeater.app

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.MessageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDraftTest {

  @Test
  fun `builds confirmed config only when contact is present`() {
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
  }
}

