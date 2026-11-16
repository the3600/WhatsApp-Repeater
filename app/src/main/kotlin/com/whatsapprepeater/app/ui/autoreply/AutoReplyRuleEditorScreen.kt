package com.whatsapprepeater.app.ui.autoreply

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.RuleMatchType

/**
 * Pantallas 3 y 4 de AUTORESPUESTAS: Crear y Editar regla local.
 */
@Composable
fun AutoReplyRuleEditorScreen(
  ruleId: Long,
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
) {
  val isEditing = ruleId > 0L
  var loaded by remember(ruleId) { mutableStateOf(!isEditing) }
  var name by remember(ruleId) { mutableStateOf("") }
  var pattern by remember(ruleId) { mutableStateOf("") }
  var matchType by remember(ruleId) { mutableStateOf(RuleMatchType.CONTAINS_PHRASE) }
  var caseSensitive by remember(ruleId) { mutableStateOf(false) }
  var responseTemplate by remember(ruleId) { mutableStateOf("") }
  var enabled by remember(ruleId) { mutableStateOf(true) }
  var priority by remember(ruleId) { mutableIntStateOf(10) }
  var targetApp by remember(ruleId) { mutableStateOf(AutoReplyTargetApp.BOTH) }
  var filterMode by remember(ruleId) { mutableStateOf(AutoReplyContactFilterMode.ALL) }
  var allowGroups by remember(ruleId) { mutableStateOf(false) }
  var selectedCsv by remember(ruleId) { mutableStateOf("") }
  var excludedCsv by remember(ruleId) { mutableStateOf("") }
  var minIntervalSec by remember(ruleId) { mutableIntStateOf(30) }
  var maxReplies by remember(ruleId) { mutableIntStateOf(5) }
  var delaySec by remember(ruleId) { mutableIntStateOf(0) }
  var scheduleEnabled by remember(ruleId) { mutableStateOf(false) }
  var scheduleStartMin by remember(ruleId) { mutableIntStateOf(9 * 60) }
  var scheduleEndMin by remember(ruleId) { mutableIntStateOf(18 * 60) }
  var scheduleDays by remember(ruleId) { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
  var liveTestInput by remember(ruleId) { mutableStateOf("") }

  LaunchedEffect(ruleId) {
    if (isEditing) {
      val existing = viewModel.getRuleOnce(ruleId)
      if (existing != null) {
        name = existing.name
        pattern = existing.pattern
        matchType = existing.matchType
        caseSensitive = existing.caseSensitive
        responseTemplate = existing.responseTemplate
        enabled = existing.enabled
        priority = existing.priority
        targetApp = existing.targetApp
        filterMode = existing.contactFilterMode
        allowGroups = existing.allowGroups
        selectedCsv = existing.selectedContactNamesOrPhones.joinToString(", ")
        excludedCsv = existing.excludedIdentifiers.joinToString(", ")
        minIntervalSec = existing.minIntervalSeconds
        maxReplies = existing.maxRepliesPerConversation
        delaySec = existing.delaySeconds
        scheduleEnabled = existing.schedule.enabled
        scheduleStartMin = existing.schedule.startMinutesOfDay
        scheduleEndMin = existing.schedule.endMinutesOfDay
        scheduleDays = existing.schedule.daysOfWeek
      }
      loaded = true
    }
  }

  val patternError = remember(pattern, matchType) {
    if (pattern.isBlank()) null else viewModel.validateRulePattern(pattern, matchType)
  }
  val canSave = loaded && name.isNotBlank() && pattern.isNotBlank() && patternError == null && responseTemplate.isNotBlank()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding(),
  ) {
    AutoReplyHeader(
      title = if (isEditing) "Editar Regla" else "Crear Regla",
      subtitle = "Motor 100 % local y configurable",
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
            Text("Regla activa", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Switch(
              checked = enabled,
              onCheckedChange = { enabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Nombre de la regla") },
            placeholder = { Text("Ej. Consulta de precios", color = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text("Tipo de coincidencia", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
          ) {
            RuleMatchType.entries.forEach { type ->
              TargetAppChip(
                label = matchTypeLabel(type),
                selected = matchType == type,
                onClick = { matchType = type },
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it.take(300) },
            label = { Text("Palabra, frase o Regex de búsqueda") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          if (patternError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(patternError, style = MaterialTheme.typography.bodySmall, color = DangerRed)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Distinguir mayúsculas / minúsculas", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Switch(
              checked = caseSensitive,
              onCheckedChange = { caseSensitive = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
        }
      }

      // Probador local instantáneo de regla
      item {
        SectionCard {
          Text("Probador rápido de coincidencia", style = MaterialTheme.typography.titleSmall, color = WhatsAppGreen)
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = liveTestInput,
            onValueChange = { liveTestInput = it.take(200) },
            label = { Text("Escribe un mensaje entrante de prueba") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          if (liveTestInput.isNotBlank() && pattern.isNotBlank() && patternError == null) {
            val matched = viewModel.testPatternMatch(liveTestInput, pattern, matchType, caseSensitive)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (matched) "Coincide con esta regla" else "No coincide con esta regla",
              style = MaterialTheme.typography.bodySmall,
              color = if (matched) WhatsAppGreen else DangerRed,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Respuesta automática + variables
      item {
        SectionCard {
          Text("Respuesta automática", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = responseTemplate,
            onValueChange = { responseTemplate = it.take(1500) },
            label = { Text("Mensaje de respuesta") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text("Insertar variables locales:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
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
                  .clickable { responseTemplate = (if (responseTemplate.isBlank()) token else "$responseTemplate $token").take(1500) },
              ) {
                Text(token, color = WhatsAppGreen, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
              }
            }
          }
        }
      }

      // Prioridad, app, límites y retraso
      item {
        SectionCard {
          Text("Prioridad, límites y aplicación", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          RuleStepperRow("Prioridad", priority, onMinus = { priority = (priority - 1).coerceAtLeast(1) }, onPlus = { priority = (priority + 1).coerceAtMost(100) })
          RuleStepperRow("Tiempo mínimo entre respuestas (s)", minIntervalSec, onMinus = { minIntervalSec = (minIntervalSec - 5).coerceAtLeast(0) }, onPlus = { minIntervalSec = (minIntervalSec + 5).coerceAtMost(3600) })
          RuleStepperRow("Máx. respuestas por conversación", maxReplies, onMinus = { maxReplies = (maxReplies - 1).coerceAtLeast(1) }, onPlus = { maxReplies = (maxReplies + 1).coerceAtMost(100) })
          RuleStepperRow("Retraso antes de responder (s)", delaySec, onMinus = { delaySec = (delaySec - 1).coerceAtLeast(0) }, onPlus = { delaySec = (delaySec + 1).coerceAtMost(60) })

          Spacer(modifier = Modifier.height(8.dp))
          Text("Aplicación destino:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TargetAppChip("Ambas", targetApp == AutoReplyTargetApp.BOTH, Modifier.weight(1f)) { targetApp = AutoReplyTargetApp.BOTH }
            TargetAppChip("WhatsApp", targetApp == AutoReplyTargetApp.WHATSAPP_ONLY, Modifier.weight(1f)) { targetApp = AutoReplyTargetApp.WHATSAPP_ONLY }
            TargetAppChip("Business", targetApp == AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY, Modifier.weight(1f)) { targetApp = AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY }
          }
        }
      }

      // Filtro por contactos y horario propio de la regla
      item {
        SectionCard {
          Text("Filtro y horario de esta regla", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
          ) {
            AutoReplyContactFilterMode.entries.forEach { mode ->
              TargetAppChip(filterLabel(mode), filterMode == mode) { filterMode = mode }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Aplicar también en grupos", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Switch(
              checked = allowGroups,
              onCheckedChange = { allowGroups = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          if (filterMode == AutoReplyContactFilterMode.SELECTED_CONTACTS) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = selectedCsv,
              onValueChange = { selectedCsv = it },
              label = { Text("Nombres o números permitidos (separados por coma)") },
              modifier = Modifier.fillMaxWidth(),
              colors = editorFieldColors(),
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = excludedCsv,
            onValueChange = { excludedCsv = it },
            label = { Text("Exclusiones de la regla (opcional, coma)") },
            modifier = Modifier.fillMaxWidth(),
            colors = editorFieldColors(),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Horario específico para esta regla", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Switch(
              checked = scheduleEnabled,
              onCheckedChange = { scheduleEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          if (scheduleEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            ScheduleSelectorBlock(
              startMinutes = scheduleStartMin,
              endMinutes = scheduleEndMin,
              days = scheduleDays,
              onStartChange = { scheduleStartMin = it },
              onEndChange = { scheduleEndMin = it },
              onDaysChange = { scheduleDays = it },
            )
          }
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
        text = if (isEditing) "Guardar cambios de regla" else "Guardar nueva regla",
        icon = Icons.Rounded.Check,
        enabled = canSave,
        onClick = {
          viewModel.saveRule(
            AutoReplyRule(
              id = ruleId,
              name = name.trim(),
              pattern = pattern.trim(),
              matchType = matchType,
              caseSensitive = caseSensitive,
              responseTemplate = responseTemplate.trim(),
              enabled = enabled,
              priority = priority,
              targetApp = targetApp,
              contactFilterMode = filterMode,
              allowGroups = allowGroups,
              selectedContactNamesOrPhones = selectedCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
              excludedIdentifiers = excludedCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
              schedule = AutoReplySchedule(
                enabled = scheduleEnabled,
                startMinutesOfDay = scheduleStartMin,
                endMinutesOfDay = scheduleEndMin,
                daysOfWeek = scheduleDays,
              ),
              minIntervalSeconds = minIntervalSec,
              maxRepliesPerConversation = maxReplies,
              delaySeconds = delaySec,
            ),
          )
          onBack()
        },
      )
    }
  }
}

@Composable
internal fun RuleStepperRow(
  label: String,
  value: Int,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      IconButton(onClick = onMinus) {
        Icon(Icons.Rounded.Remove, contentDescription = "Menos", tint = TextPrimary)
      }
      Text("$value", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      IconButton(onClick = onPlus) {
        Icon(Icons.Rounded.Add, contentDescription = "Más", tint = TextPrimary)
      }
    }
  }
}

@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = CardSurfaceElevated,
  unfocusedContainerColor = CardSurfaceElevated,
  focusedBorderColor = WhatsAppGreen,
  unfocusedBorderColor = OutlineSubtle,
  focusedTextColor = TextPrimary,
  unfocusedTextColor = TextPrimary,
)

