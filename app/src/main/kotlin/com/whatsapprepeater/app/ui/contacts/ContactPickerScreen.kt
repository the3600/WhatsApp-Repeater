package com.whatsapprepeater.app.ui.contacts

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.WizardStepHeader
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.ContactInfo

@Composable
fun ContactPickerScreen(
  viewModel: ContactPickerViewModel,
  onBack: () -> Unit,
  onConfirmContact: () -> Unit,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var permissionDeniedOnce by remember { mutableStateOf(false) }
  var showConfirmRecipientDialog by remember { mutableStateOf(false) }
  var showManualPanel by remember { mutableStateOf(false) }
  var showCountryDropdown by remember { mutableStateOf(false) }

  val countryCodes = listOf("+57", "+34", "+52", "+54", "+51", "+56", "+1")

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (!granted) permissionDeniedOnce = true
    viewModel.onPermissionStateChanged(granted)
  }

  LaunchedEffect(Unit) {
    val granted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    viewModel.onPermissionStateChanged(granted)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .navigationBarsPadding()
      .imePadding(),
  ) {
    WizardStepHeader(
      title = "Seleccionar Destinatario",
      stepIndex = 1,
      totalSteps = 4,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.hasContactsPermission) {
        item {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre o número (+57, +34, +1...)", color = TextSecondary) },
            leadingIcon = {
              Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = WhatsAppGreen)
            },
            trailingIcon = {
              if (state.searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                  Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Limpiar", tint = TextSecondary)
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CardSurface,
              unfocusedContainerColor = CardSurface,
              focusedBorderColor = WhatsAppGreen,
              unfocusedBorderColor = OutlineSubtle,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
          )
        }
      }

      if (!state.hasContactsPermission) {
        item {
          SectionCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WhatsAppGreenContainer),
                ) {
                  Icon(Icons.Rounded.Contacts, contentDescription = null, tint = WhatsAppGreen)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Permiso de Agenda", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              }
              Surface(shape = RoundedCornerShape(999.dp), color = CardSurfaceElevated) {
                Text(
                  text = "Opcional",
                  style = MaterialTheme.typography.labelSmall,
                  color = TextSecondary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Para elegir desde tu agenda, autoriza el permiso local de contactos, o ingresa un número directamente con código de país.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            LargePrimaryButton(
              text = "Sincronizar agenda",
              icon = Icons.Rounded.Sync,
              onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            LargeSecondaryButton(
              text = if (showManualPanel) "Ocultar ingreso manual" else "Ingreso manual",
              icon = Icons.Rounded.Dialpad,
              onClick = { showManualPanel = !showManualPanel },
            )
            if (permissionDeniedOnce) {
              Spacer(modifier = Modifier.height(8.dp))
              TextButton(
                onClick = {
                  val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                  )
                  context.startActivity(intent)
                },
              ) {
                Text("Abrir ajustes de la app para habilitar contactos", color = WhatsAppGreen)
              }
            }
          }
        }
      }

      // Panel de Ingreso Directo de Número Internacional (Stitch 3)
      if (showManualPanel || !state.hasContactsPermission) {
        item {
          SectionCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = WhatsAppGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ingreso Directo de Número", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              }
              Text("WhatsApp Direct", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = state.manualContactName,
              onValueChange = viewModel::onManualNameChanged,
              label = { Text("Nombre o referencia (opcional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardSurfaceElevated,
                unfocusedContainerColor = CardSurfaceElevated,
                focusedBorderColor = WhatsAppGreen,
                unfocusedBorderColor = OutlineSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
              ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Box {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = CardSurfaceElevated,
                  border = BorderStroke(1.dp, OutlineSubtle),
                  modifier = Modifier
                    .height(56.dp)
                    .clickable { showCountryDropdown = true },
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                      text = state.countryCode,
                      style = MaterialTheme.typography.titleMedium,
                      color = TextPrimary,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }
                DropdownMenu(
                  expanded = showCountryDropdown,
                  onDismissRequest = { showCountryDropdown = false },
                ) {
                  countryCodes.forEach { code ->
                    DropdownMenuItem(
                      text = { Text(code) },
                      onClick = {
                        viewModel.onCountryCodeSelected(code)
                        showCountryDropdown = false
                      },
                    )
                  }
                }
              }

              OutlinedTextField(
                value = state.manualNumberInput,
                onValueChange = viewModel::onManualNumberChanged,
                placeholder = { Text("Número sin código", color = TextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = {
                  IconButton(
                    onClick = {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                      if (clipText.isNotBlank()) {
                        viewModel.onManualNumberChanged(clipText)
                      }
                    },
                  ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Pegar", tint = WhatsAppGreen)
                  }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = CardSurfaceElevated,
                  unfocusedContainerColor = CardSurfaceElevated,
                  focusedBorderColor = WhatsAppGreen,
                  unfocusedBorderColor = OutlineSubtle,
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary,
                ),
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = state.manualValidationError ?: "Formato internacional requerido (+código y dígitos)",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.manualValidationError != null) DangerRed else TextSecondary,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "${state.manualNumberInput.length}/15",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LargeSecondaryButton(
              text = "Usar este número como destinatario",
              enabled = state.isManualNumberValid,
              onClick = {
                viewModel.selectManualNumberAsRecipient()
                showConfirmRecipientDialog = true
              },
            )
          }
        }
      }

      if (state.hasContactsPermission) {
        item {
          TextButton(
            onClick = { showManualPanel = !showManualPanel },
          ) {
            Text(
              text = if (showManualPanel) "Ocultar ingreso manual" else "+ Ingresar número manualmente",
              color = WhatsAppGreen,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      when {
        !state.hasContactsPermission -> {
          item {
            EmptyStateCard(
              icon = Icons.Rounded.ContactPhone,
              title = "No hay contactos cargados",
              description = "Permite el acceso a tu agenda o escribe el número arriba para enviar directamente por WhatsApp.",
            )
          }
        }

        state.isLoading -> {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(color = WhatsAppGreen)
            }
          }
        }

        state.loadError != null -> {
          item {
            SectionCard {
              Text("Error al cargar contactos", style = MaterialTheme.typography.titleMedium, color = DangerRed)
              Spacer(modifier = Modifier.height(6.dp))
              Text(state.loadError ?: "", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
          }
        }

        state.filteredContacts.isEmpty() -> {
          item {
            EmptyStateCard(
              icon = Icons.Rounded.ContactPhone,
              title = "Sin contactos coincidentes",
              description = if (state.searchQuery.isBlank()) {
                "No se encontraron contactos con número telefónico en tu dispositivo."
              } else {
                "Ningún contacto coincide con \"${state.searchQuery}\"."
              },
            )
          }
        }

        else -> {
          items(state.filteredContacts, key = { "${it.id}_${it.normalizedDigits}" }) { contact ->
            ContactRowCard(
              contact = contact,
              selected = state.selectedContact?.let {
                it.id == contact.id && it.normalizedDigits == contact.normalizedDigits
              } == true,
              onClick = { viewModel.selectSingleContact(contact) },
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      if (state.selectedContact != null) {
        Text(
          text = "Destinatario: ${state.selectedContact?.name} (${state.selectedContact?.phoneNumber})",
          style = MaterialTheme.typography.bodyMedium,
          color = WhatsAppGreen,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(10.dp))
      }
      LargePrimaryButton(
        text = "Continuar a Modalidad",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        enabled = state.selectedContact != null,
        onClick = { showConfirmRecipientDialog = true },
      )
    }
  }

  val selected = state.selectedContact
  if (showConfirmRecipientDialog && selected != null) {
    AlertDialog(
      onDismissRequest = { showConfirmRecipientDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Confirmar único destinatario") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Todos los mensajes de esta automatización se dirigirán exclusivamente a:")
          Spacer(modifier = Modifier.height(4.dp))
          Text(selected.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Text(selected.phoneNumber, style = MaterialTheme.typography.bodyLarge, color = WhatsAppGreen)
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            showConfirmRecipientDialog = false
            onConfirmContact()
          },
        ) {
          Text("Confirmar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmRecipientDialog = false }) {
          Text("Cambiar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun ContactRowCard(
  contact: ContactInfo,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) WhatsAppGreenContainer else CardSurface,
    ),
    border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(if (selected) WhatsAppGreen else CardSurfaceElevated),
      ) {
        Text(
          text = contact.name.firstOrNull()?.uppercase() ?: "#",
          style = MaterialTheme.typography.titleMedium,
          color = if (selected) BackgroundBlack else TextPrimary,
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = contact.name,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = contact.phoneNumber,
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
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
  }
}
