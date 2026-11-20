package com.whatsapprepeater.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.whatsapprepeater.app.autoreply.AutoReplyCoordinator
import com.whatsapprepeater.app.autoreply.AutoReplyNotificationHelper
import com.whatsapprepeater.app.scheduler.SchedulerAlarmHelper
import com.whatsapprepeater.app.service.AndroidAccessibilityDriver
import com.whatsapprepeater.app.service.AutomationNotificationHelper
import com.whatsapprepeater.app.updater.UpdateManager
import com.whatsapprepeater.core.automation.AutomationEngine
import com.whatsapprepeater.core.data.local.AppDatabase
import com.whatsapprepeater.core.data.local.DatabaseBackupManager
import com.whatsapprepeater.core.data.repository.AndroidContactsRepository
import com.whatsapprepeater.core.data.repository.AutoReplyRepository
import com.whatsapprepeater.core.data.repository.AutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.ContactsRepository
import com.whatsapprepeater.core.data.repository.DataStoreAutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.DataStoreSettingsRepository
import com.whatsapprepeater.core.data.repository.HistoryRepository
import com.whatsapprepeater.core.data.repository.RoomAutoReplyRepository
import com.whatsapprepeater.core.data.repository.RoomHistoryRepository
import com.whatsapprepeater.core.data.repository.RoomSavedConfigRepository
import com.whatsapprepeater.core.data.repository.RoomScheduledTaskRepository
import com.whatsapprepeater.core.data.repository.SavedConfigRepository
import com.whatsapprepeater.core.data.repository.ScheduledTaskRepository
import com.whatsapprepeater.core.data.repository.SettingsRepository
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.DynamicVariablesEngine
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerLogic
import com.whatsapprepeater.core.model.SchedulerRecurrence
import com.whatsapprepeater.core.model.SavedMessageList
import com.whatsapprepeater.core.model.VariableResolutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Borrador compartido y persistido localmente entre reinicios y actualizaciones de APK.
 */
data class AutomationDraft(
  val selectedContact: ContactInfo? = null,
  val isMultipleMode: Boolean = true,
  val multipleMessages: List<String> = emptyList(),
  val singleMessage: String = "",
  val repeatCount: Int = 3,
  val minIntervalSeconds: Int = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
  val maxIntervalSeconds: Int = SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS,
  val preferWhatsAppBusiness: Boolean = false,
) {
  fun toMessageMode(): MessageMode = if (isMultipleMode) {
    MessageMode.MultipleMessages(multipleMessages)
  } else {
    MessageMode.RepeatSingleMessage(singleMessage, repeatCount)
  }

  fun toIntervalRange(): IntervalRange = IntervalRange(
    minSeconds = minIntervalSeconds,
    maxSeconds = maxIntervalSeconds,
  )

  fun toConfirmedConfig(confirmed: Boolean): AutomationConfig? {
    val contact = selectedContact ?: return null
    val targetPackage = if (preferWhatsAppBusiness) {
      AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
    } else {
      AutomationConfig.WHATSAPP_PACKAGE
    }
    return AutomationConfig(
      contact = contact,
      mode = toMessageMode(),
      intervalRange = toIntervalRange(),
      userConfirmed = confirmed,
      targetPackage = targetPackage,
    )
  }

  fun toPersisted(): PersistedAutomationDraft = PersistedAutomationDraft(
    contactId = selectedContact?.id,
    contactName = selectedContact?.name,
    contactPhone = selectedContact?.phoneNumber,
    isMultipleMode = isMultipleMode,
    messages = multipleMessages,
    singleMessage = singleMessage,
    repeatCount = repeatCount,
    minIntervalSeconds = minIntervalSeconds,
    maxIntervalSeconds = maxIntervalSeconds,
    preferWhatsAppBusiness = preferWhatsAppBusiness,
    updatedAtMillis = System.currentTimeMillis(),
  )

  companion object {
    fun fromPersisted(saved: PersistedAutomationDraft): AutomationDraft {
      val name = saved.contactName
      val phone = saved.contactPhone
      val restoredContact = if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
        ContactInfo(
          id = saved.contactId ?: "${name}_$phone",
          name = name,
          phoneNumber = phone,
        )
      } else {
        null
      }
      return AutomationDraft(
        selectedContact = restoredContact,
        isMultipleMode = saved.isMultipleMode,
        multipleMessages = saved.messages,
        singleMessage = saved.singleMessage,
        repeatCount = saved.repeatCount,
        minIntervalSeconds = saved.minIntervalSeconds,
        maxIntervalSeconds = saved.maxIntervalSeconds,
        preferWhatsAppBusiness = saved.preferWhatsAppBusiness,
      )
    }
  }
}

class AppContainer(context: Context) {
  private val appContext = context.applicationContext
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    // Protección previa: respaldar SQLite antes de ejecutar cualquier migración de esquema.
    DatabaseBackupManager.backupBeforeMigrationIfNeeded(
      context = appContext,
      dbName = AppDatabase.DB_NAME,
      targetVersion = AppDatabase.CURRENT_VERSION,
    )
  }

  private val database: AppDatabase = Room.databaseBuilder(
    appContext,
    AppDatabase::class.java,
    AppDatabase.DB_NAME,
  )
    .addMigrations(
      AppDatabase.MIGRATION_1_2,
      AppDatabase.MIGRATION_2_3,
      AppDatabase.MIGRATION_3_4,
      AppDatabase.MIGRATION_4_5,
      AppDatabase.MIGRATION_5_6,
    )
    // Nunca usar fallbackToDestructiveMigration() para no borrar datos al actualizar.
    .build()

  val historyRepository: HistoryRepository = RoomHistoryRepository(database.executionHistoryDao())
  val savedConfigRepository: SavedConfigRepository = RoomSavedConfigRepository(
    savedMessageListDao = database.savedMessageListDao(),
    automationConfigDao = database.automationConfigDao(),
  )
  val scheduledTaskRepository: ScheduledTaskRepository = RoomScheduledTaskRepository(database.scheduledTaskDao())
  val autoReplyRepository: AutoReplyRepository = RoomAutoReplyRepository(
    ruleDao = database.autoReplyRuleDao(),
    logDao = database.autoReplyLogDao(),
  )
  val autoReplySettingsRepository: AutoReplySettingsRepository = DataStoreAutoReplySettingsRepository(context)
  val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
  val contactsRepository: ContactsRepository = AndroidContactsRepository(context)
  val accessibilityDriver = AndroidAccessibilityDriver(context)
  val updateManager = UpdateManager(context)
  val schedulerAlarmHelper = SchedulerAlarmHelper(appContext)
  private val notificationHelper = AutomationNotificationHelper(context)
  private val autoReplyNotificationHelper = AutoReplyNotificationHelper(context)

  val autoReplyCoordinator = AutoReplyCoordinator(
    appContext = appContext,
    autoReplyRepository = autoReplyRepository,
    autoReplySettingsRepository = autoReplySettingsRepository,
    contactsRepository = contactsRepository,
    notificationHelper = autoReplyNotificationHelper,
    scope = appScope,
  )

  val automationEngine = AutomationEngine(
    driver = accessibilityDriver,
    onExecutionFinished = { record ->
      historyRepository.recordExecution(record)
      finalizeScheduledTaskFromExecution(record)
    },
    scope = appScope,
  )

  private val _draft = MutableStateFlow(AutomationDraft())
  val draft: StateFlow<AutomationDraft> = _draft.asStateFlow()

  private val _pendingScheduleLaunch = MutableStateFlow<Long?>(null)
  val pendingScheduleLaunch: StateFlow<Long?> = _pendingScheduleLaunch.asStateFlow()

  private val _pendingActiveAutomationLaunch = MutableStateFlow(false)
  val pendingActiveAutomationLaunch: StateFlow<Boolean> = _pendingActiveAutomationLaunch.asStateFlow()

  private val _pendingOpenScheduler = MutableStateFlow(false)
  val pendingOpenScheduler: StateFlow<Boolean> = _pendingOpenScheduler.asStateFlow()

  @Volatile
  private var activeScheduledTaskId: Long? = null

  private val draftPersistMutex = Mutex()
  @Volatile
  private var draftMutatedInMemory = false

  init {
    appScope.launch {
      try {
        val persistedDraft = savedConfigRepository.getActiveDraftOnce()
        if (!draftMutatedInMemory) {
          if (persistedDraft != null) {
            _draft.value = AutomationDraft.fromPersisted(persistedDraft)
          } else {
            val saved = settingsRepository.settings.first()
            if (!draftMutatedInMemory) {
              _draft.update {
                it.copy(
                  minIntervalSeconds = saved.defaultMinIntervalSeconds,
                  maxIntervalSeconds = saved.defaultMaxIntervalSeconds,
                  preferWhatsAppBusiness = saved.preferWhatsAppBusiness,
                )
              }
            }
          }
        }
      } catch (c: CancellationException) {
        throw c
      } catch (_: Exception) {
        // Mantiene valores seguros en memoria si el primer acceso a Room/DataStore falla.
      }
    }

    appScope.launch {
      automationEngine.state.collect { state ->
        notificationHelper.syncWithState(state)
      }
    }

    appScope.launch {
      try {
        combine(
          scheduledTaskRepository.observeTasks(),
          settingsRepository.settings,
        ) { tasks, settings ->
          tasks to settings.vacationPauseEnabled
        }.collect { (tasks, vacationPaused) ->
          schedulerAlarmHelper.syncActiveScheduleNotification(tasks, vacationPaused)
        }
      } catch (c: CancellationException) {
        throw c
      } catch (_: Exception) {
        // Evita cierre ante errores transitorios de lectura en segundo plano.
      }
    }
  }

  fun updateDraft(transform: (AutomationDraft) -> AutomationDraft) {
    draftMutatedInMemory = true
    _draft.updateAndGet(transform)
    schedulePersistLatestDraft()
  }

  fun resetDraftForNewAutomation(
    defaultMin: Int,
    defaultMax: Int,
    preferBusiness: Boolean,
  ) {
    draftMutatedInMemory = true
    _draft.value = AutomationDraft(
      minIntervalSeconds = defaultMin,
      maxIntervalSeconds = defaultMax,
      preferWhatsAppBusiness = preferBusiness,
    )
    schedulePersistLatestDraft()
  }

  private fun schedulePersistLatestDraft() {
    appScope.launch {
      draftPersistMutex.withLock {
        savedConfigRepository.saveActiveDraft(_draft.value.toPersisted())
      }
    }
  }

  fun requestOpenScheduledTask(taskId: Long) {
    if (taskId > 0L) {
      _pendingScheduleLaunch.value = taskId
    }
  }

  fun clearPendingScheduledLaunch() {
    _pendingScheduleLaunch.value = null
  }

  fun requestOpenActiveAutomation() {
    _pendingActiveAutomationLaunch.value = true
  }

  fun clearPendingActiveAutomationLaunch() {
    _pendingActiveAutomationLaunch.value = false
  }

  fun requestOpenScheduler() {
    _pendingOpenScheduler.value = true
  }

  fun clearPendingOpenScheduler() {
    _pendingOpenScheduler.value = false
  }

  fun loadTemplateIntoDraft(template: SavedMessageList) {
    activeScheduledTaskId = null
    updateDraft { current ->
      current.copy(
        isMultipleMode = template.isMultipleMode,
        multipleMessages = template.messages,
        singleMessage = template.singleMessage,
        repeatCount = template.repeatCount.coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT),
      )
    }
    if (template.id > 0L) {
      appScope.launch {
        runCatching { savedConfigRepository.recordTemplateUsage(template.id) }
      }
    }
  }

  fun loadScheduledTaskIntoDraft(task: ScheduledTask) {
    activeScheduledTaskId = task.id
    val contact = ContactInfo(
      id = task.contactId.ifBlank { "${task.contactName}_${task.contactPhone}" },
      name = task.contactName,
      phoneNumber = task.contactPhone,
    )
    updateDraft {
      AutomationDraft(
        selectedContact = contact,
        isMultipleMode = task.isMultipleMode,
        multipleMessages = task.messages,
        singleMessage = task.singleMessage,
        repeatCount = task.repeatCount,
        minIntervalSeconds = task.minIntervalSeconds,
        maxIntervalSeconds = task.maxIntervalSeconds,
        preferWhatsAppBusiness = task.preferWhatsAppBusiness,
      )
    }
  }

  sealed interface ScheduledLaunchResult {
    data object Started : ScheduledLaunchResult
    data class Blocked(val reason: String) : ScheduledLaunchResult
  }

  fun markActiveScheduledTaskStarted() {
    val taskId = activeScheduledTaskId ?: return
    appScope.launch {
      runCatching {
        val task = scheduledTaskRepository.getById(taskId) ?: return@runCatching
        val now = System.currentTimeMillis()
        scheduledTaskRepository.saveTask(
          task.copy(
            status = ScheduledTaskStatus.ACTIVE,
            lastFailureReason = "",
            updatedAtMillis = now,
          ),
        )
      }
    }
  }

  suspend fun executeScheduledTaskIfReady(task: ScheduledTask): ScheduledLaunchResult {
    val readiness = schedulerAlarmHelper.checkAutoExecuteReadiness(
      isAccessibilityConnected = accessibilityDriver.isServiceConnected.value,
      isAutomationAlreadyRunning = automationEngine.state.value.isRunning || automationEngine.state.value.isPaused,
    )
    if (!readiness.canExecute) {
      return ScheduledLaunchResult.Blocked(readiness.reason ?: "Condiciones no disponibles para envío automático.")
    }

    val contact = ContactInfo(
      id = task.contactId.ifBlank { "${task.contactName}_${task.contactPhone}" },
      name = task.contactName,
      phoneNumber = task.contactPhone,
    )

    val resolvedMode = if (task.isMultipleMode) {
      val (resolved, err) = DynamicVariablesEngine.validateBatch(task.messages, contact)
      if (err != null) {
        return ScheduledLaunchResult.Blocked(err.reason)
      }
      MessageMode.MultipleMessages(resolved)
    } else {
      when (val res = DynamicVariablesEngine.resolveOrValidate(task.singleMessage, contact)) {
        is VariableResolutionResult.Resolved -> MessageMode.RepeatSingleMessage(res.text, task.repeatCount)
        is VariableResolutionResult.Unresolved -> return ScheduledLaunchResult.Blocked(res.reason)
      }
    }

    val targetPkg = if (task.preferWhatsAppBusiness) {
      AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
    } else {
      AutomationConfig.WHATSAPP_PACKAGE
    }
    val config = AutomationConfig(
      contact = contact,
      mode = resolvedMode,
      intervalRange = IntervalRange(task.minIntervalSeconds, task.maxIntervalSeconds),
      userConfirmed = true,
      targetPackage = targetPkg,
    )

    loadScheduledTaskIntoDraft(task)
    val now = System.currentTimeMillis()
    scheduledTaskRepository.saveTask(
      task.copy(
        status = ScheduledTaskStatus.ACTIVE,
        lastFailureReason = "",
        updatedAtMillis = now,
      ),
    )

    automationEngine.start(config)
    val immediateState = automationEngine.state.value
    if (immediateState.status == AutomationStatus.ERROR) {
      activeScheduledTaskId = null
      return ScheduledLaunchResult.Blocked(immediateState.errorMessage ?: "No se pudo iniciar el motor de envío.")
    }
    return ScheduledLaunchResult.Started
  }

  private suspend fun finalizeScheduledTaskFromExecution(record: ExecutionRecord) {
    val taskId = activeScheduledTaskId ?: return
    activeScheduledTaskId = null
    runCatching {
      val task = scheduledTaskRepository.getById(taskId) ?: return@runCatching
      val now = System.currentTimeMillis()
      val deltaSeconds = ((now - task.triggerAtMillis) / 1000L)
      val succeeded = record.finalStatus == ExecutionFinalStatus.COMPLETED && record.confirmedCount > 0

      if (succeeded) {
        val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
        val updated = if (nextTrigger != null) {
          task.copy(
            triggerAtMillis = nextTrigger,
            status = ScheduledTaskStatus.PENDING,
            enabled = true,
            lastExecutedAtMillis = now,
            lastPunctualityDeltaSeconds = deltaSeconds,
            lastFailureReason = "",
            updatedAtMillis = now,
          )
        } else {
          task.copy(
            status = ScheduledTaskStatus.COMPLETED,
            enabled = false,
            lastExecutedAtMillis = now,
            lastPunctualityDeltaSeconds = deltaSeconds,
            lastFailureReason = "",
            updatedAtMillis = now,
          )
        }
        scheduledTaskRepository.saveTask(updated)
        if (updated.enabled && nextTrigger != null) {
          schedulerAlarmHelper.scheduleTaskReminder(updated)
        } else {
          schedulerAlarmHelper.cancelTaskReminder(updated.id)
        }
      } else {
        val nextTrigger = SchedulerLogic.computeNextTriggerMillis(task, now)
        val updated = if (nextTrigger != null) {
          task.copy(
            triggerAtMillis = nextTrigger,
            status = ScheduledTaskStatus.FAILED,
            enabled = true,
            lastPunctualityDeltaSeconds = deltaSeconds,
            lastFailureReason = record.statusDetail,
            updatedAtMillis = now,
          )
        } else {
          task.copy(
            enabled = false,
            status = ScheduledTaskStatus.FAILED,
            lastPunctualityDeltaSeconds = deltaSeconds,
            lastFailureReason = record.statusDetail,
            updatedAtMillis = now,
          )
        }
        scheduledTaskRepository.saveTask(updated)
        if (updated.enabled && nextTrigger != null) {
          schedulerAlarmHelper.scheduleTaskReminder(updated)
        } else {
          schedulerAlarmHelper.cancelTaskReminder(updated.id)
        }
      }
    }
  }

  fun getDatabaseSizeBytes(): Long =
    appContext.getDatabasePath(AppDatabase.DB_NAME).takeIf { it.exists() }?.length() ?: 0L

  fun getUpdateCacheSizeBytes(): Long {
    val dir = java.io.File(appContext.cacheDir, "updates")
    return if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
  }

  fun clearUpdateCache() {
    val dir = java.io.File(appContext.cacheDir, "updates")
    if (dir.exists()) dir.deleteRecursively()
  }

  suspend fun factoryResetLocalData() {
    automationEngine.stop()
    autoReplyCoordinator.emergencyStop()
    historyRepository.clearHistory()
    autoReplyRepository.clearHistory()
    settingsRepository.resetToDefaults()
    autoReplySettingsRepository.resetToDefaults()
    resetDraftForNewAutomation(
      defaultMin = SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS,
      defaultMax = SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS,
      preferBusiness = false,
    )
  }
}

class WhatsAppRepeaterApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
