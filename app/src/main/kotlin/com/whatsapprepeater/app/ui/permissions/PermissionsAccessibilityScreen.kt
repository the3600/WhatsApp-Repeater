package com.whatsapprepeater.app.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WarningContainer
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer

@Composable
fun PermissionsAccessibilityScreen(
  isServiceConnected: Boolean,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var enabledInSettings by remember {
    mutableStateOf(AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(context))
  }
  var contactsGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var notificationsGranted by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }

  val notifLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted -> notificationsGranted = granted }

  fun refreshAll() {
    enabledInSettings = AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(context)
    contactsGranted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshAll()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val active = isServiceConnected || enabledInSettings

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
          .size(42.dp)
          .clip(CircleShape)
          .background(CardSurfaceElevated),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text("Servicio de Accesibilidad", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Transparencia, permisos y control total", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        SectionCard(borderColor = if (active) WhatsAppGreen else WarningAmber) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            StatusBadge(
              label = if (active) "ACTIVO" else "INACTIVO",
              containerColor = if (active) WhatsAppGreenContainer else WarningContainer,
              contentColor = if (active) WhatsAppGreen else WarningAmber,
            )
            Icon(
              imageVector = Icons.Rounded.AccessibilityNew,
              contentDescription = null,
              tint = if (active) WhatsAppGreen else WarningAmber,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = if (active) {
              "El servicio está habilitado en Android. Solo actuará cuando confirmes explícitamente una automatización."
            } else {
              "El servicio está deshabilitado en Android. WhatsApp Repeater no puede interactuar con WhatsApp hasta que lo autorices manualmente."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
          )
        }
      }

      item {
        SectionCard {
          Text("¿Qué es y por qué es necesario?", style = MaterialTheme.typography.titleMedium, color = WhatsAppGreen)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Es una API estándar del sistema Android que permite localizar el campo de texto y el botón Enviar dentro del chat abierto en WhatsApp únicamente durante los envíos confirmados por ti.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
      }

      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Visibility, contentDescription = null, tint = WhatsAppGreen)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Acciones dentro de WhatsApp", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            StatusBadge("100% Local", WhatsAppGreenContainer, WhatsAppGreen)
          }
          Spacer(modifier = Modifier.height(12.dp))
          PermissionBullet("Verificación de destinatario", "Comprueba la cabecera del chat antes de escribir o pulsar Enviar.")
          PermissionBullet("Escritura y envío confirmados", "Escribe el mensaje en la caja de texto y verifica visualmente el botón Enviar.")
          PermissionBullet("Alcance limitado por paquete", "Declarado exclusivamente para com.whatsapp y com.whatsapp.w4b.")
          PermissionBullet("Sin recolección externa", "No lee conversaciones ajenas ni transmite datos fuera del dispositivo.")
        }
      }

      item {
        SectionCard(borderColor = WhatsAppGreenContainer) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WhatsAppGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tú conservas el control en todo momento", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Puedes pausar o detener al instante desde la notificación persistente, desde el diálogo de detención de emergencia o bloqueando la pantalla.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
      }

      item {
        SectionCard {
          Text("Cómo activar el servicio en Android 13, 14 y 15", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(10.dp))
          StepGuideRow(1, "Intenta activar el interruptor en Accesibilidad", "Pulsa \"Abrir ajustes de Android\" → Aplicaciones instaladas → WhatsApp Repeater. Si aparece \"Ajustes restringidos\", cierra el aviso (esto desbloquea la opción en el siguiente paso).")
          StepGuideRow(2, "Autoriza \"Permitir ajustes restringidos\"", "En Android 13+, las apps instaladas fuera de Play Store requieren este desbloqueo oficial del sistema. Pulsa abajo \"Abrir info de la app\" → menú ⋮ arriba a la derecha → Permitir ajustes restringidos.")
          StepGuideRow(3, "Regresa a Accesibilidad y activa el servicio", "Una vez autorizado con tu PIN o huella, vuelve a Accesibilidad y enciende WhatsApp Repeater.")
          if (!active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Abrir info de la app (Ajustes restringidos)",
              icon = Icons.AutoMirrored.Rounded.OpenInNew,
              onClick = { AccessibilitySettingsHelper.openAppDetailsSettings(context) },
            )
          }
        }
      }

      item {
        SectionCard {
          Text("Otros permisos de la aplicación", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Contactos (bajo demanda)", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(
              if (contactsGranted) "Concedido" else "Se pedirá al elegir contacto",
              color = if (contactsGranted) WhatsAppGreen else TextSecondary,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Notificaciones de control", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(
              if (notificationsGranted) "Concedido" else "Pendiente",
              color = if (notificationsGranted) WhatsAppGreen else WarningAmber,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Permitir notificaciones de detención",
              onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
          }
        }
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(BackgroundBlack)
        .padding(16.dp),
    ) {
      LargePrimaryButton(
        text = "Abrir ajustes de Android",
        icon = Icons.AutoMirrored.Rounded.OpenInNew,
        onClick = { AccessibilitySettingsHelper.openAccessibilitySettings(context) },
      )
      LargeSecondaryButton(
        text = "Verificar estado del permiso",
        icon = Icons.Rounded.Refresh,
        onClick = { refreshAll() },
      )
    }
  }
}

@Composable
private fun PermissionBullet(title: String, detail: String) {
  Row(modifier = Modifier.padding(vertical = 5.dp)) {
    Icon(
      imageVector = Icons.Rounded.CheckCircle,
      contentDescription = null,
      tint = WhatsAppGreen,
      modifier = Modifier
        .padding(top = 2.dp)
        .size(18.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
      Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
  }
}

@Composable
private fun StepGuideRow(number: Int, title: String, subtitle: String) {
  Row(modifier = Modifier.padding(vertical = 6.dp)) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(WhatsAppGreenContainer),
    ) {
      Text("$number", color = WhatsAppGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
  }
}

