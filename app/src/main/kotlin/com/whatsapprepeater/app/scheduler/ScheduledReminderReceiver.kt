package com.whatsapprepeater.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.WhatsAppRepeaterApp
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Recibe alarmas previas/principales y eventos de reinicio o cambio de hora del sistema para
 * mantener activas las programaciones y ejecutar el envío real cuando Android e HyperOS lo permiten.
 */
class ScheduledReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    val action = intent?.action

    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
      action == Intent.ACTION_MY_PACKAGE_REPLACED ||
      action == Intent.ACTION_TIME_CHANGED ||
      action == Intent.ACTION_TIMEZONE_CHANGED
    ) {
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
      if (!task.enabled || task.status == ScheduledTaskStatus.PAUSED) continue
      if (task.isExpiredAt(now)) {
        repo.saveTask(task.copy(enabled = false, status = ScheduledTaskStatus.EXPIRED, updatedAtMillis = now))
        continue
      }
      if (task.triggerAtMillis <= now) {
        val reason = "La hora programada pasó mientras el dispositivo estaba apagado o restringido."
        if (!settings.vacationPauseEnabled) {
          helper.showOverdueReminderNotification(task)
        }
        val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
        val updated = if (nextTrigger != null) {
          task.copy(
            triggerAtMillis = nextTrigger,
            status = ScheduledTaskStatus.FAILED,
            lastFailureReason = reason,
            updatedAtMillis = now,
          )
        } else {
          task.copy(
            enabled = false,
            status = ScheduledTaskStatus.FAILED,
            lastFailureReason = reason,
            updatedAtMillis = now,
          )
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
    val helper = app.container.schedulerAlarmHelper
    val task = repo.getById(taskId) ?: return
    if (!task.enabled || task.status == ScheduledTaskStatus.PAUSED) return

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
        helper.showPreAlertNotification(task)
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
        helper.scheduleTaskReminder(updated)
      }
      return
    }

    if (task.autoExecuteWhenReady) {
      when (val launch = app.container.executeScheduledTaskIfReady(task)) {
        AppContainer.ScheduledLaunchResult.Started -> {
          helper.showAutoExecutionStartedNotification(task)
          return
        }
        is AppContainer.ScheduledLaunchResult.Blocked -> {
          helper.showInterventionRequiredNotification(task, launch.reason)
          val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
          val updated = if (nextTrigger != null) {
            task.copy(
              triggerAtMillis = nextTrigger,
              status = ScheduledTaskStatus.FAILED,
              lastFailureReason = launch.reason,
              updatedAtMillis = now,
            )
          } else {
            task.copy(
              enabled = false,
              status = ScheduledTaskStatus.FAILED,
              lastFailureReason = launch.reason,
              updatedAtMillis = now,
            )
          }
          repo.saveTask(updated)
          if (nextTrigger != null && updated.enabled) {
            helper.scheduleTaskReminder(updated)
          }
          return
        }
      }
    }

    // Modo aviso manual: no simula envío; notifica al usuario para confirmar y ejecutar.
    helper.showDueNotification(task)
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
      helper.scheduleTaskReminder(updated)
    }
  }
}
