package com.whatsapprepeater.app.autoreply

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper

object AutoReplyPermissionsHelper {

  fun isNotificationAccessEnabled(context: Context): Boolean =
    runCatching {
      NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

  fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    runCatching {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
      pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

  fun openNotificationAccessSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrElse {
      AccessibilitySettingsHelper.openAppDetailsSettings(context)
    }
  }

  fun openBatteryOptimizationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrElse {
      AccessibilitySettingsHelper.openAppDetailsSettings(context)
    }
  }

  fun openAppDetailsSettings(context: Context): Boolean =
    AccessibilitySettingsHelper.openAppDetailsSettings(context)
}
