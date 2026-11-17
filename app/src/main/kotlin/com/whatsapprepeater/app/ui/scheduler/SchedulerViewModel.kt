package com.whatsapprepeater.app.ui.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerLogic
import com.whatsapprepeater.core.model.SchedulerRecurrence
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SchedulerTab(val label: String) {
  UPCOMING("Próximas"),
  HISTORY("Puntualidad"),
  ATTENTION("Vencidas / Fallidas"),
}

data class SchedulerUiState(
  val tasks: List<ScheduledTask> = emptyList(),
  val selectedTab: SchedulerTab = SchedulerTab.UPCOMING,
  val vacationPauseEnabled: Boolean = false,
  val activeDraft: AutomationDraft = AutomationDraft(),
  val feedback: String? = null,
  val isErrorFeedback: Boolean = false,
) {
  val upcomingTasks: List<ScheduledTask>
    get() = tasks.filter {
      it.enabled && it.status != ScheduledTaskStatus.EXPIRED && it.status != ScheduledTaskStatus.FAILED
    }.sortedBy { it.triggerAtMillis }

  val punctualityHistory: List<ScheduledTask>
    get() = tasks.filter { it.lastExecutedAtMillis > 0L || it.status == ScheduledTaskStatus.COMPLETED }
      .sortedByDescending { it.lastExecutedAtMillis }

  val attentionTasks: List<ScheduledTask>
    get() {
      val now = System.currentTimeMillis()
      return tasks.filter {
        it.status == ScheduledTaskStatus.FAILED ||
          it.status == ScheduledTaskStatus.EXPIRED ||
          it.status == ScheduledTaskStatus.SKIPPED_VACATION ||
          (it.enabled && it.triggerAtMillis < now - 60_000L && it.status == ScheduledTaskStatus.NOTIFIED)
      }
    }

  val canScheduleCurrentDraft: Boolean
    get() = activeDraft.selectedContact != null &&
      ((activeDraft.isMultipleMode && activeDraft.multipleMessages.any { it.isNotBlank() }) ||
        (!activeDraft.isMultipleMode && activeDraft.singleMessage.isNotBlank()))
}

class SchedulerViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val selectedTab = MutableStateFlow(SchedulerTab.UPCOMING)
  private val feedback = MutableStateFlow<String?>(null)
  private val isError = MutableStateFlow(false)

  val uiState: StateFlow<SchedulerUiState> = combine(
    container.scheduledTaskRepository.observeTasks(),
    selectedTab,
    container.settingsRepository.settings,
    container.draft,
    feedback,
    isError,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    val tasks = values[0] as List<ScheduledTask>
    val tab = values[1] as SchedulerTab
    val settings = values[2] as com.whatsapprepeater.core.model.AppSettings
    val draft = values[3] as AutomationDraft
    val msg = values[4] as String?
    val err = values[5] as Boolean

    SchedulerUiState(
      tasks = tasks,
      selectedTab = tab,
      vacationPauseEnabled = settings.vacationPauseEnabled,
      activeDraft = draft,
      feedback = msg,
      isErrorFeedback = err,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SchedulerUiState(),
  )

  fun selectTab(tab: SchedulerTab) {
    selectedTab.value = tab
    feedback.value = null
  }

  fun toggleVacationPause(enabled: Boolean) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.settingsRepository.updateVacationPause(enabled)
      }
      isError.value = false
      feedback.value = if (enabled) {
        "Modo vacaciones activado: ninguna programación disparará envíos mientras permanezca activo."
      } else {
        "Modo vacaciones desactivado: las programaciones activas vuelven a su horario."
      }
    }
  }

  fun createFromCurrentDraft(
    title: String,
    triggerAtMillis: Long,
    recurrence: SchedulerRecurrence,
    expirationMillis: Long?,
    notifyBeforeMinutes: Int,
  ) {
    val draft = container.draft.value
    val contact = draft.selectedContact
    if (contact == null) {
      isError.value = true
      feedback.value = "Primero selecciona un destinatario en el flujo principal."
      return
    }
    val hasMessages = if (draft.isMultipleMode) {
      draft.multipleMessages.any { it.isNotBlank() }
    } else {
      draft.singleMessage.isNotBlank()
    }
    if (!hasMessages) {
      isError.value = true
      feedback.value = "El borrador actual no tiene mensajes configurados."
      return
    }
    val now = System.currentTimeMillis()
    val candidate = ScheduledTask(
      title = title.trim().ifEmpty { "Envío a ${contact.name}" },
      contactId = contact.id,
      contactName = contact.name,
      contactPhone = contact.phoneNumber,
      isMultipleMode = draft.isMultipleMode,
      messages = draft.multipleMessages,
      singleMessage = draft.singleMessage,
      repeatCount = draft.repeatCount,
      minIntervalSeconds = draft.minIntervalSeconds,
      maxIntervalSeconds = draft.maxIntervalSeconds,
      preferWhatsAppBusiness = draft.preferWhatsAppBusiness,
      triggerAtMillis = triggerAtMillis.coerceAtLeast(now + 15_000L),
      recurrence = recurrence,
      expirationMillis = expirationMillis,
      enabled = true,
      notifyBeforeMinutes = notifyBeforeMinutes,
      status = ScheduledTaskStatus.PENDING,
      createdAtMillis = now,
      updatedAtMillis = now,
    )

    viewModelScope.launch {
      withContext(NonCancellable) {
        val existing = container.scheduledTaskRepository.getAllOnce()
        val conflict = SchedulerLogic.findDuplicateConflict(candidate, existing)
        if (conflict != null) {
          isError.value = true
          feedback.value = "Bloqueado por seguridad: ya existe \"${conflict.title}\" con el mismo mensaje para ${conflict.contactName} en ese horario."
          return@withContext
        }
        val id = container.scheduledTaskRepository.saveTask(candidate)
        container.schedulerAlarmHelper.scheduleTaskReminder(candidate.copy(id = id))
        isError.value = false
        feedback.value = "Programación guardada y recordatorio activado."
      }
    }
  }

  fun duplicateSchedule(task: ScheduledTask) {
    val now = System.currentTimeMillis()
    val nextTime = maxOf(task.triggerAtMillis + 3_600_000L, now + 900_000L)
    val copy = task.copy(
      id = 0L,
      title = "${task.title} (copia)",
      triggerAtMillis = nextTime,
      status = ScheduledTaskStatus.PENDING,
      enabled = true,
      lastExecutedAtMillis = 0L,
      lastPunctualityDeltaSeconds = 0L,
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    viewModelScope.launch {
      withContext(NonCancellable) {
        val id = container.scheduledTaskRepository.saveTask(copy)
        container.schedulerAlarmHelper.scheduleTaskReminder(copy.copy(id = id))
        isError.value = false
        feedback.value = "Programación duplicada (+1 hora sobre la original para evitar envío doble)."
      }
    }
  }

  fun toggleTaskEnabled(task: ScheduledTask) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        val nextEnabled = !task.enabled
        val updated = task.copy(
          enabled = nextEnabled,
          status = if (nextEnabled && task.status != ScheduledTaskStatus.PENDING) ScheduledTaskStatus.PENDING else task.status,
          updatedAtMillis = System.currentTimeMillis(),
        )
        container.scheduledTaskRepository.saveTask(updated)
        if (nextEnabled) {
          container.schedulerAlarmHelper.scheduleTaskReminder(updated)
        } else {
          container.schedulerAlarmHelper.cancelTaskReminder(updated.id)
        }
      }
    }
  }

  fun deleteTask(id: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.schedulerAlarmHelper.cancelTaskReminder(id)
        container.scheduledTaskRepository.deleteTask(id)
        isError.value = false
        feedback.value = "Programación eliminada."
      }
    }
  }

  fun prepareExecutionNow(task: ScheduledTask) {
    container.loadScheduledTaskIntoDraft(task)
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SchedulerViewModel(container) as T
      }
  }
}
