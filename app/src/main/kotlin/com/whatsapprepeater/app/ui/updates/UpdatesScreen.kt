package com.whatsapprepeater.app.ui.updates

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.updater.UpdateManager
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
import com.whatsapprepeater.core.updater.UpdateCheckResult

@Composable
fun UpdatesScreen(
  viewModel: UpdatesViewModel,
  updateManager: UpdateManager,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showRepoDialog by remember { mutableStateOf(false) }
  var editOwner by remember(state.repoOwner) { mutableStateOf(state.repoOwner) }
  var editRepo by remember(state.repoName) { mutableStateOf(state.repoName) }
  var showInstallConfirmDialog by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshInstallPermission()
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
    // Header
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
        Text("Actualizaciones", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Verificación segura desde GitHub Releases", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Versión instalada e identidad fija
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text("Versión actual", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                  text = "v${state.installedApp.versionName} (código ${state.installedApp.versionCode})",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
              }
            }
            StatusBadge(
              label = "Datos protegidos",
              containerColor = WhatsAppGreenContainer,
              contentColor = WhatsAppGreen,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Paquete fijo: ${state.installedApp.applicationId}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          state.installedApp.signingCertSha256?.let { sig ->
            Text(
              text = "Firma SHA-256: ${sig.take(16)}…",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(CardSurfaceElevated)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Origen GitHub", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              Text(
                text = "${state.repoOwner}/${state.repoName}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
            }
            IconButton(onClick = { showRepoDialog = true }) {
              Icon(Icons.Rounded.Edit, contentDescription = "Editar repositorio", tint = WhatsAppGreen)
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          LargePrimaryButton(
            text = if (state.isChecking) "Consultando GitHub..." else "Buscar actualizaciones",
            icon = Icons.Rounded.Refresh,
            enabled = !state.isChecking && !state.isDownloading,
            onClick = viewModel::checkUpdates,
          )
        }
      }

      if (state.isChecking) {
        item {
          SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), color = WhatsAppGreen, strokeWidth = 2.5.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Text("Consultando versiones publicadas en GitHub Releases...", color = TextPrimary)
            }
          }
        }
      }

      if (!state.checkError.isNullOrBlank()) {
        item {
          SectionCard(borderColor = DangerRed, containerColor = DangerContainer) {
            Text("No se pudo consultar", style = MaterialTheme.typography.titleMedium, color = DangerRed)
            Spacer(modifier = Modifier.height(6.dp))
            Text(state.checkError ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          }
        }
      }

      if (!state.incompatibilityAlert.isNullOrBlank()) {
        item {
          SectionCard(borderColor = DangerRed, containerColor = DangerContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = DangerRed)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Actualización incompatible bloqueada", style = MaterialTheme.typography.titleMedium, color = DangerRed)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.incompatibilityAlert ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Tus mensajes, listas, contactos, historial y preferencias permanecen intactos.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }

      when (val result = state.checkResult) {
        is UpdateCheckResult.UpToDate -> {
          item {
            SectionCard(borderColor = WhatsAppGreen) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Tienes la última versión", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                  Text(
                    text = "Versión instalada v${result.currentVersionName} al día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                  )
                }
              }
            }
          }
        }

        is UpdateCheckResult.UpdateAvailable -> {
          val candidate = result.candidate
          item {
            SectionCard(borderColor = WhatsAppGreen) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column {
                  Text("Versión disponible", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                  Text(
                    text = candidate.releaseTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = WhatsAppGreen,
                    fontWeight = FontWeight.Bold,
                  )
                }
                StatusBadge(
                  label = candidate.tagName,
                  containerColor = WhatsAppGreenContainer,
                  contentColor = WhatsAppGreen,
                )
              }

              Spacer(modifier = Modifier.height(10.dp))
              val sizeMb = candidate.apkSizeBytes / (1024.0 * 1024.0)
              Text(
                text = "Archivo: ${candidate.apkFileName} (${"%.1f".format(sizeMb)} MB)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
              val expectedSha = result.resolvedSha256
              if (!expectedSha.isNullOrBlank()) {
                Text(
                  text = "SHA-256 esperado: ${expectedSha.take(24)}…",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }

              Spacer(modifier = Modifier.height(12.dp))
              Text("Novedades", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = candidate.releaseNotes.ifBlank { "Sin notas adicionales en la publicación." },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )

              if (state.isDownloading) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                  progress = { state.downloadProgress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                  color = WhatsAppGreen,
                  trackColor = CardSurfaceElevated,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Descargando y verificando integridad SHA-256 (${(state.downloadProgress * 100).toInt()}%)...",
                  style = MaterialTheme.typography.bodySmall,
                  color = WhatsAppGreen,
                )
              }

              if (!state.downloadError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(state.downloadError ?: "", style = MaterialTheme.typography.bodyMedium, color = DangerRed)
              }

              if (state.downloadedApkFile != null && state.verifiedSha256 != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WhatsAppGreenContainer)
                    .padding(12.dp),
                ) {
                  Icon(Icons.Rounded.Security, contentDescription = null, tint = WhatsAppGreen)
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text("APK verificado con SHA-256 y firma compatible", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                    Text(
                      text = "SHA-256: ${state.verifiedSha256?.take(28)}…",
                      style = MaterialTheme.typography.bodySmall,
                      color = TextPrimary,
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))
              if (state.isDownloading) {
                LargeSecondaryButton(
                  text = "Cancelar descarga",
                  onClick = viewModel::cancelDownload,
                )
              } else if (state.downloadedApkFile == null) {
                LargePrimaryButton(
                  text = "Descargar APK y verificar SHA-256",
                  icon = Icons.Rounded.CloudDownload,
                  enabled = !state.isDownloading,
                  onClick = { viewModel.downloadUpdate(candidate, result.resolvedSha256) },
                )
              } else {
                if (!state.canInstallUnknownApps) {
                  SectionCard(borderColor = WarningAmber, containerColor = WarningContainer) {
                    Text(
                      text = "Autorización de fuente requerida",
                      style = MaterialTheme.typography.titleMedium,
                      color = WarningAmber,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "Android requiere que habilites \"Instalar aplicaciones desconocidas\" para WhatsApp Repeater antes de abrir el instalador oficial. Tus datos no se perderán.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LargeSecondaryButton(
                      text = "Permitir instalar desde esta app",
                      onClick = { updateManager.openUnknownSourcesSettings() },
                    )
                  }
                  Spacer(modifier = Modifier.height(10.dp))
                }
                LargePrimaryButton(
                  text = "Instalar actualización",
                  icon = Icons.Rounded.InstallMobile,
                  enabled = state.canInstallUnknownApps,
                  onClick = { showInstallConfirmDialog = true },
                )
              }
            }
          }
        }

        is UpdateCheckResult.IncompatibleUpdate, null -> Unit
      }

      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = WhatsAppGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Distribución transparente y segura", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Este cliente nunca almacena tokens privados de GitHub. Si el código fuente está en un repositorio privado, publica los APK firmados y su archivo .sha256 en un repositorio público de Releases o actualiza mediante instalación manual verificada.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }
    }
  }

  if (showRepoDialog) {
    AlertDialog(
      onDismissRequest = { showRepoDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Repositorio de GitHub Releases") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editOwner,
            onValueChange = { editOwner = it },
            label = { Text("Usuario / Organización") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = WhatsAppGreen,
              unfocusedBorderColor = OutlineSubtle,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
          )
          OutlinedTextField(
            value = editRepo,
            onValueChange = { editRepo = it },
            label = { Text("Nombre del repositorio") },
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
          onClick = {
            viewModel.updateRepository(editOwner, editRepo)
            showRepoDialog = false
          },
        ) {
          Text("Guardar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showRepoDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  if (showInstallConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showInstallConfirmDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Confirmar instalación de actualización") },
      text = {
        Text(
          "Se abrirá el instalador oficial de Android para actualizar sin desinstalar. Todos tus mensajes guardados, listas, contactos, intervalos e historial se conservarán.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showInstallConfirmDialog = false
            viewModel.launchInstaller()
          },
        ) {
          Text("Abrir instalador", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showInstallConfirmDialog = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

