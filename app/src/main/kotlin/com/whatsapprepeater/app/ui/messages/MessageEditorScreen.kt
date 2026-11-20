package com.whatsapprepeater.app.ui.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CopyAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import com.whatsapprepeater.core.model.SafetyLimits

/**
 * Pantalla Paso 3 de 4 (Stitch 5._editor_de_mensajes):
 * Modalidad de secuencia de mensajes múltiples.
 */
@Composable
fun MessageEditorScreen(
  viewModel: MessageEditorViewModel,
  onBack: () -> Unit,
  onContinue: () -> Unit,
  onOpenLibrary: () -> Unit = {},
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val composerField by viewModel.newMessageField.collectAsStateWithLifecycle()
  var editingIndex by remember { mutableStateOf<Int?>(null) }
  var editingText by remember { mutableStateOf("") }
  var showPreviewDialog by remember { mutableStateOf(false) }
  var showSaveTemplateDialog by remember { mutableStateOf(false) }
  var showSavedListsDialog by remember { mutableStateOf(false) }
  var showSearchReplaceDialog by remember { mutableStateOf(false) }
  var multiSelectActive by remember { mutableStateOf(false) }
  var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
  var actionFeedback by remember { mutableStateOf<String?>(null) }

  fun copyToClipboard(label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    actionFeedback = "Copiado al portapapeles."
  }

  fun sharePlainText(text: String) {
    if (text.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
      context.startActivity(Intent.createChooser(shareIntent, "Compartir mensaje"))
    }
  }

  LaunchedEffect(Unit) {
    viewModel.setMultipleMode(true)
  }

  LaunchedEffect(state.multipleMessages.size) {
    val activeIdx = editingIndex
    if (activeIdx != null && activeIdx !in state.multipleMessages.indices) {
      editingIndex = null
    }
    selectedIndices = selectedIndices.filter { it in state.multipleMessages.indices }.toSet()
    if (state.multipleMessages.isEmpty()) {
      multiSelectActive = false
    }
  }

  val multipleSavedLists = remember(state.savedLists) {
    state.savedLists.filter { it.isMultipleMode }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundBlack)
      .navigationBarsPadding()
      .imePadding(),
  ) {
    WizardStepHeader(
      title = "Lista de Mensajes",
      stepIndex = 3,
      totalSteps = 4,
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .animateContentSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Barra de listas guardadas y Biblioteca inteligente
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          SmallActionChip(
            text = "Biblioteca (${state.savedLists.size})",
            icon = Icons.Rounded.Bookmarks,
            highlighted = false,
            onClick = { onOpenLibrary() },
            modifier = Modifier.weight(1f),
          )
          SmallActionChip(
            text = "Guardar plantilla",
            icon = Icons.Rounded.BookmarkAdd,
            highlighted = state.canProceedMultiple,
            onClick = {
              if (state.canProceedMultiple) {
                showSaveTemplateDialog = true
              }
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Redactar mensaje (Stitch 5 + Editor contextual inteligente)
      item {
        SectionCard {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Redactar Mensaje",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "${composerField.text.length} / ${SafetyLimits.MAX_MESSAGE_LENGTH}",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = composerField,
            onValueChange = viewModel::onNewMessageFieldChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Escribe tu mensaje...", color = TextSecondary) },
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
            rawText = composerField.text,
            livePreviewText = state.livePreviewText,
            variableError = state.variableError,
            detectedLinksCount = state.detectedLinksCount,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onFormatMarkdown = viewModel::wrapOrAppendFormatting,
            onInsertSnippet = viewModel::insertSnippetInComposer,
            onUndo = { viewModel.undoActiveEditor(isMultiple = true) },
            onRedo = { viewModel.redoActiveEditor(isMultiple = true) },
            onOpenSearchReplace = { showSearchReplaceDialog = true },
            onDuplicateFragment = { viewModel.duplicateFragment(isMultiple = true) },
            onClearFormatting = { viewModel.clearFormatting(isMultiple = true) },
          )
          if (state.validationError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(state.validationError ?: "", style = MaterialTheme.typography.bodySmall, color = DangerRed)
          }
          Spacer(modifier = Modifier.height(12.dp))
          LargePrimaryButton(
            text = "Agregar a la lista (${state.multipleMessages.size}/${SafetyLimits.MAX_MULTIPLE_MESSAGES_LIMIT})",
            icon = Icons.Rounded.Add,
            onClick = viewModel::addMessageToList,
          )
        }
      }

      // Cabecera Elementos (N) + Vista previa + Limpiar todo
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Elementos",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = CircleShape, color = CardSurfaceElevated) {
              Text(
                text = "${state.multipleMessages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = WhatsAppGreen,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.multipleMessages.isNotEmpty()) {
              IconButton(
                onClick = {
                  multiSelectActive = !multiSelectActive
                  if (!multiSelectActive) selectedIndices = emptySet()
                },
              ) {
                Icon(
                  Icons.Rounded.SelectAll,
                  contentDescription = "Seleccionar varios",
                  tint = if (multiSelectActive) WhatsAppGreen else TextPrimary,
                )
              }
              IconButton(onClick = { showPreviewDialog = true }) {
                Icon(Icons.Rounded.Visibility, contentDescription = "Vista previa", tint = WhatsAppGreen)
              }
              IconButton(
                onClick = {
                  editingIndex = null
                  multiSelectActive = false
                  selectedIndices = emptySet()
                  viewModel.clearAllMultipleMessages()
                },
              ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpiar todo", tint = DangerRed)
              }
            }
          }
        }
        if (!actionFeedback.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(actionFeedback ?: "", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
        }
        if (multiSelectActive && state.multipleMessages.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          SectionCard(borderColor = WhatsAppGreen, containerColor = WhatsAppGreenContainer.copy(alpha = 0.35f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "${selectedIndices.size} seleccionado(s)",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                  enabled = selectedIndices.isNotEmpty(),
                  onClick = {
                    val joined = state.multipleMessages
                      .filterIndexed { idx, _ -> idx in selectedIndices }
                      .joinToString("\n\n")
                    if (joined.isNotBlank()) copyToClipboard("Mensajes", joined)
                  },
                ) {
                  Icon(Icons.Rounded.CopyAll, contentDescription = "Copiar seleccionados", tint = WhatsAppGreen)
                }
                IconButton(
                  enabled = selectedIndices.isNotEmpty(),
                  onClick = {
                    val joined = state.multipleMessages
                      .filterIndexed { idx, _ -> idx in selectedIndices }
                      .joinToString("\n\n")
                    sharePlainText(joined)
                  },
                ) {
                  Icon(Icons.Rounded.Share, contentDescription = "Compartir seleccionados", tint = TextPrimary)
                }
                IconButton(
                  enabled = selectedIndices.isNotEmpty(),
                  onClick = {
                    viewModel.duplicateMessagesAt(selectedIndices)
                    selectedIndices = emptySet()
                    multiSelectActive = false
                    actionFeedback = "Mensajes seleccionados duplicados."
                  },
                ) {
                  Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicar seleccionados", tint = TextPrimary)
                }
                IconButton(
                  enabled = selectedIndices.isNotEmpty(),
                  onClick = {
                    viewModel.removeMessagesAt(selectedIndices)
                    selectedIndices = emptySet()
                    multiSelectActive = false
                    actionFeedback = "Mensajes seleccionados eliminados."
                  },
                ) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar seleccionados", tint = DangerRed)
                }
              }
            }
          }
        }
      }

      if (state.multipleMessages.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = "No hay mensajes en la lista",
            description = "Escribe un mensaje arriba y pulsa \"Agregar a la lista\". Cada mensaje se enviará una sola vez en orden numerado.",
          )
        }
      } else {
        itemsIndexed(state.multipleMessages) { index, message ->
          val isSelected = index in selectedIndices
          SectionCard(
            borderColor = if (isSelected) WhatsAppGreen else OutlineSubtle,
            modifier = if (multiSelectActive) {
              Modifier.clickable {
                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
              }
            } else {
              Modifier
            },
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (multiSelectActive) {
                Icon(
                  imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                  contentDescription = "Seleccionar mensaje",
                  tint = if (isSelected) WhatsAppGreen else TextSecondary,
                  modifier = Modifier.size(24.dp),
                )
              } else {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(WhatsAppGreenContainer),
                ) {
                  Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = WhatsAppGreen,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              horizontalArrangement = Arrangement.End,
              modifier = Modifier.fillMaxWidth(),
            ) {
              IconButton(onClick = { copyToClipboard("Mensaje #${index + 1}", message) }) {
                Icon(Icons.Rounded.CopyAll, contentDescription = "Copiar", tint = TextSecondary)
              }
              IconButton(onClick = { sharePlainText(message) }) {
                Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = TextSecondary)
              }
              IconButton(
                onClick = {
                  editingIndex = null
                  viewModel.moveMessageUp(index)
                },
                enabled = index > 0,
              ) {
                Icon(
                  Icons.Rounded.ArrowUpward,
                  contentDescription = "Subir",
                  tint = if (index > 0) TextPrimary else OutlineSubtle,
                )
              }
              IconButton(
                onClick = {
                  editingIndex = null
                  viewModel.moveMessageDown(index)
                },
                enabled = index < state.multipleMessages.lastIndex,
              ) {
                Icon(
                  Icons.Rounded.ArrowDownward,
                  contentDescription = "Bajar",
                  tint = if (index < state.multipleMessages.lastIndex) TextPrimary else OutlineSubtle,
                )
              }
              IconButton(
                onClick = {
                  editingIndex = null
                  viewModel.duplicateMessageAt(index)
                },
              ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicar", tint = TextPrimary)
              }
              IconButton(
                onClick = {
                  editingIndex = index
                  editingText = message
                },
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = WhatsAppGreen)
              }
              IconButton(
                onClick = {
                  editingIndex = null
                  viewModel.removeMessageAt(index)
                },
              ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = DangerRed)
              }
            }
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
        text = "Atrás",
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        onClick = onBack,
        modifier = Modifier.weight(0.4f),
      )
      LargePrimaryButton(
        text = "Continuar a Intervalos",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        enabled = state.canProceedMultiple,
        onClick = onContinue,
        modifier = Modifier.weight(0.6f),
      )
    }
  }

  val targetEditIndex = editingIndex
  if (targetEditIndex != null) {
    AlertDialog(
      onDismissRequest = { editingIndex = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Editar mensaje #${targetEditIndex + 1}") },
      text = {
        OutlinedTextField(
          value = editingText,
          onValueChange = { editingText = it.take(SafetyLimits.MAX_MESSAGE_LENGTH) },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2,
          maxLines = 5,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
      },
      confirmButton = {
        val canSaveEdit = editingText.isNotBlank()
        TextButton(
          enabled = canSaveEdit,
          onClick = {
            if (canSaveEdit && targetEditIndex in state.multipleMessages.indices) {
              viewModel.editMessageAt(targetEditIndex, editingText)
            }
            editingIndex = null
          },
        ) {
          Text(
            "Guardar",
            color = if (canSaveEdit) WhatsAppGreen else TextSecondary,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { editingIndex = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }

  if (showPreviewDialog) {
    AlertDialog(
      onDismissRequest = { showPreviewDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Vista previa de secuencia (${state.multipleMessages.size})") },
      text = {
        Column(
          modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.multipleMessages.forEachIndexed { idx, msg ->
            Text(
              text = "${idx + 1}. $msg",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPreviewDialog = false }) {
          Text("Cerrar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      },
    )
  }

  if (showSaveTemplateDialog) {
    SaveTemplateMetadataDialog(
      dialogTitle = "Guardar secuencia en Biblioteca",
      onDismiss = { showSaveTemplateDialog = false },
      onConfirm = { title, category, tags, isFav ->
        viewModel.saveCurrentAsTemplate(title, category, tags, isFav)
      },
    )
  }

  if (showSearchReplaceDialog) {
    SearchReplaceDialog(
      supportsListReplace = true,
      onDismiss = { showSearchReplaceDialog = false },
      onApply = { find, replace, applyList ->
        viewModel.searchAndReplace(find, replace, isMultiple = true, applyToSavedList = applyList)
      },
    )
  }

  if (showSavedListsDialog) {
    AlertDialog(
      onDismissRequest = { showSavedListsDialog = false },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      title = { Text("Listas guardadas") },
      text = {
        if (multipleSavedLists.isEmpty()) {
          Text("Aún no tienes listas múltiples guardadas.", color = TextSecondary)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            multipleSavedLists.forEach { saved ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(CardSurfaceElevated)
                  .clickable {
                    viewModel.loadSavedTemplate(saved)
                    showSavedListsDialog = false
                  }
                  .padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(saved.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                  val subtitle = if (saved.isMultipleMode) {
                    "${saved.messages.size} mensajes"
                  } else {
                    "Repetir ${saved.repeatCount}x"
                  }
                  Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
        TextButton(onClick = { showSavedListsDialog = false }) {
          Text("Cerrar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun SmallActionChip(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  highlighted: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = if (highlighted) WhatsAppGreenContainer else CardSurface,
    border = BorderStroke(1.dp, if (highlighted) WhatsAppGreen else OutlineSubtle),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (highlighted) WhatsAppGreen else TextSecondary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlighted) WhatsAppGreen else TextPrimary,
      )
    }
  }
}
