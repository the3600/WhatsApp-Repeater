package com.whatsapprepeater.app.ui.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityCenterScreen(
  viewModel: ActivityCenterViewModel,
  onBack: () -> Unit,
  onOpenExecutionDetail: (Long) -> Unit,
  onOpenScheduler: () -> Unit,
  onOpenDiagnostics: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionAlerts()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(CardSurfaceElevated),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text("Centro de Actividad", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Programaciones, envíos confirmados, fallos y permisos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Resumen KPI real
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          ActivityKpiTile("Confirmados", "${state.totalConfirmedMessages}", WhatsAppGreen, Modifier.weight(1f))
          ActivityKpiTile("Próximas", "${state.totalUpcomingSchedules}", WhatsAppGreen, Modifier.weight(1f))
          ActivityKpiTile(
            "Fallidos",
            "${state.totalFailedItems}",
            if (state.totalFailedItems > 0) DangerRed else TextSecondary,
            Modifier.weight(1f),
          )
          ActivityKpiTile(
            "Permisos",
            "${state.totalPermissionAlerts}",
            if (state.totalPermissionAlerts > 0) WarningAmber else WhatsAppGreen,
            Modifier.weight(1f),
          )
        }
      }

      // Buscador por destinatario
      item {
        OutlinedTextField(
          value = state.recipientQuery,
          onValueChange = viewModel::onRecipientQueryChanged,
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = WhatsAppGreen) },
          placeholder = { Text("Filtrar por destinatario, teléfono o título...", color = TextSecondary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardSurface,
            unfocusedContainerColor = CardSurface,
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
      }

      // Filtros de estado / tipo y fecha
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          ActivityCategoryFilter.entries.forEach { cat ->
            val selected = state.categoryFilter == cat
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurface,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { viewModel.setCategoryFilter(cat) },
            ) {
              Text(
                text = cat.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          ActivityDateFilter.entries.forEach { df ->
            val selected = state.dateFilter == df
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (selected) CardSurfaceElevated else CardSurface,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { viewModel.setDateFilter(df) },
            ) {
              Text(
                text = df.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) WhatsAppGreen else TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      if (state.entries.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.EventNote,
            title = "Sin actividad coincidente",
            description = "Cambia los filtros o crea una automatización/programación. Solo se registran eventos reales y confirmados.",
          )
        }
      } else {
        items(state.entries, key = { it.id }) { entry ->
          ActivityEntryCard(
            entry = entry,
            onClick = {
              when {
                entry.executionRecordId != null -> onOpenExecutionDetail(entry.executionRecordId)
                entry.scheduledTaskId != null -> onOpenScheduler()
                entry.kind == ActivityEntryKind.PERMISSION_ALERT -> onOpenDiagnostics()
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ActivityKpiTile(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(CardSurface)
      .padding(vertical = 10.dp, horizontal = 4.dp),
  ) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun ActivityEntryCard(
  entry: ActivityEntry,
  onClick: () -> Unit,
) {
  val dateStr = remember(entry.timestampMillis) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestampMillis))
  }
  val (icon, badgeBg, badgeFg) = when (entry.kind) {
    ActivityEntryKind.EXECUTION_CONFIRMED, ActivityEntryKind.AUTOREPLY_SENT ->
      Triple(Icons.Rounded.CheckCircle, WhatsAppGreenContainer, WhatsAppGreen)
    ActivityEntryKind.SCHEDULE_UPCOMING ->
      Triple(Icons.Rounded.Schedule, WhatsAppGreenContainer, WhatsAppGreen)
    ActivityEntryKind.SCHEDULE_PAUSED, ActivityEntryKind.EXECUTION_INTERRUPTED ->
      Triple(Icons.Rounded.PauseCircleOutline, WarningContainer, WarningAmber)
    ActivityEntryKind.PERMISSION_ALERT ->
      Triple(Icons.Rounded.Shield, WarningContainer, WarningAmber)
    ActivityEntryKind.SCHEDULE_FAILED, ActivityEntryKind.EXECUTION_FAILED, ActivityEntryKind.AUTOREPLY_FAILED ->
      Triple(Icons.Rounded.ErrorOutline, DangerContainer, DangerRed)
  }

  SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(entry.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          val recipientLine = if (entry.recipientPhone.isNotBlank()) "${entry.recipientName} (${entry.recipientPhone})" else entry.recipientName
          Text("$recipientLine • $dateStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
      StatusBadge(entry.badgeText, badgeBg, badgeFg)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = badgeFg, fontWeight = FontWeight.SemiBold)
    if (entry.detail.isNotBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
  }
}
