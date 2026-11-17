package com.whatsapprepeater.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsapprepeater.app.WhatsAppRepeaterApp
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Recibe alarmas previas/principales y eventos de reinicio del sistema para mantener
 * activas las programaciones locales o advertir tareas vencidas/omitidas.
 */
class ScheduledReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    val action = intent?.action

    if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
      val pendingBoot = goAsync()
      CoroutineScope(Dispatchers.IO).launch {
        try {
          handleBootReschedule(app)
        } finally {
          pendingBoot.finish()
        }
      }
      return
    }

    val taskId = intent?.getLongExtra(SchedulerAlarmHelper.EXTRA_TASK_ID, -1L) ?: -1L
    if (taskId <= 0L) return
    val isPreAlert = intent?.getBooleanExtra(SchedulerAlarmHelper.EXTRA_IS_PRE_ALERT, false) ?: false

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        handleScheduledTrigger(app, taskId, isPreAlert)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private suspend fun handleBootReschedule(app: WhatsAppRepeaterApp) {
    val repo = app.container.scheduledTaskRepository
    val helper = app.container.schedulerAlarmHelper
    val now = System.currentTimeMillis()
    val settings = app.container.settingsRepository.settings.first()
    val tasks = repo.getAllOnce()

    for (task in tasks) {
      if (!task.enabled) continue
      if (task.isExpiredAt(now)) {
        repo.saveTask(task.copy(enabled = false, status = ScheduledTaskStatus.EXPIRED, updatedAtMillis = now))
        continue
      }
      if (task.triggerAtMillis <= now) {
        if (!settings.vacationPauseEnabled) {
          helper.showOverdueReminderNotification(task)
        }
        val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
        val updated = if (nextTrigger != null) {
          task.copy(triggerAtMillis = nextTrigger, status = ScheduledTaskStatus.FAILED, updatedAtMillis = now)
        } else {
          task.copy(enabled = false, status = ScheduledTaskStatus.FAILED, updatedAtMillis = now)
        }
        repo.saveTask(updated)
        if (updated.enabled) helper.scheduleTaskReminder(updated)
      } else {
        helper.scheduleTaskReminder(task)
      }
    }
  }

  private suspend fun handleScheduledTrigger(
    app: WhatsAppRepeaterApp,
    taskId: Long,
    isPreAlert: Boolean,
  ) {
    val repo = app.container.scheduledTaskRepository
    val task = repo.getById(taskId) ?: return
    if (!task.enabled) return

    val now = System.currentTimeMillis()
    val settings = app.container.settingsRepository.settings.first()

    if (task.isExpiredAt(now)) {
      repo.saveTask(
        task.copy(
          enabled = false,
          status = ScheduledTaskStatus.EXPIRED,
          updatedAtMillis = now,
        ),
      )
      return
    }

    if (isPreAlert) {
      if (!settings.vacationPauseEnabled) {
        app.container.schedulerAlarmHelper.showPreAlertNotification(task)
      }
      return
    }

    if (settings.vacationPauseEnabled) {
      val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
      val updated = if (nextTrigger != null) {
        task.copy(
          triggerAtMillis = nextTrigger,
          status = ScheduledTaskStatus.SKIPPED_VACATION,
          updatedAtMillis = now,
        )
      } else {
        task.copy(
          enabled = false,
          status = ScheduledTaskStatus.SKIPPED_VACATION,
          updatedAtMillis = now,
        )
      }
      repo.saveTask(updated)
      if (updated.enabled) {
        app.container.schedulerAlarmHelper.scheduleTaskReminder(updated)
      }
      return
    }

    app.container.schedulerAlarmHelper.showDueNotification(task)
    val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
    val updated = if (nextTrigger != null) {
      task.copy(
        triggerAtMillis = nextTrigger,
        status = ScheduledTaskStatus.NOTIFIED,
        updatedAtMillis = now,
      )
    } else {
      task.copy(
        status = ScheduledTaskStatus.NOTIFIED,
        updatedAtMillis = now,
      )
    }
    repo.saveTask(updated)
    if (nextTrigger != null && updated.enabled) {
      app.container.schedulerAlarmHelper.scheduleTaskReminder(updated)
    }
  }
}
