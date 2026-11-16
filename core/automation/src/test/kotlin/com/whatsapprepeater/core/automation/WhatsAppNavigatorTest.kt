package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppNavigatorTest {

  private val navigator = WhatsAppNavigator()
  private val contact = ContactInfo(id = "1", name = "María López", phoneNumber = "+57 300 123 4567")

  @Test
  fun `recognizes open conversation and verifies matching contact name`() {
    val root = UiNodeSnapshot(
      nodeId = "root",
      packageName = AutomationConfig.WHATSAPP_PACKAGE,
      children = listOf(
        UiNodeSnapshot(
          nodeId = "header_title",
          text = "María López",
          bounds = NodeBounds(120, 60, 600, 140),
        ),
        UiNodeSnapshot(
          nodeId = "input_field",
          className = "android.widget.EditText",
          hintText = "Mensaje",
          text = "Hola!",
          isEditable = true,
          bounds = NodeBounds(80, 1750, 880, 1870),
        ),
        UiNodeSnapshot(
          nodeId = "send_btn",
          contentDescription = "Enviar",
          isClickable = true,
          isEnabled = true,
          bounds = NodeBounds(900, 1750, 1020, 1870),
        ),
      ),
    )

    val screen = navigator.inspectScreen(
      ScreenSnapshot(AutomationConfig.WHATSAPP_PACKAGE, 1080, 1920, root),
    )

    assertTrue(screen is WhatsAppScreen.OpenConversation)
    val open = screen as WhatsAppScreen.OpenConversation
    val verification = navigator.verifyRecipientMatches(open, contact)
    assertTrue(verification is RecipientVerificationResult.Verified)
    assertTrue(navigator.isMessageReadyInInput(open.inputNode, "Hola!"))
    assertEquals("send_btn", open.sendButtonNode?.nodeId)
  }

  @Test
  fun `rejects recipient mismatch when another chat is open`() {
    val screen = WhatsAppScreen.OpenConversation(
      headerTexts = listOf("Carlos Pérez"),
      inputNode = null,
      sendButtonNode = null,
      micButtonPresent = true,
    )

    val verification = navigator.verifyRecipientMatches(screen, contact)
    assertTrue(verification is RecipientVerificationResult.Mismatch)
  }

  @Test
  fun `detects security or captcha screen and flags blocking`() {
    val root = UiNodeSnapshot(
      nodeId = "root",
      packageName = AutomationConfig.WHATSAPP_PACKAGE,
      children = listOf(
        UiNodeSnapshot(
          nodeId = "captcha_text",
          text = "Verifica que eres humano para continuar",
          bounds = NodeBounds(80, 400, 1000, 520),
        ),
      ),
    )

    val screen = navigator.inspectScreen(
      ScreenSnapshot(AutomationConfig.WHATSAPP_PACKAGE, 1080, 1920, root),
    )

    assertTrue(screen is WhatsAppScreen.UnexpectedOrError)
    assertTrue((screen as WhatsAppScreen.UnexpectedOrError).isSecurityOrCaptcha)
  }

  @Test
  fun `recognizes home chat list with search button`() {
    val root = UiNodeSnapshot(
      nodeId = "root",
      packageName = AutomationConfig.WHATSAPP_PACKAGE,
      children = listOf(
        UiNodeSnapshot(nodeId = "tab_chats", text = "Chats", bounds = NodeBounds(40, 200, 200, 260)),
        UiNodeSnapshot(
          nodeId = "search_btn",
          contentDescription = "Buscar",
          isClickable = true,
          bounds = NodeBounds(880, 60, 980, 160),
        ),
      ),
    )

    val screen = navigator.inspectScreen(
      ScreenSnapshot(AutomationConfig.WHATSAPP_PACKAGE, 1080, 1920, root),
    )

    assertTrue(screen is WhatsAppScreen.HomeChatList)
    assertEquals("search_btn", (screen as WhatsAppScreen.HomeChatList).searchButtonNode?.nodeId)
  }

  @Test
  fun `reports external app without needing any traversed node tree`() {
    val screen = navigator.inspectScreen(
      ScreenSnapshot(
        foregroundPackage = "com.android.settings",
        screenWidth = 1080,
        screenHeight = 1920,
        root = null,
      ),
    )

    assertTrue(screen is WhatsAppScreen.ExternalApp)
    assertEquals("com.android.settings", (screen as WhatsAppScreen.ExternalApp).packageName)
  }
}

