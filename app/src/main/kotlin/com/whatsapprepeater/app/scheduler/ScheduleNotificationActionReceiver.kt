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
 * Procesa acciones directas (Pausar, Reanudar, Cancelar) desde la notificación persistente
 * del Programador sin necesidad de abrir la aplicación completa.
 */
class ScheduleNotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    val action = intent?.action ?: return
    val taskId = intent.getLongExtra(SchedulerAlarmHelper.EXTRA_TASK_ID, -1L)
    if (taskId <= 0L) return

    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val repo = app.container.scheduledTaskRepository
        val helper = app.container.schedulerAlarmHelper
        val task = repo.getById(taskId)
        val now = System.currentTimeMillis()

        when (action) {
          ACTION_PAUSE_SCHEDULE -> {
            if (task != null) {
              helper.cancelTaskReminder(task.id)
              repo.saveTask(
                task.copy(
                  enabled = false,
                  status = ScheduledTaskStatus.PAUSED,
                  updatedAtMillis = now,
                ),
              )
            }
          }

          ACTION_RESUME_SCHEDULE -> {
            if (task != null) {
              val targetTrigger = if (task.triggerAtMillis <= now + 10_000L) {
                SchedulerLogic.computeNextTriggerMillis(task, now) ?: (now + 60_000L)
              } else {
                task.triggerAtMillis
              }
              val resumed = task.copy(
                triggerAtMillis = targetTrigger,
                enabled = true,
                status = ScheduledTaskStatus.PENDING,
                lastFailureReason = "",
                updatedAtMillis = now,
              )
              repo.saveTask(resumed)
              helper.scheduleTaskReminder(resumed)
            }
          }

          ACTION_CANCEL_SCHEDULE -> {
            helper.cancelTaskReminder(taskId)
            repo.deleteTask(taskId)
          }
        }

        val allTasks = repo.getAllOnce()
        val settings = app.container.settingsRepository.settings.first()
        helper.syncActiveScheduleNotification(allTasks, settings.vacationPauseEnabled)
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION_PAUSE_SCHEDULE = "com.whatsapprepeater.app.action.PAUSE_SCHEDULE"
    const val ACTION_RESUME_SCHEDULE = "com.whatsapprepeater.app.action.RESUME_SCHEDULE"
    const val ACTION_CANCEL_SCHEDULE = "com.whatsapprepeater.app.action.CANCEL_SCHEDULE"
  }
}
