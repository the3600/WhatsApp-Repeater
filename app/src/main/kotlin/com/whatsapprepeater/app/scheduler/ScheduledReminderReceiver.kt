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
 * Recibe recordatorios programados locales, respeta Modo Vacaciones / Expiración
 * y avanza recurrencias sin romper políticas de Android.
 */
class ScheduledReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val taskId = intent?.getLongExtra(SchedulerAlarmHelper.EXTRA_TASK_ID, -1L) ?: -1L
    if (taskId <= 0L) return

    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val repo = app.container.scheduledTaskRepository
        val task = repo.getById(taskId) ?: return@launch
        if (!task.enabled) return@launch

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
          return@launch
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
          return@launch
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
      } finally {
        pendingResult.finish()
      }
    }
  }
}
