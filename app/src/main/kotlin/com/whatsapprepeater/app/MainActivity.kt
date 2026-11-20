package com.whatsapprepeater.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.scheduler.SchedulerAlarmHelper
import com.whatsapprepeater.app.ui.theme.WhatsAppRepeaterTheme
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.ThemeMode

class MainActivity : ComponentActivity() {
  private var appContainer: AppContainer? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val darkBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = darkBarStyle, navigationBarStyle = darkBarStyle)

    val container = (application as WhatsAppRepeaterApp).container
    appContainer = container
    handleIncomingIntent(intent, container)

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
      )
      val systemDark = isSystemInDarkTheme()
      val useDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
      }

      DisposableEffect(useDark) {
        val barStyle = if (useDark) {
          SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
          )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        onDispose {}
      }

      WhatsAppRepeaterTheme(themeMode = settings.themeMode) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainNavigation(container = container)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    appContainer?.let { handleIncomingIntent(intent, it) }
  }

  private fun handleIncomingIntent(intent: Intent?, container: AppContainer) {
    val safeIntent = intent ?: return
    val openActive = safeIntent.getBooleanExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_ACTIVE_AUTOMATION, false)
    if (openActive) {
      container.requestOpenActiveAutomation()
      safeIntent.removeExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_ACTIVE_AUTOMATION)
    }
    val openScheduler = safeIntent.getBooleanExtra(SchedulerAlarmHelper.EXTRA_OPEN_SCHEDULER, false)
    if (openScheduler) {
      container.requestOpenScheduler()
      safeIntent.removeExtra(SchedulerAlarmHelper.EXTRA_OPEN_SCHEDULER)
    }
    val taskId = safeIntent.getLongExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_SCHEDULED_TASK_ID, -1L)
    if (taskId > 0L) {
      container.requestOpenScheduledTask(taskId)
      safeIntent.removeExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_SCHEDULED_TASK_ID)
    }
  }
}
