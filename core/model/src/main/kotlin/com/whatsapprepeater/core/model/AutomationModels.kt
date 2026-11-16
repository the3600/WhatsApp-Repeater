package com.whatsapprepeater.core.model

/**
 * Límites de seguridad prudentes para evitar envíos excesivos o conductas masivas.
 */
object SafetyLimits {
  const val MIN_INTERVAL_SECONDS = 5
  const val MAX_INTERVAL_SECONDS = 300
  const val DEFAULT_MIN_INTERVAL_SECONDS = 15
  const val DEFAULT_MAX_INTERVAL_SECONDS = 35

  const val MIN_REPEAT_COUNT = 1
  const val DEFAULT_MAX_REPEAT_COUNT = 20
  const val MAX_REPEAT_COUNT_HARD_LIMIT = 50
  const val MAX_MULTIPLE_MESSAGES_LIMIT = 50
  const val MAX_MESSAGE_LENGTH = 2000
}

/**
 * Representa un único contacto seleccionado del teléfono del usuario.
 */
data class ContactInfo(
  val id: String,
  val name: String,
  val phoneNumber: String,
) {
  /** Solo dígitos del número para comparación tolerante con formatos internacionales. */
  val normalizedDigits: String
    get() = phoneNumber.filter { it.isDigit() }
}

/**
 * Modalidad de configuración de mensajes.
 */
sealed interface MessageMode {
  data class MultipleMessages(val messages: List<String>) : MessageMode

  data class RepeatSingleMessage(
    val message: String,
    val repeatCount: Int,
  ) : MessageMode
}

/**
 * Rango de espera aleatoria (en segundos) entre envíos.
 */
data class IntervalRange(
  val minSeconds: Int = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
  val maxSeconds: Int = SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS,
) {
  fun validate(): String? {
    if (minSeconds < SafetyLimits.MIN_INTERVAL_SECONDS) {
      return "El intervalo mínimo debe ser de al menos ${SafetyLimits.MIN_INTERVAL_SECONDS} segundos."
    }
    if (maxSeconds > SafetyLimits.MAX_INTERVAL_SECONDS) {
      return "El intervalo máximo no puede superar ${SafetyLimits.MAX_INTERVAL_SECONDS} segundos."
    }
    if (minSeconds > maxSeconds) {
      return "El intervalo mínimo no puede ser mayor que el máximo."
    }
    return null
  }

  val isValid: Boolean
    get() = validate() == null
}

/**
 * Configuración completa y explícitamente confirmada para una ejecución.
 */
data class AutomationConfig(
  val contact: ContactInfo,
  val mode: MessageMode,
  val intervalRange: IntervalRange,
  val userConfirmed: Boolean = false,
  val targetPackage: String = WHATSAPP_PACKAGE,
) {
  companion object {
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
  }
}

