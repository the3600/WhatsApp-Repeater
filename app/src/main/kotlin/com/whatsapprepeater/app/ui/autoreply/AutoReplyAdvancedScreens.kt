package com.whatsapprepeater.app.ui.autoreply

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.whatsapprepeater.app.autoreply.AutoReplyPermissionsHelper
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
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
import com.whatsapprepeater.core.autoresponder.ScheduleEvaluator
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplySchedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla 5 de AUTORESPUESTAS: Filtros de contactos, desconocidos, grupos y exclusiones.
 */
@Composable
fun AutoReplyContactFiltersScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var mode by remember { mutableStateOf(state.settings.globalContactFilterMode) }
  var allowGroups by remember { mutableStateOf(state.settings.allowGroupsByDefault) }
  var selectedList by remember { mutableStateOf(state.settings.selectedContactNamesOrPhones) }
  var excludedList by remember { mutableStateOf(state.settings.excludedIdentifiers) }
  var newExcludedInput by remember { mutableStateOf("") }
  var newSelectedInput by remember { mutableStateOf("") }
  var showContactPickDialog by remember { mutableStateOf(false) }

  LaunchedEffect(state.settings) {
    mode = state.settings.globalContactFilterMode
    allowGroups = state.settings.allowGroupsByDefault
    selectedList = state.settings.selectedContactNamesOrPhones
    excludedList = state.settings.excludedIdentifiers
  }

  LaunchedEffect(Unit) {
    viewModel.refreshSystemStatus(context)
  }

  val contactsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    viewModel.onContactsPermissionResult(granted)
    if (granted) showContactPickDialog = true
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding(),
  ) {
    AutoReplyHeader(
      title = "Filtros de Contactos",
      subtitle = "Control sobre remitentes, grupos y exclusiones",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Text("Modo de filtro predeterminado", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          AutoReplyContactFilterMode.entries.forEach { option ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { mode = option }
                .padding(vertical = 6.dp),
            ) {
              RadioButton(
                selected = mode == option,
                onClick = { mode = option },
                colors = RadioButtonDefaults.colors(selectedColor = WhatsAppGreen, unselectedColor = TextSecondary),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(filterLabel(option), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(filterDescription(option), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Permitir respuestas en grupos", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Solo cuando WhatsApp identifique conversación de grupo.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = allowGroups,
              onCheckedChange = { allowGroups = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
        }
      }

      if (mode == AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY ||
        mode == AutoReplyContactFilterMode.UNKNOWN_NUMBERS_ONLY ||
        mode == AutoReplyContactFilterMode.SELECTED_CONTACTS
      ) {
        item {
          SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              Text("Agenda local (bajo demanda)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              StatusBadge(
                label = if (state.hasContactsPermission) "Autorizado" else "Opcional",
                containerColor = if (state.hasContactsPermission) WhatsAppGreenContainer else WarningContainer,
                contentColor = if (state.hasContactsPermission) WhatsAppGreen else WarningAmber,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Las notificaciones de WhatsApp muestran el nombre visible en pantalla y no siempre exponen el teléfono completo. Si dos contactos tienen el mismo nombre, no pueden distinguirse solo por la notificación.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = if (state.hasContactsPermission) "Elegir de agenda (${state.phoneContacts.size})" else "Autorizar agenda para filtrar",
              icon = Icons.Rounded.Contacts,
              onClick = {
                if (state.hasContactsPermission) {
                  showContactPickDialog = true
                } else {
                  contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
              },
            )
          }
        }
      }

      if (mode == AutoReplyContactFilterMode.SELECTED_CONTACTS) {
        item {
          IdentifierChipManagerCard(
            title = "Contactos seleccionados (${selectedList.size})",
            placeholder = "Nombre exacto o número (ej. Ana Gómez)",
            input = newSelectedInput,
            items = selectedList,
            onInputChange = { newSelectedInput = it },
            onAdd = {
              val clean = newSelectedInput.trim()
              if (clean.isNotEmpty() && clean !in selectedList) {
                selectedList = selectedList + clean
                newSelectedInput = ""
              }
            },
            onRemove = { target -> selectedList = selectedList - target },
          )
        }
      }

      item {
        IdentifierChipManagerCard(
          title = "Lista de exclusión (${excludedList.size})",
          placeholder = "Nombre de chat, grupo o número a ignorar",
          input = newExcludedInput,
          items = excludedList,
          onInputChange = { newExcludedInput = it },
          onAdd = {
            val clean = newExcludedInput.trim()
            if (clean.isNotEmpty() && clean !in excludedList) {
              excludedList = excludedList + clean
              newExcludedInput = ""
            }
          },
          onRemove = { target -> excludedList = excludedList - target },
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      LargePrimaryButton(
        text = "Guardar filtros de contactos",
        icon = Icons.Rounded.Check,
        onClick = {
          viewModel.updateContactFilters(mode, allowGroups, selectedList, excludedList)
          onBack()
        },
      )
    }
  }

  if (showContactPickDialog) {
    AlertDialog(
      onDismissRequest = { showContactPickDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Agregar desde agenda") },
      text = {
        if (state.phoneContacts.isEmpty()) {
          Text("No hay contactos cargados en tu dispositivo.", color = TextSecondary)
        } else {
          LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(
              items = state.phoneContacts.take(80),
              key = { index, c -> "${index}_${c.id}_${c.normalizedDigits}" },
            ) { _, c ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(CardSurfaceElevated)
                  .clickable {
                    if (c.name !in selectedList) selectedList = selectedList + c.name
                    showContactPickDialog = false
                  }
                  .padding(10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(c.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                  Text(c.phoneNumber, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(Icons.Rounded.Add, contentDescription = null, tint = WhatsAppGreen)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showContactPickDialog = false }) {
          Text("Cerrar", color = TextSecondary)
        }
      },
    )
  }
}

/**
 * Pantalla 6 de AUTORESPUESTAS: Horarios globales, enfriamiento y límite por chat.
 */
@Composable
fun AutoReplyScheduleScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var scheduleEnabled by remember { mutableStateOf(state.settings.globalSchedule.enabled) }
  var startMinutes by remember { mutableIntStateOf(state.settings.globalSchedule.startMinutesOfDay) }
  var endMinutes by remember { mutableIntStateOf(state.settings.globalSchedule.endMinutesOfDay) }
  var days by remember { mutableStateOf(state.settings.globalSchedule.daysOfWeek) }
  var minIntervalSec by remember { mutableIntStateOf(state.settings.minIntervalSeconds) }
  var maxReplies by remember { mutableIntStateOf(state.settings.maxRepliesPerConversation) }

  LaunchedEffect(state.settings) {
    scheduleEnabled = state.settings.globalSchedule.enabled
    startMinutes = state.settings.globalSchedule.startMinutesOfDay
    endMinutes = state.settings.globalSchedule.endMinutesOfDay
    days = state.settings.globalSchedule.daysOfWeek
    minIntervalSec = state.settings.minIntervalSeconds
    maxReplies = state.settings.maxRepliesPerConversation
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    AutoReplyHeader(
      title = "Horarios y Límites",
      subtitle = "Franja horaria en hora local del teléfono",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Restringir horario global", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("Si está desactivado, responde todos los días 24 h.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = scheduleEnabled,
              onCheckedChange = { scheduleEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          if (scheduleEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            ScheduleSelectorBlock(
              startMinutes = startMinutes,
              endMinutes = endMinutes,
              days = days,
              onStartChange = { startMinutes = it },
              onEndChange = { endMinutes = it },
              onDaysChange = { days = it },
            )
          }
        }
      }

      item {
        SectionCard {
          Text("Prevención de spam y repeticiones", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          RuleStepperRow("Tiempo mínimo entre respuestas (s)", minIntervalSec, onMinus = { minIntervalSec = (minIntervalSec - 5).coerceAtLeast(0) }, onPlus = { minIntervalSec = (minIntervalSec + 5).coerceAtMost(3600) })
          RuleStepperRow("Máx. respuestas por conversación", maxReplies, onMinus = { maxReplies = (maxReplies - 1).coerceAtLeast(1) }, onPlus = { maxReplies = (maxReplies + 1).coerceAtMost(100) })
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      LargePrimaryButton(
        text = "Guardar horario y límites",
        icon = Icons.Rounded.Check,
        onClick = {
          viewModel.updateGlobalSchedule(
            AutoReplySchedule(scheduleEnabled, startMinutes, endMinutes, days),
          )
          viewModel.updateGlobalLimits(minIntervalSec, maxReplies)
          onBack()
        },
      )
    }
  }
}

/**
 * Pantalla 7 de AUTORESPUESTAS: Mensajes de bienvenida.
 */
@Composable
fun AutoReplyWelcomeScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var enabled by remember { mutableStateOf(state.settings.welcomeEnabled) }
  var message by remember { mutableStateOf(state.settings.welcomeMessage) }
  var cooldownHours by remember { mutableIntStateOf((state.settings.welcomeCooldownMinutes / 60).coerceAtLeast(1)) }

  LaunchedEffect(state.settings) {
    enabled = state.settings.welcomeEnabled
    message = state.settings.welcomeMessage
    cooldownHours = (state.settings.welcomeCooldownMinutes / 60).coerceAtLeast(1)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding(),
  ) {
    AutoReplyHeader(
      title = "Mensaje de Bienvenida",
      subtitle = "Se envía cuando ninguna regla prioritaria coincide",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Activar mensaje de bienvenida", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("Saluda automáticamente al primer contacto dentro de la ventana elegida.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = enabled,
              onCheckedChange = { enabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(1000) },
            label = { Text("Texto de bienvenida") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          VariableInsertBar { token -> message = (if (message.isBlank()) token else "$message $token").take(1000) }
          Spacer(modifier = Modifier.height(10.dp))
          RuleStepperRow("No repetir al mismo chat antes de (horas)", cooldownHours, onMinus = { cooldownHours = (cooldownHours - 1).coerceAtLeast(1) }, onPlus = { cooldownHours = (cooldownHours + 1).coerceAtMost(168) })
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth().background(CardSurface).padding(16.dp)) {
      LargePrimaryButton(
        text = "Guardar bienvenida",
        icon = Icons.Rounded.Check,
        enabled = !enabled || message.isNotBlank(),
        onClick = {
          viewModel.updateWelcomeConfig(enabled, message.trim(), cooldownHours * 60)
          onBack()
        },
      )
    }
  }
}

/**
 * Pantalla 8 de AUTORESPUESTAS: Mensajes de ausencia.
 */
@Composable
fun AutoReplyAwayScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var enabled by remember { mutableStateOf(state.settings.awayEnabled) }
  var message by remember { mutableStateOf(state.settings.awayMessage) }
  var scheduleEnabled by remember { mutableStateOf(state.settings.awaySchedule.enabled) }
  var startMinutes by remember { mutableIntStateOf(state.settings.awaySchedule.startMinutesOfDay) }
  var endMinutes by remember { mutableIntStateOf(state.settings.awaySchedule.endMinutesOfDay) }
  var days by remember { mutableStateOf(state.settings.awaySchedule.daysOfWeek) }

  LaunchedEffect(state.settings) {
    enabled = state.settings.awayEnabled
    message = state.settings.awayMessage
    scheduleEnabled = state.settings.awaySchedule.enabled
    startMinutes = state.settings.awaySchedule.startMinutesOfDay
    endMinutes = state.settings.awaySchedule.endMinutesOfDay
    days = state.settings.awaySchedule.daysOfWeek
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding(),
  ) {
    AutoReplyHeader(
      title = "Mensaje de Ausencia",
      subtitle = "Responde cuando estés fuera de horario o no disponible",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Activar modo ausencia", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("Tiene prioridad sobre la bienvenida durante la franja definida.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = enabled,
              onCheckedChange = { enabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(1000) },
            label = { Text("Texto de ausencia") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          VariableInsertBar { token -> message = (if (message.isBlank()) token else "$message $token").take(1000) }
        }
      }

      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Aplicar solo en horario de ausencia", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Switch(
              checked = scheduleEnabled,
              onCheckedChange = { scheduleEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          if (scheduleEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            ScheduleSelectorBlock(startMinutes, endMinutes, days, { startMinutes = it }, { endMinutes = it }, { days = it })
          }
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth().background(CardSurface).padding(16.dp)) {
      LargePrimaryButton(
        text = "Guardar mensaje de ausencia",
        icon = Icons.Rounded.Check,
        enabled = !enabled || message.isNotBlank(),
        onClick = {
          viewModel.updateAwayConfig(enabled, message.trim(), AutoReplySchedule(scheduleEnabled, startMinutes, endMinutes, days))
          onBack()
        },
      )
    }
  }
}

/**
 * Pantalla 9 de AUTORESPUESTAS: Historial opcional con privacidad explícita.
 */
@Composable
fun AutoReplyHistoryScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showClearDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    AutoReplyHeader(
      title = "Historial de Respuestas",
      subtitle = "Registro opcional estrictamente local en Room",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Guardar historial de autorespuestas", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Por privacidad, desactivado hasta que lo actives.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = state.settings.saveHistoryEnabled,
              onCheckedChange = { viewModel.updateHistoryPrivacy(it, state.settings.saveIncomingPreviewEnabled) },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Incluir vista previa del entrante", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Opcional. Si está desactivado, NO se guarda texto entrante.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = state.settings.saveIncomingPreviewEnabled,
              enabled = state.settings.saveHistoryEnabled,
              onCheckedChange = { viewModel.updateHistoryPrivacy(state.settings.saveHistoryEnabled, it) },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          if (state.history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Vaciar historial (${state.history.size})",
              icon = Icons.Rounded.DeleteSweep,
              onClick = { showClearDialog = true },
            )
          }
        }
      }

      if (state.history.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.History,
            title = if (state.settings.saveHistoryEnabled) "Sin respuestas registradas" else "Historial desactivado por privacidad",
            description = if (state.settings.saveHistoryEnabled) {
              "Las autorespuestas enviadas u omitidas aparecerán aquí cuando recibas mensajes compatibles."
            } else {
              "No se almacena ninguna notificación entrante ni respuesta hasta que habilites el historial arriba."
            },
          )
        }
      } else {
        items(state.history, key = { it.id }) { item ->
          val dateText = remember(item.timestampMillis) {
            SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(item.timestampMillis))
          }
          val isSent = item.status == AutoReplyLogStatus.SENT
          SectionCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.chatIdentifier.ifBlank { "Chat" }, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$dateText • ${item.matchedRuleName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
              StatusBadge(
                label = if (isSent) "Enviada" else "Omitida",
                containerColor = if (isSent) WhatsAppGreenContainer else WarningContainer,
                contentColor = if (isSent) WhatsAppGreen else WarningAmber,
              )
            }
            if (item.responseSent.isNotBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text("Respuesta: ${item.responseSent}", style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
            }
            if (item.incomingPreview.isNotBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text("Entrante: ${item.incomingPreview}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (item.detail.isNotBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(item.detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
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
      title = { Text("Borrar historial de autorespuestas") },
      text = { Text("¿Eliminar todos los registros locales de respuestas automáticas?") },
      confirmButton = {
        TextButton(onClick = { showClearDialog = false; viewModel.clearHistory() }) {
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

/**
 * Pantalla 10 de AUTORESPUESTAS: Permisos de notificaciones, Android 13–16 y POCO X6 Pro / HyperOS 3.
 */
@Composable
fun AutoReplyPermissionsScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemStatus(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notifReady = state.isNotificationAccessEnabled || state.runtime.isListenerConnected
  val postNotificationsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    viewModel.onPostNotificationsPermissionResult(granted)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    AutoReplyHeader(
      title = "Permisos y POCO / HyperOS",
      subtitle = "NotificationListenerService, Android 13–16 y batería",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard(borderColor = if (notifReady) WhatsAppGreen else WarningAmber) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = if (notifReady) WhatsAppGreen else WarningAmber)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Acceso a Notificaciones", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            StatusBadge(
              label = if (notifReady) "ACTIVO" else "INACTIVO",
              containerColor = if (notifReady) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (notifReady) WhatsAppGreen else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Solo observa notificaciones de WhatsApp y WhatsApp Business con RemoteInput válido. No abre chats ni captura otras aplicaciones.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          LargePrimaryButton(
            text = "Abrir Acceso a notificaciones",
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = { AutoReplyPermissionsHelper.openNotificationAccessSettings(context) },
          )
        }
      }

      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Optimización de batería", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            StatusBadge(
              label = if (state.isIgnoringBatteryOptimizations) "Sin restricción" else "Optimizada",
              containerColor = if (state.isIgnoringBatteryOptimizations) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (state.isIgnoringBatteryOptimizations) WhatsAppGreen else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Necesario en HyperOS / Android 14–16 para que el sistema no mate el servicio de notificaciones al apagar la pantalla.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          LargeSecondaryButton(
            text = "Ajustes de ahorro de batería",
            onClick = { AutoReplyPermissionsHelper.openBatteryOptimizationSettings(context) },
          )
        }
      }

      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WhatsAppGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guía POCO X6 Pro 5G (HyperOS 3 / Android 15+)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "1. Ajustes restringidos (Android 13–16): intenta activar WhatsApp Repeater en Acceso a notificaciones una vez; luego abre Info de la app → menú ⋮ → Permitir ajustes restringidos.\n" +
              "2. Inicio automático en segundo plano: en Info de la app → Permisos / Inicio automático, actívalo para recuperar el servicio tras reiniciar el POCO.\n" +
              "3. Batería → Sin restricciones y desactivar 'Pausar actividad si no se usa'.\n" +
              "4. Multitarea (Recientes): mantén pulsada la tarjeta de WhatsApp Repeater y activa el candado 🔒 para evitar que el limpiador cierre el proceso.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          LargeSecondaryButton(
            text = "Abrir info de la app (HyperOS / Ajustes restringidos)",
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = { AutoReplyPermissionsHelper.openAppDetailsSettings(context) },
          )
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          SectionCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Notificación de control y parada", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
              StatusBadge(
                label = if (state.hasPostNotificationsPermission) "Concedido" else "Opcional",
                containerColor = if (state.hasPostNotificationsPermission) WhatsAppGreenContainer else WarningContainer,
                contentColor = if (state.hasPostNotificationsPermission) WhatsAppGreen else WarningAmber,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Permite mostrar el botón rápido DETENER en la barra superior mientras el Motor 2 está activo. Sin este permiso el motor también funciona sin cerrarse.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
            if (!state.hasPostNotificationsPermission) {
              Spacer(modifier = Modifier.height(10.dp))
              LargeSecondaryButton(
                text = "Permitir notificaciones de control",
                onClick = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              )
            }
          }
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth().background(CardSurface).padding(16.dp)) {
      LargeSecondaryButton(
        text = "Verificar estado actual",
        icon = Icons.Rounded.Refresh,
        onClick = { viewModel.refreshSystemStatus(context) },
      )
    }
  }
}

@Composable
internal fun ScheduleSelectorBlock(
  startMinutes: Int,
  endMinutes: Int,
  days: Set<Int>,
  onStartChange: (Int) -> Unit,
  onEndChange: (Int) -> Unit,
  onDaysChange: (Set<Int>) -> Unit,
) {
  val dayLabels = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      dayLabels.forEach { (num, label) ->
        val selected = num in days
        Surface(
          shape = CircleShape,
          color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
          border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
          modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .clickable {
              onDaysChange(if (selected) days - num else days + num)
            },
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              text = label,
              color = if (selected) WhatsAppGreen else TextSecondary,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
    TimeStepRow("Hora de inicio", startMinutes) { delta ->
      onStartChange((startMinutes + delta + 1440) % 1440)
    }
    TimeStepRow("Hora de fin", endMinutes) { delta ->
      onEndChange((endMinutes + delta + 1440) % 1440)
    }
    if (startMinutes > endMinutes) {
      Text(
        text = "Tramo nocturno activo (${ScheduleEvaluator.formatMinutes(startMinutes)} hasta ${ScheduleEvaluator.formatMinutes(endMinutes)} del día siguiente).",
        style = MaterialTheme.typography.bodySmall,
        color = WhatsAppGreen,
      )
    }
  }
}

@Composable
private fun TimeStepRow(label: String, minutes: Int, onStep: (Int) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      IconButton(onClick = { onStep(-30) }) {
        Icon(Icons.Rounded.Remove, contentDescription = "Restar 30 min", tint = TextPrimary)
      }
      Text(
        text = ScheduleEvaluator.formatMinutes(minutes),
        style = MaterialTheme.typography.titleMedium,
        color = WhatsAppGreen,
        fontWeight = FontWeight.Bold,
      )
      IconButton(onClick = { onStep(30) }) {
        Icon(Icons.Rounded.Add, contentDescription = "Sumar 30 min", tint = TextPrimary)
      }
    }
  }
}

@Composable
private fun VariableInsertBar(onInsert: (String) -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.horizontalScroll(rememberScrollState()),
  ) {
    listOf("{nombre}", "{mensaje}", "{fecha}", "{hora}").forEach { token ->
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = CardSurfaceElevated,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .clickable { onInsert(token) },
      ) {
        Text(token, color = WhatsAppGreen, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
      }
    }
  }
}

@Composable
private fun IdentifierChipManagerCard(
  title: String,
  placeholder: String,
  input: String,
  items: List<String>,
  onInputChange: (String) -> Unit,
  onAdd: () -> Unit,
  onRemove: (String) -> Unit,
) {
  SectionCard {
    Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = input,
        onValueChange = { onInputChange(it.take(60)) },
        placeholder = { Text(placeholder, color = TextSecondary) },
        singleLine = true,
        modifier = Modifier.weight(1f),
        colors = editorFieldColors(),
      )
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = WhatsAppGreenContainer,
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onAdd),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Rounded.Add, contentDescription = "Agregar", tint = WhatsAppGreen)
        }
      }
    }
    if (items.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { entry ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(CardSurfaceElevated)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Text(entry, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { onRemove(entry) }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Rounded.Close, contentDescription = "Quitar", tint = DangerRed, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

private fun filterDescription(mode: AutoReplyContactFilterMode): String = when (mode) {
  AutoReplyContactFilterMode.ALL -> "Responde a todos los chats permitidos (salvo lista de exclusión)."
  AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY -> "Solo responde si el remitente coincide con un contacto guardado (requiere agenda)."
  AutoReplyContactFilterMode.UNKNOWN_NUMBERS_ONLY -> "Solo responde a números sin guardar o remitentes no presentes en tu agenda."
  AutoReplyContactFilterMode.SELECTED_CONTACTS -> "Únicamente a la lista de nombres o números que elijas abajo."
  AutoReplyContactFilterMode.GROUPS_ONLY -> "Aplica únicamente en notificaciones identificadas como grupo."
}

