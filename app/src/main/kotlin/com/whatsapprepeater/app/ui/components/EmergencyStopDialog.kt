package com.whatsapprepeater.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerCoral
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.SurfaceLow
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

enum class EmergencyStopStage {
  CONFIRMATION,
  STOPPING,
  STOPPED,
}

data class EmergencyStopDialogState(
  val isVisible: Boolean = false,
  val stage: EmergencyStopStage = EmergencyStopStage.CONFIRMATION,
  val recipient: String = "",
  val sentCount: Int = 0,
  val totalCount: Int = 0,
) {
  val pendingCount: Int
    get() = (totalCount - sentCount).coerceAtLeast(0)
}

/**
 * Diálogo modal de detención de emergencia basado en la especificación de Stitch
 * con transición animada en 3 etapas y respuesta háptica condicional.
 */
@Composable
fun EmergencyStopDialog(
  state: EmergencyStopDialogState,
  hapticEnabled: Boolean,
  onDismissRequest: () -> Unit,
  onConfirmEmergencyStop: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onReturnToHome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!state.isVisible) return

  val view = LocalView.current
  val configuration = LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp

  fun triggerHapticIfAllowed() {
    if (hapticEnabled) {
      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
  }

  Dialog(
    onDismissRequest = {
      if (state.stage != EmergencyStopStage.STOPPING) {
        onDismissRequest()
      }
    },
    properties = DialogProperties(
      dismissOnBackPress = state.stage != EmergencyStopStage.STOPPING,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
    ),
  ) {
    Surface(
      modifier = modifier
        .fillMaxWidth(0.92f)
        .widthIn(max = 420.dp)
        .heightIn(max = screenHeight * 0.88f)
        .clip(RoundedCornerShape(28.dp))
        .border(1.dp, DangerRed.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
      shape = RoundedCornerShape(28.dp),
      color = SurfaceLow,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AnimatedContent(
          targetState = state.stage,
          transitionSpec = { fadeIn() togetherWith fadeOut() },
          label = "EmergencyStopStageTransition",
        ) { currentStage ->
          when (currentStage) {
            EmergencyStopStage.CONFIRMATION -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF2C1B1B), CircleShape),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(34.dp),
                  )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "¿Detener automatización?",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                  text = "Se interrumpirá de inmediato el envío. El servicio liberará el control de WhatsApp y los mensajes pendientes serán descartados.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurfaceElevated, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text("Destinatario:", color = TextSecondary, fontSize = 13.sp)
                    Text(state.recipient, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text("Progreso lote:", color = TextSecondary, fontSize = 13.sp)
                    Text(
                      "${state.sentCount} de ${state.totalCount} confirmados",
                      color = WhatsAppGreen,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                    )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  LinearProgressIndicator(
                    progress = {
                      if (state.totalCount > 0) state.sentCount.toFloat() / state.totalCount else 0f
                    },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(6.dp)
                      .clip(RoundedCornerShape(3.dp)),
                    color = WhatsAppGreen,
                    trackColor = BackgroundBlack,
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = "✕ ${state.pendingCount} pendientes serán cancelados sin enviar",
                    color = DangerCoral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                  )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                  onClick = {
                    triggerHapticIfAllowed()
                    onConfirmEmergencyStop()
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics {
                      contentDescription = "Detener inmediatamente la automatización"
                    },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = Color.White,
                  ),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("DETENER AHORA", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                  onClick = onDismissRequest,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                  border = BorderStroke(1.dp, OutlineSubtle),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text("Continuar automatización", color = TextPrimary, fontWeight = FontWeight.Medium)
                }
              }
            }

            EmergencyStopStage.STOPPING -> {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator(
                  color = DangerRed,
                  strokeWidth = 3.dp,
                  modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                  text = "Deteniendo automatización...",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Liberando servicio de accesibilidad y purgando cola de mensajes.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                  textAlign = TextAlign.Center,
                )
              }
            }

            EmergencyStopStage.STOPPED -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier
                    .size(60.dp)
                    .background(WhatsAppGreenContainer, CircleShape),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(32.dp),
                  )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "Automatización Detenida",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                  text = "El proceso fue interrumpido. Se confirmaron ${state.sentCount} mensajes y se descartaron ${state.pendingCount} pendientes.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                  onClick = onNavigateToHistory,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppGreen,
                    contentColor = BackgroundBlack,
                  ),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text("Ver en Historial", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                  onClick = onReturnToHome,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                ) {
                  Text("Volver al Inicio", color = TextSecondary)
                }
              }
            }
          }
        }
      }
    }
  }
}

