package com.whatsapprepeater.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whatsapprepeater.app.BuildConfig
import com.whatsapprepeater.app.R
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.SurfaceDark
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onContinue: () -> Unit,
) {
  val progress = remember { Animatable(0.15f) }

  LaunchedEffect(Unit) {
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
    )
    delay(100L)
    onContinue()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(WhatsAppGreenContainer.copy(alpha = 0.35f), BackgroundBlack, BackgroundBlack),
        ),
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 20.dp),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = 24.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_brand_logo),
        contentDescription = "WhatsApp Repeater Logo",
        modifier = Modifier
          .size(88.dp)
          .clip(RoundedCornerShape(24.dp)),
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "WhatsApp Repeater",
        style = MaterialTheme.typography.headlineLarge,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Automatización transparente y controlada para Android",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(18.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineSubtle),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = WhatsAppGreen,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "100% Local • Sin servidores externos ni recolección de datos",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      SectionCard {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Inicializando almacenamiento local...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
          )
          Text(
            text = "${(progress.value * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = WhatsAppGreen,
            fontWeight = FontWeight.Bold,
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
          progress = { progress.value },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp)),
          color = WhatsAppGreen,
          trackColor = CardSurfaceElevated,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          SplashCheckPill("Jetpack Compose UI", Modifier.weight(1f))
          SplashCheckPill("Base Room Local", Modifier.weight(1f))
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      LargePrimaryButton(
        text = "Empezar configuración",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        onClick = onContinue,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Material 3",
        style = MaterialTheme.typography.labelMedium,
        color = TextPrimary,
      )
      Text(
        text = "Compatible con Android 9+ • Arquitectura Offline",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
      )
    }
  }
}

@Composable
private fun SplashCheckPill(label: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = CardSurfaceElevated,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.CheckCircleOutline,
        contentDescription = null,
        tint = WhatsAppGreen,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = TextPrimary,
        maxLines = 1,
      )
    }
  }
}

