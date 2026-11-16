package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.IncomingNotificationEvent

/**
 * Vista ligera de contactos locales cargados solo si el usuario habilitó READ_CONTACTS.
 */
data class SavedContactsLookup(
  val permissionGranted: Boolean = false,
  val savedNamesNormalized: Set<String> = emptySet(),
  val savedDigitTails: Set<String> = emptySet(),
) {
  fun matchesSavedContact(identifier: String?): Boolean {
    if (!permissionGranted || identifier.isNullOrBlank()) return false
    val normName = RuleMatcher.normalizeInsensitive(identifier)
    if (normName.isNotEmpty() && normName in savedNamesNormalized) {
      return true
    }
    val digits = identifier.filter { it.isDigit() }
    if (digits.length >= 7) {
      val tail = digits.takeLast(minOf(digits.length, 9))
      if (tail in savedDigitTails) return true
    }
    return false
  }

  companion object {
    fun fromContacts(permissionGranted: Boolean, contacts: List<ContactInfo>): SavedContactsLookup {
      if (!permissionGranted) return SavedContactsLookup(permissionGranted = false)
      val names = contacts.map { RuleMatcher.normalizeInsensitive(it.name) }.filter { it.isNotEmpty() }.toSet()
      val tails = contacts.mapNotNull { c ->
        val d = c.normalizedDigits
        if (d.length >= 7) d.takeLast(minOf(d.length, 9)) else null
      }.toSet()
      return SavedContactsLookup(
        permissionGranted = true,
        savedNamesNormalized = names,
        savedDigitTails = tails,
      )
    }
  }
}

/**
 * Evalúa filtros de contactos y exclusiones sin asumir que el nombre mostrado
 * en la notificación identifica inequívocamente un número telefónico.
 */
object ContactFilterEvaluator {

  private val rawPhoneTitleRegex = Regex("^\\+?[\\d\\s().-]{7,20}$")

  fun isAllowed(
    event: IncomingNotificationEvent,
    mode: AutoReplyContactFilterMode,
    allowGroups: Boolean,
    selectedIdentifiers: List<String>,
    excludedIdentifiers: List<String>,
    savedContacts: SavedContactsLookup,
  ): Boolean {
    val chatDisplay = event.conversationTitle.trim()
    val senderDisplay = event.senderName?.trim().orEmpty()

    // 1. Lista de exclusión prioritaria sobre nombre de chat o remitente.
    if (matchesAnyIdentifier(chatDisplay, excludedIdentifiers) ||
      (senderDisplay.isNotEmpty() && matchesAnyIdentifier(senderDisplay, excludedIdentifiers))
    ) {
      return false
    }

    // 2. Comprobación de grupos.
    if (mode == AutoReplyContactFilterMode.GROUPS_ONLY) {
      return event.isGroupConversation
    }
    if (event.isGroupConversation && !allowGroups) {
      return false
    }

    val effectiveCandidate = if (event.isGroupConversation && senderDisplay.isNotEmpty()) {
      senderDisplay
    } else {
      chatDisplay
    }

    return when (mode) {
      AutoReplyContactFilterMode.ALL -> true

      AutoReplyContactFilterMode.GROUPS_ONLY -> event.isGroupConversation

      AutoReplyContactFilterMode.SELECTED_CONTACTS -> {
        selectedIdentifiers.isNotEmpty() &&
          (matchesAnyIdentifier(chatDisplay, selectedIdentifiers) ||
            (senderDisplay.isNotEmpty() && matchesAnyIdentifier(senderDisplay, selectedIdentifiers)))
      }

      AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY -> {
        if (!savedContacts.permissionGranted) {
          // Si no hay permiso de agenda, fallamos cerrado para evitar responder a desconocidos.
          false
        } else {
          savedContacts.matchesSavedContact(effectiveCandidate)
        }
      }

      AutoReplyContactFilterMode.UNKNOWN_NUMBERS_ONLY -> {
        val looksLikeRawPhone = rawPhoneTitleRegex.matches(effectiveCandidate)
        if (!savedContacts.permissionGranted) {
          // Sin agenda, únicamente podemos identificar números sin nombre guardado cuando aparecen en formato numérico.
          looksLikeRawPhone
        } else {
          looksLikeRawPhone || !savedContacts.matchesSavedContact(effectiveCandidate)
        }
      }
    }
  }

  private fun matchesAnyIdentifier(candidate: String, list: List<String>): Boolean {
    if (candidate.isBlank() || list.isEmpty()) return false
    val candNorm = RuleMatcher.normalizeInsensitive(candidate)
    val candDigits = candidate.filter { it.isDigit() }
    val candTail = if (candDigits.length >= 7) candDigits.takeLast(minOf(candDigits.length, 9)) else ""

    return list.any { raw ->
      val item = raw.trim()
      if (item.isEmpty()) return@any false
      val itemNorm = RuleMatcher.normalizeInsensitive(item)
      if (candNorm == itemNorm || candNorm.contains(itemNorm)) return@any true

      val itemDigits = item.filter { it.isDigit() }
      if (candTail.isNotEmpty() && itemDigits.length >= 7) {
        val itemTail = itemDigits.takeLast(minOf(itemDigits.length, 9))
        candTail == itemTail || candDigits == itemDigits
      } else {
        false
      }
    }
  }
}

