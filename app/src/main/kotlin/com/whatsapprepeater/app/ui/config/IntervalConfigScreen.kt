package com.whatsapprepeater.app.ui.config

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.components.WizardStepHeader
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
import com.whatsapprepeater.app.ui.theme.WhatsAppEmerald
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.SafetyLimits

/**
 * Pantalla Paso 4 de 4 (Stitch 7._intervalos_de_env_o):
 * Intervalos mínimo y máximo aleatorios con límites prudentes y proyección real.
 */
@Composable
fun IntervalConfigScreen(
  viewModel: IntervalConfigViewModel,
  onBack: () -> Unit,
  onContinue: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .navigationBarsPadding(),
  ) {
    WizardStepHeader(
      title = "Intervalos de Envío",
      stepIndex = 4,
      totalSteps = 4,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Configura una pausa aleatoria prudente entre cada mensaje confirmado (entre ${SafetyLimits.MIN_INTERVAL_SECONDS}s y ${SafetyLimits.MAX_INTERVAL_SECONDS}s).",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      // Tarjeta informativa estilo Stitch 7 (con texto transparente y seguro)
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.Top) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WarningContainer),
            ) {
              Icon(Icons.Rounded.Casino, contentDescription = null, tint = WarningAmber)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Cadencia Aleatoria Prudente",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Cada envío esperará un tiempo aleatorio dentro del rango definido para evitar ráfagas repetitivas.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }
      }

      // Estado de validación de límites de espera
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Límites de Espera",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (state.isValid) WhatsAppGreenContainer else DangerContainer,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Icon(
                imageVector = if (state.isValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (state.isValid) WhatsAppGreen else DangerRed,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = state.validationError ?: "Válido: rango listo",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isValid) WhatsAppGreen else DangerRed,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      item {
        IntervalStepperCard(
          label = "Intervalo Mínimo",
          sublabel = "Pausa mín.",
          seconds = state.minSeconds,
          onDecrease = { viewModel.adjustMin(-1) },
          onIncrease = { viewModel.adjustMin(+1) },
        )
      }

      item {
        IntervalStepperCard(
          label = "Intervalo Máximo",
          sublabel = "Pausa máx.",
          seconds = state.maxSeconds,
          onDecrease = { viewModel.adjustMax(-1) },
          onIncrease = { viewModel.adjustMax(+1) },
        )
      }

      // Presets rápidos
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          PresetIntervalChip("15–35 s", state.minSeconds == 15 && state.maxSeconds == 35, Modifier.weight(1f)) {
            viewModel.applyPreset(15, 35)
          }
          PresetIntervalChip("25–50 s", state.minSeconds == 25 && state.maxSeconds == 50, Modifier.weight(1f)) {
            viewModel.applyPreset(25, 50)
          }
          PresetIntervalChip("40–90 s", state.minSeconds == 40 && state.maxSeconds == 90, Modifier.weight(1f)) {
            viewModel.applyPreset(40, 90)
          }
        }
      }

      // Simulación de Ejecución (Stitch 7)
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Timer, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Proyección de Ejecución", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            StatusBadge(
              label = "Lote: ${state.scheduledCount} envíos",
              containerColor = CardSurfaceElevated,
              contentColor = TextPrimary,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(CardSurfaceElevated)
              .padding(12.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Mín ${state.minSeconds} s", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen)
              Text("Rango Aleatorio", style = MaterialTheme.typography.labelMedium, color = WarningAmber)
              Text("Máx ${state.maxSeconds} s", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                  Brush.horizontalGradient(listOf(WhatsAppEmerald, WhatsAppGreen, WhatsAppEmerald)),
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Variabilidad calculada:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              Text(
                text = "± ${"%.1f".format(state.variabilitySeconds)} seg / mensaje",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(CardSurfaceElevated)
              .padding(12.dp),
          ) {
            Column {
              Text("Tiempo total estimado:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              Text(
                text = formatDurationApprox(state.estimatedTotalSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
              )
            }
            StatusBadge(
              label = if (state.minSeconds >= 10) "Ritmo prudente" else "Ritmo rápido",
              containerColor = if (state.minSeconds >= 10) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (state.minSeconds >= 10) WhatsAppGreen else WarningAmber,
            )
          }
        }
      }

      // Preferencias rápidas DataStore
      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Guardar como predeterminado", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Recordar este rango en DataStore", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = state.saveAsDefault,
              onCheckedChange = viewModel::setSaveAsDefault,
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Usar WhatsApp Business", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
              Text("Por defecto abre WhatsApp estándar", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = state.preferWhatsAppBusiness,
              onCheckedChange = viewModel::setPreferWhatsAppBusiness,
              colors = SwitchDefaults.colors(checkedThumbColor = BackgroundBlack, checkedTrackColor = WhatsAppGreen),
            )
          }
        }
      }
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      LargePrimaryButton(
        text = "Revisar y Confirmar",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        enabled = state.isValid,
        onClick = {
          if (viewModel.confirmAndPersist()) {
            onContinue()
          }
        },
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Parámetros guardados localmente", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }
  }
}

@Composable
private fun IntervalStepperCard(
  label: String,
  sublabel: String,
  seconds: Int,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
) {
  SectionCard {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(label, style = MaterialTheme.typography.titleSmall, color = TextSecondary)
      Icon(Icons.Rounded.HourglassBottom, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "$seconds",
        style = MaterialTheme.typography.headlineLarge,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text("seg", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurfaceElevated,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier.size(48.dp),
      ) {
        IconButton(onClick = onDecrease) {
          Icon(Icons.Rounded.Remove, contentDescription = "Reducir", tint = TextPrimary)
        }
      }
      Text(sublabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurfaceElevated,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier.size(48.dp),
      ) {
        IconButton(onClick = onIncrease) {
          Icon(Icons.Rounded.Add, contentDescription = "Aumentar", tint = TextPrimary)
        }
      }
    }
  }
}

@Composable
private fun PresetIntervalChip(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = if (selected) WhatsAppGreenContainer else CardSurface,
    border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) WhatsAppGreen else TextPrimary,
      )
    }
  }
}

private fun formatDurationApprox(totalSec: Int): String {
  val mins = totalSec / 60
  val secs = totalSec % 60
  return if (mins > 0) "~${mins}m ${secs}s" else "~${secs}s"
}

