package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import java.text.Normalizer

/**
 * Reconoce pantallas de WhatsApp de forma heurística sin depender de un único
 * identificador de recurso fijo, ya que WhatsApp puede cambiar sus IDs entre versiones.
 */
class WhatsAppNavigator {

  private val supportedPackages = setOf(
    AutomationConfig.WHATSAPP_PACKAGE,
    AutomationConfig.WHATSAPP_BUSINESS_PACKAGE,
  )

  private val securityOrCaptchaKeywords = listOf(
    "captcha",
    "verify you are human",
    "verifica que eres humano",
    "security check",
    "verificacion de seguridad",
    "account is banned",
    "cuenta esta suspendida",
    "cuenta suspendida",
    "temporarily banned",
    "temporalmente suspendida",
    "this account cannot use whatsapp",
    "esta cuenta no puede usar whatsapp",
    "two-step verification",
    "verificacion en dos pasos",
    "enter pin",
    "ingresa tu pin",
  )

  private val errorDialogKeywords = listOf(
    "isn't on whatsapp",
    "is not on whatsapp",
    "no esta en whatsapp",
    "no tiene whatsapp",
    "no se encontro el numero",
    "the phone number",
    "el numero de telefono",
    "couldn't start chat",
    "no se pudo iniciar el chat",
    "invite to whatsapp",
    "invitar a whatsapp",
    "update whatsapp",
    "actualiza whatsapp",
    "you blocked this contact",
    "bloqueaste a este contacto",
  )

  private val sendDescriptions = listOf("enviar", "send")
  private val micDescriptions = listOf("mensaje de voz", "nota de voz", "voice message", "record audio")
  private val searchKeywords = listOf("buscar", "search")
  private val composerHints = listOf("mensaje", "message", "escribe", "type a message")

  fun inspectScreen(snapshot: ScreenSnapshot?): WhatsAppScreen {
    if (snapshot == null) {
      return WhatsAppScreen.UnexpectedOrError(
        reason = "No se pudo leer la jerarquía visual de la pantalla actual.",
        isSecurityOrCaptcha = false,
      )
    }

    val pkg = snapshot.foregroundPackage ?: snapshot.root?.packageName
    if (pkg != null && pkg !in supportedPackages) {
      return WhatsAppScreen.ExternalApp(pkg)
    }

    val root = snapshot.root ?: return WhatsAppScreen.UnexpectedOrError(
      reason = "No se pudo leer la jerarquía visual de la pantalla actual.",
      isSecurityOrCaptcha = false,
    )

    val visibleNodes = root.flatten().filter { it.isVisibleToUser }
    if (visibleNodes.isEmpty()) {
      return WhatsAppScreen.UnexpectedOrError(
        reason = "La pantalla de WhatsApp no expone nodos visibles para accesibilidad.",
        isSecurityOrCaptcha = false,
      )
    }

    // 1. Detectar bloqueos, CAPTCHA o diálogos de error primero (prioridad de seguridad).
    detectBlockingOrErrorScreen(visibleNodes)?.let { return it }

    val width = snapshot.screenWidth.coerceAtLeast(1080)
    val height = snapshot.screenHeight.coerceAtLeast(1920)

    // 2. Verificar si estamos en modo Búsqueda (EditText en la zona superior).
    val topSearchInput = findTopSearchInput(visibleNodes, height)
    if (topSearchInput != null) {
      val results = findClickableListRows(visibleNodes, minTop = topSearchInput.bounds.bottom)
      return WhatsAppScreen.SearchMode(
        searchInputNode = topSearchInput,
        resultRows = results,
      )
    }

    // 3. Verificar si estamos en una conversación abierta (campo de entrada en zona media/inferior).
    val composerInput = findMessageComposerInput(visibleNodes, height)
    if (composerInput != null) {
      val headerTexts = extractHeaderTexts(visibleNodes, height)
      val sendButton = findSendButton(visibleNodes, composerInput, width)
      val micPresent = visibleNodes.any { node ->
        val desc = normalizeText(node.contentDescription)
        micDescriptions.any { desc.contains(it) }
      }
      return WhatsAppScreen.OpenConversation(
        headerTexts = headerTexts,
        inputNode = composerInput,
        sendButtonNode = sendButton,
        micButtonPresent = micPresent,
      )
    }

    // 4. Verificar pantalla principal / lista de conversaciones.
    val searchButton = findSearchButton(visibleNodes, height)
    val chatRows = findClickableListRows(visibleNodes, minTop = (height * 0.12f).toInt())
    if (searchButton != null || hasMainTabs(visibleNodes)) {
      return WhatsAppScreen.HomeChatList(
        searchButtonNode = searchButton,
        chatRows = chatRows,
      )
    }

    return WhatsAppScreen.UnexpectedOrError(
      reason = "Pantalla de WhatsApp no reconocida o transición en curso.",
      isSecurityOrCaptcha = false,
    )
  }

  fun verifyRecipientMatches(
    screen: WhatsAppScreen.OpenConversation,
    contact: ContactInfo,
  ): RecipientVerificationResult {
    if (screen.headerTexts.isEmpty()) {
      return RecipientVerificationResult.Mismatch(
        observedHeaders = emptyList(),
        reason = "No se encontró el nombre ni el número del destinatario en la cabecera.",
      )
    }

    val targetNameNorm = normalizeText(contact.name)
    val targetDigits = contact.normalizedDigits

    for (header in screen.headerTexts) {
      val headerNorm = normalizeText(header)
      if (headerNorm.isEmpty()) continue

      if (targetNameNorm.length >= 2 && (headerNorm == targetNameNorm || headerNorm.contains(targetNameNorm) || targetNameNorm.contains(headerNorm))) {
        return RecipientVerificationResult.Verified(matchedText = header)
      }

      val headerDigits = header.filter { it.isDigit() }
      if (digitsMatch(targetDigits, headerDigits)) {
        return RecipientVerificationResult.Verified(matchedText = header)
      }
    }

    return RecipientVerificationResult.Mismatch(
      observedHeaders = screen.headerTexts,
      reason = "La cabecera abierta (${screen.headerTexts.joinToString()}) no coincide con ${contact.name} (${contact.phoneNumber}).",
    )
  }

  fun isMessageReadyInInput(inputNode: UiNodeSnapshot?, expectedText: String): Boolean {
    if (inputNode == null) return false
    val currentText = inputNode.text?.trim() ?: return false
    val expectedTrimmed = expectedText.trim()
    if (currentText.isEmpty()) return false
    // WhatsApp puede respetar exactamente el texto escrito; evitamos aceptar texto de marcador de posición.
    val hintNorm = normalizeText(inputNode.hintText)
    if (normalizeText(currentText) == hintNorm && hintNorm.isNotEmpty()) {
      return false
    }
    return currentText == expectedTrimmed || currentText.contains(expectedTrimmed)
  }

  fun findMatchingChatRow(
    candidates: List<UiNodeSnapshot>,
    contact: ContactInfo,
  ): UiNodeSnapshot? {
    val targetName = normalizeText(contact.name)
    val targetDigits = contact.normalizedDigits

    return candidates.firstOrNull { row ->
      val allRowTexts = row.flatten().mapNotNull { it.text ?: it.contentDescription }
      allRowTexts.any { candidateText ->
        val norm = normalizeText(candidateText)
        val digits = candidateText.filter { it.isDigit() }
        (targetName.length >= 2 && (norm == targetName || norm.contains(targetName))) ||
          digitsMatch(targetDigits, digits)
      }
    }
  }

  private fun detectBlockingOrErrorScreen(nodes: List<UiNodeSnapshot>): WhatsAppScreen.UnexpectedOrError? {
    val combinedTexts = nodes.mapNotNull { node ->
      listOfNotNull(node.text, node.contentDescription).joinToString(" ").takeIf { it.isNotBlank() }
    }

    for (raw in combinedTexts) {
      val norm = normalizeText(raw)
      if (securityOrCaptchaKeywords.any { norm.contains(it) }) {
        return WhatsAppScreen.UnexpectedOrError(
          reason = "Se detectó una verificación de seguridad, PIN o CAPTCHA: \"$raw\".",
          isSecurityOrCaptcha = true,
        )
      }
      if (errorDialogKeywords.any { norm.contains(it) }) {
        return WhatsAppScreen.UnexpectedOrError(
          reason = "WhatsApp mostró un aviso o error: \"$raw\".",
          isSecurityOrCaptcha = false,
        )
      }
    }
    return null
  }

  private fun findTopSearchInput(nodes: List<UiNodeSnapshot>, screenHeight: Int): UiNodeSnapshot? {
    val topLimit = (screenHeight * 0.25f).toInt()
    return nodes.firstOrNull { node ->
      val isEdit = node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
      val isTop = node.bounds.top in 0..topLimit
      val idOrHint = normalizeText("${node.viewIdResourceName.orEmpty()} ${node.hintText.orEmpty()} ${node.contentDescription.orEmpty()}")
      isEdit && isTop && (searchKeywords.any { idOrHint.contains(it) } || node.bounds.top < (screenHeight * 0.18f).toInt())
    }
  }

  private fun findMessageComposerInput(nodes: List<UiNodeSnapshot>, screenHeight: Int): UiNodeSnapshot? {
    val minComposerTop = (screenHeight * 0.35f).toInt()
    val editableNodes = nodes.filter { node ->
      node.isEnabled && (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true)
    }
    // Preferir EditText en zona media/inferior (incluso cuando el teclado está abierto).
    return editableNodes.firstOrNull { node ->
      val meta = normalizeText("${node.viewIdResourceName.orEmpty()} ${node.hintText.orEmpty()} ${node.contentDescription.orEmpty()}")
      val looksLikeComposer = composerHints.any { meta.contains(it) } || meta.contains("entry")
      val notSearch = !searchKeywords.any { meta.contains(it) }
      notSearch && (looksLikeComposer || node.bounds.top >= minComposerTop)
    } ?: editableNodes.lastOrNull { it.bounds.top >= (screenHeight * 0.25f).toInt() }
  }

  private fun findSendButton(
    nodes: List<UiNodeSnapshot>,
    composerInput: UiNodeSnapshot,
    screenWidth: Int,
  ): UiNodeSnapshot? {
    // 1. Buscar por descripción de accesibilidad ("Enviar" / "Send") o ID con "send",
    // excluyendo el botón de nota de voz.
    val byDescription = nodes.firstOrNull { node ->
      if (!node.isEnabled || !node.isClickable) return@firstOrNull false
      val desc = normalizeText(node.contentDescription)
      val id = normalizeText(node.viewIdResourceName)
      val isMic = micDescriptions.any { desc.contains(it) } || id.contains("voice") || id.contains("mic")
      val isSend = sendDescriptions.any { desc == it || desc.startsWith("$it ") } || id.endsWith(":id/send") || id.contains("send_btn")
      !isMic && isSend
    }
    if (byDescription != null) return byDescription

    // 2. Respaldo geométrico: botón clickable a la derecha del campo de texto y alineado verticalmente,
    // siempre que no sea el botón de micrófono.
    val inputCenterY = composerInput.bounds.centerY
    return nodes.firstOrNull { node ->
      if (!node.isEnabled || !node.isClickable) return@firstOrNull false
      val desc = normalizeText(node.contentDescription)
      val isMic = micDescriptions.any { desc.contains(it) }
      val toTheRight = node.bounds.left >= composerInput.bounds.right - 20 && node.bounds.right <= screenWidth
      val verticallyAligned = kotlin.math.abs(node.bounds.centerY - inputCenterY) <= (composerInput.bounds.height + 60)
      !isMic && toTheRight && verticallyAligned
    }
  }

  private fun extractHeaderTexts(nodes: List<UiNodeSnapshot>, screenHeight: Int): List<String> {
    val headerBottomLimit = (screenHeight * 0.22f).toInt()
    return nodes
      .asSequence()
      .filter { node ->
        node.bounds.bottom in 1..headerBottomLimit &&
          !node.isEditable &&
          node.className?.contains("EditText", ignoreCase = true) != true
      }
      .mapNotNull { it.text?.trim()?.takeIf { txt -> txt.isNotEmpty() } }
      .filterNot { txt ->
        val norm = normalizeText(txt)
        norm in setOf("hoy", "today", "ayer", "yesterday", "en linea", "online", "escribiendo...", "typing...")
      }
      .distinct()
      .toList()
  }

  private fun findSearchButton(nodes: List<UiNodeSnapshot>, screenHeight: Int): UiNodeSnapshot? {
    val topLimit = (screenHeight * 0.25f).toInt()
    return nodes.firstOrNull { node ->
      node.isEnabled && node.isClickable && node.bounds.top <= topLimit && run {
        val meta = normalizeText("${node.contentDescription.orEmpty()} ${node.text.orEmpty()} ${node.viewIdResourceName.orEmpty()}")
        searchKeywords.any { meta.contains(it) }
      }
    }
  }

  private fun findClickableListRows(nodes: List<UiNodeSnapshot>, minTop: Int): List<UiNodeSnapshot> {
    return nodes.filter { node ->
      node.isClickable &&
        node.isEnabled &&
        node.bounds.top >= minTop &&
        node.bounds.height >= 60 &&
        node.children.isNotEmpty()
    }
  }

  private fun hasMainTabs(nodes: List<UiNodeSnapshot>): Boolean {
    val tabLabels = setOf("chats", "novedades", "updates", "llamadas", "calls", "comunidades", "communities")
    return nodes.any { node ->
      val txt = normalizeText(node.text)
      txt in tabLabels
    }
  }

  private fun digitsMatch(a: String, b: String): Boolean {
    if (a.length < 7 || b.length < 7) return false
    if (a == b) return true
    val tailA = a.takeLast(minOf(a.length, 9))
    val tailB = b.takeLast(minOf(b.length, 9))
    return tailA == tailB || a.endsWith(b) || b.endsWith(a)
  }

  private fun normalizeText(input: String?): String {
    if (input.isNullOrBlank()) return ""
    val decomposed = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
      .replace("\\s+".toRegex(), " ")
      .trim()
  }
}

