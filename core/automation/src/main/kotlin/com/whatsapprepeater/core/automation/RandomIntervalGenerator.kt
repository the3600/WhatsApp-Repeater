package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.IntervalRange
import kotlin.random.Random

/**
 * Generador de intervalos aleatorios dentro de un rango validado.
 * Su propósito es configurar pausas humanas prudentes entre envíos confirmados,
 * nunca evadir controles antispam ni enviar mensajes masivos.
 */
class RandomIntervalGenerator(
  private val random: Random = Random.Default,
) {
  fun nextIntervalSeconds(range: IntervalRange): Int {
    val validationError = range.validate()
    require(validationError == null) { validationError ?: "Rango de intervalo inválido" }
    if (range.minSeconds == range.maxSeconds) {
      return range.minSeconds
    }
    return random.nextInt(from = range.minSeconds, until = range.maxSeconds + 1)
  }
}

