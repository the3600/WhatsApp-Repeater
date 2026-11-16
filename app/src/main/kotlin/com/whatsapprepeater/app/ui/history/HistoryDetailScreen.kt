package com.whatsapprepeater.app.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.MessageTraceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla Detalle de Registro / Registro Interrumpido (Stitch detalle_de_historial_interrumpido):
 * Muestra progreso, motivo exacto de interrupción/seguridad, trazabilidad por mensaje y recuperación.
 */
@Composable
fun HistoryDetailScreen(
  recordId: Long,
  viewModel: HistoryViewModel,
  onBack: () -> Unit,
  onResumePending: () -> Unit,
) {
  val record by viewModel.observeRecord(recordId).collectAsStateWithLifecycle(initialValue = null)
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var templateSaved by remember(recordId) { mutableStateOf(false) }

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
          Text(
            text = "Lote ID: #${record?.batchCode ?: recordId}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
          )
          Text("Detalle de Ejecución", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
      }
      val status = record?.finalStatus
      if (status != null) {
        val (label, bg, fg) = when (status) {
          ExecutionFinalStatus.COMPLETED -> Triple("Completado", WhatsAppGreenContainer, WhatsAppGreen)
          ExecutionFinalStatus.STOPPED_BY_USER -> Triple("Interrumpido", DangerContainer, DangerRed)
          ExecutionFinalStatus.FAILED_SAFETY_CHECK -> Triple("Seguridad", WarningContainer, WarningAmber)
          ExecutionFinalStatus.FAILED_ERROR -> Triple("Error", DangerContainer, DangerRed)
        }
        StatusBadge(label, bg, fg)
      }
    }

    val rec = record
    if (rec == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Registro no encontrado", color = TextSecondary)
      }
      return
    }

    val dateStr = remember(rec.timestampMillis) {
      SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(rec.timestampMillis))
    }
    val progress = if (rec.scheduledCount > 0) rec.confirmedCount.toFloat() / rec.scheduledCount else 0f

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(WhatsAppGreenContainer),
              ) {
                Icon(Icons.Rounded.Phone, contentDescription = null, tint = WhatsAppGreen)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(rec.contactName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("${rec.contactPhone} • $dateStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
            }
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WhatsAppGreen)
          }
        }
      }

      item {
        SectionCard {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Progreso del Lote", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
              text = "${rec.confirmedCount} de ${rec.scheduledCount} (${(progress * 100).toInt()}%)",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(999.dp)),
            color = WhatsAppGreen,
            trackColor = if (rec.finalStatus == ExecutionFinalStatus.COMPLETED) CardSurfaceElevated else DangerContainer,
          )
          Spacer(modifier = Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AuditStatCard("ENVIADOS", "${rec.confirmedCount}", "Confirmados por UI", WhatsAppGreen, Modifier.weight(1f))
            AuditStatCard("CANCELADOS", "${rec.cancelledCount}", "Purgados en cola", DangerCoral, Modifier.weight(1f))
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val sec = (rec.durationMillis / 1000L).toInt()
            AuditStatCard("TIEMPO ACTIVO", "${sec}s", "Duración real", TextPrimary, Modifier.weight(1f))
            AuditStatCard(
              "CADENCIA",
              if (rec.averageIntervalSeconds > 0.0) "${"%.1f".format(rec.averageIntervalSeconds)}s" else "—",
              "Intervalo medio",
              WarningAmber,
              Modifier.weight(1f),
            )
          }
        }
      }

      if (rec.finalStatus != ExecutionFinalStatus.COMPLETED) {
        item {
          SectionCard(borderColor = WarningAmber.copy(alpha = 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.PanTool, contentDescription = null, tint = DangerRed)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Auditoría de la Interrupción", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurfaceElevated)
                .padding(12.dp),
            ) {
              Text("Motivo reportado:", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
              Spacer(modifier = Modifier.height(4.dp))
              Text(rec.statusDetail, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "La cola restante se detuvo de forma segura sin reintentos ocultos en segundo plano.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }

      if (rec.messageTraces.isNotEmpty()) {
        item {
          SectionCard {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Trazabilidad por Mensaje", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("${rec.scheduledCount} envíos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(10.dp))
          }
        }
        items(rec.messageTraces, key = { it.index }) { trace ->
          val timeStr = remember(trace.timestampMillis) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(trace.timestampMillis))
          }
          val (icon, tint, statusTag) = when (trace.status) {
            MessageTraceStatus.CONFIRMED -> Triple(Icons.Rounded.CheckCircle, WhatsAppGreen, timeStr)
            MessageTraceStatus.CANCELLED -> Triple(Icons.Rounded.Cancel, DangerCoral, "Sin enviar")
            MessageTraceStatus.FAILED -> Triple(Icons.Rounded.ErrorOutline, DangerRed, "Error")
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(CardSurface)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Mensaje #${trace.index}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text(
                text = trace.messagePreview,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = CardSurfaceElevated) {
              Text(
                text = statusTag,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      if (rec.cancelledCount > 0) {
        LargePrimaryButton(
          text = "Reanudar pendientes (${rec.cancelledCount} mensajes)",
          icon = Icons.Rounded.Refresh,
          onClick = {
            viewModel.prepareRetryPending(rec)
            onResumePending()
          },
        )
      }
      if (rec.messageTraces.isNotEmpty()) {
        LargeSecondaryButton(
          text = if (templateSaved) "Plantilla guardada en Listas" else "Crear nueva plantilla con este lote",
          icon = if (templateSaved) Icons.Rounded.CheckCircle else Icons.Rounded.BookmarkAdd,
          enabled = !templateSaved,
          onClick = {
            viewModel.saveBatchAsTemplate(rec)
            templateSaved = true
          },
        )
      }
      TextButton(
        onClick = { showDeleteConfirmDialog = true },
      ) {
        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = DangerCoral, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Eliminar este registro de auditoría", color = DangerCoral)
      }
    }
  }

  if (showDeleteConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirmDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Eliminar registro de auditoría") },
      text = { Text("¿Deseas eliminar permanentemente este registro del historial local?") },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirmDialog = false
            viewModel.deleteRecord(recordId)
            onBack()
          },
        ) {
          Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirmDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun AuditStatCard(
  title: String,
  value: String,
  subtitle: String,
  valueColor: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(CardSurfaceElevated)
      .padding(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Spacer(modifier = Modifier.height(4.dp))
    Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
  }
}

