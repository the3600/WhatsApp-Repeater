package com.whatsapprepeater.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.BuildConfig
import com.whatsapprepeater.app.ui.components.BottomTab
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
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

/**
 * Pantalla 13 (Stitch 13._ajustes_y_respaldo):
 * Preferencias generales, intervalos predeterminados, respaldo AES-256-GCM vía SAF y almacenamiento.
 */
@Composable
fun SettingsBackupScreen(
  viewModel: SettingsBackupViewModel,
  onOpenUpdates: () -> Unit,
  onOpenPermissions: () -> Unit,
  onSelectBottomTab: (BottomTab) -> Unit,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
  var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
  var passphraseInput by remember { mutableStateOf("") }
  var showClearHistoryDialog by remember { mutableStateOf(false) }
  var showFactoryResetDialog by remember { mutableStateOf(false) }

  val createBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) {
      passphraseInput = ""
      pendingExportUri = uri
    }
  }

  val openBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      passphraseInput = ""
      pendingImportUri = uri
    }
  }

  LaunchedEffect(Unit) {
    viewModel.refreshStorageStats(context)
  }

  Scaffold(
    containerColor = BackgroundBlack,
    topBar = {
      StitchMainTopBar(
        title = "Ajustes del Sistema",
        isAccessibilityReady = state.isAccessibilityReady,
        isAutomationRunning = state.activeAutomationState.isRunning || state.activeAutomationState.isPaused,
        onOpenPermissions = onOpenPermissions,
      )
    },
    bottomBar = {
      WhatsAppRepeaterBottomBar(
        currentTab = BottomTab.SETTINGS,
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
      if (!state.feedbackMessage.isNullOrBlank()) {
        item {
          SectionCard(
            borderColor = if (state.isErrorFeedback) DangerRed else WhatsAppGreen,
            containerColor = if (state.isErrorFeedback) DangerContainer else WhatsAppGreenContainer,
          ) {
            Text(
              text = state.feedbackMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
            )
          }
        }
      }

      // PREFERENCIAS GENERALES
      item {
        Text("PREFERENCIAS GENERALES", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
      item {
        SectionCard {
          PreferenceInfoRow(
            icon = Icons.Rounded.DarkMode,
            title = "Tema de la aplicación",
            subtitle = "Modo oscuro OLED activo",
            trailing = { StatusBadge("Activo", WhatsAppGreenContainer, WhatsAppGreen) },
          )
          Spacer(modifier = Modifier.height(12.dp))
          PreferenceSwitchRow(
            icon = Icons.Rounded.Vibration,
            title = "Vibración háptica",
            subtitle = "Feedback táctil al confirmar detención de emergencia",
            checked = state.settings.hapticFeedbackEnabled,
            onCheckedChange = viewModel::setHapticFeedback,
          )
          Spacer(modifier = Modifier.height(12.dp))
          PreferenceSwitchRow(
            icon = Icons.Rounded.LightMode,
            title = "Mantener pantalla encendida",
            subtitle = "Evita que la pantalla se apague durante la automatización",
            checked = state.settings.keepScreenOnDuringAutomation,
            onCheckedChange = viewModel::setKeepScreenOn,
          )
          Spacer(modifier = Modifier.height(12.dp))
          PreferenceSwitchRow(
            icon = Icons.Rounded.BusinessCenter,
            title = "Preferir WhatsApp Business",
            subtitle = "Usar com.whatsapp.w4b como aplicación por defecto",
            checked = state.settings.preferWhatsAppBusiness,
            onCheckedChange = viewModel::setPreferWhatsAppBusiness,
          )
        }
      }

      // INTERVALOS PREDETERMINADOS
      item {
        Text("INTERVALOS PREDETERMINADOS", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
      item {
        SectionCard {
          IntervalDefaultRow(
            title = "Pausa mínima base",
            subtitle = "Tiempo mínimo antes del siguiente envío",
            seconds = state.settings.defaultMinIntervalSeconds,
            onDecrease = { viewModel.adjustDefaultMinInterval(-1) },
            onIncrease = { viewModel.adjustDefaultMinInterval(+1) },
          )
          Spacer(modifier = Modifier.height(12.dp))
          IntervalDefaultRow(
            title = "Pausa máxima base",
            subtitle = "Margen superior del intervalo aleatorio",
            seconds = state.settings.defaultMaxIntervalSeconds,
            onDecrease = { viewModel.adjustDefaultMaxInterval(-1) },
            onIncrease = { viewModel.adjustDefaultMaxInterval(+1) },
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(CardSurfaceElevated)
              .padding(10.dp),
          ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Valores base prudentes conservados entre actualizaciones mediante DataStore.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }

      // RESPALDO Y RESTAURACIÓN LOCAL
      item {
        Text("RESPALDO Y RESTAURACIÓN LOCAL", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Exporta e importa tus listas, mensajes, contactos de referencia, historial y preferencias en un archivo .json cifrado con AES-256-GCM y verificación SHA-256.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          LargePrimaryButton(
            text = "Exportar copia de seguridad (.json cifrado)",
            icon = Icons.Rounded.Download,
            onClick = { createBackupLauncher.launch("whatsapp-repeater-backup.json") },
          )
          Spacer(modifier = Modifier.height(10.dp))
          LargeSecondaryButton(
            text = "Restaurar desde archivo local",
            icon = Icons.Rounded.UploadFile,
            onClick = { openBackupLauncher.launch(arrayOf("application/json", "*/*")) },
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(CardSurfaceElevated)
              .padding(12.dp),
          ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = if (state.preMigrationBackupsCount > 0) {
                "Respaldos automáticos pre-migración guardados: ${state.preMigrationBackupsCount}"
              } else {
                "Los respaldos pre-migración se crean automáticamente al actualizar el esquema Room."
              },
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }

      // ZONA DE SEGURIDAD Y ALMACENAMIENTO
      item {
        Text("ZONA DE SEGURIDAD Y ALMACENAMIENTO", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
      item {
        SectionCard {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StorageBox(
              title = "Base Room / SQLite",
              value = "${(state.dbSizeBytes / 1024.0).coerceAtLeast(0.0).toInt()} KB",
              note = "Normal • Migraciones OK",
              modifier = Modifier.weight(1f),
            )
            StorageBox(
              title = "Caché descargas APK",
              value = "${(state.updateCacheBytes / 1024.0).toInt()} KB",
              note = if (state.updateCacheBytes == 0L) "Limpio" else "Toca para limpiar",
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.clearUpdateCache(context) },
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          ActionRowItem(
            icon = Icons.Rounded.AccessibilityNew,
            title = "Permisos y Servicio de Accesibilidad",
            tint = WhatsAppGreen,
            onClick = onOpenPermissions,
          )
          ActionRowItem(
            icon = Icons.Rounded.SystemUpdate,
            title = "Actualizaciones de la aplicación (v${BuildConfig.VERSION_NAME})",
            tint = WhatsAppGreen,
            onClick = onOpenUpdates,
          )
          ActionRowItem(
            icon = Icons.Rounded.DeleteSweep,
            title = "Borrar registros de historial",
            tint = TextPrimary,
            onClick = { showClearHistoryDialog = true },
          )
          ActionRowItem(
            icon = Icons.Rounded.RestartAlt,
            title = "Restablecer configuración de fábrica",
            tint = DangerCoral,
            onClick = { showFactoryResetDialog = true },
          )
        }
      }

      item {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        ) {
          Text(
            text = "WhatsApp Repeater v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
          )
          Text(
            text = "${BuildConfig.APPLICATION_ID} • Jetpack Compose Material 3",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }
    }
  }

  // Diálogo de contraseña para exportar copia cifrada
  val exportTarget = pendingExportUri
  if (exportTarget != null) {
    AlertDialog(
      onDismissRequest = { pendingExportUri = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Cifrar copia de seguridad") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Introduce una clave (mínimo 4 caracteres) para cifrar con AES-256-GCM:")
          OutlinedTextField(
            value = passphraseInput,
            onValueChange = { passphraseInput = it },
            label = { Text("Clave de cifrado") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = WhatsAppGreen,
              unfocusedBorderColor = OutlineSubtle,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = passphraseInput.length >= 4,
          onClick = {
            viewModel.exportEncryptedBackup(context, exportTarget, passphraseInput)
            pendingExportUri = null
          },
        ) {
          Text("Exportar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingExportUri = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  // Diálogo de contraseña para restaurar copia cifrada
  val importTarget = pendingImportUri
  if (importTarget != null) {
    AlertDialog(
      onDismissRequest = { pendingImportUri = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Descifrar y restaurar copia") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Introduce la clave con la que fue cifrado el respaldo. Se verificará el SHA-256 antes de restaurar:")
          OutlinedTextField(
            value = passphraseInput,
            onValueChange = { passphraseInput = it },
            label = { Text("Clave del respaldo") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = WhatsAppGreen,
              unfocusedBorderColor = OutlineSubtle,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = passphraseInput.length >= 4,
          onClick = {
            viewModel.importEncryptedBackup(context, importTarget, passphraseInput)
            pendingImportUri = null
          },
        ) {
          Text("Restaurar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingImportUri = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  if (showClearHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showClearHistoryDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Borrar historial") },
      text = { Text("¿Eliminar todos los registros de ejecución almacenados en Room?") },
      confirmButton = {
        TextButton(
          onClick = {
            showClearHistoryDialog = false
            viewModel.clearHistory(context)
          },
        ) {
          Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearHistoryDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  if (showFactoryResetDialog) {
    AlertDialog(
      onDismissRequest = { showFactoryResetDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Restablecer valores de fábrica") },
      text = { Text("Se borrará el historial local y se restaurarán los intervalos y preferencias por defecto.") },
      confirmButton = {
        TextButton(
          onClick = {
            showFactoryResetDialog = false
            viewModel.factoryReset(context)
          },
        ) {
          Text("Restablecer", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showFactoryResetDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun PreferenceInfoRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  trailing: @Composable () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(CardSurfaceElevated),
    ) {
      Icon(icon, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
    trailing()
  }
}

@Composable
private fun PreferenceSwitchRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  PreferenceInfoRow(icon = icon, title = title, subtitle = subtitle) {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
    )
  }
}

@Composable
private fun IntervalDefaultRow(
  title: String,
  subtitle: String,
  seconds: Int,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Surface(shape = CircleShape, color = CardSurfaceElevated, modifier = Modifier.size(36.dp)) {
        IconButton(onClick = onDecrease) {
          Icon(Icons.Rounded.Remove, contentDescription = "Menos", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
      }
      Text("${seconds}s", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      Surface(shape = CircleShape, color = CardSurfaceElevated, modifier = Modifier.size(36.dp)) {
        IconButton(onClick = onIncrease) {
          Icon(Icons.Rounded.Add, contentDescription = "Más", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}

@Composable
private fun StorageBox(
  title: String,
  value: String,
  note: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(CardSurfaceElevated)
      .padding(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Spacer(modifier = Modifier.height(4.dp))
    Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
    Text(note, style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
  }
}

@Composable
private fun ActionRowItem(
  icon: ImageVector,
  title: String,
  tint: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = CardSurfaceElevated,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Text(title, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.weight(1f))
      Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
    }
  }
}

