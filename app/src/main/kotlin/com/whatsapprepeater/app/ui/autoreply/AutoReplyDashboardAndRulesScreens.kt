package com.whatsapprepeater.app.ui.autoreply

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.BottomTab
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.components.StitchMainTopBar
import com.whatsapprepeater.app.ui.components.WhatsAppRepeaterBottomBar
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerCoral
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OnWhatsAppGreen
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.autoresponder.ScheduleEvaluator
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.RuleMatchType

/**
 * Pantalla 1 de AUTORESPUESTAS: Panel principal del autorespondedor.
 */
@Composable
fun AutoReplyDashboardScreen(
  viewModel: AutoReplyViewModel,
  onOpenRules: () -> Unit,
  onCreateRule: () -> Unit,
  onOpenContactFilters: () -> Unit,
  onOpenSchedule: () -> Unit,
  onOpenWelcome: () -> Unit,
  onOpenAway: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenAutoReplyPermissions: () -> Unit,
  onOpenAccessibilityPermissions: () -> Unit,
  onSelectBottomTab: (BottomTab) -> Unit,
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

  val serviceReady = state.isNotificationAccessEnabled || state.runtime.isListenerConnected
  val isActivelyResponding = state.settings.masterEnabled && serviceReady

  Scaffold(
    containerColor = BackgroundBlack,
    topBar = {
      StitchMainTopBar(
        title = "Autorespuestas",
        isAccessibilityReady = state.isAccessibilityReady,
        isAutomationRunning = state.isAutomationRunning,
        onOpenPermissions = onOpenAccessibilityPermissions,
      )
    },
    bottomBar = {
      WhatsAppRepeaterBottomBar(
        currentTab = BottomTab.AUTORESPONDER,
        onSelectTab = onSelectBottomTab,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Estado maestro del Motor 2 + Parada de emergencia
      item {
        SectionCard(
          borderColor = if (isActivelyResponding) WhatsAppGreen else if (!serviceReady) WarningAmber else OutlineSubtle,
        ) {
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
                  .background(if (isActivelyResponding) WhatsAppGreenContainer else CardSurfaceElevated),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoMode,
                  contentDescription = null,
                  tint = if (isActivelyResponding) WhatsAppGreen else TextSecondary,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Motor 2: Autorespuestas", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                  text = when {
                    !serviceReady -> "Acceso a notificaciones pendiente"
                    isActivelyResponding -> "Activo por RemoteInput (sin IA externa)"
                    else -> "Pausado manualmente"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = if (isActivelyResponding) WhatsAppGreen else TextSecondary,
                )
              }
            }
            Switch(
              checked = state.settings.masterEnabled,
              onCheckedChange = { wantEnable ->
                if (wantEnable && !serviceReady) {
                  onOpenAutoReplyPermissions()
                } else {
                  viewModel.setMasterEnabled(wantEnable)
                }
              },
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("Reglas activas", "${state.rules.count { it.enabled }}/${state.rules.size}", WhatsAppGreen, Modifier.weight(1f))
            StatPill("Enviadas hoy", "${state.runtime.sentThisSession}", TextPrimary, Modifier.weight(1f))
            StatPill("Omitidas", "${state.runtime.skippedThisSession}", TextSecondary, Modifier.weight(1f))
          }

          if (state.runtime.lastActionSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Último evento: ${state.runtime.lastActionSummary}",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          if (state.settings.masterEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = viewModel::emergencyStop,
              colors = ButtonDefaults.buttonColors(containerColor = DangerCoral, contentColor = Color(0xFF4A0A0A)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            ) {
              Icon(Icons.Rounded.StopCircle, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("PARADA DE EMERGENCIA AUTORESPUESTAS", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      if (!serviceReady || !state.isIgnoringBatteryOptimizations) {
        item {
          SectionCard(borderColor = WarningAmber, containerColor = WarningContainer.copy(alpha = 0.7f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = WarningAmber)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Configuración de permisos y POCO / HyperOS", style = MaterialTheme.typography.titleSmall, color = WarningAmber, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (!serviceReady) {
                "Debes autorizar el Acceso a Notificaciones en Android para leer y responder mensajes entrantes vía RemoteInput."
              } else {
                "En POCO X6 Pro / HyperOS 3, desactivar la restricción de batería y activar Autostart evita que el sistema cierre el servicio."
              },
              style = MaterialTheme.typography.bodySmall,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Configurar permisos y HyperOS",
              onClick = onOpenAutoReplyPermissions,
            )
          }
        }
      }

      // Acciones principales
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          LargePrimaryButton(
            text = "Nueva regla",
            icon = Icons.Rounded.Add,
            onClick = onCreateRule,
            modifier = Modifier.weight(1f),
          )
          LargeSecondaryButton(
            text = "Ver reglas (${state.rules.size})",
            icon = Icons.AutoMirrored.Rounded.Rule,
            onClick = onOpenRules,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Menú completo de las 10 vistas de Autorespuestas
      item {
        Text("CONFIGURACIÓN DEL AUTORESPONDEDOR", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
      item {
        SectionCard {
          DashboardNavRow(
            icon = Icons.AutoMirrored.Rounded.Rule,
            title = "Lista y prioridad de reglas",
            subtitle = "${state.rules.count { it.enabled }} activas • Coincidencia exacta, palabra, frase y Regex",
            onClick = onOpenRules,
          )
          DashboardNavRow(
            icon = Icons.Rounded.FilterList,
            title = "Filtros de contactos y grupos",
            subtitle = "Modo: ${filterLabel(state.settings.globalContactFilterMode)} • ${state.settings.excludedIdentifiers.size} excluidos",
            onClick = onOpenContactFilters,
          )
          DashboardNavRow(
            icon = Icons.Rounded.Schedule,
            title = "Horarios y límites de envío",
            subtitle = if (state.settings.globalSchedule.enabled) {
              "${ScheduleEvaluator.formatMinutes(state.settings.globalSchedule.startMinutesOfDay)} - ${ScheduleEvaluator.formatMinutes(state.settings.globalSchedule.endMinutesOfDay)}"
            } else {
              "24/7 • Espera ${state.settings.minIntervalSeconds}s • Máx ${state.settings.maxRepliesPerConversation}/chat"
            },
            onClick = onOpenSchedule,
          )
          DashboardNavRow(
            icon = Icons.Rounded.WavingHand,
            title = "Mensajes de bienvenida",
            subtitle = if (state.settings.welcomeEnabled) "Activo (cada ${state.settings.welcomeCooldownMinutes / 60}h por chat)" else "Desactivado",
            onClick = onOpenWelcome,
          )
          DashboardNavRow(
            icon = Icons.Rounded.NightsStay,
            title = "Mensajes de ausencia",
            subtitle = if (state.settings.awayEnabled) "Activo según horario configurado" else "Desactivado",
            onClick = onOpenAway,
          )
          DashboardNavRow(
            icon = Icons.Rounded.History,
            title = "Historial de respuestas",
            subtitle = if (state.settings.saveHistoryEnabled) "${state.history.size} registros locales" else "Desactivado (privacidad estricta)",
            onClick = onOpenHistory,
          )
          DashboardNavRow(
            icon = Icons.Rounded.Security,
            title = "Permisos y guía POCO X6 Pro / HyperOS",
            subtitle = if (serviceReady) "Acceso a notificaciones conectado" else "Pendiente habilitar en Android",
            onClick = onOpenAutoReplyPermissions,
          )
        }
      }

      // Aplicación objetivo y protección antibucle
      item {
        SectionCard {
          Text("App objetivo y antibucle", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TargetAppChip("Ambas", state.settings.targetApp == AutoReplyTargetApp.BOTH, Modifier.weight(1f)) {
              viewModel.updateTargetApp(AutoReplyTargetApp.BOTH)
            }
            TargetAppChip("WhatsApp", state.settings.targetApp == AutoReplyTargetApp.WHATSAPP_ONLY, Modifier.weight(1f)) {
              viewModel.updateTargetApp(AutoReplyTargetApp.WHATSAPP_ONLY)
            }
            TargetAppChip("Business", state.settings.targetApp == AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY, Modifier.weight(1f)) {
              viewModel.updateTargetApp(AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY)
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Prevención de bucles", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Evita ciclos infinitos entre autorespondedores y ecos.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = state.settings.preventLoopsEnabled,
              onCheckedChange = viewModel::updateLoopProtection,
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
        }
      }

      // Limitaciones técnicas reales de Android RemoteInput
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text("Transparencia y límites de RemoteInput", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "• Cada notificación con RemoteInput admite una respuesta única; no se envían ráfagas múltiples desde una misma notificación.\n• Si WhatsApp agrupa varios chats en una tarjeta resumen sin RemoteInput, Android no permite responder hasta que llegue una notificación individual.\n• La pantalla se evalúa 100 % local sin IA de pago ni servidores.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Pantalla 2 de AUTORESPUESTAS: Lista de reglas locales con prioridad y activación rápida.
 */
@Composable
fun AutoReplyRulesScreen(
  viewModel: AutoReplyViewModel,
  onBack: () -> Unit,
  onCreateRule: () -> Unit,
  onEditRule: (Long) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var query by remember { mutableStateOf("") }
  var deleteTarget by remember { mutableStateOf<AutoReplyRule?>(null) }

  val filtered = remember(state.rules, query) {
    val clean = query.trim().lowercase()
    if (clean.isEmpty()) {
      state.rules
    } else {
      state.rules.filter {
        it.name.lowercase().contains(clean) ||
          it.pattern.lowercase().contains(clean) ||
          it.responseTemplate.lowercase().contains(clean)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    AutoReplyHeader(
      title = "Reglas de Autorespuesta",
      subtitle = "Ordenadas de mayor a menor prioridad",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Buscar por nombre, palabra clave o respuesta...", color = TextSecondary) },
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

      if (filtered.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Rounded.Rule,
            title = if (state.rules.isEmpty()) "Aún no tienes reglas creadas" else "Sin reglas coincidentes",
            description = "Crea tu primera regla con coincidencia exacta, palabra, frase, varias palabras o Regex.",
            actionButtonText = if (state.rules.isEmpty()) "Crear primera regla" else null,
            onActionClick = if (state.rules.isEmpty()) onCreateRule else null,
          )
        }
      } else {
        items(filtered, key = { it.id }) { rule ->
          SectionCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  StatusBadge(
                    label = "P${rule.priority}",
                    containerColor = WhatsAppGreenContainer,
                    contentColor = WhatsAppGreen,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "${matchTypeLabel(rule.matchType)}: \"${rule.pattern}\"",
                  style = MaterialTheme.typography.bodySmall,
                  color = WhatsAppGreen,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Switch(
                checked = rule.enabled,
                onCheckedChange = { viewModel.setRuleEnabled(rule.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Respuesta: ${rule.responseTemplate}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Espera ${rule.minIntervalSeconds}s • Máx ${rule.maxRepliesPerConversation}/chat",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
              Row {
                IconButton(onClick = { onEditRule(rule.id) }) {
                  Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = WhatsAppGreen)
                }
                IconButton(onClick = { deleteTarget = rule }) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = DangerRed)
                }
              }
            }
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
        text = "Crear nueva regla",
        icon = Icons.Rounded.Add,
        onClick = onCreateRule,
      )
    }
  }

  val deleting = deleteTarget
  if (deleting != null) {
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Eliminar regla") },
      text = { Text("¿Eliminar permanentemente la regla \"${deleting.name}\"?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteRule(deleting.id)
            deleteTarget = null
          },
        ) {
          Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
internal fun AutoReplyHeader(
  title: String,
  subtitle: String,
  onBack: () -> Unit,
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
        .size(42.dp)
        .clip(CircleShape)
        .background(CardSurfaceElevated),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun StatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(CardSurfaceElevated)
      .padding(vertical = 8.dp, horizontal = 6.dp),
  ) {
    Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun DashboardNavRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(CardSurfaceElevated)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
  }
}

@Composable
internal fun TargetAppChip(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
    border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) WhatsAppGreen else TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

internal fun matchTypeLabel(type: RuleMatchType): String = when (type) {
  RuleMatchType.EXACT -> "Exacto"
  RuleMatchType.CONTAINS_WORD -> "Contiene palabra"
  RuleMatchType.CONTAINS_PHRASE -> "Contiene frase"
  RuleMatchType.CONTAINS_ALL_WORDS -> "Varias palabras"
  RuleMatchType.REGEX -> "Regex"
}

internal fun filterLabel(mode: com.whatsapprepeater.core.model.AutoReplyContactFilterMode): String = when (mode) {
  com.whatsapprepeater.core.model.AutoReplyContactFilterMode.ALL -> "Todos"
  com.whatsapprepeater.core.model.AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY -> "Solo guardados"
  com.whatsapprepeater.core.model.AutoReplyContactFilterMode.UNKNOWN_NUMBERS_ONLY -> "Desconocidos"
  com.whatsapprepeater.core.model.AutoReplyContactFilterMode.SELECTED_CONTACTS -> "Seleccionados"
  com.whatsapprepeater.core.model.AutoReplyContactFilterMode.GROUPS_ONLY -> "Solo grupos"
}

