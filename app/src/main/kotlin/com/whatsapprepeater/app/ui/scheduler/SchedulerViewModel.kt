package com.whatsapprepeater.app.ui.scheduler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
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
  PAUSED("Pausadas"),
  FINISHED("Completadas / Fallidas"),
  ALL("Todas"),
}

data class ScheduleEditorInput(
  val taskId: Long = 0L,
  val title: String,
  val contact: ContactInfo,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val triggerAtMillis: Long,
  val recurrence: SchedulerRecurrence,
  val customWeekDays: List<Int>,
  val expirationMillis: Long?,
  val notifyBeforeMinutes: Int,
  val autoExecuteWhenReady: Boolean,
)

data class SchedulerUiState(
  val tasks: List<ScheduledTask> = emptyList(),
  val selectedTab: SchedulerTab = SchedulerTab.UPCOMING,
  val vacationPauseEnabled: Boolean = false,
  val activeDraft: AutomationDraft = AutomationDraft(),
  val savedTemplates: List<SavedMessageList> = emptyList(),
  val phoneContacts: List<ContactInfo> = emptyList(),
  val hasContactsPermission: Boolean = false,
  val isAccessibilityConnected: Boolean = false,
  val canScheduleExactAlarms: Boolean = true,
  val feedback: String? = null,
  val isErrorFeedback: Boolean = false,
  val savedConfirmationTask: ScheduledTask? = null,
) {
  val upcomingTasks: List<ScheduledTask>
    get() = tasks.filter {
      it.enabled &&
        it.status != ScheduledTaskStatus.EXPIRED &&
        it.status != ScheduledTaskStatus.FAILED &&
        it.status != ScheduledTaskStatus.COMPLETED &&
        it.status != ScheduledTaskStatus.PAUSED
    }.sortedBy { it.triggerAtMillis }

  val pausedTasks: List<ScheduledTask>
    get() = tasks.filter {
      !it.enabled &&
        it.status != ScheduledTaskStatus.COMPLETED &&
        it.status != ScheduledTaskStatus.FAILED &&
        it.status != ScheduledTaskStatus.EXPIRED ||
        it.status == ScheduledTaskStatus.PAUSED ||
        it.status == ScheduledTaskStatus.SKIPPED_VACATION
    }.sortedBy { it.triggerAtMillis }

  val finishedOrFailedTasks: List<ScheduledTask>
    get() = tasks.filter {
      it.status == ScheduledTaskStatus.COMPLETED ||
        it.status == ScheduledTaskStatus.FAILED ||
        it.status == ScheduledTaskStatus.EXPIRED
    }.sortedByDescending { maxOf(it.lastExecutedAtMillis, it.updatedAtMillis) }

  val canScheduleCurrentDraft: Boolean
    get() = activeDraft.selectedContact != null &&
      (((activeDraft.isMultipleMode && activeDraft.multipleMessages.any { it.isNotBlank() }) ||
        (!activeDraft.isMultipleMode && activeDraft.singleMessage.isNotBlank())) ||
        savedTemplates.isNotEmpty())
}

class SchedulerViewModel(
  private val container: AppContainer,
  private val appContext: Context,
) : ViewModel() {

  private val selectedTab = MutableStateFlow(SchedulerTab.UPCOMING)
  private val feedback = MutableStateFlow<String?>(null)
  private val isError = MutableStateFlow(false)
  private val savedConfirmation = MutableStateFlow<ScheduledTask?>(null)
  private val phoneContacts = MutableStateFlow<List<ContactInfo>>(emptyList())
  private val contactsPermission = MutableStateFlow(false)
  private val exactAlarmReady = MutableStateFlow(container.schedulerAlarmHelper.canScheduleExactAlarms())

  private val baseInputsFlow = combine(
    container.scheduledTaskRepository.observeTasks(),
    container.savedConfigRepository.observeSavedMessageLists(),
    container.settingsRepository.settings,
    container.draft,
    container.accessibilityDriver.isServiceConnected,
  ) { tasks, templates, settings, draft, a11yConnected ->
    BaseInputs(tasks, templates, settings, draft, a11yConnected)
  }

  private val systemInputsFlow = combine(
    phoneContacts,
    contactsPermission,
    exactAlarmReady,
    savedConfirmation,
  ) { contacts, hasPerm, exactReady, confirmTask ->
    SystemInputs(contacts, hasPerm, exactReady, confirmTask)
  }

  val uiState: StateFlow<SchedulerUiState> = combine(
    baseInputsFlow,
    systemInputsFlow,
    selectedTab,
    feedback,
    isError,
  ) { base, sys, tab, msg, err ->
    SchedulerUiState(
      tasks = base.tasks,
      selectedTab = tab,
      vacationPauseEnabled = base.settings.vacationPauseEnabled,
      activeDraft = base.draft,
      savedTemplates = base.templates,
      phoneContacts = sys.contacts,
      hasContactsPermission = sys.hasContactsPermission,
      isAccessibilityConnected = base.a11yConnected,
      canScheduleExactAlarms = sys.canExactAlarm,
      feedback = msg,
      isErrorFeedback = err,
      savedConfirmationTask = sys.savedConfirmation,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SchedulerUiState(),
  )

  private data class BaseInputs(
    val tasks: List<ScheduledTask>,
    val templates: List<SavedMessageList>,
    val settings: com.whatsapprepeater.core.model.AppSettings,
    val draft: AutomationDraft,
    val a11yConnected: Boolean,
  )

  private data class SystemInputs(
    val contacts: List<ContactInfo>,
    val hasContactsPermission: Boolean,
    val canExactAlarm: Boolean,
    val savedConfirmation: ScheduledTask?,
  )

  init {
    refreshEnvironment()
  }

  fun refreshEnvironment() {
    exactAlarmReady.value = container.schedulerAlarmHelper.canScheduleExactAlarms()
    val granted = ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    contactsPermission.value = granted
    if (granted) {
      viewModelScope.launch {
        runCatching { container.contactsRepository.loadPhoneContacts() }
          .onSuccess { phoneContacts.value = it }
      }
    }
  }

  fun openExactAlarmSettings() {
    container.schedulerAlarmHelper.openExactAlarmSettings()
  }

  fun selectTab(tab: SchedulerTab) {
    selectedTab.value = tab
    feedback.value = null
  }

  fun dismissSavedConfirmation() {
    savedConfirmation.value = null
  }

  fun clearFeedback() {
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

  fun saveSchedule(input: ScheduleEditorInput, onSaved: () -> Unit = {}) {
    val cleanPhone = input.contact.phoneNumber.trim()
    val cleanName = input.contact.name.trim().ifEmpty { cleanPhone }
    if (cleanPhone.filter { it.isDigit() }.length < 6 && cleanName.isBlank()) {
      isError.value = true
      feedback.value = "Selecciona un contacto o ingresa un número válido (mínimo 6 dígitos)."
      return
    }

    val cleanMessages = input.messages.map { it.trim() }.filter { it.isNotEmpty() }
    val cleanSingle = input.singleMessage.trim()
    val hasMessages = if (input.isMultipleMode) cleanMessages.isNotEmpty() else cleanSingle.isNotEmpty()
    if (!hasMessages) {
      isError.value = true
      feedback.value = "Escribe al menos un mensaje o carga una plantilla antes de guardar."
      return
    }

    val safeMin = input.minIntervalSeconds.coerceAtLeast(SafetyLimits.MIN_INTERVAL_SECONDS)
    val safeMax = maxOf(safeMin, input.maxIntervalSeconds.coerceAtMost(SafetyLimits.MAX_INTERVAL_SECONDS))
    val intervalErr = IntervalRange(safeMin, safeMax).validate()
    if (intervalErr != null) {
      isError.value = true
      feedback.value = intervalErr
      return
    }

    if (input.recurrence == SchedulerRecurrence.CUSTOM_DAYS && input.customWeekDays.isEmpty()) {
      isError.value = true
      feedback.value = "Selecciona al menos un día de la semana para la recurrencia personalizada."
      return
    }

    val now = System.currentTimeMillis()
    val baseTrigger = input.triggerAtMillis.coerceAtLeast(now + 15_000L)
    val alignedTrigger = SchedulerLogic.normalizeWeekDaysForFirstTrigger(
      startMillis = baseTrigger,
      recurrence = input.recurrence,
      customWeekDays = input.customWeekDays,
    )

    if (input.expirationMillis != null && input.expirationMillis <= alignedTrigger) {
      isError.value = true
      feedback.value = "La fecha y hora de finalización deben ser posteriores al primer envío."
      return
    }

    val safeRepeats = input.repeatCount.coerceIn(
      SafetyLimits.MIN_REPEAT_COUNT,
      SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT,
    )

    val candidate = ScheduledTask(
      id = input.taskId,
      title = input.title.trim().ifEmpty { "Envío a $cleanName" },
      contactId = input.contact.id.ifBlank { "${cleanName}_$cleanPhone" },
      contactName = cleanName,
      contactPhone = cleanPhone,
      isMultipleMode = input.isMultipleMode,
      messages = if (input.isMultipleMode) cleanMessages else emptyList(),
      singleMessage = if (input.isMultipleMode) "" else cleanSingle,
      repeatCount = if (input.isMultipleMode) 1 else safeRepeats,
      minIntervalSeconds = safeMin,
      maxIntervalSeconds = safeMax,
      preferWhatsAppBusiness = input.preferWhatsAppBusiness,
      triggerAtMillis = alignedTrigger,
      recurrence = input.recurrence,
      customWeekDays = if (input.recurrence == SchedulerRecurrence.CUSTOM_DAYS) input.customWeekDays.sorted() else emptyList(),
      expirationMillis = input.expirationMillis,
      enabled = true,
      notifyBeforeMinutes = input.notifyBeforeMinutes,
      autoExecuteWhenReady = input.autoExecuteWhenReady,
      status = ScheduledTaskStatus.PENDING,
      lastFailureReason = "",
      createdAtMillis = if (input.taskId == 0L) now else 0L,
      updatedAtMillis = now,
    )

    viewModelScope.launch {
      withContext(NonCancellable) {
        val existingList = container.scheduledTaskRepository.getAllOnce()
        val preservedCreated = if (input.taskId > 0L) {
          existingList.firstOrNull { it.id == input.taskId }?.createdAtMillis ?: now
        } else {
          now
        }
        val toSave = candidate.copy(createdAtMillis = preservedCreated)
        val conflict = SchedulerLogic.findDuplicateConflict(toSave, existingList)
        if (conflict != null) {
          isError.value = true
          feedback.value = "Bloqueado por seguridad: ya existe \"${conflict.title}\" con el mismo contenido para ${conflict.contactName} en ese horario."
          return@withContext
        }
        val savedId = container.scheduledTaskRepository.saveTask(toSave)
        val persisted = toSave.copy(id = savedId)
        container.schedulerAlarmHelper.scheduleTaskReminder(persisted)
        isError.value = false
        feedback.value = if (input.taskId > 0L) {
          "Programación actualizada y alarma sincronizada."
        } else {
          "Programación guardada correctamente."
        }
        savedConfirmation.value = persisted
        onSaved()
      }
    }
  }

  fun pauseTask(task: ScheduledTask) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.schedulerAlarmHelper.cancelTaskReminder(task.id)
        container.scheduledTaskRepository.saveTask(
          task.copy(
            enabled = false,
            status = ScheduledTaskStatus.PAUSED,
            updatedAtMillis = System.currentTimeMillis(),
          ),
        )
        isError.value = false
        feedback.value = "Programación \"${task.title}\" pausada."
      }
    }
  }

  fun resumeTask(task: ScheduledTask) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        val now = System.currentTimeMillis()
        val targetTrigger = if (task.triggerAtMillis <= now + 10_000L) {
          SchedulerLogic.computeNextTriggerMillis(task, now) ?: (now + 60_000L)
        } else {
          task.triggerAtMillis
        }
        val updated = task.copy(
          triggerAtMillis = targetTrigger,
          enabled = true,
          status = ScheduledTaskStatus.PENDING,
          lastFailureReason = "",
          updatedAtMillis = now,
        )
        container.scheduledTaskRepository.saveTask(updated)
        container.schedulerAlarmHelper.scheduleTaskReminder(updated)
        isError.value = false
        feedback.value = "Programación \"${task.title}\" reanudada."
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
    fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          SchedulerViewModel(container, context.applicationContext) as T
      }
  }
}
