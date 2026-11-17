package com.whatsapprepeater.app.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.whatsapprepeater.app.MainActivity
import com.whatsapprepeater.app.R
import com.whatsapprepeater.core.model.ScheduledTask

/**
 * Programa recordatorios de [ScheduledTask] (alerta previa, disparo y aviso de vencidos)
 * usando `setAndAllowWhileIdle` dentro de las políticas de Android/HyperOS sin permisos invasivos.
 */
class SchedulerAlarmHelper(
  private val context: Context,
) {
  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val alarmManager =
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  fun ensureChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Programaciones y Recordatorios",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Alertas previas, envíos programados y avisos de tareas vencidas"
    }
    notificationManager.createNotificationChannel(channel)
  }

  fun scheduleTaskReminder(task: ScheduledTask) {
    if (!task.enabled || task.id <= 0L) {
      cancelTaskReminder(task.id)
      return
    }
    val now = System.currentTimeMillis()
    val triggerAt = task.triggerAtMillis.coerceAtLeast(now + 5_000L)
    setWakeupBroadcast(
      requestCode = requestCodeFor(task.id),
      taskId = task.id,
      isPreAlert = false,
      triggerAtMillis = triggerAt,
    )

    if (task.notifyBeforeMinutes > 0) {
      val preAt = task.triggerAtMillis - task.notifyBeforeMinutes * 60_000L
      if (preAt > now + 5_000L) {
        setWakeupBroadcast(
          requestCode = PRE_ALERT_CODE_OFFSET + requestCodeFor(task.id),
          taskId = task.id,
          isPreAlert = true,
          triggerAtMillis = preAt,
        )
      } else {
        cancelBroadcast(PRE_ALERT_CODE_OFFSET + requestCodeFor(task.id))
      }
    } else {
      cancelBroadcast(PRE_ALERT_CODE_OFFSET + requestCodeFor(task.id))
    }
  }

  fun cancelTaskReminder(taskId: Long) {
    if (taskId <= 0L) return
    val baseCode = requestCodeFor(taskId)
    cancelBroadcast(baseCode)
    cancelBroadcast(PRE_ALERT_CODE_OFFSET + baseCode)
  }

  fun showPreAlertNotification(task: ScheduledTask) {
    val body = "En ${task.notifyBeforeMinutes} min: ${task.contactName} (${task.totalMessagesCount} msg). Verifica que el teléfono esté desbloqueado."
    postTaskNotification(
      notificationId = PRE_NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Aviso previo: ${task.title}",
      body = body,
      actionLabel = "Abrir ahora",
    )
  }

  fun showDueNotification(task: ScheduledTask) {
    val body = "${task.contactName} • ${task.totalMessagesCount} mensaje(s). Toca para revisar y enviar."
    postTaskNotification(
      notificationId = NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Programación lista: ${task.title}",
      body = body,
      actionLabel = "Preparar y ejecutar",
    )
  }

  fun showOverdueReminderNotification(task: ScheduledTask) {
    val body = "${task.contactName}: la hora programada pasó mientras el dispositivo estaba apagado o restringido. Toca para ejecutar o reprogramar."
    postTaskNotification(
      notificationId = OVERDUE_NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Programación pendiente/vencida: ${task.title}",
      body = body,
      actionLabel = "Revisar ahora",
    )
  }

  private fun setWakeupBroadcast(
    requestCode: Int,
    taskId: Long,
    isPreAlert: Boolean,
    triggerAtMillis: Long,
  ) {
    val intent = Intent(context, ScheduledReminderReceiver::class.java).apply {
      putExtra(EXTRA_TASK_ID, taskId)
      putExtra(EXTRA_IS_PRE_ALERT, isPreAlert)
    }
    val pending = PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    runCatching {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }
  }

  private fun cancelBroadcast(requestCode: Int) {
    val intent = Intent(context, ScheduledReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
    alarmManager.cancel(pending)
    pending.cancel()
  }

  private fun postTaskNotification(
    notificationId: Int,
    task: ScheduledTask,
    title: String,
    body: String,
    actionLabel: String,
  ) {
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

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(openPending)
      .addAction(0, actionLabel, openPending)
      .build()

    runCatching {
      notificationManager.notify(notificationId, notification)
    }
  }

  private fun requestCodeFor(taskId: Long): Int = ((taskId % 50_000L).toInt()).coerceAtLeast(1)

  companion object {
    const val CHANNEL_ID = "whatsapp_repeater_scheduler"
    const val EXTRA_TASK_ID = "extra_scheduled_task_id"
    const val EXTRA_IS_PRE_ALERT = "extra_scheduled_is_pre_alert"
    const val EXTRA_LAUNCH_SCHEDULED_TASK_ID = "extra_launch_scheduled_task_id"
    private const val PRE_ALERT_CODE_OFFSET = 100_000
    private const val NOTIFICATION_BASE_ID = 71_000
    private const val PRE_NOTIFICATION_BASE_ID = 72_000
    private const val OVERDUE_NOTIFICATION_BASE_ID = 73_000
  }
}
