package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.RuleMatchType
import java.text.Normalizer

/**
 * Motor local de coincidencias sin conexión ni APIs externas.
 */
object RuleMatcher {

  fun matches(
    incomingText: String,
    pattern: String,
    matchType: RuleMatchType,
    caseSensitive: Boolean = false,
  ): Boolean {
    val cleanPattern = pattern.trim()
    val cleanIncoming = incomingText.trim()
    if (cleanPattern.isEmpty() || cleanIncoming.isEmpty()) return false

    return when (matchType) {
      RuleMatchType.EXACT -> {
        if (caseSensitive) {
          cleanIncoming == cleanPattern
        } else {
          normalizeInsensitive(cleanIncoming) == normalizeInsensitive(cleanPattern)
        }
      }

      RuleMatchType.CONTAINS_WORD -> {
        val (target, needle) = prepareTexts(cleanIncoming, cleanPattern, caseSensitive)
        val escaped = Regex.escape(needle)
        val wordRegex = Regex("(^|[^\\p{L}\\p{N}_])$escaped($|[^\\p{L}\\p{N}_])")
        wordRegex.containsMatchIn(target)
      }

      RuleMatchType.CONTAINS_PHRASE -> {
        val (target, needle) = prepareTexts(cleanIncoming, cleanPattern, caseSensitive)
        target.contains(needle)
      }

      RuleMatchType.CONTAINS_ALL_WORDS -> {
        val (target, needle) = prepareTexts(cleanIncoming, cleanPattern, caseSensitive)
        val words = needle.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
        words.isNotEmpty() && words.all { word ->
          val escaped = Regex.escape(word)
          Regex("(^|[^\\p{L}\\p{N}_])$escaped($|[^\\p{L}\\p{N}_])").containsMatchIn(target)
        }
      }

      RuleMatchType.REGEX -> {
        runCatching {
          val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
          Regex(cleanPattern, options).containsMatchIn(cleanIncoming)
        }.getOrDefault(false)
      }
    }
  }

  fun validatePattern(pattern: String, matchType: RuleMatchType): String? {
    val clean = pattern.trim()
    if (clean.isEmpty()) return "La palabra o expresión de búsqueda no puede estar vacía."
    if (matchType == RuleMatchType.REGEX) {
      val err = runCatching { Regex(clean) }.exceptionOrNull()
      if (err != null) {
        return "Expresión regular inválida: ${err.message ?: "revisa la sintaxis"}"
      }
    }
    return null
  }

  internal fun normalizeInsensitive(input: String): String {
    val decomposed = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
      .replace("\\s+".toRegex(), " ")
      .trim()
  }

  private fun prepareTexts(
    incoming: String,
    pattern: String,
    caseSensitive: Boolean,
  ): Pair<String, String> = if (caseSensitive) {
    incoming to pattern
  } else {
    normalizeInsensitive(incoming) to normalizeInsensitive(pattern)
  }
}

