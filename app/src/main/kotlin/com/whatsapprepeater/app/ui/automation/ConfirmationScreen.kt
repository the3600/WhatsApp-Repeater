package com.whatsapprepeater.app.ui.automation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

/**
 * Pantalla 8 (Stitch 8._confirmaci_n):
 * Resumen previo obligatorio y autorización responsable antes de iniciar el envío.
 */
@Composable
fun ConfirmationScreen(
  viewModel: AutomationViewModel,
  onBack: () -> Unit,
  onModifyParameters: () -> Unit,
  onOpenPermissions: () -> Unit,
  onStartConfirmed: () -> Unit,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val notificationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    viewModel.startAutomation()
    onStartConfirmed()
  }

  val draft = state.draft
  val contact = draft.selectedContact
  val totalMessages = state.totalScheduledPreview
  val avgDelay = (draft.minIntervalSeconds + draft.maxIntervalSeconds) / 2.0
  val estSeconds = ((totalMessages - 1).coerceAtLeast(0) * avgDelay + totalMessages * 3.0).toInt()
  val previewSnippet = if (draft.isMultipleMode) {
    draft.multipleMessages.firstOrNull().orEmpty()
  } else {
    draft.singleMessage
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
            .size(42.dp)
            .clip(CircleShape)
            .background(CardSurfaceElevated),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Resumen de Automatización", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          }
        }
      }
      StatusBadge(
        label = if (state.isServiceConnected) "Listo para iniciar" else "Sin permiso",
        containerColor = if (state.isServiceConnected) WhatsAppGreenContainer else WarningContainer,
        contentColor = if (state.isServiceConnected) WhatsAppGreen else WarningAmber,
      )
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Destinatario validado + Grid 2x2 + Contenido
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(WhatsAppGreenContainer),
              ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = WhatsAppGreen)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Destinatario validado", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                  text = contact?.name ?: "Sin destinatario",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = contact?.phoneNumber ?: "",
                  style = MaterialTheme.typography.bodyMedium,
                  color = WhatsAppGreen,
                )
              }
            }
            Surface(shape = RoundedCornerShape(999.dp), color = CardSurfaceElevated) {
              Text(
                text = if (draft.preferWhatsAppBusiness) "WA Business" else "WhatsApp",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryMetricBox(
              icon = Icons.Rounded.Repeat,
              label = "Modalidad",
              value = if (draft.isMultipleMode) "Mensajes múltiples" else "Mensaje único",
              sub = if (draft.isMultipleMode) "Orden numerado" else "Repetición controlada",
              modifier = Modifier.weight(1f),
            )
            SummaryMetricBox(
              icon = Icons.AutoMirrored.Rounded.Send,
              label = "Cantidad",
              value = "$totalMessages ${if (totalMessages == 1) "envío" else "envíos"}",
              sub = "Lote programado",
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryMetricBox(
              icon = Icons.Rounded.Timer,
              label = "Cadencia",
              value = "Entre ${draft.minIntervalSeconds}s y ${draft.maxIntervalSeconds}s",
              sub = "Variación aleatoria",
              valueHighlight = true,
              modifier = Modifier.weight(1f),
            )
            SummaryMetricBox(
              icon = Icons.Rounded.HourglassBottom,
              label = "Duración total",
              value = if (estSeconds >= 60) "~${estSeconds / 60}m ${estSeconds % 60}s" else "~${estSeconds}s",
              sub = "estimados",
              valueHighlight = true,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(CardSurfaceElevated)
              .padding(12.dp),
          ) {
            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Contenido a enviar", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              Text(
                text = if (previewSnippet.isBlank()) "Sin texto" else "\"$previewSnippet\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (!state.isServiceConnected) {
        item {
          SectionCard(borderColor = WarningAmber, containerColor = WarningContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = WarningAmber)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Servicio de Accesibilidad Inactivo", style = MaterialTheme.typography.titleMedium, color = WarningAmber)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Para iniciar el envío debes activar manualmente el servicio de accesibilidad de WhatsApp Repeater.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Ir a Permisos y Accesibilidad",
              onClick = onOpenPermissions,
            )
          }
        }
      }

      // Atención: Envío Real Automatizado (Stitch 8)
      item {
        SectionCard(borderColor = WarningAmber.copy(alpha = 0.7f), containerColor = WarningContainer.copy(alpha = 0.85f)) {
          Row(verticalAlignment = Alignment.Top) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WarningAmber),
            ) {
              Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = BackgroundBlack)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Atención: Envío Real Automatizado",
                style = MaterialTheme.typography.titleMedium,
                color = WarningAmber,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Se abrirá WhatsApp en primer plano y se enviarán mensajes reales mediante el Servicio de Accesibilidad. Mantén la pantalla desbloqueada y no manipules el chat durante la secuencia.",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Interrupción inmediata disponible en pantalla y notificación",
                  style = MaterialTheme.typography.bodySmall,
                  color = WarningAmber,
                )
              }
            }
          }
        }
      }

      // Checkbox de responsabilidad explícita (Stitch 8)
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = CardSurface,
          border = BorderStroke(
            1.dp,
            if (state.userCheckboxConfirmed) WhatsAppGreen else OutlineSubtle,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { viewModel.setUserCheckboxConfirmed(!state.userCheckboxConfirmed) },
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
          ) {
            Checkbox(
              checked = state.userCheckboxConfirmed,
              onCheckedChange = viewModel::setUserCheckboxConfirmed,
              colors = CheckboxDefaults.colors(
                checkedColor = WhatsAppGreen,
                uncheckedColor = TextSecondary,
                checkmarkColor = BackgroundBlack,
              ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Entiendo que los mensajes son reales y asumo la responsabilidad del envío.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Requerido para activar el motor de accesibilidad",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      LargePrimaryButton(
        text = "Iniciar automatización",
        icon = Icons.Rounded.PlayArrow,
        enabled = state.canStartAutomation,
        onClick = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
          ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            viewModel.startAutomation()
            onStartConfirmed()
          }
        },
      )
      LargeSecondaryButton(
        text = "Modificar parámetros",
        icon = Icons.Rounded.Tune,
        onClick = onModifyParameters,
      )
    }
  }
}

@Composable
private fun SummaryMetricBox(
  icon: ImageVector,
  label: String,
  value: String,
  sub: String,
  modifier: Modifier = Modifier,
  valueHighlight: Boolean = false,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(CardSurfaceElevated)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(15.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      color = if (valueHighlight) WhatsAppGreen else TextPrimary,
      fontWeight = FontWeight.Bold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
  }
}

