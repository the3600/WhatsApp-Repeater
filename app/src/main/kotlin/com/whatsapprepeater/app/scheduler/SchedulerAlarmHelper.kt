package com.whatsapprepeater.app.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.whatsapprepeater.app.MainActivity
import com.whatsapprepeater.app.R
import com.whatsapprepeater.core.model.ScheduledTask

/**
 * Programa recordatorios exactos/oportunos de [ScheduledTask] dentro de las políticas de Android/HyperOS
 * y emite notificaciones accionables en 1 toque (sin simular ejecución oculta con pantalla bloqueada).
 */
class SchedulerAlarmHelper(
  private val context: Context,
) {
  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val alarmManager =
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Programaciones y Recordatorios",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Alertas locales de envíos programados y tareas pendientes"
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun scheduleTaskReminder(task: ScheduledTask) {
    if (!task.enabled || task.id <= 0L) {
      cancelTaskReminder(task.id)
      return
    }
    val intent = Intent(context, ScheduledReminderReceiver::class.java).apply {
      putExtra(EXTRA_TASK_ID, task.id)
    }
    val pending = PendingIntent.getBroadcast(
      context,
      requestCodeFor(task.id),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val triggerAt = task.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
    runCatching {
      val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
      if (canExact) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      }
    }
  }

  fun cancelTaskReminder(taskId: Long) {
    if (taskId <= 0L) return
    val intent = Intent(context, ScheduledReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
      context,
      requestCodeFor(taskId),
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
    alarmManager.cancel(pending)
    pending.cancel()
  }

  fun showDueNotification(task: ScheduledTask) {
    ensureChannel()
    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_LAUNCH_SCHEDULED_TASK_ID, task.id)
    }
    val openPending = PendingIntent.getActivity(
      context,
      20_000 + requestCodeFor(task.id),
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val subtitle = "${task.contactName} • ${task.totalMessagesCount} mensaje(s). Toca para revisar y enviar."
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("Programación lista: ${task.title}")
      .setContentText(subtitle)
      .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(openPending)
      .addAction(0, "Preparar y ejecutar", openPending)
      .build()

    runCatching {
      notificationManager.notify(NOTIFICATION_BASE_ID + requestCodeFor(task.id), notification)
    }
  }

  private fun requestCodeFor(taskId: Long): Int = ((taskId % 50_000L).toInt()).coerceAtLeast(1)

  companion object {
    const val CHANNEL_ID = "whatsapp_repeater_scheduler"
    const val EXTRA_TASK_ID = "extra_scheduled_task_id"
    const val EXTRA_LAUNCH_SCHEDULED_TASK_ID = "extra_launch_scheduled_task_id"
    private const val NOTIFICATION_BASE_ID = 71_000
  }
}
