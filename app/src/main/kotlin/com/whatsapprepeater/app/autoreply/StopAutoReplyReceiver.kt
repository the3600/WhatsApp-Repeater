package com.whatsapprepeater.app.autoreply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsapprepeater.app.WhatsAppRepeaterApp

/**
 * Permite ejecutar la parada de emergencia del Motor 2 desde la notificación persistente.
 */
class StopAutoReplyReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    app.container.autoReplyCoordinator.emergencyStop()
  }
}

