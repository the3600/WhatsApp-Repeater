package com.whatsapprepeater.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.whatsapprepeater.app.scheduler.SchedulerAlarmHelper
import com.whatsapprepeater.app.ui.theme.WhatsAppRepeaterTheme

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
      WhatsAppRepeaterTheme {
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
    val taskId = safeIntent.getLongExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_SCHEDULED_TASK_ID, -1L)
    if (taskId > 0L) {
      container.requestOpenScheduledTask(taskId)
      safeIntent.removeExtra(SchedulerAlarmHelper.EXTRA_LAUNCH_SCHEDULED_TASK_ID)
    }
  }
}
