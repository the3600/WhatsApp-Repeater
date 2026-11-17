package com.whatsapprepeater.app.ui.messages

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapprepeater.app.ui.components.LargePrimaryButton
import com.whatsapprepeater.app.ui.components.LargeSecondaryButton
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.components.WizardStepHeader
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WarningAmber
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.SafetyLimits

/**
 * Pantalla Paso 3 de 4 (Stitch 6._configuraci_n_de_repetici_n):
 * Modalidad de repetición de un único mensaje.
 */
@Composable
fun RepetitionConfigScreen(
  viewModel: MessageEditorViewModel,
  onBack: () -> Unit,
  onContinue: () -> Unit,
  onOpenLibrary: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val singleField by viewModel.singleMessageField.collectAsStateWithLifecycle()
  var showSaveTemplateDialog by remember { mutableStateOf(false) }
  var showSavedTemplatesDialog by remember { mutableStateOf(false) }
  var showSearchReplaceDialog by remember { mutableStateOf(false) }
  val repeatTemplates = remember(state.savedLists) {
    state.savedLists.filter { !it.isMultipleMode }
  }

  LaunchedEffect(Unit) {
    viewModel.setMultipleMode(false)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .navigationBarsPadding()
      .imePadding(),
  ) {
    WizardStepHeader(
      title = "Mensaje y Repeticiones",
      stepIndex = 3,
      totalSteps = 4,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        SectionCard {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Mensaje a repetir", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
              text = "${state.singleMessage.length} / ${SafetyLimits.MAX_MESSAGE_LENGTH}",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = singleField,
            onValueChange = viewModel::onSingleMessageFieldChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Escribe el mensaje exacto que deseas repetir...", color = TextSecondary) },
            minLines = 3,
            maxLines = 6,
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
          SmartEditorToolkit(
            rawText = singleField.text,
            livePreviewText = state.livePreviewText,
            variableError = state.variableError,
            detectedLinksCount = state.detectedLinksCount,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onFormatMarkdown = viewModel::applySingleMessageFormatting,
            onInsertSnippet = viewModel::insertSnippetInSingleMessage,
            onUndo = { viewModel.undoActiveEditor(isMultiple = false) },
            onRedo = { viewModel.redoActiveEditor(isMultiple = false) },
            onOpenSearchReplace = { showSearchReplaceDialog = true },
            onDuplicateFragment = { viewModel.duplicateFragment(isMultiple = false) },
            onClearFormatting = { viewModel.clearFormatting(isMultiple = false) },
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            TextButton(onClick = onOpenLibrary) {
              Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Biblioteca (${state.savedLists.size})", color = WhatsAppGreen)
            }
            if (state.singleMessage.isNotBlank()) {
              TextButton(
                onClick = { showSaveTemplateDialog = true },
              ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar plantilla", color = WhatsAppGreen)
              }
            }
          }
        }
      }

      item {
        SectionCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Cantidad de envíos", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                "Selecciona el total de repeticiones exactas",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
            StatusBadge(
              label = "Máx ${state.maxRepeatAllowed}",
              containerColor = WhatsAppGreenContainer,
              contentColor = WhatsAppGreen,
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Stepper grande centrado estilo Stitch 6
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = CardSurfaceElevated,
              border = BorderStroke(1.dp, OutlineSubtle),
              modifier = Modifier.size(56.dp),
            ) {
              IconButton(
                onClick = { viewModel.onRepeatCountChanged(state.repeatCount - 1) },
                enabled = state.repeatCount > SafetyLimits.MIN_REPEAT_COUNT,
              ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Restar", tint = TextPrimary)
              }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${state.repeatCount}",
                style = MaterialTheme.typography.headlineLarge,
                color = WhatsAppGreen,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "REPETICIONES",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
              )
            }

            Surface(
              shape = RoundedCornerShape(16.dp),
              color = CardSurfaceElevated,
              border = BorderStroke(1.dp, OutlineSubtle),
              modifier = Modifier.size(56.dp),
            ) {
              IconButton(
                onClick = { viewModel.onRepeatCountChanged(state.repeatCount + 1) },
                enabled = state.repeatCount < state.maxRepeatAllowed,
              ) {
                Icon(Icons.Rounded.Add, contentDescription = "Sumar", tint = TextPrimary)
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          Text("Accesos rápidos sugeridos:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 10, 25, 50).forEach { preset ->
              val selected = state.repeatCount == preset
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
                border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(999.dp))
                  .clickable {
                    viewModel.selectRepeatPreset(preset)
                  },
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                  Text(
                    text = "${preset}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) WhatsAppGreen else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      }

      // Resumen de carga + Vista previa
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WhatsAppGreenContainer),
            ) {
              Icon(Icons.Rounded.FormatListNumbered, contentDescription = null, tint = WhatsAppGreen)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("RESUMEN DE CARGA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
              Text(
                text = "Total de mensajes programados: ${state.repeatCount} ${if (state.repeatCount == 1) "envío" else "envíos"}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          if (state.singleMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Vista previa: \"${state.singleMessage.trim()}\"",
              style = MaterialTheme.typography.bodySmall,
              color = WhatsAppGreen,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(CardSurfaceElevated)
              .padding(12.dp),
          ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "La app respeta límites prudentes (tope seguro: ${SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT} mensajes por lote).",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(CardSurface)
        .padding(16.dp),
    ) {
      LargeSecondaryButton(
        text = "Volver",
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        onClick = onBack,
        modifier = Modifier.weight(0.4f),
      )
      LargePrimaryButton(
        text = "Continuar a Intervalos",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        enabled = state.canProceedRepeat,
        onClick = onContinue,
        modifier = Modifier.weight(0.6f),
      )
    }
  }

  if (showSaveTemplateDialog) {
    SaveTemplateMetadataDialog(
      dialogTitle = "Guardar plantilla en Biblioteca",
      onDismiss = { showSaveTemplateDialog = false },
      onConfirm = { title, category, tags, isFav ->
        viewModel.saveCurrentAsTemplate(title, category, tags, isFav)
      },
    )
  }

  if (showSearchReplaceDialog) {
    SearchReplaceDialog(
      supportsListReplace = false,
      onDismiss = { showSearchReplaceDialog = false },
      onApply = { find, replace, _ ->
        viewModel.searchAndReplace(find, replace, isMultiple = false, applyToSavedList = false)
      },
    )
  }

  if (showSavedTemplatesDialog) {
    AlertDialog(
      onDismissRequest = { showSavedTemplatesDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Plantillas de repetición") },
      text = {
        if (repeatTemplates.isEmpty()) {
          Text("Aún no tienes plantillas de repetición guardadas.", color = TextSecondary)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeatTemplates.forEach { saved ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(CardSurfaceElevated)
                  .clickable {
                    viewModel.loadSavedTemplate(saved)
                    showSavedTemplatesDialog = false
                  }
                  .padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(saved.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                  Text(
                    "Repetir ${saved.repeatCount}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                }
                IconButton(onClick = { viewModel.deleteSavedTemplate(saved.id) }) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = DangerRed)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSavedTemplatesDialog = false }) {
          Text("Cerrar", color = TextSecondary)
        }
      },
    )
  }
}

