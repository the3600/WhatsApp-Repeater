package com.whatsapprepeater.app.scheduler

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.whatsapprepeater.app.MainActivity
import com.whatsapprepeater.app.R
import com.whatsapprepeater.core.model.ScheduleCountdownFormatter
import com.whatsapprepeater.core.model.ScheduleNotificationMode
import com.whatsapprepeater.core.model.ScheduledTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutoExecuteReadiness(
  val canExecute: Boolean,
  val reason: String? = null,
)

/**
 * Gestiona alarmas exactas/compatibles y el Centro de Notificaciones del Programador:
 * - Notificación persistente consolidada (ID único, sin duplicados) con cuenta regresiva
 *   nativa de Android (`setUsesChronometer` + `setChronometerCountDown`, sin consumo extra de batería)
 *   y acciones directas Pausar / Reanudar / Cancelar / Abrir app.
 * - Notificaciones de alta prioridad para avisos previos, inicio de ejecución e intervención requerida.
 */
class SchedulerAlarmHelper(
  private val context: Context,
) {
  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val alarmManager =
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  fun ensureChannel() {
    val alertsChannel = NotificationChannel(
      CHANNEL_ID,
      "Alertas de programación y ejecución",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Alertas previas, ejecución programada y avisos cuando se requiere intervención"
    }
    val statusChannel = NotificationChannel(
      ACTIVE_STATUS_CHANNEL_ID,
      "Programaciones activas (cuenta regresiva)",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Muestra la próxima programación activa o pausada con cuenta regresiva y acciones rápidas"
      setShowBadge(false)
    }
    notificationManager.createNotificationChannel(alertsChannel)
    notificationManager.createNotificationChannel(statusChannel)
  }

  fun hasNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
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

  /**
   * Sincroniza la notificación persistente consolidada de programaciones activas/pausadas/fallidas.
   * Utiliza un único ID fijo para evitar notificaciones duplicadas y usa el cronómetro nativo
   * en cuenta regresiva de Android (eficiente en batería, sin hilos ni servicios permanentes).
   */
  fun syncActiveScheduleNotification(
    tasks: List<ScheduledTask>,
    vacationPauseEnabled: Boolean,
  ) {
    val now = System.currentTimeMillis()
    val state = ScheduleCountdownFormatter.deriveNotificationState(
      tasks = tasks,
      vacationPauseEnabled = vacationPauseEnabled,
      nowMillis = now,
    )
    val task = state.primaryTask
    if (state.mode == ScheduleNotificationMode.NONE || task == null) {
      notificationManager.cancel(ACTIVE_SCHEDULE_NOTIFICATION_ID)
      return
    }
    if (!hasNotificationPermission()) return

    ensureChannel()
    val dateFmt = SimpleDateFormat("dd/MM • h:mm a", Locale.forLanguageTag("es")).format(Date(task.triggerAtMillis))
    val typeLabel = if (task.isMultipleMode) {
      "Secuencia (${task.totalMessagesCount} mensajes)"
    } else {
      "Mensaje único (${task.repeatCount}x)"
    }
    val openSchedulerPending = buildOpenSchedulerPendingIntent()

    val builder = NotificationCompat.Builder(context, ACTIVE_STATUS_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setOnlyAlertOnce(true)
      .setContentIntent(openSchedulerPending)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    when (state.mode) {
      ScheduleNotificationMode.ACTIVE -> {
        val remainingText = ScheduleCountdownFormatter.formatRemaining(state.remainingMillis)
        val extraSuffix = if (state.activeCount > 1) " (+${state.activeCount - 1} más)" else ""
        val title = "Programación activa: ${task.title}"
        val shortLine = "${task.contactName} • Próximo: $dateFmt • Faltan $remainingText"
        val bigText = buildString {
          append("Destinatario: ${task.contactName} (${task.contactPhone})\n")
          append("Próximo envío: $dateFmt\n")
          append("Faltan: $remainingText\n")
          append("Tipo: $typeLabel • ${task.recurrence.label}\n")
          append("Pendientes: ${task.totalMessagesCount} msg$extraSuffix")
        }

        builder
          .setContentTitle(title)
          .setSubText("Programación activa$extraSuffix")
          .setContentText(shortLine)
          .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
          .setOngoing(true)
          .setWhen(task.triggerAtMillis)
          .setShowWhen(true)
          .setUsesChronometer(task.triggerAtMillis > now)
          .setChronometerCountDown(true)
          .addAction(0, "Pausar", buildScheduleActionPendingIntent(task.id, ScheduleNotificationActionReceiver.ACTION_PAUSE_SCHEDULE, 1))
          .addAction(0, "Cancelar", buildScheduleActionPendingIntent(task.id, ScheduleNotificationActionReceiver.ACTION_CANCEL_SCHEDULE, 2))
          .addAction(0, "Abrir", openSchedulerPending)
      }

      ScheduleNotificationMode.PAUSED, ScheduleNotificationMode.VACATION_PAUSED -> {
        val pausedTitle = if (state.mode == ScheduleNotificationMode.VACATION_PAUSED) {
          "Programación pausada (Vacaciones)"
        } else {
          "Programación pausada: ${task.title}"
        }
        val bigText = buildString {
          append("Destinatario: ${task.contactName} (${task.contactPhone})\n")
          append("Horario original: $dateFmt\n")
          append("Estado: ${if (state.mode == ScheduleNotificationMode.VACATION_PAUSED) "Pausa por vacaciones" else "Pausada por el usuario"}\n")
          append("Tipo: $typeLabel")
        }
        builder
          .setContentTitle(pausedTitle)
          .setSubText("Pausada")
          .setContentText("${task.contactName} • Pausada ($dateFmt)")
          .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
          .setOngoing(false)
          .setUsesChronometer(false)
          .addAction(0, "Reanudar", buildScheduleActionPendingIntent(task.id, ScheduleNotificationActionReceiver.ACTION_RESUME_SCHEDULE, 3))
          .addAction(0, "Cancelar", buildScheduleActionPendingIntent(task.id, ScheduleNotificationActionReceiver.ACTION_CANCEL_SCHEDULE, 4))
          .addAction(0, "Abrir", openSchedulerPending)
      }

      ScheduleNotificationMode.FAILED -> {
        val reason = task.lastFailureReason.ifBlank { "No pudo ejecutarse automáticamente." }
        val bigText = buildString {
          append("Destinatario: ${task.contactName} (${task.contactPhone})\n")
          append("Estado: Fallida / Intervención requerida\n")
          append("Motivo: $reason")
        }
        val retryPending = buildOpenTaskConfirmationPendingIntent(task.id)
        builder
          .setContentTitle("Error en programación: ${task.title}")
          .setSubText("Requiere revisión")
          .setContentText("${task.contactName} • $reason")
          .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
          .setOngoing(false)
          .setUsesChronometer(false)
          .addAction(0, "Revisar", openSchedulerPending)
          .addAction(0, "Enviar ahora", retryPending)
      }

      ScheduleNotificationMode.NONE -> return
    }

    runCatching {
      notificationManager.notify(ACTIVE_SCHEDULE_NOTIFICATION_ID, builder.build())
    }
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

  private fun buildOpenSchedulerPendingIntent(): PendingIntent {
    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_SCHEDULER, true)
    }
    return PendingIntent.getActivity(
      context,
      40_001,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildOpenTaskConfirmationPendingIntent(taskId: Long): PendingIntent {
    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_LAUNCH_SCHEDULED_TASK_ID, taskId)
    }
    return PendingIntent.getActivity(
      context,
      20_000 + requestCodeFor(taskId),
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildScheduleActionPendingIntent(
    taskId: Long,
    action: String,
    slot: Int,
  ): PendingIntent {
    val intent = Intent(context, ScheduleNotificationActionReceiver::class.java).apply {
      this.action = action
      putExtra(EXTRA_TASK_ID, taskId)
    }
    return PendingIntent.getBroadcast(
      context,
      50_000 + slot * 1000 + (requestCodeFor(taskId) % 999),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
    if (!hasNotificationPermission()) return
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
    const val ACTIVE_STATUS_CHANNEL_ID = "whatsapp_repeater_scheduler_status"
    const val EXTRA_TASK_ID = "extra_scheduled_task_id"
    const val EXTRA_IS_PRE_ALERT = "extra_scheduled_is_pre_alert"
    const val EXTRA_LAUNCH_SCHEDULED_TASK_ID = "extra_launch_scheduled_task_id"
    const val EXTRA_LAUNCH_ACTIVE_AUTOMATION = "extra_launch_active_automation"
    const val EXTRA_OPEN_SCHEDULER = "extra_open_scheduler"
    private const val ACTIVE_SCHEDULE_NOTIFICATION_ID = 70_100
    private const val PRE_ALERT_CODE_OFFSET = 100_000
    private const val NOTIFICATION_BASE_ID = 71_000
    private const val PRE_NOTIFICATION_BASE_ID = 72_000
    private const val OVERDUE_NOTIFICATION_BASE_ID = 73_000
  }
}
