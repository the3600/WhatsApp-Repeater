package com.whatsapprepeater.app.ui.scheduler

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
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

private val weekDayShortLabels = mapOf(
  1 to "Lun",
  2 to "Mar",
  3 to "Mié",
  4 to "Jue",
  5 to "Vie",
  6 to "Sáb",
  7 to "Dom",
)

@Composable
fun SchedulerScreen(
  viewModel: SchedulerViewModel,
  onBack: () -> Unit,
  onOpenWizardToPrepareDraft: () -> Unit,
  onExecutePreparedTask: () -> Unit,
  onOpenPermissions: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  var showEditorForTask by remember { mutableStateOf<Pair<Boolean, ScheduledTask?>>(false to null) }
  var taskPendingDelete by remember { mutableStateOf<ScheduledTask?>(null) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshEnvironment()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val visibleTasks = when (state.selectedTab) {
    SchedulerTab.UPCOMING -> state.upcomingTasks
    SchedulerTab.PAUSED -> state.pausedTasks
    SchedulerTab.FINISHED -> state.finishedOrFailedTasks
    SchedulerTab.ALL -> state.tasks.sortedBy { it.triggerAtMillis }
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
        Text("Calendario visual, reloj y envío programado real", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Botón principal SIEMPRE visible para crear programación completa desde aquí
      item {
        LargePrimaryButton(
          text = "Nueva programación",
          icon = Icons.Rounded.Add,
          onClick = {
            viewModel.clearFeedback()
            showEditorForTask = true to null
          },
        )
      }

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

      // Panel honesto de requisitos del sistema (Accesibilidad, Alarmas exactas, HyperOS / desbloqueo)
      item {
        SectionCard(
          borderColor = if (state.isAccessibilityConnected && state.canScheduleExactAlarms) OutlineSubtle else WarningAmber,
        ) {
          Text("Requisitos del envío automático", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
          ) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (state.isAccessibilityConnected) WhatsAppGreenContainer else WarningContainer,
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenPermissions),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(
                  Icons.Rounded.AccessibilityNew,
                  contentDescription = null,
                  tint = if (state.isAccessibilityConnected) WhatsAppGreen else WarningAmber,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (state.isAccessibilityConnected) "Accesibilidad activa" else "Activar Accesibilidad",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (state.isAccessibilityConnected) WhatsAppGreen else WarningAmber,
                  fontWeight = FontWeight.Bold,
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (state.canScheduleExactAlarms) WhatsAppGreenContainer else WarningContainer,
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = { viewModel.openExactAlarmSettings() }),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(
                  Icons.Rounded.AlarmOn,
                  contentDescription = null,
                  tint = if (state.canScheduleExactAlarms) WhatsAppGreen else WarningAmber,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (state.canScheduleExactAlarms) "Alarma exacta OK" else "Permitir alarma exacta",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (state.canScheduleExactAlarms) WhatsAppGreen else WarningAmber,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "WhatsApp requiere pantalla encendida y desbloqueada para enviar mensajes. Si el teléfono está bloqueado o HyperOS suspende la app en segundo plano, no se simulará un envío: recibirás un aviso de Intervención requerida.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }

      // Modo vacaciones global
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
                    "Activa: ninguna programación ejecutará envíos hasta desactivarla"
                  } else {
                    "Desactivada: las programaciones activas siguen su horario"
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

      // Pestañas
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
              SchedulerTab.PAUSED -> state.pausedTasks.size
              SchedulerTab.FINISHED -> state.finishedOrFailedTasks.size
              SchedulerTab.ALL -> state.tasks.size
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
            title = "Sin programaciones en esta vista",
            description = "Pulsa «Nueva programación» arriba para elegir contacto, mensaje único o secuencia, fecha con calendario visual y hora exacta.",
          )
        }
      } else if (state.selectedTab == SchedulerTab.UPCOMING) {
        val groupedByDay = visibleTasks.groupBy { task ->
          SimpleDateFormat("EEEE, dd MMM yyyy", Locale.forLanguageTag("es")).format(Date(task.triggerAtMillis))
        }
        groupedByDay.forEach { (dayLabel, dayTasks) ->
          item(key = "day_header_$dayLabel") {
            Text(
              text = dayLabel.replaceFirstChar { it.uppercase() },
              style = MaterialTheme.typography.labelLarge,
              color = WhatsAppGreen,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          items(dayTasks, key = { "upcoming_${it.id}" }) { task ->
            ScheduledTaskCard(
              task = task,
              onEdit = { showEditorForTask = true to task },
              onPause = { viewModel.pauseTask(task) },
              onResume = { viewModel.resumeTask(task) },
              onDelete = { taskPendingDelete = task },
              onExecuteNow = {
                viewModel.prepareExecutionNow(task)
                onExecutePreparedTask()
              },
            )
          }
        }
      } else {
        items(visibleTasks, key = { "${state.selectedTab.name}_${it.id}" }) { task ->
          ScheduledTaskCard(
            task = task,
            onEdit = { showEditorForTask = true to task },
            onPause = { viewModel.pauseTask(task) },
            onResume = { viewModel.resumeTask(task) },
            onDelete = { taskPendingDelete = task },
            onExecuteNow = {
              viewModel.prepareExecutionNow(task)
              onExecutePreparedTask()
            },
          )
        }
      }
    }
  }

  if (showEditorForTask.first) {
    ScheduleFullEditorDialog(
      initialTask = showEditorForTask.second,
      activeDraft = state.activeDraft,
      savedTemplates = state.savedTemplates,
      phoneContacts = state.phoneContacts,
      hasContactsPermission = state.hasContactsPermission,
      onDismiss = { showEditorForTask = false to null },
      onSave = { editorInput ->
        viewModel.saveSchedule(editorInput) {
          showEditorForTask = false to null
        }
      },
    )
  }

  val confirmedSaved = state.savedConfirmationTask
  if (confirmedSaved != null) {
    val savedDate = remember(confirmedSaved.triggerAtMillis) {
      SimpleDateFormat("EEEE dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es")).format(Date(confirmedSaved.triggerAtMillis))
    }
    AlertDialog(
      onDismissRequest = viewModel::dismissSavedConfirmation,
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Programación guardada", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "\"${confirmedSaved.title}\" para ${confirmedSaved.contactName} quedó programada para el $savedDate.",
            color = TextPrimary,
          )
          Text(
            text = if (confirmedSaved.autoExecuteWhenReady) {
              "Intentará ejecutar el envío real automáticamente al llegar la hora si la accesibilidad está activa y la pantalla desbloqueada."
            } else {
              "Recibirás un aviso puntual con botón para revisarlo e iniciarlo."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = viewModel::dismissSavedConfirmation) {
          Text("Entendido", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
    )
  }

  val pendingDel = taskPendingDelete
  if (pendingDel != null) {
    AlertDialog(
      onDismissRequest = { taskPendingDelete = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Eliminar programación") },
      text = { Text("¿Eliminar \"${pendingDel.title}\" y cancelar sus alarmas locales?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteTask(pendingDel.id)
            taskPendingDelete = null
          },
        ) {
          Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { taskPendingDelete = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun ScheduledTaskCard(
  task: ScheduledTask,
  onEdit: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDelete: () -> Unit,
  onExecuteNow: () -> Unit,
) {
  val dateStr = remember(task.triggerAtMillis) {
    SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date(task.triggerAtMillis))
  }
  val endStr = remember(task.expirationMillis) {
    task.expirationMillis?.let {
      SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
    }
  }
  val (statusBg, statusFg) = when (task.status) {
    ScheduledTaskStatus.COMPLETED -> WhatsAppGreenContainer to WhatsAppGreen
    ScheduledTaskStatus.ACTIVE -> WhatsAppGreenContainer to WhatsAppGreen
    ScheduledTaskStatus.FAILED, ScheduledTaskStatus.EXPIRED -> DangerContainer to DangerRed
    ScheduledTaskStatus.PAUSED, ScheduledTaskStatus.SKIPPED_VACATION, ScheduledTaskStatus.NOTIFIED ->
      WarningContainer to WarningAmber
    ScheduledTaskStatus.PENDING -> CardSurfaceElevated to WhatsAppGreen
  }
  val recurrenceText = if (task.recurrence == SchedulerRecurrence.CUSTOM_DAYS && task.customWeekDays.isNotEmpty()) {
    task.customWeekDays.sorted().mapNotNull { weekDayShortLabels[it] }.joinToString(", ")
  } else {
    task.recurrence.label
  }
  val isPauseable = task.enabled &&
    task.status != ScheduledTaskStatus.PAUSED &&
    task.status != ScheduledTaskStatus.COMPLETED

  SectionCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(task.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("${task.contactName} (${task.contactPhone})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("Inicio / próximo: $dateStr", style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen, fontWeight = FontWeight.SemiBold)
        if (endStr != null) {
          Text("Finalización: $endStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
      }
      StatusBadge(task.status.label, statusBg, statusFg)
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
      StatusBadge(recurrenceText, CardSurfaceElevated, TextSecondary)
      val modeBadge = if (task.isMultipleMode) {
        "Secuencia (${task.totalMessagesCount}) • ${task.minIntervalSeconds}-${task.maxIntervalSeconds}s"
      } else {
        "Único (${task.repeatCount}x)" + if (task.repeatCount > 1) " • ${task.minIntervalSeconds}-${task.maxIntervalSeconds}s" else ""
      }
      StatusBadge(modeBadge, CardSurfaceElevated, TextPrimary)
      if (task.autoExecuteWhenReady) {
        StatusBadge("Auto-envío si desbloqueado", WhatsAppGreenContainer, WhatsAppGreen)
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

    if (task.lastFailureReason.isNotBlank()) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(DangerContainer)
          .padding(8.dp),
      ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = task.lastFailureReason,
          style = MaterialTheme.typography.bodySmall,
          color = TextPrimary,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onEdit) {
          Icon(Icons.Rounded.Edit, contentDescription = "Editar programación", tint = TextPrimary)
        }
        if (isPauseable) {
          IconButton(onClick = onPause) {
            Icon(Icons.Rounded.Pause, contentDescription = "Pausar programación", tint = WarningAmber)
          }
        } else {
          IconButton(onClick = onResume) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Reanudar programación", tint = WhatsAppGreen)
          }
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
          Text("Enviar ahora", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
