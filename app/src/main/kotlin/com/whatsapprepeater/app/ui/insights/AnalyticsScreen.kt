package com.whatsapprepeater.app.ui.insights

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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

@Composable
fun AnalyticsScreen(
  viewModel: InsightsViewModel,
  onBack: () -> Unit,
) {
  val stats by viewModel.analytics.collectAsStateWithLifecycle()

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
        Text("Analíticas Locales y Privadas", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Solo contadores en tu dispositivo (cero texto, cero nube)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WhatsAppGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Motor 1 — Automatización por lote", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBox("Ejecuciones", "${stats.totalMotor1Runs}", TextPrimary, Modifier.weight(1f))
            MetricBox("Completadas", "${stats.completedMotor1Runs}", WhatsAppGreen, Modifier.weight(1f))
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBox("Interrumpidas", "${stats.interruptedMotor1Runs}", WarningAmber, Modifier.weight(1f))
            MetricBox("Errores", "${stats.errorMotor1Runs}", DangerRed, Modifier.weight(1f))
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Efectividad de entrega", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
              text = "${stats.totalMessagesConfirmed}/${stats.totalMessagesScheduled} (${stats.deliveryRatePercent}%)",
              style = MaterialTheme.typography.labelMedium,
              color = WhatsAppGreen,
              fontWeight = FontWeight.Bold,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { stats.deliveryRatePercent / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(999.dp)),
            color = WhatsAppGreen,
            trackColor = CardSurfaceElevated,
          )
        }
      }

      item {
        SectionCard {
          Text("Motor 2 — Autorespuestas", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBox("Respondidos", "${stats.totalMotor2Sent}", WhatsAppGreen, Modifier.weight(1f))
            MetricBox("Omitidos", "${stats.totalMotor2Skipped}", TextSecondary, Modifier.weight(1f))
            MetricBox("Fallidos", "${stats.totalMotor2Failed}", DangerRed, Modifier.weight(1f))
          }
        }
      }

      item {
        SectionCard {
          Text("Programador Local", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBox("Pendientes", "${stats.pendingSchedules}", WarningAmber, Modifier.weight(1f))
            MetricBox("Ejecutadas", "${stats.completedSchedules}", WhatsAppGreen, Modifier.weight(1f))
            MetricBox("Vencidas/Err", "${stats.missedOrFailedSchedules}", DangerRed, Modifier.weight(1f))
          }
        }
      }

      item {
        SectionCard {
          Text("Plantillas más utilizadas", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          if (stats.topTemplates.isEmpty()) {
            Text("Aún no se han reutilizado plantillas guardadas.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
          } else {
            stats.topTemplates.forEach { stat ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 5.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(stat.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                  Text(stat.category, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                StatusBadge("${stat.useCount} usos", WhatsAppGreenContainer, WhatsAppGreen)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricBox(
  label: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(CardSurfaceElevated)
      .padding(10.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Spacer(modifier = Modifier.height(2.dp))
    Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
  }
}
