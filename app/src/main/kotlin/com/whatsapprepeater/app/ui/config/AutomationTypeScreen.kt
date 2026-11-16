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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.WizardStepHeader
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

/**
 * Pantalla Paso 2 de 4 (Stitch 4._tipo_de_automatizaci_n):
 * Permite elegir explícitamente entre Repetir mensaje único o Secuencia de mensajes múltiples.
 */
@Composable
fun AutomationTypeScreen(
  container: AppContainer,
  onBack: () -> Unit,
  onContinueRepeatMode: () -> Unit,
  onContinueMultipleMode: () -> Unit,
) {
  val draft by container.draft.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .navigationBarsPadding(),
  ) {
    WizardStepHeader(
      title = "Elige el tipo de envío",
      stepIndex = 2,
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
          text = "Selecciona cómo deseas que el servicio despache los mensajes a WhatsApp de manera automatizada.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      item {
        ModeOptionCard(
          title = "Repetir mensaje único",
          badge = "Frecuente",
          description = "Escribe un único texto y define cuántas veces consecutivas se enviará al contacto con los intervalos configurados.",
          hint = "Ideal para recordatorios, alertas y confirmaciones",
          icon = Icons.Rounded.Repeat,
          selected = !draft.isMultipleMode,
          onClick = { container.updateDraft { it.copy(isMultipleMode = false) } },
        )
      }

      item {
        ModeOptionCard(
          title = "Secuencia de mensajes múltiples",
          badge = null,
          description = "Crea una lista de mensajes distintos. La aplicación enviará cada mensaje uno por uno en el orden establecido.",
          hint = "Ideal para instrucciones por partes, avisos o seguimiento",
          icon = Icons.Rounded.FormatListNumbered,
          selected = draft.isMultipleMode,
          onClick = { container.updateDraft { it.copy(isMultipleMode = true) } },
        )
      }

      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WhatsAppGreenContainer),
            ) {
              Icon(Icons.Rounded.Verified, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "MODO SELECCIONADO",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
              )
              Text(
                text = if (draft.isMultipleMode) {
                  "Secuencia ordenada de mensajes diferentes"
                } else {
                  "Repetición controlada de un mismo mensaje"
                },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
            }
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
        text = "Continuar con la configuración",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        onClick = {
          if (draft.isMultipleMode) onContinueMultipleMode() else onContinueRepeatMode()
        },
      )
      Spacer(modifier = Modifier.height(6.dp))
      TextButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Volver al paso anterior", color = TextSecondary)
      }
    }
  }
}

@Composable
private fun ModeOptionCard(
  title: String,
  badge: String?,
  description: String,
  hint: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) WhatsAppGreenContainer.copy(alpha = 0.55f) else CardSurface,
    ),
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) WhatsAppGreen else OutlineSubtle,
    ),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurfaceElevated),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) WhatsAppGreen else TextPrimary,
          )
        }
        RadioButton(
          selected = selected,
          onClick = onClick,
          colors = RadioButtonDefaults.colors(
            selectedColor = WhatsAppGreen,
            unselectedColor = TextSecondary,
          ),
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
          fontWeight = FontWeight.Bold,
        )
        if (badge != null) {
          Spacer(modifier = Modifier.width(8.dp))
          Surface(shape = RoundedCornerShape(999.dp), color = WhatsAppGreenContainer) {
            Text(
              text = badge,
              style = MaterialTheme.typography.labelSmall,
              color = WhatsAppGreen,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Lightbulb,
          contentDescription = null,
          tint = if (selected) WhatsAppGreen else WarningAmber,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = if (selected) WhatsAppGreen else TextSecondary,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

