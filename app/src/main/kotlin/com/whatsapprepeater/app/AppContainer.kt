package com.whatsapprepeater.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.whatsapprepeater.app.autoreply.AutoReplyCoordinator
import com.whatsapprepeater.app.autoreply.AutoReplyNotificationHelper
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
import com.whatsapprepeater.core.data.repository.SavedConfigRepository
import com.whatsapprepeater.core.data.repository.SettingsRepository
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SafetyLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
    // Nunca usar fallbackToDestructiveMigration() para no borrar datos al actualizar.
    .build()

  val historyRepository: HistoryRepository = RoomHistoryRepository(database.executionHistoryDao())
  val savedConfigRepository: SavedConfigRepository = RoomSavedConfigRepository(
    savedMessageListDao = database.savedMessageListDao(),
    automationConfigDao = database.automationConfigDao(),
  )
  val autoReplyRepository: AutoReplyRepository = RoomAutoReplyRepository(
    ruleDao = database.autoReplyRuleDao(),
    logDao = database.autoReplyLogDao(),
  )
  val autoReplySettingsRepository: AutoReplySettingsRepository = DataStoreAutoReplySettingsRepository(context)
  val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
  val contactsRepository: ContactsRepository = AndroidContactsRepository(context)
  val accessibilityDriver = AndroidAccessibilityDriver(context)
  val updateManager = UpdateManager(context)
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
    },
    scope = appScope,
  )

  private val _draft = MutableStateFlow(AutomationDraft())
  val draft: StateFlow<AutomationDraft> = _draft.asStateFlow()

  @Volatile
  private var draftMutatedInMemory = false

  init {
    appScope.launch {
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
    }

    appScope.launch {
      automationEngine.state.collect { state ->
        notificationHelper.syncWithState(state)
      }
    }
  }

  fun updateDraft(transform: (AutomationDraft) -> AutomationDraft) {
    draftMutatedInMemory = true
    _draft.update(transform)
    val snapshot = _draft.value
    appScope.launch {
      savedConfigRepository.saveActiveDraft(snapshot.toPersisted())
    }
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
    val snapshot = _draft.value
    appScope.launch {
      savedConfigRepository.saveActiveDraft(snapshot.toPersisted())
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
