package com.whatsapprepeater.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.app.ui.components.BottomTab
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.components.StitchMainTopBar
import com.whatsapprepeater.app.ui.components.WhatsAppRepeaterBottomBar
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OnWhatsAppGreen
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppEmerald
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onCreateAutomation: () -> Unit,
  onResumeSavedDraft: () -> Unit,
  onOpenActiveAutomation: () -> Unit,
  onOpenPermissions: () -> Unit,
  onOpenHistoryTab: () -> Unit,
  onOpenHistoryDetail: (Long) -> Unit,
  onSelectBottomTab: (BottomTab) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showQuickGuideDialog by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemStatus(
          AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(context),
        )
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    containerColor = BackgroundBlack,
    topBar = {
      StitchMainTopBar(
        title = "Inicio",
        isAccessibilityReady = state.isAccessibilityReady,
        isAutomationRunning = state.activeAutomationState.isRunning || state.activeAutomationState.isPaused,
        onOpenPermissions = onOpenPermissions,
      )
    },
    bottomBar = {
      WhatsAppRepeaterBottomBar(
        currentTab = BottomTab.HOME,
        onSelectTab = onSelectBottomTab,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Banner si hay ejecución en curso
      item {
        AnimatedVisibility(
          visible = state.activeAutomationState.isRunning || state.activeAutomationState.isPaused,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          SectionCard(
            borderColor = WhatsAppGreen,
            containerColor = WhatsAppGreenContainer,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (state.activeAutomationState.isPaused) "Automatización en pausa" else "Automatización activa",
                  style = MaterialTheme.typography.titleMedium,
                  color = WhatsAppGreen,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = state.activeAutomationState.statusDetail,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextPrimary,
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              IconButton(
                onClick = onOpenActiveAutomation,
                modifier = Modifier
                  .clip(CircleShape)
                  .background(WhatsAppGreen),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Ver ejecución", tint = OnWhatsAppGreen)
              }
            }
          }
        }
      }

      // Tarjeta Servicio de Accesibilidad (diseño Stitch 2)
      item {
        val active = state.isAccessibilityReady
        SectionCard(borderColor = if (active) WhatsAppEmerald else WarningAmber) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(if (active) WhatsAppGreenContainer else WarningContainer),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AccessibilityNew,
                  contentDescription = null,
                  tint = if (active) WhatsAppGreen else WarningAmber,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Servicio de Accesibilidad",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Permiso del sistema Android",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
            }
            StatusBadge(
              label = if (active) "Activo" else "Inactivo",
              containerColor = if (active) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (active) WhatsAppGreen else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = if (active) {
              "Conectado y listo para automatizar únicamente las secuencias confirmadas por ti."
            } else {
              "Requerido para interactuar con WhatsApp de forma automatizada y enviar los mensajes programados."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          if (!state.isWhatsAppInstalled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Aviso: No se detectó WhatsApp ni WhatsApp Business instalado.",
              style = MaterialTheme.typography.bodySmall,
              color = DangerRed,
            )
          }
          Spacer(modifier = Modifier.height(14.dp))
          LargeSecondaryButton(
            text = if (active) "Gestionar permisos y accesibilidad" else "Conceder permiso en Ajustes",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = onOpenPermissions,
          )
        }
      }

      // Tarjeta Hero Verde "Crear nueva automatización" (diseño Stitch 2)
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable {
              viewModel.prepareNewAutomation()
              onCreateAutomation()
            },
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = WhatsAppGreen),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AddCircleOutline,
              contentDescription = null,
              tint = OnWhatsAppGreen,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Crear nueva automatización",
                style = MaterialTheme.typography.titleLarge,
                color = OnWhatsAppGreen,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Configura destinatario, mensajes e intervalos",
                style = MaterialTheme.typography.bodyMedium,
                color = OnWhatsAppGreen.copy(alpha = 0.85f),
              )
            }
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OnWhatsAppGreen.copy(alpha = 0.14f)),
            ) {
              Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = OnWhatsAppGreen,
              )
            }
          }
        }
      }

      // Acceso directo si existe una configuración guardada previamente
      if (state.hasSavedDraft && !state.activeAutomationState.isRunning && !state.activeAutomationState.isPaused) {
        item {
          LargeSecondaryButton(
            text = "Usar última configuración (${state.savedDraft.selectedContact?.name ?: ""})",
            icon = Icons.Rounded.BookmarkAdded,
            onClick = onResumeSavedDraft,
          )
        }
      }

      // Actividad reciente (real o estado vacío real)
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        ) {
          Text(
            text = "Actividad reciente",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
          )
          if (state.recentExecutions.isNotEmpty()) {
            Text(
              text = "Ver todo",
              style = MaterialTheme.typography.labelLarge,
              color = WhatsAppGreen,
              modifier = Modifier.clickable(onClick = onOpenHistoryTab),
            )
          }
        }
      }

      if (state.recentExecutions.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.RepeatOn,
            title = "Todavía no tienes automatizaciones",
            description = "Crea tu primera automatización para comenzar. Los envíos reales y resultados aparecerán aquí automáticamente.",
            actionButtonText = "Guía rápida de uso",
            onActionClick = { showQuickGuideDialog = true },
          )
        }
      } else {
        items(state.recentExecutions.take(3), key = { it.id }) { record ->
          RecentExecutionCard(
            record = record,
            onClick = { onOpenHistoryDetail(record.id) },
          )
        }
      }

      // Privacidad y control total
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.Top) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WhatsAppGreenContainer),
            ) {
              Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = WhatsAppGreen,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Privacidad y control total",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tus datos se procesan exclusivamente en este dispositivo. Puedes pausar o cancelar cualquier envío en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }
      }
    }
  }

  if (showQuickGuideDialog) {
    AlertDialog(
      onDismissRequest = { showQuickGuideDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = WhatsAppGreen) },
      title = { Text("Guía rápida de uso") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("1. Habilita el Servicio de Accesibilidad desde Ajustes.")
          Text("2. Pulsa \"Crear nueva automatización\" y elige un único destinatario.")
          Text("3. Selecciona Repetir mensaje único o Secuencia de mensajes múltiples.")
          Text("4. Configura un rango aleatorio prudente entre envíos.")
          Text("5. Confirma el resumen y supervisa o detén el envío en cualquier momento.")
        }
      },
      confirmButton = {
        TextButton(onClick = { showQuickGuideDialog = false }) {
          Text("Entendido", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
    )
  }
}

@Composable
private fun RecentExecutionCard(
  record: ExecutionRecord,
  onClick: () -> Unit,
) {
  val dateText = remember(record.timestampMillis) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.timestampMillis))
  }
  val (statusLabel, badgeBg, badgeFg, icon) = when (record.finalStatus) {
    ExecutionFinalStatus.COMPLETED -> Quad(
      "Completado",
      WhatsAppGreenContainer,
      WhatsAppGreen,
      Icons.Rounded.CheckCircle,
    )
    ExecutionFinalStatus.STOPPED_BY_USER -> Quad(
      "Interrumpido",
      WarningContainer,
      WarningAmber,
      Icons.Rounded.WarningAmber,
    )
    ExecutionFinalStatus.FAILED_SAFETY_CHECK, ExecutionFinalStatus.FAILED_ERROR -> Quad(
      "Con error",
      DangerContainer,
      DangerRed,
      Icons.Rounded.ErrorOutline,
    )
  }

  SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = record.contactName,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${record.contactPhone} • $dateText",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Surface(shape = RoundedCornerShape(999.dp), color = badgeBg) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
          Icon(imageVector = icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = badgeFg,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "${record.confirmedCount}/${record.scheduledCount} mensajes confirmados • Errores: ${record.errorCount}",
      style = MaterialTheme.typography.bodySmall,
      color = TextSecondary,
    )
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
