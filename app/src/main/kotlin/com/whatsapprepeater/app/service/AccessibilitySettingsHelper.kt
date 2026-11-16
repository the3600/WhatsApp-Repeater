package com.whatsapprepeater.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object AccessibilitySettingsHelper {
  fun isAccessibilityEnabledInSettings(context: Context): Boolean =
    runCatching {
      val expected = ComponentName(context, AutomationAccessibilityService::class.java).flattenToString()
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return@runCatching false
      enabledServices
        .split(':')
        .any { it.equals(expected, ignoreCase = true) }
    }.getOrDefault(false)

  fun openAccessibilitySettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrElse {
      openAppDetailsSettings(context)
    }
  }

  fun openAppDetailsSettings(context: Context): Boolean {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrElse {
      runCatching {
        context.startActivity(
          Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
      }.getOrDefault(false)
    }
  }
}
