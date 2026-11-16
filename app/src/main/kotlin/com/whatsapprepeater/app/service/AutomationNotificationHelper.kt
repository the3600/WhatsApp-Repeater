package com.whatsapprepeater.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.whatsapprepeater.app.MainActivity
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.AutomationStatus

/**
 * Muestra una notificación visible con acción DETENER siempre que la automatización
 * esté activa o pausada, garantizando transparencia total para el usuario.
 */
class AutomationNotificationHelper(
  private val context: Context,
) {
  companion object {
    private const val CHANNEL_ID = "whatsapp_repeater_automation"
    private const val NOTIFICATION_ID = 4102
  }

  init {
    ensureChannel()
  }

  fun syncWithState(state: AutomationState) {
    if (!state.isRunning && !state.isPaused) {
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }

    val openAppIntent = PendingIntent.getActivity(
      context,
      1,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val stopIntent = PendingIntent.getBroadcast(
      context,
      2,
      Intent(context, StopAutomationReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title = if (state.status == AutomationStatus.PAUSED) {
      "WhatsApp Repeater (Pausado)"
    } else {
      "WhatsApp Repeater en ejecución"
    }

    val contactName = state.contact?.name ?: "Contacto"
    val body = "$contactName • ${state.sentConfirmed}/${state.totalScheduled} • ${state.statusDetail}"

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openAppIntent)
      .addAction(
        android.R.drawable.ic_media_pause,
        "DETENER",
        stopIntent,
      )
      .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  private fun ensureChannel() {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Estado de automatización",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Muestra el progreso en vivo y un botón de detención inmediata."
    }
    manager.createNotificationChannel(channel)
  }
}

