package com.whatsapprepeater.app.ui.automation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.EmergencyStopDialog
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerCoral
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.AutomationStatus

/**
 * Pantalla 9 y 10 (Stitch 9._automatizaci_n_en_curso + 10._automatizaci_n_finalizada):
 * Monitoreo en tiempo real, diálogo modal de detención de emergencia y pantalla de finalización.
 */
@Composable
fun AutomationScreen(
  viewModel: AutomationViewModel,
  onBack: () -> Unit,
  onDoneToHome: () -> Unit,
  onNavigateToHistory: () -> Unit,
) {
  val view = LocalView.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val engine = state.engineState

  // Mantener pantalla encendida si está activo en preferencias durante una ejecución.
  DisposableEffect(engine.isRunning, state.settings.keepScreenOnDuringAutomation) {
    val shouldKeepOn = engine.isRunning && state.settings.keepScreenOnDuringAutomation
    view.keepScreenOn = shouldKeepOn
    onDispose { view.keepScreenOn = false }
  }

  if (engine.status == AutomationStatus.COMPLETED) {
    CompletedAutomationContent(
      state = state,
      onReturnHome = onDoneToHome,
      onNavigateToHistory = onNavigateToHistory,
      onRepeatSameConfig = viewModel::repeatSameConfiguration,
    )
  } else {
    RunningOrStoppedContent(
      state = state,
      onBack = onBack,
      onPause = viewModel::pauseAutomation,
      onResume = viewModel::resumeAutomation,
      onRequestEmergencyStop = viewModel::requestEmergencyStopDialog,
      onRetry = viewModel::repeatSameConfiguration,
      onNavigateToHistory = onNavigateToHistory,
      onReturnHome = onDoneToHome,
    )
  }

  EmergencyStopDialog(
    state = state.emergencyDialog,
    hapticEnabled = state.settings.hapticFeedbackEnabled,
    onDismissRequest = viewModel::dismissEmergencyStopDialog,
    onConfirmEmergencyStop = viewModel::confirmEmergencyStop,
    onNavigateToHistory = {
      viewModel.dismissEmergencyStopDialog()
      onNavigateToHistory()
    },
    onReturnToHome = {
      viewModel.dismissEmergencyStopDialog()
      onDoneToHome()
    },
  )
}

@Composable
private fun RunningOrStoppedContent(
  state: AutomationUiState,
  onBack: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onRequestEmergencyStop: () -> Unit,
  onRetry: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onReturnHome: () -> Unit,
) {
  val engine = state.engineState
  val contact = engine.contact ?: state.draft.selectedContact
  val scheduled = if (engine.totalScheduled > 0) engine.totalScheduled else state.totalScheduledPreview
  val sent = engine.sentConfirmed
  val pending = if (engine.totalScheduled > 0) engine.pendingCount else scheduled
  val animatedProgress by animateFloatAsState(
    targetValue = engine.progress,
    animationSpec = tween(350),
    label = "running_progress",
  )

  val (badgeText, badgeBg, badgeFg) = when (engine.status) {
    AutomationStatus.PAUSED -> Triple("Pausado", WarningContainer, WarningAmber)
    AutomationStatus.STOPPED -> Triple("Interrumpido", WarningContainer, WarningAmber)
    AutomationStatus.ERROR -> Triple("Detenido", DangerContainer, DangerRed)
    else -> Triple("Servicio Activo", WhatsAppGreenContainer, WhatsAppGreen)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(CardSurfaceElevated),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text("Automatización Activa", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Control por Servicio de Accesibilidad", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
          }
        }
      }
      StatusBadge(badgeText, badgeBg, badgeFg)
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Destinatario actual + progreso
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(WhatsAppGreenContainer),
            ) {
              Icon(Icons.Rounded.Person, contentDescription = null, tint = WhatsAppGreen)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("DESTINATARIO ACTUAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
              Text(
                text = contact?.name ?: "Contacto",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
              )
              if (contact != null) {
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = "$sent",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = " / $scheduled mensajes",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
              )
            }
            Text(
              text = "${(animatedProgress * 100).toInt()}%",
              style = MaterialTheme.typography.titleLarge,
              color = WhatsAppGreen,
              fontWeight = FontWeight.Bold,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(999.dp)),
            color = WhatsAppGreen,
            trackColor = CardSurfaceElevated,
          )

          Spacer(modifier = Modifier.height(14.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LiveStatPill("Confirmados", "$sent", WhatsAppGreen, Modifier.weight(1f))
            LiveStatPill(
              "En curso",
              if (engine.isRunning) "1" else "0",
              WarningAmber,
              Modifier.weight(1f),
            )
            LiveStatPill("Pendientes", "$pending", TextSecondary, Modifier.weight(1f))
          }
        }
      }

      // Despachando en WhatsApp + Pausa aleatoria
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(engine.statusDetail, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            if (scheduled > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              StatusBadge(
                label = "Envío #${(engine.currentMessageIndex + 1).coerceAtMost(scheduled)}",
                containerColor = WhatsAppGreenContainer,
                contentColor = WhatsAppGreen,
              )
            }
          }

          if (!engine.currentMessagePreview.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurfaceElevated)
                .padding(12.dp),
            ) {
              Text(
                text = engine.currentMessagePreview ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          if (engine.remainingIntervalSeconds > 0 || engine.activeIntervalRangeText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurfaceElevated)
                .padding(12.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(WarningContainer),
                ) {
                  Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Pausa entre envíos", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                  Text(
                    "Intervalo aleatorio (${engine.activeIntervalRangeText})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                }
              }
              if (engine.remainingIntervalSeconds > 0) {
                Text(
                  text = "${engine.remainingIntervalSeconds}s",
                  style = MaterialTheme.typography.titleMedium,
                  color = WarningAmber,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }

      if (!engine.errorMessage.isNullOrBlank()) {
        item {
          SectionCard(borderColor = DangerRed, containerColor = DangerContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = DangerRed)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Intervención requerida", style = MaterialTheme.typography.titleMedium, color = DangerRed)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(engine.errorMessage ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          }
        }
      }

      item {
        SectionCard {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Protocolo de seguridad activo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Si WhatsApp cambia de conversación o aparece una alerta inesperada, la ejecución se detendrá automáticamente. Pulsa DETENER INMEDIATAMENTE para cancelar.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      when {
        engine.isRunning -> {
          LargeSecondaryButton(
            text = "Pausar automatización",
            icon = Icons.Rounded.Pause,
            onClick = onPause,
          )
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = DangerCoral,
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp),
          ) {
            androidx.compose.material3.Button(
              onClick = onRequestEmergencyStop,
              colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = DangerCoral,
                contentColor = Color(0xFF4A0A0A),
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              Icon(Icons.Rounded.StopCircle, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("DETENER INMEDIATAMENTE", fontWeight = FontWeight.Bold)
            }
          }
        }

        engine.isPaused -> {
          LargePrimaryButton(
            text = "Reanudar automatización",
            icon = Icons.Rounded.PlayArrow,
            onClick = onResume,
          )
          androidx.compose.material3.Button(
            onClick = onRequestEmergencyStop,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = DangerCoral,
              contentColor = Color(0xFF4A0A0A),
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp),
          ) {
            Icon(Icons.Rounded.StopCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("DETENER INMEDIATAMENTE", fontWeight = FontWeight.Bold)
          }
        }

        else -> {
          LargePrimaryButton(
            text = "Reintentar envío",
            icon = Icons.Rounded.Refresh,
            onClick = onRetry,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LargeSecondaryButton(
              text = "Historial",
              icon = Icons.Rounded.History,
              onClick = onNavigateToHistory,
              modifier = Modifier.weight(1f),
            )
            LargeSecondaryButton(
              text = "Inicio",
              icon = Icons.Rounded.Home,
              onClick = onReturnHome,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CompletedAutomationContent(
  state: AutomationUiState,
  onReturnHome: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onRepeatSameConfig: () -> Unit,
) {
  val engine = state.engineState
  val contact = engine.contact ?: state.draft.selectedContact
  val elapsedSec = ((System.currentTimeMillis() - engine.startedAtMillis).coerceAtLeast(1000L) / 1000L).toInt()
  val effectiveness = if (engine.totalScheduled > 0) {
    (engine.sentConfirmed * 100) / engine.totalScheduled
  } else 100

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(WhatsAppGreenContainer),
        ) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = "¡Automatización Completada!",
          style = MaterialTheme.typography.headlineMedium,
          color = TextPrimary,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Todos los mensajes programados fueron procesados con éxito.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusBadge(
          label = "Lote #${engine.batchId.ifBlank { "WR" }} • Finalizado con éxito",
          containerColor = WhatsAppGreenContainer,
          contentColor = WhatsAppGreen,
        )
      }
    }

    item {
      SectionCard {
        Text("Resumen de Ejecución", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        CompletionRow("Destinatario confirmado", "${contact?.name.orEmpty()} (${contact?.phoneNumber.orEmpty()})", WhatsAppGreen)
        CompletionRow(
          "Mensajes confirmados",
          "${engine.sentConfirmed} de ${engine.totalScheduled} ($effectiveness% efectividad)",
          WhatsAppGreen,
        )
        CompletionRow(
          "Fallidos / Omitidos",
          "${engine.errorCount} errores reportados",
          if (engine.errorCount == 0) TextPrimary else DangerRed,
        )
        CompletionRow(
          "Duración total",
          if (elapsedSec >= 60) "${elapsedSec / 60} min ${elapsedSec % 60} seg" else "$elapsedSec seg",
          TextPrimary,
        )
        CompletionRow(
          "Modo utilizado",
          if (state.draft.isMultipleMode) "Secuencia de mensajes múltiples" else "Repetición de mensaje único",
          TextPrimary,
        )
      }
    }

    item {
      SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(WhatsAppGreenContainer),
          ) {
            Icon(Icons.Rounded.Archive, contentDescription = null, tint = WhatsAppGreen)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("Registro Guardado", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
              text = "Este reporte fue guardado localmente en tu Historial de automatizaciones.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }
    }

    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        LargePrimaryButton(
          text = "Volver al Inicio",
          icon = Icons.Rounded.Home,
          onClick = onReturnHome,
        )
        LargeSecondaryButton(
          text = "Ver en Historial",
          icon = Icons.Rounded.History,
          onClick = onNavigateToHistory,
        )
        TextButton(onClick = onRepeatSameConfig) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Repetir misma configuración", color = WhatsAppGreen, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
private fun LiveStatPill(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(CardSurfaceElevated)
      .padding(vertical = 10.dp, horizontal = 6.dp),
  ) {
    Text(text = value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
  }
}

@Composable
private fun CompletionRow(label: String, value: String, valueColor: Color) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(0.45f))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = valueColor,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(0.55f),
    )
  }
}
