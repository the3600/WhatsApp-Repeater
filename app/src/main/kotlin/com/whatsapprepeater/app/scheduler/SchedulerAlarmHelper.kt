package com.whatsapprepeater.app.scheduler

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.whatsapprepeater.app.MainActivity
import com.whatsapprepeater.app.R
import com.whatsapprepeater.core.model.ScheduledTask

data class AutoExecuteReadiness(
  val canExecute: Boolean,
  val reason: String? = null,
)

/**
 * Programa alarmas de [ScheduledTask] (alerta previa, disparo real y aviso de vencidos)
 * usando alarmas exactas cuando están autorizadas y fallback seguro con `setAndAllowWhileIdle`,
 * verificando honestamente accesibilidad, pantalla desbloqueada y restricciones de Android/HyperOS.
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
      "Programaciones y Envíos",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Alertas previas, ejecución programada y avisos cuando se requiere intervención"
    }
    notificationManager.createNotificationChannel(channel)
  }

  fun canScheduleExactAlarms(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    } else {
      true
    }
  }

  fun openExactAlarmSettings(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
      data = "package:${context.packageName}".toUri()
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun checkAutoExecuteReadiness(
    isAccessibilityConnected: Boolean,
    isAutomationAlreadyRunning: Boolean,
  ): AutoExecuteReadiness {
    if (isAutomationAlreadyRunning) {
      return AutoExecuteReadiness(
        canExecute = false,
        reason = "Ya hay otra automatización activa en curso.",
      )
    }
    if (!isAccessibilityConnected) {
      return AutoExecuteReadiness(
        canExecute = false,
        reason = "El servicio de Accesibilidad estaba inactivo o detenido por Android/HyperOS.",
      )
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val isInteractive = powerManager?.isInteractive ?: true
    val isLocked = keyguardManager?.isKeyguardLocked ?: false

    if (!isInteractive || isLocked) {
      return AutoExecuteReadiness(
        canExecute = false,
        reason = "El teléfono tenía la pantalla apagada o bloqueada. WhatsApp requiere que el dispositivo esté desbloqueado para enviar.",
      )
    }
    return AutoExecuteReadiness(canExecute = true)
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
    val modeHint = if (task.autoExecuteWhenReady) {
      "El envío intentará ejecutarse automáticamente si el teléfono está desbloqueado."
    } else {
      "Recibirás un acceso directo para confirmarlo e iniciarlo."
    }
    val body = "En ${task.notifyBeforeMinutes} min: ${task.contactName} (${task.totalMessagesCount} msg). $modeHint"
    postTaskNotification(
      notificationId = PRE_NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Aviso previo: ${task.title}",
      body = body,
      actionLabel = "Abrir programación",
      openActiveAutomation = false,
    )
  }

  fun showAutoExecutionStartedNotification(task: ScheduledTask) {
    val body = "Iniciando envío real hacia ${task.contactName} (${task.totalMessagesCount} msg). Toca para ver progreso o detener."
    postTaskNotification(
      notificationId = NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Ejecutando programación: ${task.title}",
      body = body,
      actionLabel = "Ver progreso",
      openActiveAutomation = true,
    )
  }

  fun showInterventionRequiredNotification(task: ScheduledTask, reason: String) {
    val body = "${task.contactName}: No se pudo completar automáticamente ($reason). Toca para revisar y enviar ahora."
    postTaskNotification(
      notificationId = NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Intervención requerida: ${task.title}",
      body = body,
      actionLabel = "Abrir y enviar",
      openActiveAutomation = false,
    )
  }

  fun showDueNotification(task: ScheduledTask) {
    val body = "${task.contactName} • ${task.totalMessagesCount} mensaje(s). Toca para abrir y confirmar el envío."
    postTaskNotification(
      notificationId = NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Programación lista: ${task.title}",
      body = body,
      actionLabel = "Preparar y enviar",
      openActiveAutomation = false,
    )
  }

  fun showOverdueReminderNotification(task: ScheduledTask) {
    val body = "${task.contactName}: la hora programada pasó mientras el dispositivo estaba apagado o restringido por Android/HyperOS. No se simuló ningún envío."
    postTaskNotification(
      notificationId = OVERDUE_NOTIFICATION_BASE_ID + requestCodeFor(task.id),
      task = task,
      title = "Programación vencida: ${task.title}",
      body = body,
      actionLabel = "Revisar ahora",
      openActiveAutomation = false,
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
      if (canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
      }
    }.onFailure {
      runCatching {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
      }
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
    openActiveAutomation: Boolean,
  ) {
    ensureChannel()
    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      if (openActiveAutomation) {
        putExtra(EXTRA_LAUNCH_ACTIVE_AUTOMATION, true)
      } else {
        putExtra(EXTRA_LAUNCH_SCHEDULED_TASK_ID, task.id)
      }
    }
    val openPending = PendingIntent.getActivity(
      context,
      (if (openActiveAutomation) 30_000 else 20_000) + requestCodeFor(task.id),
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
    const val EXTRA_LAUNCH_ACTIVE_AUTOMATION = "extra_launch_active_automation"
    private const val PRE_ALERT_CODE_OFFSET = 100_000
    private const val NOTIFICATION_BASE_ID = 71_000
    private const val PRE_NOTIFICATION_BASE_ID = 72_000
    private const val OVERDUE_NOTIFICATION_BASE_ID = 73_000
  }
}
