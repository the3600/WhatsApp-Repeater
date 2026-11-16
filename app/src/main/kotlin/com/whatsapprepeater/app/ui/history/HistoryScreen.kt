package com.whatsapprepeater.app.ui.history

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.BottomTab
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StitchMainTopBar
import com.whatsapprepeater.app.ui.components.WhatsAppRepeaterBottomBar
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla 11 (Stitch 11._historial):
 * Historial real persistido en Room, filtros y gestión manual (sin datos falsos).
 */
@Composable
fun HistoryScreen(
  viewModel: HistoryViewModel,
  onCreateFirstAutomation: () -> Unit,
  onOpenDetail: (Long) -> Unit,
  onRetryRecord: (ExecutionRecord) -> Unit,
  onOpenBackupSettings: () -> Unit,
  onOpenPermissions: () -> Unit,
  onSelectBottomTab: (BottomTab) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = BackgroundBlack,
    topBar = {
      StitchMainTopBar(
        title = "Historial de Envíos",
        isAccessibilityReady = state.isAccessibilityReady,
        isAutomationRunning = state.activeAutomationState.isRunning || state.activeAutomationState.isPaused,
        onOpenPermissions = onOpenPermissions,
      )
    },
    bottomBar = {
      WhatsAppRepeaterBottomBar(
        currentTab = BottomTab.HISTORY,
        onSelectTab = onSelectBottomTab,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Filtros estilo Stitch 11
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(4.dp),
        ) {
          FilterTabPill(
            text = "Todos (${state.allRecords.size})",
            selected = state.filter == HistoryFilter.ALL,
            onClick = { viewModel.setFilter(HistoryFilter.ALL) },
            modifier = Modifier.weight(1f),
          )
          FilterTabPill(
            text = "Completados",
            selected = state.filter == HistoryFilter.COMPLETED,
            onClick = { viewModel.setFilter(HistoryFilter.COMPLETED) },
            modifier = Modifier.weight(1f),
          )
          FilterTabPill(
            text = "Fallidos",
            selected = state.filter == HistoryFilter.FAILED,
            onClick = { viewModel.setFilter(HistoryFilter.FAILED) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      if (state.filteredRecords.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.History,
            title = if (state.allRecords.isEmpty()) "Aún no hay actividad" else "Sin registros en este filtro",
            description = if (state.allRecords.isEmpty()) {
              "Las automatizaciones completadas, pausadas o canceladas se registrarán aquí con su reporte real."
            } else {
              "Cambia el filtro superior para ver otros registros almacenados."
            },
            actionButtonText = if (state.allRecords.isEmpty()) "Crear primera automatización" else null,
            onActionClick = if (state.allRecords.isEmpty()) onCreateFirstAutomation else null,
          )
        }
      } else {
        items(state.filteredRecords, key = { it.id }) { record ->
          HistoryRecordCard(
            record = record,
            onOpenDetail = { onOpenDetail(record.id) },
            onRetry = {
              viewModel.prepareRetryPending(record)
              onRetryRecord(record)
            },
            onDelete = { viewModel.deleteRecord(record.id) },
          )
        }
      }

      // Gestión y limpieza manual (Stitch 11)
      item {
        SectionCard {
          Text("Gestión y limpieza manual", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Los registros se conservan únicamente en el almacenamiento local del dispositivo.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LargeSecondaryButton(
              text = "Borrar todo",
              icon = Icons.Rounded.DeleteSweep,
              enabled = state.allRecords.isNotEmpty(),
              onClick = { showClearDialog = true },
              modifier = Modifier.weight(1f),
            )
            LargeSecondaryButton(
              text = "Exportar",
              icon = Icons.Rounded.Download,
              onClick = onOpenBackupSettings,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Borrar historial completo") },
      text = { Text("¿Deseas eliminar todos los registros de ejecuciones locales?") },
      confirmButton = {
        TextButton(
          onClick = {
            showClearDialog = false
            viewModel.clearAllHistory()
          },
        ) {
          Text("Borrar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun FilterTabPill(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = if (selected) CardSurfaceElevated else CardSurface,
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) TextPrimary else TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun HistoryRecordCard(
  record: ExecutionRecord,
  onOpenDetail: () -> Unit,
  onRetry: () -> Unit,
  onDelete: () -> Unit,
) {
  val dateText = remember(record.timestampMillis) {
    SimpleDateFormat("dd/MM, HH:mm", Locale.getDefault()).format(Date(record.timestampMillis))
  }
  val isSuccess = record.finalStatus == ExecutionFinalStatus.COMPLETED
  val badgeBg = if (isSuccess) WhatsAppGreenContainer else DangerContainer
  val badgeFg = if (isSuccess) WhatsAppGreen else DangerRed
  val statusIcon = when (record.finalStatus) {
    ExecutionFinalStatus.COMPLETED -> Icons.Rounded.CheckCircle
    ExecutionFinalStatus.STOPPED_BY_USER -> Icons.Rounded.WarningAmber
    ExecutionFinalStatus.FAILED_SAFETY_CHECK, ExecutionFinalStatus.FAILED_ERROR -> Icons.Rounded.ErrorOutline
  }

  SectionCard(modifier = Modifier.clickable(onClick = onOpenDetail)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(badgeBg),
        ) {
          Icon(
            imageVector = if (isSuccess) Icons.AutoMirrored.Rounded.Chat else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = badgeFg,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = record.contactName,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$dateText • ${record.contactPhone}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }
      Surface(shape = RoundedCornerShape(999.dp), color = badgeBg) {
        Text(
          text = "${record.confirmedCount}/${record.scheduledCount} msgs",
          style = MaterialTheme.typography.labelSmall,
          color = badgeFg,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }

    if (record.statusDetail.isNotBlank()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = record.statusDetail,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(statusIcon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = when (record.finalStatus) {
            ExecutionFinalStatus.COMPLETED -> "Completado sin errores"
            ExecutionFinalStatus.STOPPED_BY_USER -> "Interrumpido (${record.confirmedCount}/${record.scheduledCount})"
            ExecutionFinalStatus.FAILED_SAFETY_CHECK -> "Detenido por seguridad"
            ExecutionFinalStatus.FAILED_ERROR -> "Fallido"
          },
          style = MaterialTheme.typography.bodySmall,
          color = badgeFg,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!isSuccess && record.cancelledCount > 0) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = CardSurfaceElevated,
            border = BorderStroke(1.dp, OutlineSubtle),
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .clickable(onClick = onRetry),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
              Spacer(modifier = Modifier.width(4.dp))
              Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(14.dp))
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = CardSurfaceElevated,
            border = BorderStroke(1.dp, OutlineSubtle),
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .clickable(onClick = onOpenDetail),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text("Detalles", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
              Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
          }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}

