package com.whatsapprepeater.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.whatsapprepeater.app.ui.theme.WhatsAppRepeaterTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val darkBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = darkBarStyle, navigationBarStyle = darkBarStyle)

    val container = (application as WhatsAppRepeaterApp).container
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
}
