package com.whatsapprepeater.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DynamicVariableDescriptor(
  val token: String,
  val label: String,
  val description: String,
)

sealed interface VariableResolutionResult {
  data class Resolved(val text: String) : VariableResolutionResult
  data class Unresolved(
    val missingOrInvalidTokens: List<String>,
    val reason: String,
  ) : VariableResolutionResult
}

/**
 * Resuelve y valida variables dinámicas locales ({nombre}, {fecha}, {hora}, {dia}, {numero})
 * sin inventar datos faltantes ni permitir envío de marcadores sin resolver.
 */
object DynamicVariablesEngine {

  val supportedVariables: List<DynamicVariableDescriptor> = listOf(
    DynamicVariableDescriptor("{nombre}", "Nombre", "Nombre del contacto seleccionado"),
    DynamicVariableDescriptor("{numero}", "Número", "Teléfono del destinatario"),
    DynamicVariableDescriptor("{fecha}", "Fecha", "Fecha local actual (dd/MM/yyyy)"),
    DynamicVariableDescriptor("{hora}", "Hora", "Hora local actual (HH:mm)"),
    DynamicVariableDescriptor("{dia}", "Día", "Día de la semana en español"),
  )

  private val tokenRegex = Regex("\\{([^{}]+)\\}")
  private val spanishLocale = Locale.forLanguageTag("es")
  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", spanishLocale)
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", spanishLocale)

  fun findPlaceholders(template: String): List<String> =
    tokenRegex.findAll(template).map { "{${it.groupValues[1].trim()}}" }.toList()

  fun containsPlaceholders(template: String): Boolean = tokenRegex.containsMatchIn(template)

  fun resolveOrValidate(
    template: String,
    contact: ContactInfo?,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): VariableResolutionResult {
    if (!containsPlaceholders(template)) {
      return VariableResolutionResult.Resolved(template)
    }

    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
    val dateStr = zdt.format(dateFormatter)
    val timeStr = zdt.format(timeFormatter)
    val dayName = zdt.dayOfWeek.getDisplayName(TextStyle.FULL, spanishLocale)
      .replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }

    val contactName = contact?.name?.trim().orEmpty()
    val contactPhone = contact?.phoneNumber?.trim().orEmpty()
    val problems = linkedSetOf<String>()

    val resolved = tokenRegex.replace(template) { match ->
      val key = match.groupValues[1].trim().lowercase(Locale.ROOT)
      when (key) {
        "nombre" -> {
          val looksLikeOnlyDigits = contactName.isBlank() ||
            contactName.all { !it.isLetter() }
          if (looksLikeOnlyDigits) {
            problems.add("{nombre}")
            match.value
          } else {
            contactName
          }
        }
        "numero" -> {
          if (contactPhone.filter { it.isDigit() }.length < 4) {
            problems.add("{numero}")
            match.value
          } else {
            contactPhone
          }
        }
        "fecha" -> dateStr
        "hora" -> timeStr
        "dia" -> dayName
        else -> {
          problems.add("{$key}")
          match.value
        }
      }
    }

    if (problems.isNotEmpty()) {
      val list = problems.toList()
      return VariableResolutionResult.Unresolved(
        missingOrInvalidTokens = list,
        reason = "Variables sin resolver o dato ausente: ${list.joinToString(", ")}. Corrige el contacto o el texto antes de continuar.",
      )
    }
    return VariableResolutionResult.Resolved(resolved)
  }

  fun validateBatch(
    messages: List<String>,
    contact: ContactInfo?,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Pair<List<String>, VariableResolutionResult.Unresolved?> {
    val resolvedList = ArrayList<String>(messages.size)
    val allProblems = linkedSetOf<String>()
    for (msg in messages) {
      when (val res = resolveOrValidate(msg, contact, nowMillis, zoneId)) {
        is VariableResolutionResult.Resolved -> resolvedList.add(res.text)
        is VariableResolutionResult.Unresolved -> {
          allProblems.addAll(res.missingOrInvalidTokens)
          resolvedList.add(msg)
        }
      }
    }
    return if (allProblems.isEmpty()) {
      resolvedList to null
    } else {
      val list = allProblems.toList()
      resolvedList to VariableResolutionResult.Unresolved(
        missingOrInvalidTokens = list,
        reason = "No se pueden enviar mensajes con variables sin resolver (${list.joinToString(", ")}). Completa o edita el texto.",
      )
    }
  }
}
