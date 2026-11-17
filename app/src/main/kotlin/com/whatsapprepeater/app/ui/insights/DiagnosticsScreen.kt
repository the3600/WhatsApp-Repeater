package com.whatsapprepeater.app.ui.insights

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.DiagnosticActionType
import com.whatsapprepeater.core.model.DiagnosticItem
import com.whatsapprepeater.core.model.DiagnosticStatus

@Composable
fun DiagnosticsScreen(
  viewModel: InsightsViewModel,
  onBack: () -> Unit,
  onOpenAccessibility: () -> Unit,
  onOpenAutoReplyPermissions: () -> Unit,
  onOpenUpdates: () -> Unit,
  onOpenEditor: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val items by viewModel.diagnostics.collectAsStateWithLifecycle()
  val isXiaomi by viewModel.isXiaomiHyperOsDevice.collectAsStateWithLifecycle()

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    viewModel.runDiagnostics(context)
  }

  LaunchedEffect(Unit) {
    viewModel.runDiagnostics(context)
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.runDiagnostics(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val issuesCount = items.count { it.status != DiagnosticStatus.OK }

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
          Text("Diagnóstico del Sistema", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          Text("Verificación real de permisos, servicios y equipo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
      }
      StatusBadge(
        label = if (issuesCount == 0) "Todo OK" else "$issuesCount avisos",
        containerColor = if (issuesCount == 0) WhatsAppGreenContainer else WarningContainer,
        contentColor = if (issuesCount == 0) WhatsAppGreen else WarningAmber,
      )
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        LargePrimaryButton(
          text = "Ejecutar comprobación completa",
          icon = Icons.Rounded.Refresh,
          onClick = { viewModel.runDiagnostics(context) },
        )
      }

      if (isXiaomi) {
        item {
          SectionCard(borderColor = WhatsAppGreen.copy(alpha = 0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Guía rápida Xiaomi / POCO / HyperOS 3", style = MaterialTheme.typography.titleSmall, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Para evitar cierres o revocación de servicios en POCO X6 Pro / HyperOS:\n1. Activa 'Inicio automático' para WhatsApp Repeater.\n2. Selecciona 'Sin restricciones' en Ahorro de batería.\n3. Bloquea la app con el candado en la vista de Recientes.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }

      items(items, key = { it.id }) { check ->
        DiagnosticCheckCard(
          item = check,
          onAction = {
            when (check.actionType) {
              DiagnosticActionType.OPEN_ACCESSIBILITY -> onOpenAccessibility()
              DiagnosticActionType.OPEN_NOTIFICATION_LISTENER -> onOpenAutoReplyPermissions()
              DiagnosticActionType.REQUEST_POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
              }
              DiagnosticActionType.REQUEST_CONTACTS -> {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
              }
              DiagnosticActionType.OPEN_BATTERY_SETTINGS -> {
                val batteryIntent = Intent(
                  Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = runCatching { context.startActivity(batteryIntent); true }.getOrDefault(false)
                if (!opened) AccessibilitySettingsHelper.openAppDetailsSettings(context)
              }
              DiagnosticActionType.OPEN_UNKNOWN_SOURCES -> {
                val pkgIntent = Intent(
                  Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                  Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(pkgIntent) }
              }
              DiagnosticActionType.OPEN_UPDATES -> onOpenUpdates()
              DiagnosticActionType.OPEN_EDITOR -> onOpenEditor()
              DiagnosticActionType.NONE -> Unit
            }
          },
        )
      }
    }
  }
}

@Composable
private fun DiagnosticCheckCard(
  item: DiagnosticItem,
  onAction: () -> Unit,
) {
  val (icon, tint, borderColor) = when (item.status) {
    DiagnosticStatus.OK -> Triple(Icons.Rounded.CheckCircle, WhatsAppGreen, WhatsAppGreen.copy(alpha = 0.5f))
    DiagnosticStatus.WARNING -> Triple(Icons.Rounded.WarningAmber, WarningAmber, WarningAmber.copy(alpha = 0.6f))
    DiagnosticStatus.ACTION_REQUIRED -> Triple(Icons.Rounded.ErrorOutline, DangerRed, DangerRed.copy(alpha = 0.7f))
  }
  val containerBg = when (item.status) {
    DiagnosticStatus.OK -> null
    DiagnosticStatus.WARNING -> WarningContainer.copy(alpha = 0.45f)
    DiagnosticStatus.ACTION_REQUIRED -> DangerContainer.copy(alpha = 0.45f)
  }

  SectionCard(
    borderColor = borderColor,
    containerColor = containerBg ?: com.whatsapprepeater.app.ui.theme.CardSurface,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = tint)
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }
    if (!item.recommendation.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(item.recommendation ?: "", style = MaterialTheme.typography.bodySmall, color = tint)
    }
    if (item.actionType != DiagnosticActionType.NONE && !item.actionLabel.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(10.dp))
      LargeSecondaryButton(
        text = item.actionLabel ?: "Corregir",
        onClick = onAction,
      )
    }
  }
}
