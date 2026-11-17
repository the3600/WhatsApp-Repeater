package com.whatsapprepeater.app.ui.scheduler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
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
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ScheduledTaskStatus
import com.whatsapprepeater.core.model.SchedulerRecurrence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SchedulerScreen(
  viewModel: SchedulerViewModel,
  onBack: () -> Unit,
  onOpenWizardToPrepareDraft: () -> Unit,
  onExecutePreparedTask: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showCreateDialog by remember { mutableStateOf(false) }

  val visibleTasks = when (state.selectedTab) {
    SchedulerTab.UPCOMING -> state.upcomingTasks
    SchedulerTab.HISTORY -> state.punctualityHistory
    SchedulerTab.ATTENTION -> state.attentionTasks
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
        Text("Programador Local", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Calendario, recurrencias, vacaciones y puntualidad", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.feedback.isNullOrBlank()) {
        item {
          SectionCard(
            borderColor = if (state.isErrorFeedback) DangerRed else WhatsAppGreen,
            containerColor = if (state.isErrorFeedback) DangerContainer else WhatsAppGreenContainer,
          ) {
            Text(state.feedback ?: "", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      // Modo vacaciones (pausa global de programaciones)
      item {
        SectionCard(
          borderColor = if (state.vacationPauseEnabled) WarningAmber else OutlineSubtle,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Rounded.BeachAccess,
                contentDescription = null,
                tint = if (state.vacationPauseEnabled) WarningAmber else WhatsAppGreen,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text("Pausa por vacaciones", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                  text = if (state.vacationPauseEnabled) {
                    "Activa: los recordatorios pendientes se omiten temporalmente"
                  } else {
                    "Desactivada: tus programaciones notificarán puntualmente"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
            }
            Switch(
              checked = state.vacationPauseEnabled,
              onCheckedChange = viewModel::toggleVacationPause,
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WarningAmber),
            )
          }
        }
      }

      // Botón principal de creación desde el borrador activo
      item {
        if (state.canScheduleCurrentDraft) {
          LargePrimaryButton(
            text = "Programar borrador actual (${state.activeDraft.selectedContact?.name ?: ""})",
            icon = Icons.Rounded.Add,
            onClick = { showCreateDialog = true },
          )
        } else {
          SectionCard {
            Text("¿Quieres programar un nuevo envío?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Configura primero un contacto y mensaje en el asistente o selecciona una programación existente para duplicarla.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LargePrimaryButton(
              text = "Configurar mensaje ahora",
              icon = Icons.Rounded.Schedule,
              onClick = onOpenWizardToPrepareDraft,
            )
          }
        }
      }

      // Pestañas Agenda / Puntualidad / Vencidas
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          SchedulerTab.entries.forEach { tab ->
            val selected = state.selectedTab == tab
            val badgeCount = when (tab) {
              SchedulerTab.UPCOMING -> state.upcomingTasks.size
              SchedulerTab.HISTORY -> state.punctualityHistory.size
              SchedulerTab.ATTENTION -> state.attentionTasks.size
            }
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurface,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { viewModel.selectTab(tab) },
            ) {
              Text(
                text = "${tab.label} ($badgeCount)",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      if (visibleTasks.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.EventAvailable,
            title = "Sin tareas en esta vista",
            description = "Las programaciones locales emiten un recordatorio puntual en 1 toque respetando restricciones de Android e HyperOS.",
          )
        }
      } else {
        items(visibleTasks, key = { it.id }) { task ->
          ScheduledTaskCard(
            task = task,
            onToggleEnabled = { viewModel.toggleTaskEnabled(task) },
            onDuplicate = { viewModel.duplicateSchedule(task) },
            onDelete = { viewModel.deleteTask(task.id) },
            onExecuteNow = {
              viewModel.prepareExecutionNow(task)
              onExecutePreparedTask()
            },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateScheduleDialog(
      defaultTitle = "Recordatorio ${state.activeDraft.selectedContact?.name ?: ""}",
      onDismiss = { showCreateDialog = false },
      onCreate = { title, triggerAt, recurrence, expiration, notifyMins ->
        viewModel.createFromCurrentDraft(title, triggerAt, recurrence, expiration, notifyMins)
        showCreateDialog = false
      },
    )
  }
}

@Composable
private fun ScheduledTaskCard(
  task: ScheduledTask,
  onToggleEnabled: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onExecuteNow: () -> Unit,
) {
  val dateStr = remember(task.triggerAtMillis) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(task.triggerAtMillis))
  }
  val (statusBg, statusFg) = when (task.status) {
    ScheduledTaskStatus.COMPLETED -> WhatsAppGreenContainer to WhatsAppGreen
    ScheduledTaskStatus.FAILED, ScheduledTaskStatus.EXPIRED -> DangerContainer to DangerRed
    ScheduledTaskStatus.SKIPPED_VACATION, ScheduledTaskStatus.NOTIFIED -> WarningContainer to WarningAmber
    ScheduledTaskStatus.PENDING -> CardSurfaceElevated to WhatsAppGreen
  }

  SectionCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(task.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("${task.contactName} • $dateStr", style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
      }
      Switch(
        checked = task.enabled,
        onCheckedChange = { onToggleEnabled() },
        colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
      )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusBadge(task.status.label, statusBg, statusFg)
      StatusBadge(task.recurrence.label, CardSurfaceElevated, TextSecondary)
      if (task.lastExecutedAtMillis > 0L) {
        val punctuality = if (task.lastPunctualityDeltaSeconds <= 60L) {
          "Puntual (${task.lastPunctualityDeltaSeconds}s)"
        } else {
          "+${task.lastPunctualityDeltaSeconds / 60L}m retardo"
        }
        StatusBadge(punctuality, CardSurfaceElevated, WhatsAppGreen)
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = task.primaryPreview.ifBlank { "Sin texto" },
      style = MaterialTheme.typography.bodySmall,
      color = TextSecondary,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )

    Spacer(modifier = Modifier.height(8.dp))
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row {
        IconButton(onClick = onDuplicate) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicar programación", tint = TextPrimary)
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar programación", tint = DangerRed)
        }
      }
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = WhatsAppGreenContainer,
        border = BorderStroke(1.dp, WhatsAppGreen),
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onExecuteNow),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Preparar ahora", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun CreateScheduleDialog(
  defaultTitle: String,
  onDismiss: () -> Unit,
  onCreate: (title: String, triggerAtMillis: Long, recurrence: SchedulerRecurrence, expirationMillis: Long?, notifyBeforeMinutes: Int) -> Unit,
) {
  var title by remember { mutableStateOf(defaultTitle.take(48)) }
  var offsetMinutes by remember { mutableIntStateOf(60) }
  var recurrence by remember { mutableStateOf(SchedulerRecurrence.NONE) }
  var expirationDays by remember { mutableStateOf<Int?>(null) }
  val nowSnapshot = remember { System.currentTimeMillis() }
  val triggerMillis by remember(offsetMinutes) {
    mutableLongStateOf(nowSnapshot + offsetMinutes * 60_000L)
  }
  val previewDate = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(triggerMillis))

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    textContentColor = TextSecondary,
    title = { Text("Nueva programación") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(48) },
          label = { Text("Título de la tarea") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )

        Text("Cuándo recordar ($previewDate):", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          listOf(15 to "+15 min", 60 to "+1 hora", 240 to "+4 horas", 1440 to "+24 horas").forEach { (mins, label) ->
            val selected = offsetMinutes == mins
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { offsetMinutes = mins },
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }

        Text("Recurrencia:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          SchedulerRecurrence.entries.forEach { rec ->
            val selected = recurrence == rec
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { recurrence = rec },
            ) {
              Text(
                text = rec.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }

        Text("Expiración:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          listOf(null to "Sin expiración", 7 to "7 días", 30 to "30 días").forEach { (days, label) ->
            val selected = expirationDays == days
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { expirationDays = days },
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val expMillis = expirationDays?.let { nowSnapshot + it * 86_400_000L }
          onCreate(title, System.currentTimeMillis() + offsetMinutes * 60_000L, recurrence, expMillis, 5)
        },
      ) {
        Text("Guardar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar", color = TextSecondary)
      }
    },
  )
}
