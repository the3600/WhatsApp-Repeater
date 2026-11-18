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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatsapprepeater.app.AutomationDraft
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OnWhatsAppGreen
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.SafetyLimits
import com.whatsapprepeater.core.model.SavedMessageList
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.SchedulerRecurrence
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

private val weekDayOptions = listOf(
  1 to "L",
  2 to "M",
  3 to "X",
  4 to "J",
  5 to "V",
  6 to "S",
  7 to "D",
)

/**
 * Editor visual completo de programaciones con Calendario Material 3, Reloj visual Material 3,
 * selector de contacto, modo único o secuencia, intervalo, recurrencia por días y resumen previo.
 */
@Composable
fun ScheduleFullEditorDialog(
  initialTask: ScheduledTask?,
  activeDraft: AutomationDraft,
  savedTemplates: List<SavedMessageList>,
  phoneContacts: List<ContactInfo>,
  hasContactsPermission: Boolean,
  onDismiss: () -> Unit,
  onSave: (ScheduleEditorInput) -> Unit,
) {
  val now = remember { System.currentTimeMillis() }
  val initialContact = remember(initialTask, activeDraft) {
    initialTask?.let {
      ContactInfo(
        id = it.contactId.ifBlank { "${it.contactName}_${it.contactPhone}" },
        name = it.contactName,
        phoneNumber = it.contactPhone,
      )
    } ?: activeDraft.selectedContact
  }

  var selectedContact by remember { mutableStateOf(initialContact) }
  var manualName by remember { mutableStateOf(initialContact?.name.orEmpty()) }
  var manualPhone by remember { mutableStateOf(initialContact?.phoneNumber.orEmpty()) }
  var showManualContactFields by remember { mutableStateOf(initialContact == null && !hasContactsPermission) }
  var showContactPickerModal by remember { mutableStateOf(false) }

  var title by remember {
    mutableStateOf(
      initialTask?.title ?: initialContact?.let { "Envío a ${it.name}" } ?: "",
    )
  }
  var isMultipleMode by remember {
    mutableStateOf(initialTask?.isMultipleMode ?: activeDraft.isMultipleMode)
  }
  var singleMessage by remember {
    mutableStateOf(initialTask?.singleMessage ?: activeDraft.singleMessage)
  }
  var repeatCount by remember {
    mutableIntStateOf((initialTask?.repeatCount ?: activeDraft.repeatCount).coerceAtLeast(1))
  }
  val sequenceMessages = remember {
    mutableStateListOf<String>().apply {
      val seed = initialTask?.messages ?: activeDraft.multipleMessages
      addAll(seed.filter { it.isNotBlank() })
    }
  }
  var newSequenceDraft by remember { mutableStateOf("") }

  var minIntervalSec by remember {
    mutableIntStateOf(
      (initialTask?.minIntervalSeconds ?: activeDraft.minIntervalSeconds)
        .coerceAtLeast(SafetyLimits.MIN_INTERVAL_SECONDS),
    )
  }
  var maxIntervalSec by remember {
    mutableIntStateOf(
      (initialTask?.maxIntervalSeconds ?: activeDraft.maxIntervalSeconds)
        .coerceAtLeast(minIntervalSec),
    )
  }
  var preferWhatsAppBusiness by remember {
    mutableStateOf(initialTask?.preferWhatsAppBusiness ?: activeDraft.preferWhatsAppBusiness)
  }

  var startMillis by remember {
    mutableLongStateOf(
      initialTask?.triggerAtMillis?.coerceAtLeast(now + 60_000L)
        ?: (now + 30 * 60_000L),
    )
  }
  var recurrence by remember {
    mutableStateOf(initialTask?.recurrence ?: SchedulerRecurrence.NONE)
  }
  val selectedWeekDays = remember {
    mutableStateListOf<Int>().apply {
      val initialDays = initialTask?.customWeekDays?.takeIf { it.isNotEmpty() } ?: listOf(1, 3, 5)
      addAll(initialDays)
    }
  }
  var hasEndDateTime by remember { mutableStateOf(initialTask?.expirationMillis != null) }
  var endMillis by remember {
    mutableLongStateOf(
      initialTask?.expirationMillis ?: (now + 7L * 86_400_000L),
    )
  }
  var notifyBeforeMinutes by remember {
    mutableIntStateOf(initialTask?.notifyBeforeMinutes ?: 5)
  }
  var autoExecuteWhenReady by remember {
    mutableStateOf(initialTask?.autoExecuteWhenReady ?: true)
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  var showStartDatePicker by remember { mutableStateOf(false) }
  var showStartTimePicker by remember { mutableStateOf(false) }
  var showEndDatePicker by remember { mutableStateOf(false) }
  var showEndTimePicker by remember { mutableStateOf(false) }

  val effectiveContact = selectedContact ?: run {
    val cleanPhone = manualPhone.trim()
    val cleanName = manualName.trim().ifEmpty { cleanPhone }
    if (cleanPhone.filter { it.isDigit() }.length >= 6 || cleanName.isNotBlank()) {
      ContactInfo(id = "manual_$cleanPhone", name = cleanName, phoneNumber = cleanPhone)
    } else {
      null
    }
  }

  val totalSends = if (isMultipleMode) sequenceMessages.size else if (singleMessage.isNotBlank()) repeatCount else 0
  val needsInterval = (isMultipleMode && sequenceMessages.size > 1) || (!isMultipleMode && repeatCount > 1)
  val startFormatted = remember(startMillis) {
    SimpleDateFormat("EEE dd MMM yyyy • HH:mm", Locale.forLanguageTag("es")).format(Date(startMillis))
  }
  val endFormatted = remember(endMillis) {
    SimpleDateFormat("EEE dd MMM yyyy • HH:mm", Locale.forLanguageTag("es")).format(Date(endMillis))
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundBlack)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
    ) {
      // Cabecera superior
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .background(CardSurface)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (initialTask == null) "Nueva programación" else "Editar programación",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Calendario visual, reloj y envío real controlado",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(CardSurfaceElevated),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextPrimary)
        }
      }

      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (!validationError.isNullOrBlank()) {
          item {
            SectionCard(borderColor = DangerRed) {
              Text(validationError ?: "", color = DangerRed, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        // 1. Título y Destinatario
        item {
          SectionCard {
            Text("1. Título y destinatario", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = title,
              onValueChange = { title = it.take(56) },
              label = { Text("Nombre de la programación") },
              placeholder = { Text("Ej. Recordatorio de cita") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              colors = darkFieldColors(),
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (effectiveContact != null && !showManualContactFields) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(CardSurfaceElevated)
                  .padding(12.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .size(38.dp)
                      .clip(CircleShape)
                      .background(WhatsAppGreenContainer),
                  ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = WhatsAppGreen)
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(effectiveContact.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(effectiveContact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
                  }
                }
                TextButton(onClick = { showContactPickerModal = true }) {
                  Text("Cambiar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                }
              }
            } else {
              Text(
                text = "Destinatario no seleccionado aún",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              if (phoneContacts.isNotEmpty()) {
                ActionPillButton(
                  text = "Elegir contacto",
                  icon = Icons.Rounded.Contacts,
                  selected = true,
                  onClick = { showContactPickerModal = true },
                  modifier = Modifier.weight(1f),
                )
              }
              ActionPillButton(
                text = if (showManualContactFields) "Ocultar manual" else "Número manual",
                icon = Icons.Rounded.Person,
                selected = showManualContactFields,
                onClick = { showManualContactFields = !showManualContactFields },
                modifier = Modifier.weight(1f),
              )
            }

            if (showManualContactFields) {
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedTextField(
                value = manualName,
                onValueChange = {
                  manualName = it
                  selectedContact = null
                },
                label = { Text("Nombre exacto en WhatsApp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
              )
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedTextField(
                value = manualPhone,
                onValueChange = {
                  manualPhone = it
                  selectedContact = null
                },
                label = { Text("Teléfono con código país (ej. +525576294320)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Preferir WhatsApp Business", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
              Switch(
                checked = preferWhatsAppBusiness,
                onCheckedChange = { preferWhatsAppBusiness = it },
                colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
              )
            }
          }
        }

        // 2. Mensaje único, secuencia o plantilla guardada
        item {
          SectionCard {
            Text("2. Contenido del envío", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              ActionPillButton(
                text = "Mensaje único",
                icon = null,
                selected = !isMultipleMode,
                onClick = { isMultipleMode = false },
                modifier = Modifier.weight(1f),
              )
              ActionPillButton(
                text = "Secuencia múltiple",
                icon = null,
                selected = isMultipleMode,
                onClick = { isMultipleMode = true },
                modifier = Modifier.weight(1f),
              )
            }

            if (savedTemplates.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text("Cargar desde plantilla guardada:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
              ) {
                savedTemplates.forEach { tpl ->
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = CardSurfaceElevated,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                      .clip(RoundedCornerShape(999.dp))
                      .clickable {
                        isMultipleMode = tpl.isMultipleMode
                        if (tpl.isMultipleMode) {
                          sequenceMessages.clear()
                          sequenceMessages.addAll(tpl.messages.filter { it.isNotBlank() })
                        } else {
                          singleMessage = tpl.singleMessage
                          repeatCount = tpl.repeatCount.coerceAtLeast(1)
                        }
                        if (title.isBlank()) title = tpl.title
                      },
                  ) {
                    Text(
                      text = tpl.title,
                      style = MaterialTheme.typography.labelSmall,
                      color = TextPrimary,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!isMultipleMode) {
              OutlinedTextField(
                value = singleMessage,
                onValueChange = { singleMessage = it.take(SafetyLimits.MAX_MESSAGE_LENGTH) },
                label = { Text("Mensaje a enviar") },
                placeholder = { Text("Escribe tu mensaje...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(CardSurfaceElevated)
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              ) {
                Text("Repeticiones exactas", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { if (repeatCount > 1) repeatCount-- },
                    enabled = repeatCount > 1,
                  ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Restar repetición", tint = TextPrimary)
                  }
                  Text(
                    text = "${repeatCount}x",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhatsAppGreen,
                    fontWeight = FontWeight.Bold,
                  )
                  IconButton(
                    onClick = { if (repeatCount < SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT) repeatCount++ },
                  ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Sumar repetición", tint = TextPrimary)
                  }
                }
              }
            } else {
              OutlinedTextField(
                value = newSequenceDraft,
                onValueChange = { newSequenceDraft = it.take(SafetyLimits.MAX_MESSAGE_LENGTH) },
                label = { Text("Agregar mensaje a la secuencia") },
                placeholder = { Text("Escribe el siguiente mensaje...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
              )
              Spacer(modifier = Modifier.height(8.dp))
              ActionPillButton(
                text = "Agregar mensaje (${sequenceMessages.size}/${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT})",
                icon = Icons.Rounded.Add,
                selected = newSequenceDraft.isNotBlank(),
                onClick = {
                  val clean = newSequenceDraft.trim()
                  if (clean.isNotEmpty() && sequenceMessages.size < SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT) {
                    sequenceMessages.add(clean)
                    newSequenceDraft = ""
                  }
                },
                modifier = Modifier.fillMaxWidth(),
              )
              if (sequenceMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                sequenceMessages.forEachIndexed { idx, msg ->
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 3.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(CardSurfaceElevated)
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                  ) {
                    StatusBadge("${idx + 1}", WhatsAppGreenContainer, WhatsAppGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = msg,
                      style = MaterialTheme.typography.bodySmall,
                      color = TextPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    IconButton(
                      onClick = { sequenceMessages.removeAt(idx) },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar", tint = DangerRed, modifier = Modifier.size(18.dp))
                    }
                  }
                }
              }
            }

            if (needsInterval) {
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Intervalo entre mensajes", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                IntervalStepperBox(
                  label = "Mínimo",
                  seconds = minIntervalSec,
                  onDecrement = { if (minIntervalSec > SafetyLimits.MIN_INTERVAL_SECONDS) minIntervalSec-- },
                  onIncrement = {
                    if (minIntervalSec < SafetyLimits.MAX_INTERVAL_SECONDS) {
                      minIntervalSec++
                      if (maxIntervalSec < minIntervalSec) maxIntervalSec = minIntervalSec
                    }
                  },
                  modifier = Modifier.weight(1f),
                )
                IntervalStepperBox(
                  label = "Máximo",
                  seconds = maxIntervalSec,
                  onDecrement = { if (maxIntervalSec > minIntervalSec) maxIntervalSec-- },
                  onIncrement = { if (maxIntervalSec < SafetyLimits.MAX_INTERVAL_SECONDS) maxIntervalSec++ },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        // 3. Fecha y hora de inicio + Frecuencia + Fecha de fin
        item {
          SectionCard {
            Text("3. Fecha, hora y frecuencia", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen)
            Spacer(modifier = Modifier.height(10.dp))

            Text("Fecha y hora de inicio", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              ActionPillButton(
                text = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es")).format(Date(startMillis)),
                icon = Icons.Rounded.CalendarMonth,
                selected = true,
                onClick = { showStartDatePicker = true },
                modifier = Modifier.weight(1.2f),
              )
              ActionPillButton(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis)),
                icon = Icons.Rounded.Schedule,
                selected = true,
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(0.8f),
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
              listOf(5 to "+5 min", 15 to "+15 min", 60 to "+1 h", 1440 to "+24 h").forEach { (mins, label) ->
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = CardSurfaceElevated,
                  border = BorderStroke(1.dp, OutlineSubtle),
                  modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { startMillis = System.currentTimeMillis() + mins * 60_000L },
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Frecuencia", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
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
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  )
                }
              }
            }

            if (recurrence == SchedulerRecurrence.CUSTOM_DAYS) {
              Spacer(modifier = Modifier.height(10.dp))
              Text("Días de la semana:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
              Spacer(modifier = Modifier.height(6.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                weekDayOptions.forEach { (dayVal, shortLabel) ->
                  val active = dayVal in selectedWeekDays
                  Surface(
                    shape = CircleShape,
                    color = if (active) WhatsAppGreen else CardSurfaceElevated,
                    border = BorderStroke(1.dp, if (active) WhatsAppGreen else OutlineSubtle),
                    modifier = Modifier
                      .weight(1f)
                      .height(38.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (active) {
                          if (selectedWeekDays.size > 1) selectedWeekDays.remove(dayVal)
                        } else {
                          selectedWeekDays.add(dayVal)
                        }
                      },
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) OnWhatsAppGreen else TextPrimary,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }

            if (recurrence != SchedulerRecurrence.NONE) {
              Spacer(modifier = Modifier.height(14.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Fecha y hora de finalización", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                  Text("Detener repetición automáticamente tras esa fecha", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                  checked = hasEndDateTime,
                  onCheckedChange = { enabled ->
                    hasEndDateTime = enabled
                    if (enabled && endMillis <= startMillis) {
                      endMillis = startMillis + 7L * 86_400_000L
                    }
                  },
                  colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
                )
              }
              if (hasEndDateTime) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  ActionPillButton(
                    text = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es")).format(Date(endMillis)),
                    icon = Icons.Rounded.CalendarMonth,
                    selected = false,
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1.2f),
                  )
                  ActionPillButton(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endMillis)),
                    icon = Icons.Rounded.Schedule,
                    selected = false,
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(0.8f),
                  )
                }
              }
            }
          }
        }

        // 4. Ejecución automática vs aviso + alerta previa
        item {
          SectionCard {
            Text("4. Ejecución y alertas", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Bolt, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Iniciar envío real automáticamente", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = if (autoExecuteWhenReady) {
                    "Al llegar la hora abrirá WhatsApp e iniciará el envío si Accesibilidad está activa y el teléfono desbloqueado."
                  } else {
                    "Solo emitirá una notificación con acceso directo para que confirmes manualmente."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
              Switch(
                checked = autoExecuteWhenReady,
                onCheckedChange = { autoExecuteWhenReady = it },
                colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Aviso previo antes de la hora:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
              listOf(0 to "Sin previa", 5 to "5 min antes", 15 to "15 min antes", 30 to "30 min antes").forEach { (mins, label) ->
                val sel = notifyBeforeMinutes == mins
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = if (sel) WhatsAppGreenContainer else CardSurfaceElevated,
                  border = BorderStroke(1.dp, if (sel) WhatsAppGreen else OutlineSubtle),
                  modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { notifyBeforeMinutes = mins },
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) WhatsAppGreen else TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }
        }

        // 5. Resumen visual antes de guardar
        item {
          SectionCard(borderColor = WhatsAppGreen, containerColor = WhatsAppGreenContainer.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Resumen antes de guardar", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SummaryLine("Destinatario", effectiveContact?.let { "${it.name} (${it.phoneNumber})" } ?: "Sin elegir")
            SummaryLine(
              "Contenido",
              if (isMultipleMode) {
                "Secuencia de ${sequenceMessages.size} mensaje(s)"
              } else {
                "Mensaje único (${repeatCount}x)"
              },
            )
            if (needsInterval) {
              SummaryLine("Intervalo", "${minIntervalSec}s a ${maxIntervalSec}s")
            }
            SummaryLine("Inicio", startFormatted)
            SummaryLine(
              "Frecuencia",
              if (recurrence == SchedulerRecurrence.CUSTOM_DAYS) {
                val labels = weekDayOptions.filter { it.first in selectedWeekDays }.joinToString(" ") { it.second }
                "Días específicos ($labels)"
              } else {
                recurrence.label
              },
            )
            if (recurrence != SchedulerRecurrence.NONE && hasEndDateTime) {
              SummaryLine("Finalización", endFormatted)
            }
            SummaryLine(
              "Disparo",
              if (autoExecuteWhenReady) "Envío real automático (si desbloqueado)" else "Solo notificación manual",
            )
          }
        }
      }

      // Barra inferior fija: Cancelar y Guardar programación
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(CardSurface)
          .padding(16.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = CardSurfaceElevated,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier
            .weight(0.4f)
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("Cancelar", color = TextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }

        LargePrimaryButton(
          text = "Guardar programación",
          icon = Icons.Rounded.NotificationsActive,
          modifier = Modifier.weight(0.6f),
          onClick = {
            if (effectiveContact == null) {
              validationError = "Selecciona o ingresa un contacto destinatario."
              return@LargePrimaryButton
            }
            if (totalSends <= 0) {
              validationError = "Agrega el mensaje o secuencia que se enviará."
              return@LargePrimaryButton
            }
            if (hasEndDateTime && recurrence != SchedulerRecurrence.NONE && endMillis <= startMillis) {
              validationError = "La fecha de finalización debe ser posterior a la fecha de inicio."
              return@LargePrimaryButton
            }
            validationError = null
            onSave(
              ScheduleEditorInput(
                taskId = initialTask?.id ?: 0L,
                title = title,
                contact = effectiveContact,
                isMultipleMode = isMultipleMode,
                messages = sequenceMessages.toList(),
                singleMessage = singleMessage,
                repeatCount = repeatCount,
                minIntervalSeconds = minIntervalSec,
                maxIntervalSeconds = maxIntervalSec,
                preferWhatsAppBusiness = preferWhatsAppBusiness,
                triggerAtMillis = startMillis,
                recurrence = recurrence,
                customWeekDays = selectedWeekDays.toList(),
                expirationMillis = if (recurrence != SchedulerRecurrence.NONE && hasEndDateTime) endMillis else null,
                notifyBeforeMinutes = notifyBeforeMinutes,
                autoExecuteWhenReady = autoExecuteWhenReady,
              ),
            )
          },
        )
      }
    }
  }

  if (showStartDatePicker) {
    VisualMaterialDatePickerModal(
      initialMillis = startMillis,
      onDismiss = { showStartDatePicker = false },
      onDatePicked = { updatedMillis ->
        startMillis = updatedMillis
        showStartDatePicker = false
      },
    )
  }

  if (showStartTimePicker) {
    VisualMaterialTimePickerModal(
      initialMillis = startMillis,
      onDismiss = { showStartTimePicker = false },
      onTimePicked = { updatedMillis ->
        startMillis = updatedMillis
        showStartTimePicker = false
      },
    )
  }

  if (showEndDatePicker) {
    VisualMaterialDatePickerModal(
      initialMillis = endMillis,
      onDismiss = { showEndDatePicker = false },
      onDatePicked = { updatedMillis ->
        endMillis = updatedMillis
        showEndDatePicker = false
      },
    )
  }

  if (showEndTimePicker) {
    VisualMaterialTimePickerModal(
      initialMillis = endMillis,
      onDismiss = { showEndTimePicker = false },
      onTimePicked = { updatedMillis ->
        endMillis = updatedMillis
        showEndTimePicker = false
      },
    )
  }

  if (showContactPickerModal) {
    ContactChooserModal(
      contacts = phoneContacts,
      onDismiss = { showContactPickerModal = false },
      onSelect = { chosen ->
        selectedContact = chosen
        manualName = chosen.name
        manualPhone = chosen.phoneNumber
        showManualContactFields = false
        if (title.isBlank()) title = "Envío a ${chosen.name}"
        showContactPickerModal = false
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualMaterialDatePickerModal(
  initialMillis: Long,
  onDismiss: () -> Unit,
  onDatePicked: (Long) -> Unit,
) {
  val zone = ZoneId.systemDefault()
  val currentZdt = remember(initialMillis) {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(initialMillis), zone)
  }
  val initialUtcMidnight = remember(currentZdt) {
    currentZdt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  }
  val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMidnight)

  DatePickerDialog(
    onDismissRequest = onDismiss,
    colors = DatePickerDefaults.colors(containerColor = CardSurface),
    confirmButton = {
      TextButton(
        onClick = {
          val pickedUtc = datePickerState.selectedDateMillis ?: initialUtcMidnight
          val pickedDate = Instant.ofEpochMilli(pickedUtc).atZone(ZoneOffset.UTC).toLocalDate()
          val merged = pickedDate
            .atTime(currentZdt.hour, currentZdt.minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
          onDatePicked(merged)
        },
      ) {
        Text("Aplicar fecha", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar", color = TextSecondary)
      }
    },
  ) {
    DatePicker(
      state = datePickerState,
      colors = DatePickerDefaults.colors(
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        headlineContentColor = WhatsAppGreen,
        weekdayContentColor = TextSecondary,
        subheadContentColor = TextPrimary,
        navigationContentColor = TextPrimary,
        yearContentColor = TextPrimary,
        currentYearContentColor = WhatsAppGreen,
        selectedYearContentColor = BackgroundBlack,
        selectedYearContainerColor = WhatsAppGreen,
        dayContentColor = TextPrimary,
        todayContentColor = WhatsAppGreen,
        todayDateBorderColor = WhatsAppGreen,
        selectedDayContentColor = BackgroundBlack,
        selectedDayContainerColor = WhatsAppGreen,
      ),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualMaterialTimePickerModal(
  initialMillis: Long,
  onDismiss: () -> Unit,
  onTimePicked: (Long) -> Unit,
) {
  val zone = ZoneId.systemDefault()
  val currentZdt = remember(initialMillis) {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(initialMillis), zone)
  }
  val timePickerState = rememberTimePickerState(
    initialHour = currentZdt.hour,
    initialMinute = currentZdt.minute,
    is24Hour = true,
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    title = { Text("Selecciona la hora", fontWeight = FontWeight.Bold) },
    text = {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        TimePicker(
          state = timePickerState,
          colors = TimePickerDefaults.colors(
            clockDialColor = CardSurfaceElevated,
            clockDialSelectedContentColor = BackgroundBlack,
            clockDialUnselectedContentColor = TextPrimary,
            selectorColor = WhatsAppGreen,
            containerColor = CardSurface,
            periodSelectorBorderColor = WhatsAppGreen,
            timeSelectorSelectedContainerColor = WhatsAppGreenContainer,
            timeSelectorUnselectedContainerColor = CardSurfaceElevated,
            timeSelectorSelectedContentColor = WhatsAppGreen,
            timeSelectorUnselectedContentColor = TextPrimary,
          ),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val updated = currentZdt.toLocalDate()
            .atTime(timePickerState.hour, timePickerState.minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
          onTimePicked(updated)
        },
      ) {
        Text("Aplicar hora", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar", color = TextSecondary)
      }
    },
  )
}

@Composable
private fun ContactChooserModal(
  contacts: List<ContactInfo>,
  onDismiss: () -> Unit,
  onSelect: (ContactInfo) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(contacts, query) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) contacts.take(60)
    else contacts.filter {
      it.name.lowercase().contains(q) || it.phoneNumber.contains(q)
    }.take(60)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    title = { Text("Seleccionar destinatario") },
    text = {
      Column(modifier = Modifier.heightIn(max = 380.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = WhatsAppGreen) },
          placeholder = { Text("Buscar contacto o número...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = darkFieldColors(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items(filtered, key = { "${it.id}_${it.phoneNumber}" }) { item ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurfaceElevated)
                .clickable { onSelect(item) }
                .padding(10.dp),
            ) {
              Icon(Icons.Rounded.Person, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(item.phoneNumber, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar", color = TextSecondary)
      }
    },
  )
}

@Composable
private fun ActionPillButton(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
    border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
      if (icon != null) {
        Icon(
          icon,
          contentDescription = null,
          tint = if (selected) WhatsAppGreen else TextPrimary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) WhatsAppGreen else TextPrimary,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun IntervalStepperBox(
  label: String,
  seconds: Int,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(CardSurfaceElevated)
      .padding(vertical = 6.dp, horizontal = 8.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Rounded.Remove, contentDescription = "Menos", tint = TextPrimary, modifier = Modifier.size(16.dp))
      }
      Text("${seconds}s", style = MaterialTheme.typography.titleSmall, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = "Más", tint = TextPrimary, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun SummaryLine(label: String, value: String) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(0.35f))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = TextPrimary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(0.65f),
    )
  }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = CardSurfaceElevated,
  unfocusedContainerColor = CardSurfaceElevated,
  focusedBorderColor = WhatsAppGreen,
  unfocusedBorderColor = OutlineSubtle,
  focusedTextColor = TextPrimary,
  unfocusedTextColor = TextPrimary,
  focusedLabelColor = WhatsAppGreen,
  unfocusedLabelColor = TextSecondary,
)
