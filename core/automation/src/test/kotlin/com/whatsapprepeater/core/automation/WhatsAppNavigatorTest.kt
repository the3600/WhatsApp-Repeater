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

  @Test
  fun `rejects short single or two character header substring false positives`() {
    val target = ContactInfo(id = "2", name = "Ana María", phoneNumber = "+57 300 999 8888")
    val falseHeaders = listOf("a", "n", "an", "ma", "x", "1", "En línea")
    for (badHeader in falseHeaders) {
      val screen = WhatsAppScreen.OpenConversation(
        headerTexts = listOf(badHeader),
        inputNode = null,
        sendButtonNode = null,
        micButtonPresent = true,
      )
      val verification = navigator.verifyRecipientMatches(screen, target)
      assertTrue(
        "No debe validar cabecera ambigua '$badHeader' para '${target.name}'",
        verification is RecipientVerificationResult.Mismatch,
      )
    }

    // Exact 2-char contact match remains valid
    val shortExactContact = ContactInfo(id = "3", name = "Al", phoneNumber = "+57 300 000 1111")
    val exactMatchScreen = WhatsAppScreen.OpenConversation(
      headerTexts = listOf("Al"),
      inputNode = null,
      sendButtonNode = null,
      micButtonPresent = true,
    )
    assertTrue(
      navigator.verifyRecipientMatches(exactMatchScreen, shortExactContact) is RecipientVerificationResult.Verified,
    )

    // Short 2-char contact name must NOT match longer unrelated name "Alberto" in search rows
    val albertoRow = UiNodeSnapshot(
      nodeId = "row_alberto",
      isClickable = true,
      children = listOf(UiNodeSnapshot(nodeId = "row_text", text = "Alberto Gómez")),
    )
    assertEquals(null, navigator.findMatchingChatRow(listOf(albertoRow), shortExactContact))
  }
}

