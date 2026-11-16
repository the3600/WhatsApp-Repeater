package com.whatsapprepeater.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapprepeater.app.R
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OnWhatsAppGreen
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.SurfaceDark
import com.whatsapprepeater.app.ui.theme.SurfaceLow
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

enum class BottomTab(val label: String, val icon: ImageVector) {
  HOME("Inicio", Icons.Rounded.Home),
  CREATE("Crear", Icons.Rounded.AddCircle),
  AUTORESPONDER("Auto", Icons.Rounded.AutoAwesome),
  HISTORY("Historial", Icons.Rounded.History),
  SETTINGS("Ajustes", Icons.Rounded.Settings),
}

@Composable
fun StitchMainTopBar(
  title: String,
  isAccessibilityReady: Boolean,
  isAutomationRunning: Boolean,
  onOpenPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = SurfaceLow,
    tonalElevation = 2.dp,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_brand_logo),
        contentDescription = "WhatsApp Repeater",
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(10.dp)),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "WhatsApp Repeater",
          style = MaterialTheme.typography.labelMedium,
          color = WhatsAppGreen,
          maxLines = 1,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      StatusBadge(
        label = when {
          isAutomationRunning -> "Activo"
          isAccessibilityReady -> "Listo"
          else -> "Inactivo"
        },
        containerColor = if (isAccessibilityReady) WhatsAppGreenContainer else WarningContainer,
        contentColor = if (isAccessibilityReady) WhatsAppGreen else WarningAmber,
      )
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(
        onClick = onOpenPermissions,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(if (isAccessibilityReady) WhatsAppGreen else CardSurfaceElevated),
      ) {
        Icon(
          imageVector = Icons.Rounded.AccessibilityNew,
          contentDescription = "Permisos y accesibilidad",
          tint = if (isAccessibilityReady) OnWhatsAppGreen else TextPrimary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
fun WhatsAppRepeaterBottomBar(
  currentTab: BottomTab,
  onSelectTab: (BottomTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationBar(
    containerColor = SurfaceLow,
    tonalElevation = 6.dp,
    modifier = modifier,
  ) {
    BottomTab.entries.forEach { tab ->
      val selected = currentTab == tab
      NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
        label = {
          Text(
            text = tab.label,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = WhatsAppGreen,
          selectedTextColor = TextPrimary,
          indicatorColor = WhatsAppGreen.copy(alpha = 0.2f),
          unselectedIconColor = TextSecondary,
          unselectedTextColor = TextSecondary,
        ),
      )
    }
  }
}

@Composable
fun SectionCard(
  modifier: Modifier = Modifier,
  borderColor: Color = OutlineSubtle,
  containerColor: Color = CardSurface,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      content = content,
    )
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  description: String,
  actionButtonText: String? = null,
  onActionClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, OutlineSubtle),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(CardSurfaceElevated),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = TextSecondary,
          modifier = Modifier.size(30.dp),
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )
      if (actionButtonText != null && onActionClick != null) {
        Spacer(modifier = Modifier.height(18.dp))
        Button(
          onClick = onActionClick,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
          ),
          border = BorderStroke(1.dp, OutlineSubtle),
        ) {
          Text(actionButtonText, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
fun FourStepMilestoneBar(
  currentStep: Int,
  modifier: Modifier = Modifier,
) {
  val labels = listOf("Contacto", "Modalidad", "Mensajes", "Intervalos")
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(SurfaceDark)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    labels.forEachIndexed { idx, label ->
      val stepNum = idx + 1
      val done = stepNum < currentStep
      val active = stepNum == currentStep
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
              when {
                done || active -> WhatsAppGreen
                else -> CardSurfaceElevated
              },
            ),
        ) {
          if (done) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = BackgroundBlack,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Text(
              text = "$stepNum",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (active) BackgroundBlack else TextSecondary,
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = if (done || active) WhatsAppGreen else TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun WizardStepHeader(
  title: String,
  stepIndex: Int,
  totalSteps: Int,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showMilestones: Boolean = true,
) {
  val animatedProgress by animateFloatAsState(
    targetValue = stepIndex.toFloat() / totalSteps.toFloat(),
    animationSpec = tween(durationMillis = 350),
    label = "wizard_progress",
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(CardSurfaceElevated),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Volver",
          tint = TextPrimary,
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
        Text(
          text = "Paso $stepIndex de $totalSteps",
          style = MaterialTheme.typography.bodyMedium,
          color = WhatsAppGreen,
        )
      }
    }
    Spacer(modifier = Modifier.height(10.dp))
    if (showMilestones && totalSteps == 4) {
      FourStepMilestoneBar(currentStep = stepIndex)
    } else {
      LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(999.dp)),
        color = WhatsAppGreen,
        trackColor = CardSurfaceElevated,
      )
    }
  }
}

@Composable
fun StatusBadge(
  label: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  val animatedBg by animateColorAsState(
    targetValue = containerColor,
    animationSpec = tween(250),
    label = "badge_bg",
  )
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = animatedBg,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(contentColor),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun LargePrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  containerColor: Color = WhatsAppGreen,
  contentColor: Color = OnWhatsAppGreen,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .fillMaxWidth()
      .height(54.dp),
    shape = RoundedCornerShape(16.dp),
    contentPadding = PaddingValues(horizontal = 20.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = containerColor,
      contentColor = contentColor,
      disabledContainerColor = CardSurfaceElevated,
      disabledContentColor = TextSecondary,
    ),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun LargeDangerButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  LargePrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    enabled = enabled,
    containerColor = DangerRed,
    contentColor = Color.White,
  )
}

@Composable
fun LargeSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .fillMaxWidth()
      .height(52.dp),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, OutlineSubtle),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = TextPrimary,
      containerColor = CardSurfaceElevated,
    ),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
