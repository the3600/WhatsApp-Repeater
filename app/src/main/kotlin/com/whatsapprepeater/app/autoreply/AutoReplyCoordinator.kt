package com.whatsapprepeater.app.autoreply

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.whatsapprepeater.core.autoresponder.AutoReplyEngine
import com.whatsapprepeater.core.autoresponder.SavedContactsLookup
import com.whatsapprepeater.core.data.repository.AutoReplyRepository
import com.whatsapprepeater.core.data.repository.AutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.ContactsRepository
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyDecision
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.IncomingNotificationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AutoReplyRuntimeState(
  val isListenerConnected: Boolean = false,
  val sentThisSession: Int = 0,
  val skippedThisSession: Int = 0,
  val lastActionSummary: String = "En espera de actividad",
)

data class RemoteInputDispatchResult(
  val sent: Boolean,
  val failureReason: String? = null,
)

/**
 * Coordina el NotificationListenerService con AutoReplyEngine, Room, DataStore y RemoteInput.
 */
class AutoReplyCoordinator(
  private val appContext: Context,
  private val autoReplyRepository: AutoReplyRepository,
  private val autoReplySettingsRepository: AutoReplySettingsRepository,
  private val contactsRepository: ContactsRepository,
  private val notificationHelper: AutoReplyNotificationHelper,
  private val scope: CoroutineScope,
  private val engine: AutoReplyEngine = AutoReplyEngine(),
) {

  private val _runtimeState = MutableStateFlow(AutoReplyRuntimeState())
  val runtimeState: StateFlow<AutoReplyRuntimeState> = _runtimeState.asStateFlow()

  private val pendingDispatchJobs = mutableSetOf<Job>()
  private val contactsLookupMutex = Mutex()
  @Volatile
  private var cachedContactsLookup: Pair<Long, SavedContactsLookup>? = null

  init {
    scope.launch {
      try {
        autoReplySettingsRepository.settings.collect { settings ->
          val runtime = _runtimeState.value
          notificationHelper.syncStatus(
            masterEnabled = settings.masterEnabled,
            listenerConnected = runtime.isListenerConnected,
            sentSessionCount = runtime.sentThisSession,
          )
        }
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        safeLogError("Error sincronizando notificación de estado del Motor 2", e)
      }
    }
  }

  fun setListenerConnected(connected: Boolean) {
    _runtimeState.update {
      it.copy(
        isListenerConnected = connected,
        lastActionSummary = if (connected) "Servicio conectado en Android" else "Servicio desconectado",
      )
    }
    scope.launch {
      try {
        val settings = autoReplySettingsRepository.settings.first()
        notificationHelper.syncStatus(
          masterEnabled = settings.masterEnabled,
          listenerConnected = connected,
          sentSessionCount = _runtimeState.value.sentThisSession,
        )
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        safeLogError("Error actualizando estado de conexión del listener", e)
      }
    }
  }

  fun emergencyStop() {
    synchronized(pendingDispatchJobs) {
      pendingDispatchJobs.forEach { it.cancel() }
      pendingDispatchJobs.clear()
    }
    engine.clearRuntimeState()
    _runtimeState.update {
      it.copy(lastActionSummary = "Parada de emergencia activada")
    }
    scope.launch {
      withContext(NonCancellable) {
        try {
          autoReplySettingsRepository.updateSettings { it.copy(masterEnabled = false) }
        } catch (e: Exception) {
          safeLogError("Error desactivando masterEnabled en parada de emergencia", e)
          _runtimeState.update {
            it.copy(lastActionSummary = "Parada aplicada en memoria (aviso al guardar: ${e.message})")
          }
        }
        notificationHelper.syncStatus(
          masterEnabled = false,
          listenerConnected = _runtimeState.value.isListenerConnected,
          sentSessionCount = _runtimeState.value.sentThisSession,
        )
      }
    }
  }

  fun handleIncomingNotification(
    event: IncomingNotificationEvent,
    sendViaRemoteInput: (String) -> RemoteInputDispatchResult,
  ) {
    val job = scope.launch(Dispatchers.Default) {
      try {
        val settings = autoReplySettingsRepository.settings.first()
        if (!settings.masterEnabled) return@launch

        val rules = autoReplyRepository.getRulesOnce()
        val contactsLookup = loadContactsIfNeeded(settings, rules)

        when (val decision = engine.evaluate(event, settings, rules, contactsLookup)) {
          null -> return@launch

          is AutoReplyDecision.Skip -> {
            _runtimeState.update {
              it.copy(
                skippedThisSession = it.skippedThisSession + 1,
                lastActionSummary = decision.reason,
              )
            }
            persistLogIfEnabled(settings, event, matchedRule = "Omitida", responseSent = "", status = decision.status, detail = decision.reason)
          }

          is AutoReplyDecision.Reply -> {
            if (decision.delaySeconds > 0) {
              delay(decision.delaySeconds * 1_000L)
            }
            // Reverificar estado activo tras el retraso antes de disparar RemoteInput.
            val latestSettings = autoReplySettingsRepository.settings.first()
            if (!latestSettings.masterEnabled) return@launch

            val dispatchResult = sendViaRemoteInput(decision.responseText)
            if (dispatchResult.sent) {
              engine.recordReplyDispatched(decision)
              _runtimeState.update {
                val nextSent = it.sentThisSession + 1
                notificationHelper.syncStatus(
                  masterEnabled = latestSettings.masterEnabled,
                  listenerConnected = it.isListenerConnected,
                  sentSessionCount = nextSent,
                )
                it.copy(
                  sentThisSession = nextSent,
                  lastActionSummary = "Respondido (${decision.ruleName}) a ${event.conversationTitle}",
                )
              }
              persistLogIfEnabled(
                settings = latestSettings,
                event = event,
                matchedRule = decision.ruleName,
                responseSent = decision.responseText,
                status = AutoReplyLogStatus.SENT,
                detail = "Enviado mediante RemoteInput",
              )
            } else {
              val detailReason = dispatchResult.failureReason
                ?: "El PendingIntent de RemoteInput fue rechazado o expiró."
              _runtimeState.update {
                it.copy(lastActionSummary = "No se pudo despachar RemoteInput en ${event.conversationTitle}: $detailReason")
              }
              persistLogIfEnabled(
                settings = latestSettings,
                event = event,
                matchedRule = decision.ruleName,
                responseSent = decision.responseText,
                status = AutoReplyLogStatus.FAILED_SEND,
                detail = detailReason,
              )
            }
          }
        }
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        safeLogError("Error procesando notificación entrante en Motor 2", e)
        _runtimeState.update {
          it.copy(lastActionSummary = "Error procesando notificación: ${e.message ?: e.javaClass.simpleName}")
        }
      }
    }

    synchronized(pendingDispatchJobs) {
      pendingDispatchJobs.add(job)
    }
    job.invokeOnCompletion {
      synchronized(pendingDispatchJobs) {
        pendingDispatchJobs.remove(job)
      }
    }
  }

  private suspend fun loadContactsIfNeeded(
    settings: AutoReplySettings,
    rules: List<AutoReplyRule>,
  ): SavedContactsLookup {
    val needsContacts = settings.globalContactFilterMode.requiresSavedLookup() ||
      rules.any { it.enabled && it.contactFilterMode.requiresSavedLookup() }
    if (!needsContacts) return SavedContactsLookup(permissionGranted = false)

    val granted = ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return SavedContactsLookup(permissionGranted = false)

    val now = System.currentTimeMillis()
    val cached = cachedContactsLookup
    if (cached != null && now - cached.first < 60_000L) {
      return cached.second
    }

    return contactsLookupMutex.withLock {
      val innerNow = System.currentTimeMillis()
      val innerCached = cachedContactsLookup
      if (innerCached != null && innerNow - innerCached.first < 60_000L) {
        return@withLock innerCached.second
      }
      val loaded = runCatching { contactsRepository.loadPhoneContacts() }.getOrDefault(emptyList())
      SavedContactsLookup.fromContacts(permissionGranted = true, contacts = loaded).also {
        cachedContactsLookup = innerNow to it
      }
    }
  }

  private fun AutoReplyContactFilterMode.requiresSavedLookup(): Boolean =
    this == AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY ||
      this == AutoReplyContactFilterMode.UNKNOWN_NUMBERS_ONLY

  private suspend fun persistLogIfEnabled(
    settings: AutoReplySettings,
    event: IncomingNotificationEvent,
    matchedRule: String,
    responseSent: String,
    status: AutoReplyLogStatus,
    detail: String,
  ) {
    if (!settings.saveHistoryEnabled) return
    val preview = if (settings.saveIncomingPreviewEnabled) event.messageText.take(120) else ""
    withContext(NonCancellable) {
      runCatching {
        autoReplyRepository.recordLog(
          AutoReplyLogEntry(
            timestampMillis = System.currentTimeMillis(),
            packageName = event.packageName,
            chatIdentifier = event.conversationTitle.take(80),
            matchedRuleName = matchedRule,
            responseSent = responseSent.take(300),
            incomingPreview = preview,
            status = status,
            detail = detail,
          ),
        )
      }.onFailure { err ->
        safeLogError("Error guardando historial de autorespuesta", err)
      }
    }
  }

  private fun safeLogError(message: String, error: Throwable) {
    runCatching { Log.e(TAG, message, error) }
  }

  companion object {
    private const val TAG = "AutoReplyCoordinator"
  }
}
