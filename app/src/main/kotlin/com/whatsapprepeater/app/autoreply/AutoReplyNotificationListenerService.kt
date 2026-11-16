package com.whatsapprepeater.app.autoreply

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

  private val coordinator: AutoReplyCoordinator
    get() = (application as WhatsAppRepeaterApp).container.autoReplyCoordinator

  override fun onListenerConnected() {
    super.onListenerConnected()
    coordinator.setListenerConnected(true)
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    coordinator.setListenerConnected(false)
    runCatching {
      requestRebind(ComponentName(this, AutoReplyNotificationListenerService::class.java))
    }
  }

  override fun onDestroy() {
    coordinator.setListenerConnected(false)
    super.onDestroy()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    val pkg = sbn.packageName ?: return
    if (pkg !in allowedPackages) return

    val notification = sbn.notification ?: return
    // Ignorar tarjetas resumen agrupadas que no pertenecen a un chat individual respondible.
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

    val replyAction = findReplyAction(notification)
    val extras = notification.extras ?: Bundle.EMPTY
    val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

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
      notificationKey = sbn.key,
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

    coordinator.handleIncomingNotification(event) { replyText ->
      val action = replyAction ?: return@handleIncomingNotification false
      dispatchRemoteInput(action, replyText)
    }
  }

  private fun findReplyAction(notification: Notification): NotificationCompat.Action? {
    val count = NotificationCompat.getActionCount(notification)
    for (i in 0 until count) {
      val action = NotificationCompat.getAction(notification, i) ?: continue
      val inputs = action.remoteInputs
      if (!inputs.isNullOrEmpty() && action.actionIntent != null) {
        return action
      }
    }
    return null
  }

  private fun dispatchRemoteInput(
    action: NotificationCompat.Action,
    replyText: String,
  ): Boolean {
    val remoteInputs = action.remoteInputs ?: return false
    val pendingIntent = action.actionIntent ?: return false
    return try {
      val localIntent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
      val results = Bundle()
      val frameworkInputs = remoteInputs.map { compat ->
        results.putCharSequence(compat.resultKey, replyText)
        RemoteInput.Builder(compat.resultKey)
          .setLabel(compat.label)
          .setChoices(compat.choices)
          .setAllowFreeFormInput(compat.allowFreeFormInput)
          .addExtras(compat.extras)
          .build()
      }.toTypedArray()

      RemoteInput.addResultsToIntent(frameworkInputs, localIntent, results)
      pendingIntent.send(this, 0, localIntent)
      true
    } catch (_: Exception) {
      false
    }
  }
}

