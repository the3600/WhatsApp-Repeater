package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.AutoReplySchedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Evalúa horarios y días semanales en hora local del teléfono sin dependencias de red.
 */
object ScheduleEvaluator {

  fun isActiveAt(
    schedule: AutoReplySchedule,
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Boolean {
    if (!schedule.enabled) return true
    if (schedule.daysOfWeek.isEmpty()) return false

    val dateTime: ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    val dayValue = dateTime.dayOfWeek.value // 1 = Lunes .. 7 = Domingo
    val minuteOfDay = dateTime.hour * 60 + dateTime.minute
    val start = schedule.startMinutesOfDay.coerceIn(0, 1439)
    val end = schedule.endMinutesOfDay.coerceIn(0, 1439)

    if (start == end) {
      // Mismo minuto inicio/fin equivale a 24 horas en los días habilitados.
      return dayValue in schedule.daysOfWeek
    }

    return if (start < end) {
      dayValue in schedule.daysOfWeek && minuteOfDay in start until end
    } else {
      // Horario nocturno que cruza medianoche (ej. 20:00 -> 08:00):
      // parte posterior a inicio en el día actual O parte previa al fin respecto al día anterior.
      val prevDay = if (dayValue == 1) 7 else dayValue - 1
      (dayValue in schedule.daysOfWeek && minuteOfDay >= start) ||
        (prevDay in schedule.daysOfWeek && minuteOfDay < end)
    }
  }

  fun formatMinutes(minutesOfDay: Int): String {
    val clamped = minutesOfDay.coerceIn(0, 1439)
    val h = clamped / 60
    val m = clamped % 60
    return "%02d:%02d".format(h, m)
  }
}

