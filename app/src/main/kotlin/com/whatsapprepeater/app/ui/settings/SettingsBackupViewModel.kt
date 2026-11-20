package com.whatsapprepeater.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.core.data.backup.LocalBackupManager
import com.whatsapprepeater.core.data.local.DatabaseBackupManager
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
import com.whatsapprepeater.core.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsBackupUiState(
  val settings: AppSettings = AppSettings(),
  val isAccessibilityReady: Boolean = false,
  val activeAutomationState: AutomationState = AutomationState(),
  val dbSizeBytes: Long = 0L,
  val updateCacheBytes: Long = 0L,
  val preMigrationBackupsCount: Int = 0,
  val isProcessingBackup: Boolean = false,
  val feedbackMessage: String? = null,
  val isErrorFeedback: Boolean = false,
)

class SettingsBackupViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val dbSizeBytes = MutableStateFlow(container.getDatabaseSizeBytes())
  private val cacheBytes = MutableStateFlow(container.getUpdateCacheSizeBytes())
  private val preMigrationCount = MutableStateFlow(0)
  private val isProcessingBackup = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)
  private val isErrorFeedback = MutableStateFlow(false)

  val uiState: StateFlow<SettingsBackupUiState> = combine(
    container.settingsRepository.settings,
    container.accessibilityDriver.isServiceConnected,
    container.automationEngine.state,
    dbSizeBytes,
    cacheBytes,
    preMigrationCount,
    isProcessingBackup,
    feedbackMessage,
    isErrorFeedback,
  ) { values ->
    SettingsBackupUiState(
      settings = values[0] as AppSettings,
      isAccessibilityReady = values[1] as Boolean,
      activeAutomationState = values[2] as AutomationState,
      dbSizeBytes = values[3] as Long,
      updateCacheBytes = values[4] as Long,
      preMigrationBackupsCount = values[5] as Int,
      isProcessingBackup = values[6] as Boolean,
      feedbackMessage = values[7] as String?,
      isErrorFeedback = values[8] as Boolean,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SettingsBackupUiState(),
  )

  fun refreshStorageStats(context: Context) {
    dbSizeBytes.value = container.getDatabaseSizeBytes()
    cacheBytes.value = container.getUpdateCacheSizeBytes()
    preMigrationCount.value = DatabaseBackupManager.listBackups(context).size
  }

  fun setHapticFeedback(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.updateHapticFeedback(enabled) }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.updateKeepScreenOn(enabled) }
  }

  fun setPreferWhatsAppBusiness(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.updatePreferWhatsAppBusiness(enabled) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { container.settingsRepository.updateThemeMode(mode) }
  }

  fun adjustDefaultMinInterval(delta: Int) {
    val s = uiState.value.settings
    val newMin = (s.defaultMinIntervalSeconds + delta).coerceIn(
      SafetyLimits.MIN_INTERVAL_SECONDS,
      s.defaultMaxIntervalSeconds,
    )
    viewModelScope.launch { container.settingsRepository.updateDefaultIntervals(newMin, s.defaultMaxIntervalSeconds) }
  }

  fun adjustDefaultMaxInterval(delta: Int) {
    val s = uiState.value.settings
    val newMax = (s.defaultMaxIntervalSeconds + delta).coerceIn(
      s.defaultMinIntervalSeconds,
      SafetyLimits.MAX_INTERVAL_SECONDS,
    )
    viewModelScope.launch { container.settingsRepository.updateDefaultIntervals(s.defaultMinIntervalSeconds, newMax) }
  }

  fun exportEncryptedBackup(context: Context, uri: Uri, passphrase: String) {
    if (isProcessingBackup.value) return
    viewModelScope.launch {
      isProcessingBackup.value = true
      try {
        val settings = container.settingsRepository.settings.first()
        val lists = container.savedConfigRepository.observeSavedMessageLists().first()
        val draft = container.savedConfigRepository.getActiveDraftOnce()
        val history = container.historyRepository.getAllExecutionsOnce()
        val autoReplyRules = container.autoReplyRepository.getRulesOnce()
        val autoReplySettings = container.autoReplySettingsRepository.settings.first()
        val scheduledTasks = container.scheduledTaskRepository.getAllOnce()

        val encrypted = withContext(Dispatchers.Default) {
          LocalBackupManager.exportEncryptedJson(
            passphrase = passphrase,
            settings = settings,
            savedLists = lists,
            activeDraft = draft,
            history = history,
            autoReplyRules = autoReplyRules,
            autoReplySettings = autoReplySettings,
            scheduledTasks = scheduledTasks,
          )
        }

        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encrypted.toByteArray(Charsets.UTF_8))
          } ?: error("No se pudo abrir el archivo de destino.")
        }

        isErrorFeedback.value = false
        feedbackMessage.value = "Copia cifrada AES-256-GCM exportada con éxito."
      } catch (e: Exception) {
        isErrorFeedback.value = true
        feedbackMessage.value = e.message ?: "Error al exportar el respaldo."
      } finally {
        isProcessingBackup.value = false
      }
    }
  }

  fun importEncryptedBackup(context: Context, uri: Uri, passphrase: String) {
    if (isProcessingBackup.value) return
    viewModelScope.launch {
      isProcessingBackup.value = true
      try {
        val raw = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val maxChars = 10_000_000
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
              val n = reader.read(buf)
              if (n < 0) break
              if (sb.length + n > maxChars) {
                error("El archivo seleccionado excede el tamaño máximo soportado (10 MB).")
              }
              sb.append(buf, 0, n)
            }
            sb.toString()
          } ?: error("No se pudo leer el archivo seleccionado.")
        }

        val payload = withContext(Dispatchers.Default) {
          LocalBackupManager.importEncryptedJson(raw, passphrase)
        }

        // Restaurar preferencias
        container.settingsRepository.updateDefaultIntervals(
          payload.settings.defaultMinIntervalSeconds,
          payload.settings.defaultMaxIntervalSeconds,
        )
        container.settingsRepository.updateMaxRepeatCount(payload.settings.maxRepeatCount)
        container.settingsRepository.updatePreferWhatsAppBusiness(payload.settings.preferWhatsAppBusiness)
        container.settingsRepository.updateHapticFeedback(payload.settings.hapticFeedbackEnabled)
        container.settingsRepository.updateKeepScreenOn(payload.settings.keepScreenOnDuringAutomation)
        container.settingsRepository.updateVacationPause(payload.settings.vacationPauseEnabled)
        val restoredTheme = runCatching { ThemeMode.valueOf(payload.settings.themeMode) }.getOrDefault(ThemeMode.DARK)
        container.settingsRepository.updateThemeMode(restoredTheme)
        container.settingsRepository.updateGitHubRepository(
          payload.settings.githubRepoOwner,
          payload.settings.githubRepoName,
        )

        // Restaurar listas guardadas sin duplicar existentes
        val existingListKeys = container.savedConfigRepository
          .observeSavedMessageLists()
          .first()
          .map { "${it.title}_${it.updatedAtMillis}" }
          .toSet()

        payload.savedLists.forEach { dto ->
          val key = "${dto.title}_${dto.updatedAtMillis}"
          if (key !in existingListKeys) {
            container.savedConfigRepository.saveMessageList(
              SavedMessageList(
                title = dto.title,
                isMultipleMode = dto.isMultipleMode,
                messages = dto.messages,
                singleMessage = dto.singleMessage,
                repeatCount = dto.repeatCount,
                updatedAtMillis = dto.updatedAtMillis,
                category = dto.category,
                tags = dto.tags,
                isFavorite = dto.isFavorite,
                isPinned = dto.isPinned,
                useCount = dto.useCount,
                lastUsedAtMillis = dto.lastUsedAtMillis,
              ),
            )
          }
        }

        // Restaurar borrador activo si existe
        payload.activeDraft?.let { d ->
          val persisted = PersistedAutomationDraft(
            contactId = d.contactId,
            contactName = d.contactName,
            contactPhone = d.contactPhone,
            isMultipleMode = d.isMultipleMode,
            messages = d.messages,
            singleMessage = d.singleMessage,
            repeatCount = d.repeatCount,
            minIntervalSeconds = d.minIntervalSeconds,
            maxIntervalSeconds = d.maxIntervalSeconds,
            preferWhatsAppBusiness = d.preferWhatsAppBusiness,
            updatedAtMillis = d.updatedAtMillis,
          )
          container.updateDraft { AutomationDraft.fromPersisted(persisted) }
        }

        // Restaurar registros de historial evitando duplicados exactos
        val existingHistoryKeys = container.historyRepository
          .getAllExecutionsOnce()
          .map { "${it.timestampMillis}_${it.contactPhone}_${it.batchCode}" }
          .toSet()

        LocalBackupManager.toHistoryRecords(payload.history).forEach { record ->
          val key = "${record.timestampMillis}_${record.contactPhone}_${record.batchCode}"
          if (key !in existingHistoryKeys) {
            container.historyRepository.recordExecution(record)
          }
        }

        // Restaurar reglas y configuración del Motor 2 sin duplicar
        val existingRuleKeys = container.autoReplyRepository
          .getRulesOnce()
          .map { "${it.name}_${it.pattern}_${it.matchType.name}_${it.priority}_${it.responseTemplate.hashCode()}" }
          .toSet()

        LocalBackupManager.toAutoReplyRules(payload.autoReplyRules).forEach { rule ->
          val key = "${rule.name}_${rule.pattern}_${rule.matchType.name}_${rule.priority}_${rule.responseTemplate.hashCode()}"
          if (key !in existingRuleKeys) {
            container.autoReplyRepository.saveRule(rule)
          }
        }
        payload.autoReplySettings?.let { restoredAuto ->
          val parsed = LocalBackupManager.toAutoReplySettings(restoredAuto)
          container.autoReplySettingsRepository.updateSettings { parsed }
        }

        // Restaurar programaciones sin duplicar existentes
        val existingScheduleKeys = container.scheduledTaskRepository
          .getAllOnce()
          .map { "${it.title}_${it.contactPhone}_${it.triggerAtMillis}" }
          .toSet()

        LocalBackupManager.toScheduledTasks(payload.scheduledTasks).forEach { task ->
          val key = "${task.title}_${task.contactPhone}_${task.triggerAtMillis}"
          if (key !in existingScheduleKeys) {
            val savedId = container.scheduledTaskRepository.saveTask(task)
            if (task.enabled) {
              container.schedulerAlarmHelper.scheduleTaskReminder(task.copy(id = savedId))
            }
          }
        }

        refreshStorageStats(context)
        isErrorFeedback.value = false
        feedbackMessage.value = "Respaldo verificado (SHA-256) y restaurado correctamente."
      } catch (e: Exception) {
        isErrorFeedback.value = true
        feedbackMessage.value = e.message ?: "Clave incorrecta o respaldo dañado."
      } finally {
        isProcessingBackup.value = false
      }
    }
  }

  fun clearHistory(context: Context) {
    viewModelScope.launch {
      container.historyRepository.clearHistory()
      refreshStorageStats(context)
      isErrorFeedback.value = false
      feedbackMessage.value = "Registros de historial eliminados."
    }
  }

  fun clearUpdateCache(context: Context) {
    container.clearUpdateCache()
    refreshStorageStats(context)
    isErrorFeedback.value = false
    feedbackMessage.value = "Caché temporal de actualizaciones limpiada."
  }

  fun factoryReset(context: Context) {
    viewModelScope.launch {
      container.factoryResetLocalData()
      refreshStorageStats(context)
      isErrorFeedback.value = false
      feedbackMessage.value = "Configuración y datos locales restablecidos."
    }
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsBackupViewModel(container) as T
      }
  }
}

