package com.whatsapprepeater.app.autoreply

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

/**
 * Muestra una notificación visible con acción DETENER siempre que el Motor 2
 * de autorespuestas esté activado, manteniendo transparencia total para el usuario.
 */
class AutoReplyNotificationHelper(
  private val context: Context,
) {
  companion object {
    private const val CHANNEL_ID = "whatsapp_repeater_autoreply"
    private const val NOTIFICATION_ID = 4103
  }

  init {
    ensureChannel()
  }

  fun syncStatus(
    masterEnabled: Boolean,
    listenerConnected: Boolean,
    sentSessionCount: Int,
  ) {
    if (!masterEnabled) {
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
      11,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val stopIntent = PendingIntent.getBroadcast(
      context,
      12,
      Intent(context, StopAutoReplyReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val statusText = if (listenerConnected) {
      "Escuchando notificaciones WhatsApp • Respuestas enviadas: $sentSessionCount"
    } else {
      "Activo en configuración • Falta conectar Acceso a Notificaciones"
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle("Autorespuestas WhatsApp Repeater activas")
      .setContentText(statusText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
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
      "Estado de autorespuestas",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Muestra si el motor de autorespuestas está activo y permite detenerlo al instante."
    }
    manager.createNotificationChannel(channel)
  }
}

