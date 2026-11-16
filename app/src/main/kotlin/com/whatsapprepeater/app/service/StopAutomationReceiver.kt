package com.whatsapprepeater.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsapprepeater.app.WhatsAppRepeaterApp

/**
 * Permite detener inmediatamente la automatización desde la notificación visible.
 */
class StopAutomationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val app = context.applicationContext as? WhatsAppRepeaterApp ?: return
    app.container.automationEngine.stop()
  }
}

