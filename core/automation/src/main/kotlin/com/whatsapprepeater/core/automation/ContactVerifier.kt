package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.ContactInfo
import java.text.Normalizer

data class HeaderCandidate(
  val text: String,
  val isPrimaryTitle: Boolean = true,
  val sourceId: String? = null,
)

/**
 * Verificador estricto de destinatario para conversaciones de WhatsApp y WhatsApp Business.
 * Normaliza formato/BiDi, separa títulos de estados/mensajes secundarios, compara nombre y
 * teléfono cuando están disponibles, y bloquea coincidencias ambiguas o parciales peligrosas.
 */
object ContactVerifier {

  private val invisibleCharsRegex =
    Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFE0E\\uFE0F\\uFEFF]")
  private val unicodeSpacesRegex =
    Regex("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\s]+")
  private val unicodeDashRegex = Regex("[\\u2010-\\u2015\\u2212]")
  private val timePatternRegex =
    Regex("\\b\\d{1,2}:\\d{2}(?:\\s*[ap]\\.?\\s*m\\.?)?\\b", RegexOption.IGNORE_CASE)
  private val phoneTokenRegex = Regex("(?:\\+?\\d[\\d\\s().-]{5,18}\\d)")

  private val knownSecondaryStatusExact = setOf(
    "hoy",
    "today",
    "ayer",
    "yesterday",
    "en linea",
    "online",
    "escribiendo",
    "escribiendo...",
    "typing",
    "typing...",
    "grabando audio",
    "grabando audio...",
    "recording audio",
    "recording audio...",
    "cuenta de empresa",
    "business account",
    "cuenta comercial oficial",
    "official business account",
    "verificado",
    "verified",
    "toca aqui para info del contacto",
    "toca aqui para info. del contacto",
    "tap here for contact info",
    "pulsa para obtener info del contacto",
    "llamando",
    "llamando...",
    "calling...",
    "cifrado de extremo a extremo",
    "end to end encrypted",
    "whatsapp",
    "whatsapp business",
  )

  private val nonIdentitySuffixTokens = setOf(
    "cuenta",
    "de",
    "empresa",
    "business",
    "account",
    "official",
    "oficial",
    "verificado",
    "verificada",
    "verified",
    "whatsapp",
    "en",
    "linea",
    "online",
    "escribiendo",
    "typing",
  )

  private val statusSplitMarkers = listOf(
    "toca aqui para info",
    "tap here for contact info",
    "pulsa para obtener info",
    "ult. vez",
    "ultima vez",
    "last seen",
    "cuenta de empresa",
    "business account",
    "cifrado de extremo a extremo",
  )

  fun verify(
    headerTexts: List<String>,
    contact: ContactInfo,
  ): RecipientVerificationResult {
    val candidates = headerTexts
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .mapIndexed { idx, txt -> HeaderCandidate(text = txt, isPrimaryTitle = idx == 0) }
    return verifyCandidates(candidates, contact)
  }

  fun verifyCandidates(
    candidates: List<HeaderCandidate>,
    contact: ContactInfo,
  ): RecipientVerificationResult {
    if (candidates.isEmpty()) {
      return RecipientVerificationResult.Mismatch(
        observedHeaders = emptyList(),
        reason = "No se encontró el nombre ni el número del destinatario en la cabecera de WhatsApp.",
      )
    }

    val cleanedCandidates = candidates
      .flatMap { splitCompositeCandidate(it) }
      .distinctBy { "${it.isPrimaryTitle}:${normalizeText(it.text)}" }

    val observedDisplay = candidates.map { it.text }.distinct()
    val targetDigits = normalizePhoneDigits(contact.phoneNumber)

    // 1. Si hay un número telefónico explícito en cabecera, es señal fuerte;
    // pero si hay conflicto con el teléfono esperado, se rechaza inmediatamente.
    val observedPhones = cleanedCandidates
      .flatMap { extractPhoneDigitCandidates(it.text) }
      .distinct()

    if (targetDigits.length >= 7 && observedPhones.isNotEmpty()) {
      val matchingPhone = observedPhones.firstOrNull { phonesMatch(targetDigits, it) }
      if (matchingPhone != null) {
        val matchingRaw = cleanedCandidates.firstOrNull { cand ->
          extractPhoneDigitCandidates(cand.text).any { it == matchingPhone }
        }?.text ?: contact.phoneNumber
        return RecipientVerificationResult.Verified(matchedText = matchingRaw)
      }
      // Si la cabecera muestra otro número distinto y ninguno coincide con el contacto, detener por seguridad.
      return RecipientVerificationResult.Mismatch(
        observedHeaders = observedDisplay,
        reason = "El número visible en cabecera (${observedPhones.first()}) no coincide con el destinatario esperado ${contact.name} (${contact.phoneNumber}).",
      )
    }

    // 2. Preparar normalización del nombre esperado.
    val targetRawClean = sanitizeHeaderFragment(contact.name)
    val targetNorm = normalizeAlphaNumeric(targetRawClean)
    val targetTokens = tokenizeIdentityWords(targetRawClean)

    if (targetNorm.isBlank() && targetDigits.length < 7) {
      return RecipientVerificationResult.Mismatch(
        observedHeaders = observedDisplay,
        reason = "El destinatario seleccionado no tiene nombre ni número válidos para verificar.",
      )
    }

    // 3. Evaluar cada candidato limpio priorizando títulos de barra superior sobre subtítulos.
    var truncatedTooShortCandidate: String? = null
    var partialSimilarCandidate: String? = null

    val ordered = cleanedCandidates.sortedByDescending { it.isPrimaryTitle }
    for (candidate in ordered) {
      val cleaned = sanitizeHeaderFragment(candidate.text)
      if (cleaned.isBlank() || isPureNoise(cleaned)) continue

      // Descartar líneas que claramente parecen mensajes de chat con prefijo horario (ej. "5:25 p. m. - Yo te llamo").
      if (looksLikeChatMessageLine(candidate.text)) continue

      // Caso A: nombre largo truncado con "..." o "…"
      if (isEllipsized(candidate.text)) {
        val beforeEllipsis = sanitizeHeaderFragment(stripTrailingEllipsis(candidate.text))
        val prefixNorm = normalizeAlphaNumeric(beforeEllipsis)
        if (prefixNorm.isNotEmpty() && targetNorm.startsWith(prefixNorm)) {
          val ratio = prefixNorm.length.toDouble() / targetNorm.length.coerceAtLeast(1)
          if (prefixNorm.length >= 10 && ratio >= 0.55) {
            return RecipientVerificationResult.Verified(matchedText = candidate.text)
          } else {
            truncatedTooShortCandidate = candidate.text
          }
        }
        continue
      }

      val candNorm = normalizeAlphaNumeric(cleaned)
      if (candNorm.isBlank()) continue

      // Caso B: coincidencia exacta tras normalizar acentos, espacios, BiDi, emojis finales y separadores.
      if (candNorm == targetNorm) {
        return RecipientVerificationResult.Verified(matchedText = candidate.text)
      }

      // Caso C: comparar palabras de identidad de forma segura.
      val candTokens = tokenizeIdentityWords(cleaned)
      if (targetTokens.isNotEmpty() && candTokens.isNotEmpty()) {
        val candIdentityTokens = candTokens.filterNot { it in nonIdentitySuffixTokens }
        val targetIdentityTokens = targetTokens.filterNot { it in nonIdentitySuffixTokens }

        if (candIdentityTokens == targetIdentityTokens && candIdentityTokens.isNotEmpty()) {
          return RecipientVerificationResult.Verified(matchedText = candidate.text)
        }

        // Detectar contactos similares con palabras adicionales ("Juan Pérez" vs "Juan Pérez García" o "Ana" vs "Ana María").
        if (tokensOverlapSignificantly(targetTokens, candTokens)) {
          partialSimilarCandidate = candidate.text
        }
      }
    }

    // Fallback conservador cuando el nombre solo contenía símbolos/emojis exactos sin alfanuméricos.
    if (targetNorm.isBlank()) {
      val exactRawTarget = normalizeText(contact.name)
      for (candidate in ordered) {
        val candRaw = normalizeText(stripTrailingSeparators(candidate.text))
        if (candRaw == exactRawTarget && candRaw.isNotBlank()) {
          return RecipientVerificationResult.Verified(matchedText = candidate.text)
        }
      }
    }

    val detailedReason = when {
      truncatedTooShortCandidate != null ->
        "La cabecera \"$truncatedTooShortCandidate\" está truncada y es demasiado corta para confirmar con seguridad a ${contact.name} (${contact.phoneNumber})."
      partialSimilarCandidate != null ->
        "Coincidencia ambigua: la cabecera abierta (\"$partialSimilarCandidate\") contiene diferencias o palabras extra respecto a ${contact.name} (${contact.phoneNumber}). Podría ser otro contacto."
      else ->
        "La cabecera abierta (${observedDisplay.joinToString()}) no coincide con ${contact.name} (${contact.phoneNumber})."
    }

    return RecipientVerificationResult.Mismatch(
      observedHeaders = observedDisplay,
      reason = detailedReason,
    )
  }

  fun isPureNoise(text: String): Boolean {
    val norm = normalizeText(text)
    if (norm.isBlank()) return true
    if (norm in knownSecondaryStatusExact) return true
    if (norm.startsWith("ult. vez") || norm.startsWith("ultima vez") || norm.startsWith("last seen")) return true
    if (norm.startsWith("toca aqui") || norm.startsWith("tap here") || norm.startsWith("pulsa para")) return true
    if (timePatternRegex.matches(norm)) return true
    return false
  }

  fun normalizeText(input: String?): String {
    if (input.isNullOrBlank()) return ""
    val stripped = input
      .replace(invisibleCharsRegex, "")
      .replace(unicodeDashRegex, "-")
      .replace(unicodeSpacesRegex, " ")
      .trim()
      .lowercase()
    val decomposed = Normalizer.normalize(stripped, Normalizer.Form.NFD)
    return decomposed
      .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
      .replace(unicodeSpacesRegex, " ")
      .trim()
  }

  fun normalizePhoneDigits(phone: String?): String {
    if (phone.isNullOrBlank()) return ""
    val rawDigits = phone.filter { it.isDigit() }
    // Normalización específica México (+52 1 XX XXXX XXXX -> 52 XX XXXX XXXX) para coincidencia fiable.
    return if (rawDigits.length == 13 && rawDigits.startsWith("521")) {
      "52" + rawDigits.substring(3)
    } else {
      rawDigits
    }
  }

  fun phonesMatch(expectedRawDigits: String, observedRawDigits: String): Boolean {
    val a = normalizePhoneDigits(expectedRawDigits)
    val b = normalizePhoneDigits(observedRawDigits)
    if (a.length < 7 || b.length < 7) return false
    if (a == b) return true
    if (a.length >= 10 && b.length >= 10) {
      return a.takeLast(10) == b.takeLast(10)
    }
    return a.endsWith(b) || b.endsWith(a)
  }

  fun extractPhoneDigitCandidates(text: String): List<String> {
    val cleaned = normalizeTextWithoutLowering(text)
    return phoneTokenRegex.findAll(cleaned)
      .map { match -> normalizePhoneDigits(match.value) }
      .filter { it.length in 7..15 }
      .distinct()
      .toList()
  }

  private fun splitCompositeCandidate(candidate: HeaderCandidate): List<HeaderCandidate> {
    val raw = candidate.text
      .replace(invisibleCharsRegex, "")
      .replace(unicodeSpacesRegex, " ")
      .trim()
    if (raw.isEmpty()) return emptyList()

    val lines = raw.split('\n', '\r', '•', '|', '·')
      .flatMap { part ->
        // Si un nodo agrupa "Nombre -, 5:25 p. m. - mensaje", separar justo antes de ", HH:MM".
        val commaTimeSplit = part.split(Regex(",\\s*(?=\\d{1,2}:\\d{2})"))
        commaTimeSplit
      }
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    return if (lines.isEmpty()) listOf(candidate) else lines.mapIndexed { i, seg ->
      HeaderCandidate(
        text = seg,
        isPrimaryTitle = candidate.isPrimaryTitle && i == 0,
        sourceId = candidate.sourceId,
      )
    }
  }

  private fun sanitizeHeaderFragment(raw: String): String {
    var text = normalizeText(raw)
    for (marker in statusSplitMarkers) {
      val idx = text.indexOf(marker)
      if (idx > 0) {
        text = text.substring(0, idx).trim()
      }
    }
    text = stripTrailingSeparators(text)
    return text
  }

  private fun stripTrailingSeparators(text: String): String {
    return text.trim().trimEnd('-', '–', '—', ',', ';', ':', '•', '·', '|', ' ')
  }

  private fun looksLikeChatMessageLine(raw: String): Boolean {
    val norm = normalizeText(raw)
    val match = timePatternRegex.find(norm) ?: return false
    // Si la cadena empieza con hora ("5:25 p. m. - ...") es un mensaje/estado con sello temporal, no el título del contacto.
    return match.range.first <= 2
  }

  private fun isEllipsized(raw: String): Boolean {
    val cleaned = normalizeTextWithoutLowering(raw).trimEnd()
    return cleaned.endsWith("...") || cleaned.endsWith("…")
  }

  private fun stripTrailingEllipsis(raw: String): String {
    var s = normalizeTextWithoutLowering(raw).trimEnd()
    while (s.endsWith("…") || s.endsWith(".")) {
      s = s.dropLast(1).trimEnd()
    }
    return s
  }

  private fun normalizeAlphaNumeric(normalizedText: String): String {
    return normalizedText
      .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
      .replace(unicodeSpacesRegex, " ")
      .trim()
  }

  private fun tokenizeIdentityWords(normalizedText: String): List<String> {
    return normalizeAlphaNumeric(normalizedText)
      .split(' ')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private fun tokensOverlapSignificantly(a: List<String>, b: List<String>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    val setA = a.toSet()
    val setB = b.toSet()
    val common = setA.intersect(setB)
    return common.isNotEmpty()
  }

  private fun normalizeTextWithoutLowering(input: String): String {
    return input
      .replace(invisibleCharsRegex, "")
      .replace(unicodeDashRegex, "-")
      .replace(unicodeSpacesRegex, " ")
      .trim()
  }
}
