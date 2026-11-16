package com.whatsapprepeater.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.whatsapprepeater.app.WhatsAppRepeaterApp

/**
 * Servicio de accesibilidad declarado explícitamente y activado manualmente por el usuario.
 * Observa únicamente la ventana activa de WhatsApp durante una automatización iniciada por el usuario.
 */
class AutomationAccessibilityService : AccessibilityService() {

  @Volatile
  var lastForegroundPackage: String? = null
    private set

  private val appContainer
    get() = (application as WhatsAppRepeaterApp).container

  override fun onServiceConnected() {
    super.onServiceConnected()
    appContainer.accessibilityDriver.attachService(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString()
    if (!pkg.isNullOrBlank()) {
      lastForegroundPackage = pkg
    }
  }

  override fun onInterrupt() {
    appContainer.automationEngine.stop()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    appContainer.automationEngine.stop()
    appContainer.accessibilityDriver.detachService(this)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    appContainer.automationEngine.stop()
    appContainer.accessibilityDriver.detachService(this)
    super.onDestroy()
  }
}

