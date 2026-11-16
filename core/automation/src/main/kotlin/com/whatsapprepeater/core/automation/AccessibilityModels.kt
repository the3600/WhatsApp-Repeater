package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.ContactInfo

data class NodeBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  val width: Int get() = (right - left).coerceAtLeast(0)
  val height: Int get() = (bottom - top).coerceAtLeast(0)
  val centerX: Int get() = (left + right) / 2
  val centerY: Int get() = (top + bottom) / 2
}

/**
 * Snapshot inmutable de un nodo de accesibilidad para inspección sin depender
 * directamente del ciclo de vida de AccessibilityNodeInfo.
 */
data class UiNodeSnapshot(
  val nodeId: String,
  val packageName: String? = null,
  val className: String? = null,
  val viewIdResourceName: String? = null,
  val text: String? = null,
  val contentDescription: String? = null,
  val hintText: String? = null,
  val isClickable: Boolean = false,
  val isEditable: Boolean = false,
  val isEnabled: Boolean = true,
  val isVisibleToUser: Boolean = true,
  val bounds: NodeBounds = NodeBounds(0, 0, 0, 0),
  val children: List<UiNodeSnapshot> = emptyList(),
) {
  fun flatten(): List<UiNodeSnapshot> = buildList {
    add(this@UiNodeSnapshot)
    for (child in children) {
      addAll(child.flatten())
    }
  }
}

data class ScreenSnapshot(
  val foregroundPackage: String?,
  val screenWidth: Int,
  val screenHeight: Int,
  val root: UiNodeSnapshot?,
)

sealed interface WhatsAppScreen {
  data class OpenConversation(
    val headerTexts: List<String>,
    val inputNode: UiNodeSnapshot?,
    val sendButtonNode: UiNodeSnapshot?,
    val micButtonPresent: Boolean,
  ) : WhatsAppScreen

  data class HomeChatList(
    val searchButtonNode: UiNodeSnapshot?,
    val chatRows: List<UiNodeSnapshot>,
  ) : WhatsAppScreen

  data class SearchMode(
    val searchInputNode: UiNodeSnapshot?,
    val resultRows: List<UiNodeSnapshot>,
  ) : WhatsAppScreen

  data class UnexpectedOrError(
    val reason: String,
    val isSecurityOrCaptcha: Boolean,
  ) : WhatsAppScreen

  data class ExternalApp(
    val packageName: String?,
  ) : WhatsAppScreen
}

sealed interface RecipientVerificationResult {
  data class Verified(val matchedText: String) : RecipientVerificationResult
  data class Mismatch(val observedHeaders: List<String>, val reason: String) : RecipientVerificationResult
}

/**
 * Interfaz que desacopla el motor de automatización del servicio de accesibilidad real.
 */
interface AccessibilityDriver {
  val isServiceConnected: kotlinx.coroutines.flow.StateFlow<Boolean>

  fun openWhatsAppChat(contact: ContactInfo, targetPackage: String): Boolean

  fun openWhatsAppHome(targetPackage: String): Boolean

  fun captureScreenSnapshot(): ScreenSnapshot?

  fun clickNode(nodeId: String): Boolean

  fun setText(nodeId: String, text: String): Boolean

  fun navigateBack(): Boolean
}

