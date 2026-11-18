package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo

/**
 * Reconoce pantallas de WhatsApp y WhatsApp Business de forma estructural y heurística
 * (sin depender de un único ID fijo), aislando la cabecera real del destinatario respecto
 * de burbujas de mensajes, citas o pestañas principales.
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
    "este numero no esta registrado",
  )

  private val sendDescriptions = listOf("enviar", "send", "enviar mensaje", "send message")
  private val micDescriptions = listOf("mensaje de voz", "nota de voz", "voice message", "record audio", "grabar audio")
  private val searchKeywords = listOf("buscar", "search")
  private val composerHints = listOf("mensaje", "message", "escribe", "type a message")
  private val toolbarActionNoise = setOf(
    "atras",
    "navegar hacia arriba",
    "back",
    "navigate up",
    "videollamada",
    "video call",
    "llamar",
    "llamada",
    "llamada de voz",
    "voice call",
    "call",
    "buscar",
    "search",
    "mas opciones",
    "more options",
    "camara",
    "camera",
    "adjuntar",
    "attach",
  )

  private val primaryHeaderIdHints = listOf(
    "conversation_contact_name",
    "action_bar_title",
    "toolbar_title",
    "contact_name",
    "chat_title",
  )

  private val secondaryHeaderIdHints = listOf(
    "conversation_contact_status",
    "conversation_contact_subtitle",
    "action_bar_subtitle",
    "business",
  )

  private val headerContainerIdHints = listOf(
    "conversation_contact",
    "conversation_toolbar",
    "toolbar",
    "action_bar",
  )

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

    // 1. Detectar bloqueos, CAPTCHA o diálogos de error primero (prioridad máxima de seguridad).
    detectBlockingOrErrorScreen(visibleNodes)?.let { return it }

    val width = snapshot.screenWidth.coerceAtLeast(1080)
    val height = snapshot.screenHeight.coerceAtLeast(1920)
    val mainTabsPresent = hasMainTabs(visibleNodes)

    // 2. Verificar modo Búsqueda activo (EditText en parte superior sin tabs principales bloqueando).
    val topSearchInput = findTopSearchInput(visibleNodes, height)
    if (topSearchInput != null && !mainTabsPresent) {
      val results = findClickableListRows(visibleNodes, minTop = topSearchInput.bounds.bottom)
      return WhatsAppScreen.SearchMode(
        searchInputNode = topSearchInput,
        resultRows = results,
      )
    }

    // 3. Verificar conversación abierta:
    // Si están las pestañas principales ("Chats / Novedades / Llamadas"), solo aceptar conversación
    // si existe explícitamente el compositor real (:id/entry o hint de mensaje abajo), evitando confundir
    // la barra "Preguntar a Meta AI o buscar" con una conversación abierta.
    val composerInput = findMessageComposerInput(visibleNodes, height, requireStrongSignal = mainTabsPresent)
    if (composerInput != null) {
      val headerCandidates = extractHeaderCandidates(root, visibleNodes, height)
      val headerTexts = headerCandidates.map { it.text }.distinct()
      val sendButton = findSendButton(visibleNodes, composerInput, width)
      val micPresent = visibleNodes.any { node ->
        val desc = ContactVerifier.normalizeText(node.contentDescription)
        val id = ContactVerifier.normalizeText(node.viewIdResourceName)
        micDescriptions.any { desc.contains(it) } || id.contains("voice_note") || id.contains("mic")
      }
      return WhatsAppScreen.OpenConversation(
        headerTexts = headerTexts,
        inputNode = composerInput,
        sendButtonNode = sendButton,
        micButtonPresent = micPresent,
        headerCandidates = headerCandidates,
      )
    }

    // 4. Pantalla principal / lista de chats.
    val searchButton = findSearchButton(visibleNodes, height)
    val chatRows = findClickableListRows(visibleNodes, minTop = (height * 0.12f).toInt())
    if (searchButton != null || mainTabsPresent) {
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
    val candidates = if (screen.headerCandidates.isNotEmpty()) {
      screen.headerCandidates
    } else {
      screen.headerTexts.mapIndexed { idx, txt -> HeaderCandidate(text = txt, isPrimaryTitle = idx == 0) }
    }
    return ContactVerifier.verifyCandidates(candidates, contact)
  }

  fun isMessageReadyInInput(inputNode: UiNodeSnapshot?, expectedText: String): Boolean {
    if (inputNode == null) return false
    val currentText = inputNode.text?.trim() ?: return false
    val expectedTrimmed = expectedText.trim()
    if (currentText.isEmpty() || expectedTrimmed.isEmpty()) return false
    val hintNorm = ContactVerifier.normalizeText(inputNode.hintText)
    val currentNorm = ContactVerifier.normalizeText(currentText)
    if (hintNorm.isNotEmpty() && currentNorm == hintNorm) {
      return false
    }
    val expectedNorm = ContactVerifier.normalizeText(expectedTrimmed)
    return currentText == expectedTrimmed ||
      currentNorm == expectedNorm ||
      currentText.contains(expectedTrimmed) ||
      currentNorm.contains(expectedNorm)
  }

  fun isComposerClearedAfterSend(inputNode: UiNodeSnapshot?, sentText: String): Boolean {
    if (inputNode == null) return false
    val current = inputNode.text?.trim().orEmpty()
    if (current.isEmpty()) return true
    val hintNorm = ContactVerifier.normalizeText(inputNode.hintText)
    val currentNorm = ContactVerifier.normalizeText(current)
    if (hintNorm.isNotEmpty() && currentNorm == hintNorm) return true
    return !isMessageReadyInInput(inputNode, sentText)
  }

  fun findMatchingChatRow(
    candidates: List<UiNodeSnapshot>,
    contact: ContactInfo,
  ): UiNodeSnapshot? {
    return candidates.firstOrNull { row ->
      val flattened = row.flatten()
      val titleTexts = flattened
        .filter { node ->
          val id = ContactVerifier.normalizeText(node.viewIdResourceName)
          id.contains("contact_name") || id.contains("row_title") || id.contains("title")
        }
        .mapNotNull { it.text?.trim()?.takeIf { t -> t.isNotEmpty() } }

      val fallbackTexts = flattened
        .mapNotNull { it.text?.trim()?.takeIf { t -> t.isNotEmpty() } }

      val rowTexts = (titleTexts.ifEmpty { fallbackTexts.take(2) }).distinct()
      if (rowTexts.isEmpty()) {
        false
      } else {
        val headerCandidates = rowTexts.mapIndexed { idx, txt ->
          HeaderCandidate(text = txt, isPrimaryTitle = idx == 0)
        }
        ContactVerifier.verifyCandidates(headerCandidates, contact) is RecipientVerificationResult.Verified
      }
    }
  }

  private fun detectBlockingOrErrorScreen(nodes: List<UiNodeSnapshot>): WhatsAppScreen.UnexpectedOrError? {
    val combinedTexts = nodes.mapNotNull { node ->
      listOfNotNull(node.text, node.contentDescription).joinToString(" ").takeIf { it.isNotBlank() }
    }

    for (raw in combinedTexts) {
      val norm = ContactVerifier.normalizeText(raw)
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
      val idOrHint = ContactVerifier.normalizeText(
        "${node.viewIdResourceName.orEmpty()} ${node.hintText.orEmpty()} ${node.contentDescription.orEmpty()}",
      )
      val isMetaAiPill = idOrHint.contains("meta ai")
      !isMetaAiPill &&
        isEdit &&
        isTop &&
        (searchKeywords.any { idOrHint.contains(it) } || node.bounds.top < (screenHeight * 0.18f).toInt())
    }
  }

  private fun findMessageComposerInput(
    nodes: List<UiNodeSnapshot>,
    screenHeight: Int,
    requireStrongSignal: Boolean,
  ): UiNodeSnapshot? {
    val minComposerTop = (screenHeight * 0.35f).toInt()
    val editableNodes = nodes.filter { node ->
      node.isEnabled && (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true)
    }

    val strongMatch = editableNodes.firstOrNull { node ->
      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val meta = ContactVerifier.normalizeText(
        "$id ${node.hintText.orEmpty()} ${node.contentDescription.orEmpty()}",
      )
      val isEntryId = id.endsWith(":id/entry") || id.contains("conversation_entry")
      val looksLikeComposer = isEntryId || composerHints.any { meta.contains(it) }
      val notSearchOrMeta = !searchKeywords.any { meta.contains(it) } && !meta.contains("meta ai")
      notSearchOrMeta && looksLikeComposer && node.bounds.top >= (screenHeight * 0.25f).toInt()
    }
    if (strongMatch != null) return strongMatch
    if (requireStrongSignal) return null

    return editableNodes.lastOrNull { node ->
      val meta = ContactVerifier.normalizeText(
        "${node.viewIdResourceName.orEmpty()} ${node.hintText.orEmpty()} ${node.contentDescription.orEmpty()}",
      )
      val notSearch = !searchKeywords.any { meta.contains(it) } && !meta.contains("meta ai")
      notSearch && node.bounds.top >= minComposerTop
    }
  }

  private fun findSendButton(
    nodes: List<UiNodeSnapshot>,
    composerInput: UiNodeSnapshot,
    screenWidth: Int,
  ): UiNodeSnapshot? {
    val byIdOrDesc = nodes.firstOrNull { node ->
      if (!node.isEnabled || !node.isClickable) return@firstOrNull false
      val desc = ContactVerifier.normalizeText(node.contentDescription)
      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val isMic = micDescriptions.any { desc.contains(it) } || id.contains("voice") || id.contains("mic")
      val isSend = sendDescriptions.any { desc == it || desc.startsWith("$it ") } ||
        id.endsWith(":id/send") ||
        id.contains("send_btn")
      !isMic && isSend
    }
    if (byIdOrDesc != null) return byIdOrDesc

    val inputCenterY = composerInput.bounds.centerY
    return nodes.firstOrNull { node ->
      if (!node.isEnabled || !node.isClickable) return@firstOrNull false
      val desc = ContactVerifier.normalizeText(node.contentDescription)
      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val isMic = micDescriptions.any { desc.contains(it) } || id.contains("voice") || id.contains("mic")
      val toTheRight = node.bounds.left >= composerInput.bounds.right - 20 && node.bounds.right <= screenWidth
      val verticallyAligned = kotlin.math.abs(node.bounds.centerY - inputCenterY) <= (composerInput.bounds.height + 60)
      !isMic && toTheRight && verticallyAligned
    }
  }

  /**
   * Extrae únicamente evidencia de la barra de cabecera real de la conversación,
   * excluyendo las burbujas del chat (`RecyclerView` / `ListView`) y los botones de acción.
   */
  private fun extractHeaderCandidates(
    root: UiNodeSnapshot,
    visibleNodes: List<UiNodeSnapshot>,
    screenHeight: Int,
  ): List<HeaderCandidate> {
    val messageListContainers = visibleNodes.filter { node ->
      val cls = node.className.orEmpty()
      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val isListClass = cls.contains("RecyclerView", ignoreCase = true) ||
        cls.contains("ListView", ignoreCase = true)
      val isMessageListId = id.endsWith(":id/list") || id.contains("messages_list")
      (isListClass || isMessageListId) && node.bounds.height >= (screenHeight * 0.25f).toInt()
    }

    val messageListNodeIds = messageListContainers
      .flatMap { it.flatten() }
      .map { it.nodeId }
      .toSet()

    val minMessageListTop = messageListContainers
      .mapNotNull { it.bounds.top.takeIf { top -> top > 0 } }
      .minOrNull()

    val strictTopLimit = minMessageListTop
      ?.let { minOf((it - 2).coerceAtLeast(1), (screenHeight * 0.20f).toInt()) }
      ?: (screenHeight * 0.16f).toInt()

    val candidates = mutableListOf<HeaderCandidate>()

    // 1. Prioridad máxima: IDs explícitos de cabecera en WhatsApp / WhatsApp Business.
    for (node in visibleNodes) {
      if (node.nodeId in messageListNodeIds) continue
      if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) continue
      if (node.bounds.top > (screenHeight * 0.22f).toInt()) continue

      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val isPrimaryId = primaryHeaderIdHints.any { id.contains(it) }
      val isSecondaryId = secondaryHeaderIdHints.any { id.contains(it) }

      if (isPrimaryId || isSecondaryId) {
        extractCleanNodeHeaderTexts(node, allowContentDesc = true).forEach { txt ->
          candidates.add(
            HeaderCandidate(
              text = txt,
              isPrimaryTitle = isPrimaryId,
              sourceId = node.viewIdResourceName,
            ),
          )
        }
      }
    }

    // 2. Respaldo estructural por ubicación arriba de la lista de mensajes / zona Toolbar.
    for (node in visibleNodes) {
      if (node.nodeId in messageListNodeIds) continue
      if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) continue
      if (node.bounds.bottom !in 1..strictTopLimit) continue

      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      val allowDesc = headerContainerIdHints.any { id.contains(it) } ||
        primaryHeaderIdHints.any { id.contains(it) } ||
        (node.text.isNullOrBlank() && !node.isClickable)

      val texts = extractCleanNodeHeaderTexts(node, allowContentDesc = allowDesc)
      for (txt in texts) {
        val norm = ContactVerifier.normalizeText(txt)
        if (norm in toolbarActionNoise) continue
        if (ContactVerifier.isPureNoise(txt)) continue
        val isSecondary = secondaryHeaderIdHints.any { id.contains(it) } || candidates.isNotEmpty()
        candidates.add(
          HeaderCandidate(
            text = txt,
            isPrimaryTitle = !isSecondary,
            sourceId = node.viewIdResourceName,
          ),
        )
      }
    }

    // Si algún título aparece truncado con "..." en node.text y su contenedor padre tiene contentDescription completa,
    // también extraer la contentDescription del contenedor de cabecera cercano.
    for (node in visibleNodes) {
      if (node.nodeId in messageListNodeIds) continue
      if (node.bounds.bottom !in 1..strictTopLimit) continue
      val id = ContactVerifier.normalizeText(node.viewIdResourceName)
      if (headerContainerIdHints.any { id.contains(it) }) {
        val desc = node.contentDescription?.trim()
        if (!desc.isNullOrBlank() && ContactVerifier.normalizeText(desc) !in toolbarActionNoise && !ContactVerifier.isPureNoise(desc)) {
          candidates.add(HeaderCandidate(text = desc, isPrimaryTitle = false, sourceId = node.viewIdResourceName))
        }
      }
    }

    // Mantener compatibilidad si no se encontraron nodos por encima del límite estricto (p. ej. árbol sintético sin RecyclerView).
    if (candidates.isEmpty() && messageListContainers.isEmpty()) {
      val legacyLimit = (screenHeight * 0.22f).toInt()
      for (node in visibleNodes) {
        if (node.bounds.bottom in 1..legacyLimit &&
          !node.isEditable &&
          node.className?.contains("EditText", ignoreCase = true) != true
        ) {
          extractCleanNodeHeaderTexts(node, allowContentDesc = false).forEachIndexed { idx, txt ->
            if (!ContactVerifier.isPureNoise(txt) && ContactVerifier.normalizeText(txt) !in toolbarActionNoise) {
              candidates.add(HeaderCandidate(text = txt, isPrimaryTitle = idx == 0, sourceId = node.viewIdResourceName))
            }
          }
        }
      }
    }

    return candidates.distinctBy { "${it.isPrimaryTitle}:${ContactVerifier.normalizeText(it.text)}" }
  }

  private fun extractCleanNodeHeaderTexts(
    node: UiNodeSnapshot,
    allowContentDesc: Boolean,
  ): List<String> {
    val result = mutableListOf<String>()
    val text = node.text?.trim()
    if (!text.isNullOrBlank()) {
      result.add(text)
    }
    if (allowContentDesc) {
      val desc = node.contentDescription?.trim()
      if (!desc.isNullOrBlank() && desc != text) {
        result.add(desc)
      }
    }
    return result
  }

  private fun findSearchButton(nodes: List<UiNodeSnapshot>, screenHeight: Int): UiNodeSnapshot? {
    val topLimit = (screenHeight * 0.25f).toInt()
    return nodes.firstOrNull { node ->
      node.isEnabled && node.isClickable && node.bounds.top <= topLimit && run {
        val meta = ContactVerifier.normalizeText(
          "${node.contentDescription.orEmpty()} ${node.text.orEmpty()} ${node.viewIdResourceName.orEmpty()}",
        )
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
      val txt = ContactVerifier.normalizeText(node.text)
      txt in tabLabels
    }
  }
}
