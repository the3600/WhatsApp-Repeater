package com.whatsapprepeater.app.ui.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.app.ui.components.LargeDangerButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityPrivacyScreen(
  viewModel: SecurityPrivacyViewModel,
  onBack: () -> Unit,
  onOpenSettingsBackup: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  onOpenPermissions: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showClearHistoryConfirm by remember { mutableStateOf(false) }
  var showFactoryResetConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    viewModel.runSecurityAudit(context)
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.runSecurityAudit(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val verifiedTimeText = remember(state.lastVerifiedAtMillis) {
    if (state.lastVerifiedAtMillis <= 0L) {
      "Pendiente"
    } else {
      SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(state.lastVerifiedAtMillis))
    }
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
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
          Text("Centro de Seguridad y Privacidad", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          Text("Estado real de cifrado, firma, permisos y control local", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.feedbackMessage.isNullOrBlank()) {
        item {
          SectionCard(
            borderColor = if (state.isErrorFeedback) DangerRed else WhatsAppGreen,
            containerColor = if (state.isErrorFeedback) DangerContainer else WhatsAppGreenContainer,
          ) {
            Text(state.feedbackMessage ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          }
        }
      }

      // 1. Estado criptográfico y firma
      item {
        SectionCard(
          borderColor = if (state.keystoreHealthy) WhatsAppGreen.copy(alpha = 0.6f) else WarningAmber,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Integridad criptográfica local", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            StatusBadge(
              label = if (state.keystoreHealthy) "Protegido" else "Revisar",
              containerColor = if (state.keystoreHealthy) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (state.keystoreHealthy) WhatsAppGreen else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          SecurityDetailTile(
            icon = Icons.Rounded.Key,
            title = "Android Keystore (AES-256-GCM)",
            subtitle = state.keystoreDetail,
            ok = state.keystoreHealthy,
          )
          Spacer(modifier = Modifier.height(8.dp))
          SecurityDetailTile(
            icon = Icons.Rounded.Shield,
            title = if (state.matchesPermanentProductionCert) {
              "Firma permanente de producción verificada"
            } else {
              "Firma local / depuración detectada"
            },
            subtitle = "${state.applicationId} • ${state.appVersionLabel}",
            ok = state.matchesPermanentProductionCert,
          )
          if (state.installedCertSha256.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurfaceElevated)
                .clickable {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                  cb?.setPrimaryClip(ClipData.newPlainText("SHA-256 Certificado", state.installedCertSha256))
                  Toast.makeText(context, "Huella SHA-256 copiada", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Certificado SHA-256 instalado", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                  text = state.installedCertSha256,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace,
                  color = TextPrimary,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar huella", tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Última verificación en vivo: $verifiedTimeText",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          LargePrimaryButton(
            text = if (state.isAuditing) "Verificando integridad..." else "Verificar integridad ahora",
            icon = Icons.Rounded.Refresh,
            enabled = !state.isAuditing,
            onClick = { viewModel.runSecurityAudit(context) },
          )
        }
      }

      // 2. Política de cifrado, privacidad y red
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, tint = WhatsAppGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cifrado, privacidad y red", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.height(10.dp))
          PrivacyPolicyBullet(
            title = "Procesamiento 100% local (sin telemetría)",
            body = "Tus mensajes, contactos, reglas, programaciones e historial permanecen únicamente en tu teléfono. No existe ningún servidor analítico ni de seguimiento.",
          )
          Spacer(modifier = Modifier.height(8.dp))
          PrivacyPolicyBullet(
            title = "Respaldos cifrados con AES-256-GCM + SHA-256",
            body = "Las copias manuales derivan la clave mediante PBKDF2-HMAC-SHA256 (65,536 iteraciones) y verifican integridad antes de restaurar.",
          )
          Spacer(modifier = Modifier.height(8.dp))
          PrivacyPolicyBullet(
            title = "Respaldo en nube Android restringido a cifrado E2E",
            body = "data_extraction_rules exige disableIfNoEncryptionCapabilities=\"true\" para cualquier respaldo del sistema.",
          )
          Spacer(modifier = Modifier.height(8.dp))
          PrivacyPolicyBullet(
            title = "Única conexión externa opcional: GitHub Releases (HTTPS)",
            body = "Solo se consulta bajo demanda al buscar actualizaciones. Las descargas exigen HTTPS de GitHub, coincidencia de SHA-256 y firma permanente idéntica.",
          )
          Spacer(modifier = Modifier.height(8.dp))
          PrivacyPolicyBullet(
            title = "Cero credenciales de WhatsApp",
            body = "La app jamás solicita contraseñas, códigos SMS, cookies ni sesiones de WhatsApp y nunca evade verificaciones ni CAPTCHAs.",
          )
        }
      }

      // 3. Permisos sensibles
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Lock, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Permisos activos", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            StatusBadge(
              label = "${state.grantedPermissionsCount}/${state.permissions.size} activos",
              containerColor = WhatsAppGreenContainer,
              contentColor = WhatsAppGreen,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          state.permissions.forEach { perm ->
            Row(
              verticalAlignment = Alignment.Top,
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            ) {
              Icon(
                imageVector = if (perm.isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (perm.isGranted) WhatsAppGreen else WarningAmber,
                modifier = Modifier
                  .padding(top = 2.dp)
                  .size(18.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(perm.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(perm.scopeDetail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          LargeSecondaryButton(
            text = "Gestionar accesibilidad y permisos",
            icon = Icons.Rounded.PrivacyTip,
            onClick = onOpenPermissions,
          )
          Spacer(modifier = Modifier.height(8.dp))
          LargeSecondaryButton(
            text = "Abrir ajustes del sistema para esta app",
            icon = Icons.Rounded.SettingsApplications,
            onClick = { AccessibilitySettingsHelper.openAppDetailsSettings(context) },
          )
        }
      }

      // 4. Almacenamiento local y retención
      item {
        SectionCard {
          Text("Almacenamiento y datos guardados", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Base Room local: ${state.dbSizeKb} KB • Caché APK: ${state.updateCacheKb} KB • Respaldos pre-migración: ${state.preMigrationBackupsCount}",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Plantillas: ${state.savedTemplatesCount} • Programaciones: ${state.schedulesCount} • Historial Motor 1: ${state.motor1HistoryCount} • Historial Motor 2: ${state.motor2HistoryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          LargeSecondaryButton(
            text = "Exportar o restaurar respaldo cifrado",
            icon = Icons.Rounded.EnhancedEncryption,
            onClick = onOpenSettingsBackup,
          )
          Spacer(modifier = Modifier.height(8.dp))
          LargeSecondaryButton(
            text = "Abrir diagnóstico técnico completo",
            icon = Icons.Rounded.HealthAndSafety,
            onClick = onOpenDiagnostics,
          )
          if (state.updateCacheKb > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            LargeSecondaryButton(
              text = "Limpiar caché de descargas (${state.updateCacheKb} KB)",
              icon = Icons.Rounded.DeleteSweep,
              onClick = { viewModel.clearUpdateCache(context) },
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          LargeSecondaryButton(
            text = "Borrar historiales de envíos y autorespuestas",
            icon = Icons.Rounded.DeleteSweep,
            onClick = { showClearHistoryConfirm = true },
          )
          Spacer(modifier = Modifier.height(8.dp))
          LargeDangerButton(
            text = "Restablecer todos los datos locales",
            icon = Icons.Rounded.RestartAlt,
            onClick = { showFactoryResetConfirm = true },
          )
        }
      }
    }
  }

  if (showClearHistoryConfirm) {
    AlertDialog(
      onDismissRequest = { showClearHistoryConfirm = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("¿Eliminar historiales locales?") },
      text = {
        Text("Se eliminarán de forma permanente los ${state.motor1HistoryCount} registros de Motor 1 y los ${state.motor2HistoryCount} registros de Motor 2. Tus plantillas y programaciones se conservarán.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showClearHistoryConfirm = false
            viewModel.clearAllHistories(context)
          },
        ) {
          Text("Eliminar historiales", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearHistoryConfirm = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  if (showFactoryResetConfirm) {
    AlertDialog(
      onDismissRequest = { showFactoryResetConfirm = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Confirmar restablecimiento total") },
      text = {
        Text("Esta acción eliminará el historial local, reglas de autorespuesta, programaciones, borrador activo, caché temporal y restaurará tus preferencias a los valores predeterminados. No afecta a WhatsApp ni a tus chats.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showFactoryResetConfirm = false
            viewModel.factoryReset(context)
          },
        ) {
          Text("Restablecer todo", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showFactoryResetConfirm = false }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun SecurityDetailTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  ok: Boolean,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = CardSurfaceElevated,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(12.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(if (ok) WhatsAppGreenContainer else WarningContainer),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (ok) WhatsAppGreen else WarningAmber,
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }
  }
}

@Composable
private fun PrivacyPolicyBullet(
  title: String,
  body: String,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(2.dp))
    Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
  }
}
