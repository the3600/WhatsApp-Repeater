package com.whatsapprepeater.app.autoreply

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whatsapprepeater.app.WhatsAppRepeaterApp
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.IncomingNotificationEvent

/**
 * Servicio oficial NotificationListenerService para autorespuestas por RemoteInput.
 * Solo inspecciona notificaciones entrantes de WhatsApp / WhatsApp Business con RemoteInput válido.
 */
class AutoReplyNotificationListenerService : NotificationListenerService() {

  private val allowedPackages = setOf(
    AutomationConfig.WHATSAPP_PACKAGE,
    AutomationConfig.WHATSAPP_BUSINESS_PACKAGE,
  )

  private val coordinator: AutoReplyCoordinator?
    get() = (applicationContext as? WhatsAppRepeaterApp)?.container?.autoReplyCoordinator

  override fun onListenerConnected() {
    super.onListenerConnected()
    coordinator?.setListenerConnected(true)
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    coordinator?.setListenerConnected(false)
    if (AutoReplyPermissionsHelper.isNotificationAccessEnabled(this)) {
      runCatching {
        requestRebind(ComponentName(this, AutoReplyNotificationListenerService::class.java))
      }
    }
  }

  override fun onDestroy() {
    coordinator?.setListenerConnected(false)
    super.onDestroy()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    val pkg = sbn.packageName ?: return
    if (pkg !in allowedPackages) return

    val activeCoordinator = coordinator ?: return
    val notification = sbn.notification ?: return
    // Ignorar tarjetas resumen agrupadas que no pertenecen a un chat individual respondible.
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

    val parsed = runCatching { extractEventAndReplyAction(sbn, pkg, notification) }
      .onFailure { err -> Log.e(TAG, "Error leyendo notificación entrante", err) }
      .getOrNull() ?: return

    val (event, replyAction) = parsed
    activeCoordinator.handleIncomingNotification(event) { replyText ->
      val action = replyAction ?: return@handleIncomingNotification RemoteInputDispatchResult(
        sent = false,
        failureReason = "La notificación no expone una acción RemoteInput de texto.",
      )
      dispatchRemoteInput(action, replyText)
    }
  }

  private fun extractEventAndReplyAction(
    sbn: StatusBarNotification,
    pkg: String,
    notification: Notification,
  ): Pair<IncomingNotificationEvent, Notification.Action?> {
    val replyAction = findReplyAction(notification)
    val extras = notification.extras ?: Bundle.EMPTY
    val messagingStyle = runCatching {
      NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
    }.getOrNull()

    val latestMessage = messagingStyle?.messages?.lastOrNull()
    val localUserName = messagingStyle?.user?.name?.toString()?.trim()
    val senderName = latestMessage?.person?.name?.toString()?.trim()
      ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
    val rawTitle = messagingStyle?.conversationTitle?.toString()?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
      .orEmpty()

    val isGroup = messagingStyle?.isGroupConversation == true ||
      extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

    val messageText = latestMessage?.text?.toString()?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
      ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
      .orEmpty()

    val isSelf = (latestMessage != null && latestMessage.person == null) ||
      (!localUserName.isNullOrBlank() && senderName.equals(localUserName, ignoreCase = true)) ||
      senderName.equals("Tú", ignoreCase = true) ||
      senderName.equals("You", ignoreCase = true)

    val event = IncomingNotificationEvent(
      notificationKey = sbn.key.orEmpty(),
      packageName = pkg,
      conversationTitle = rawTitle,
      senderName = senderName,
      messageText = messageText,
      isGroupConversation = isGroup,
      isGroupSummary = false,
      isSelfMessage = isSelf,
      hasReplyAction = replyAction != null,
      timestampMillis = sbn.postTime,
    )
    return event to replyAction
  }

  private fun findReplyAction(notification: Notification): Notification.Action? {
    val actions = notification.actions ?: return null
    val validReplyActions = actions.filter { action ->
      val inputs = action.remoteInputs
      action.actionIntent != null &&
        !inputs.isNullOrEmpty() &&
        inputs.any { it.allowFreeFormInput }
    }
    if (validReplyActions.isEmpty()) return null
    return validReplyActions.firstOrNull {
      it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
    } ?: validReplyActions.first()
  }

  private fun dispatchRemoteInput(
    action: Notification.Action,
    replyText: String,
  ): RemoteInputDispatchResult {
    val remoteInputs = action.remoteInputs
    val pendingIntent = action.actionIntent
    if (remoteInputs.isNullOrEmpty() || pendingIntent == null) {
      return RemoteInputDispatchResult(
        sent = false,
        failureReason = "Acción RemoteInput sin PendingIntent o entrada válida.",
      )
    }

    return try {
      val localIntent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
      val results = Bundle()
      val freeFormInputs = remoteInputs.filter { it.allowFreeFormInput }
      val targetInputs = if (freeFormInputs.isNotEmpty()) freeFormInputs.toTypedArray() else remoteInputs

      for (input in targetInputs) {
        results.putCharSequence(input.resultKey, replyText)
      }

      RemoteInput.addResultsToIntent(targetInputs, localIntent, results)
      pendingIntent.send(this, 0, localIntent)
      RemoteInputDispatchResult(sent = true)
    } catch (e: Exception) {
      Log.e(TAG, "Error enviando RemoteInput", e)
      RemoteInputDispatchResult(
        sent = false,
        failureReason = "${e.javaClass.simpleName}: ${e.message ?: "fallo al despachar PendingIntent"}",
      )
    }
  }

  companion object {
    private const val TAG = "AutoReplyListener"
  }
}
