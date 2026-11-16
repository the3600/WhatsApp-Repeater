package com.whatsapprepeater.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whatsapprepeater.app.ui.components.EmergencyStopDialog
import com.whatsapprepeater.app.ui.components.EmergencyStopDialogState
import com.whatsapprepeater.app.ui.components.EmergencyStopStage
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.theme.WhatsAppRepeaterTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeComponentsUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun emptyStateCard_displaysSpanishTitleAndTriggersAction() {
    var clicked = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        EmptyStateCard(
          icon = Icons.Rounded.History,
          title = "Aún no hay actividad",
          description = "Sin registros reales todavía.",
          actionButtonText = "Crear primera automatización",
          onActionClick = { clicked = true },
        )
      }
    }

    composeRule.onNodeWithText("Aún no hay actividad").assertIsDisplayed()
    composeRule.onNodeWithText("Crear primera automatización").performClick()
    assertTrue(clicked)
  }

  @Test
  fun emergencyStopDialog_displaysConfirmationAndInvokesStopCallback() {
    var confirmed = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        EmergencyStopDialog(
          state = EmergencyStopDialogState(
            isVisible = true,
            stage = EmergencyStopStage.CONFIRMATION,
            recipient = "Ana Gómez (+57 300 111 2222)",
            sentCount = 2,
            totalCount = 5,
          ),
          hapticEnabled = false,
          onDismissRequest = {},
          onConfirmEmergencyStop = { confirmed = true },
          onNavigateToHistory = {},
          onReturnToHome = {},
        )
      }
    }

    composeRule.onNodeWithText("¿Detener automatización?").assertIsDisplayed()
    composeRule.onNodeWithText("DETENER AHORA").performClick()
    assertTrue(confirmed)
  }
}

