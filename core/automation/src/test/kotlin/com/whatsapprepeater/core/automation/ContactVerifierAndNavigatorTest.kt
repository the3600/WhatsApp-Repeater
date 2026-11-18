package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactVerifierAndNavigatorTest {

  private val navigator = WhatsAppNavigator()

  @Test
  fun `verifies exact contact name with invisible BiDi marks, NBSP and trailing separator`() {
    val contact = ContactInfo(
      id = "c1",
      name = "\u200EMi\u00A0Princesa hermosa\uFE0F",
      phoneNumber = "+525576294320",
    )
    val result = ContactVerifier.verify(
      headerTexts = listOf("Mi Princesa hermosa -"),
      contact = contact,
    )
    assertTrue("Debe verificar con normalización de formato y separador", result is RecipientVerificationResult.Verified)
  }

  @Test
  fun `separates toolbar header from RecyclerView recent chat messages including timestamps`() {
    val contact = ContactInfo(id = "c1", name = "Mi Princesa hermosa", phoneNumber = "+525576294320")
    val root = UiNodeSnapshot(
      nodeId = "root",
      packageName = AutomationConfig.WHATSAPP_PACKAGE,
      children = listOf(
        UiNodeSnapshot(
          nodeId = "toolbar",
          viewIdResourceName = "com.whatsapp:id/conversation_toolbar",
          bounds = NodeBounds(0, 80, 1080, 230),
          children = listOf(
            UiNodeSnapshot(
              nodeId = "contact_name",
              viewIdResourceName = "com.whatsapp:id/conversation_contact_name",
              text = "Mi Princesa hermosa",
              bounds = NodeBounds(140, 100, 720, 165),
            ),
            UiNodeSnapshot(
              nodeId = "contact_status",
              viewIdResourceName = "com.whatsapp:id/conversation_contact_status",
              text = "toca aquí para info. del contacto",
              bounds = NodeBounds(140, 166, 720, 215),
            ),
          ),
        ),
        UiNodeSnapshot(
          nodeId = "msg_list",
          className = "androidx.recyclerview.widget.RecyclerView",
          viewIdResourceName = "android:id/list",
          bounds = NodeBounds(0, 231, 1080, 1730),
          children = listOf(
            UiNodeSnapshot(
              nodeId = "msg_1",
              text = "5:25 p. m. - Yo te llamo. Ahora sí",
              bounds = NodeBounds(80, 240, 900, 320),
            ),
            UiNodeSnapshot(
              nodeId = "msg_2",
              text = "Otro Contacto Falso",
              bounds = NodeBounds(80, 325, 900, 390),
            ),
          ),
        ),
        UiNodeSnapshot(
          nodeId = "entry",
          className = "android.widget.EditText",
          viewIdResourceName = "com.whatsapp:id/entry",
          hintText = "Mensaje",
          isEditable = true,
          bounds = NodeBounds(80, 1750, 880, 1870),
        ),
      ),
    )

    val screen = navigator.inspectScreen(
      ScreenSnapshot(AutomationConfig.WHATSAPP_PACKAGE, 1080, 1920, root),
    )
    assertTrue(screen is WhatsAppScreen.OpenConversation)
    val open = screen as WhatsAppScreen.OpenConversation

    assertTrue(
      "No deben entrar mensajes del RecyclerView en headerTexts: ${open.headerTexts}",
      open.headerTexts.none { it.contains("Yo te llamo") || it.contains("Otro Contacto") },
    )
    val verified = navigator.verifyRecipientMatches(open, contact)
    assertTrue(verified is RecipientVerificationResult.Verified)
  }

  @Test
  fun `verifies matching phone numbers including Mexico +521 vs +52 and rejects conflicting numbers`() {
    val contact = ContactInfo(id = "mx1", name = "Mi Princesa hermosa", phoneNumber = "+525576294320")

    val matchWith521 = ContactVerifier.verify(
      headerTexts = listOf("+52 1 55 7629 4320", "Cuenta de empresa"),
      contact = contact,
    )
    assertTrue(matchWith521 is RecipientVerificationResult.Verified)

    val mismatchPhone = ContactVerifier.verify(
      headerTexts = listOf("Mi Princesa hermosa", "+52 55 7629 9999"),
      contact = contact,
    )
    assertTrue("Número diferente debe bloquear por seguridad", mismatchPhone is RecipientVerificationResult.Mismatch)
  }

  @Test
  fun `rejects similar contact names to prevent confusing two contacts`() {
    val juan = ContactInfo(id = "j1", name = "Juan Pérez", phoneNumber = "+573001112222")
    val mismatch = ContactVerifier.verify(
      headerTexts = listOf("Juan Pérez García"),
      contact = juan,
    )
    assertTrue("No debe confundir Juan Pérez con Juan Pérez García", mismatch is RecipientVerificationResult.Mismatch)

    val ana = ContactInfo(id = "a1", name = "Ana", phoneNumber = "+573001113333")
    val mismatchAna = ContactVerifier.verify(
      headerTexts = listOf("Ana María"),
      contact = ana,
    )
    assertTrue("No debe confundir Ana con Ana María", mismatchAna is RecipientVerificationResult.Mismatch)
  }

  @Test
  fun `handles truncated long contact names safely vs ambiguous short truncation`() {
    val longContact = ContactInfo(
      id = "l1",
      name = "Distribuidora Internacional del Norte S.A.",
      phoneNumber = "+573004445555",
    )

    val descriptiveTruncated = ContactVerifier.verify(
      headerTexts = listOf("Distribuidora Internacio…"),
      contact = longContact,
    )
    assertTrue(descriptiveTruncated is RecipientVerificationResult.Verified)

    val tooShortTruncated = ContactVerifier.verify(
      headerTexts = listOf("Distr..."),
      contact = longContact,
    )
    assertTrue("Truncamiento demasiado corto es ambiguo y debe detenerse", tooShortTruncated is RecipientVerificationResult.Mismatch)
  }

  @Test
  fun `rejects ambiguous timestamps or message-only headers and handles WhatsApp Business subtitle`() {
    val bizContact = ContactInfo(id = "b1", name = "Soporte Ventas", phoneNumber = "+573009990000")

    val onlyTime = ContactVerifier.verify(
      headerTexts = listOf("5:25 p. m.", "En línea"),
      contact = bizContact,
    )
    assertTrue(onlyTime is RecipientVerificationResult.Mismatch)

    val bizHeader = ContactVerifier.verify(
      headerTexts = listOf("Soporte Ventas • Cuenta de empresa"),
      contact = bizContact,
    )
    assertTrue(bizHeader is RecipientVerificationResult.Verified)
  }

  @Test
  fun `does not confuse home chat list Meta AI search bar with open conversation`() {
    val root = UiNodeSnapshot(
      nodeId = "root",
      packageName = AutomationConfig.WHATSAPP_PACKAGE,
      children = listOf(
        UiNodeSnapshot(nodeId = "tab_chats", text = "Chats", bounds = NodeBounds(40, 2100, 200, 2200)),
        UiNodeSnapshot(
          nodeId = "meta_search",
          className = "android.widget.EditText",
          hintText = "Preguntar a Meta AI o buscar",
          isEditable = true,
          bounds = NodeBounds(40, 260, 1040, 360),
        ),
        UiNodeSnapshot(
          nodeId = "row1",
          isClickable = true,
          bounds = NodeBounds(0, 400, 1080, 560),
          children = listOf(
            UiNodeSnapshot(nodeId = "r1_name", text = "Mi Princesa hermosa"),
            UiNodeSnapshot(nodeId = "r1_msg", text = "5:25 p. m. - Yo te llamo. Ahora sí"),
          ),
        ),
      ),
    )

    val screen = navigator.inspectScreen(
      ScreenSnapshot(AutomationConfig.WHATSAPP_PACKAGE, 1080, 2400, root),
    )
    assertEquals(WhatsAppScreen.HomeChatList::class, screen::class)
  }
}
