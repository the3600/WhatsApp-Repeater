package com.whatsapprepeater.app.ui.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.whatsapprepeater.app.ui.theme.BackgroundBlack
import com.whatsapprepeater.app.ui.theme.CardSurface
import com.whatsapprepeater.app.ui.theme.CardSurfaceElevated
import com.whatsapprepeater.app.ui.theme.DangerContainer
import com.whatsapprepeater.app.ui.theme.DangerRed
import com.whatsapprepeater.app.ui.theme.OutlineSubtle
import com.whatsapprepeater.app.ui.theme.TextPrimary
import com.whatsapprepeater.app.ui.theme.TextSecondary
import com.whatsapprepeater.app.ui.theme.WhatsAppGreen
import com.whatsapprepeater.app.ui.theme.WhatsAppGreenContainer
import com.whatsapprepeater.core.model.DynamicVariablesEngine

private val quickEmojis = listOf("👋", "✅", "📌", "⏰", "🙏", "✨", "🔥", "💬")
val templateCategories = listOf("General", "Ventas", "Recordatorios", "Soporte", "Seguimiento")

/**
 * Barra contextual inteligente de edición (formato manteniendo selección, deshacer/rehacer,
 * saltos de línea, copiar, buscar/reemplazar, variables dinámicas y emojis rápidos).
 */
@Composable
fun SmartEditorToolkit(
  rawText: String,
  livePreviewText: String,
  variableError: String?,
  detectedLinksCount: Int,
  canUndo: Boolean,
  canRedo: Boolean,
  onFormatMarkdown: (String) -> Unit,
  onInsertSnippet: (String) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onOpenSearchReplace: () -> Unit,
) {
  val context = LocalContext.current
  var copiedNotice by remember { mutableStateOf(false) }

  Spacer(modifier = Modifier.height(8.dp))

  // Fila 1: Herramientas de formato y edición
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
  ) {
    ToolbarFormatChip("B", "Negrita", FontWeight.Bold) { onFormatMarkdown("*") }
    ToolbarFormatChip("I", "Cursiva", FontWeight.Medium, italic = true) { onFormatMarkdown("_") }
    ToolbarFormatChip("S", "Tachado", FontWeight.Medium, strike = true) { onFormatMarkdown("~") }
    ToolbarFormatChip("M", "Monoespacio", FontWeight.SemiBold) { onFormatMarkdown("```") }

    ToolbarIconAction(
      icon = Icons.AutoMirrored.Rounded.Undo,
      description = "Deshacer",
      enabled = canUndo,
      onClick = onUndo,
    )
    ToolbarIconAction(
      icon = Icons.AutoMirrored.Rounded.Redo,
      description = "Rehacer",
      enabled = canRedo,
      onClick = onRedo,
    )
    ToolbarIconAction(
      icon = Icons.AutoMirrored.Rounded.KeyboardReturn,
      description = "Insertar salto de línea",
      enabled = true,
      onClick = { onInsertSnippet("\n") },
    )
    ToolbarIconAction(
      icon = Icons.Rounded.FindReplace,
      description = "Buscar y reemplazar",
      enabled = rawText.isNotEmpty(),
      onClick = onOpenSearchReplace,
    )
    ToolbarIconAction(
      icon = Icons.Rounded.ContentCopy,
      description = "Copiar mensaje formateado",
      enabled = rawText.isNotBlank(),
      onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Message", livePreviewText.ifBlank { rawText }))
        copiedNotice = true
      },
    )
  }

  Spacer(modifier = Modifier.height(8.dp))

  // Fila 2: Variables dinámicas locales y seguras
  Text(
    text = "Variables seguras y emojis rápidos:",
    style = MaterialTheme.typography.labelSmall,
    color = TextSecondary,
  )
  Spacer(modifier = Modifier.height(4.dp))
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
  ) {
    DynamicVariablesEngine.supportedVariables.forEach { variable ->
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = WhatsAppGreenContainer,
        border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.6f)),
        modifier = Modifier
          .defaultMinSize(minHeight = 36.dp)
          .clip(RoundedCornerShape(999.dp))
          .clickable {
            copiedNotice = false
            onInsertSnippet(variable.token)
          },
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
          Text(
            text = variable.token,
            style = MaterialTheme.typography.labelMedium,
            color = WhatsAppGreen,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
    quickEmojis.forEach { emoji ->
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardSurfaceElevated,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier
          .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable { onInsertSnippet(emoji) },
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
          Text(text = emoji)
        }
      }
    }
  }

  // Metadatos rápidos (enlaces, líneas y aviso de copia)
  val linesCount = if (rawText.isEmpty()) 0 else rawText.count { it == '\n' } + 1
  Spacer(modifier = Modifier.height(8.dp))
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = "$linesCount ${if (linesCount == 1) "línea" else "líneas"}",
      style = MaterialTheme.typography.labelSmall,
      color = TextSecondary,
    )
    if (detectedLinksCount > 0) {
      Surface(shape = RoundedCornerShape(999.dp), color = CardSurfaceElevated) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
          Icon(Icons.Rounded.Link, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(13.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "$detectedLinksCount enlace(s)",
            style = MaterialTheme.typography.labelSmall,
            color = WhatsAppGreen,
          )
        }
      }
    }
    if (copiedNotice) {
      Text(
        text = "• Copiado",
        style = MaterialTheme.typography.labelSmall,
        color = WhatsAppGreen,
      )
    }
  }

  // Vista previa resuelta en tiempo real cuando existen marcadores {variable}
  if (DynamicVariablesEngine.containsPlaceholders(rawText)) {
    Spacer(modifier = Modifier.height(8.dp))
    val hasErr = variableError != null
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (hasErr) DangerContainer else WhatsAppGreenContainer.copy(alpha = 0.55f))
        .padding(10.dp),
    ) {
      Text(
        text = if (hasErr) "Validación de variables" else "Vista previa resuelta:",
        style = MaterialTheme.typography.labelSmall,
        color = if (hasErr) DangerRed else WhatsAppGreen,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = variableError ?: livePreviewText,
        style = MaterialTheme.typography.bodySmall,
        color = TextPrimary,
      )
    }
  }
}

@Composable
private fun ToolbarFormatChip(
  label: String,
  description: String,
  weight: FontWeight,
  italic: Boolean = false,
  strike: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = CardSurfaceElevated,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier
      .size(40.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .semantics { contentDescription = description },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        color = TextPrimary,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
      )
    }
  }
}

@Composable
private fun ToolbarIconAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = CardSurfaceElevated,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.size(40.dp),
  ) {
    IconButton(onClick = onClick, enabled = enabled) {
      Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (enabled) TextPrimary else OutlineSubtle,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun SearchReplaceDialog(
  supportsListReplace: Boolean,
  onDismiss: () -> Unit,
  onApply: (find: String, replace: String, applyToList: Boolean) -> Int,
) {
  var findText by remember { mutableStateOf("") }
  var replaceText by remember { mutableStateOf("") }
  var applyToList by remember { mutableStateOf(supportsListReplace) }
  var resultNotice by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    textContentColor = TextSecondary,
    title = { Text("Buscar y reemplazar") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = findText,
          onValueChange = { findText = it; resultNotice = null },
          label = { Text("Buscar texto") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
        OutlinedTextField(
          value = replaceText,
          onValueChange = { replaceText = it },
          label = { Text("Reemplazar por") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
        if (supportsListReplace) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { applyToList = !applyToList },
          ) {
            Checkbox(
              checked = applyToList,
              onCheckedChange = { applyToList = it },
              colors = CheckboxDefaults.colors(
                checkedColor = WhatsAppGreen,
                checkmarkColor = BackgroundBlack,
              ),
            )
            Text("Aplicar también a toda la lista", color = TextPrimary)
          }
        }
        resultNotice?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = findText.isNotEmpty(),
        onClick = {
          val replaced = onApply(findText, replaceText, applyToList)
          if (replaced > 0) {
            onDismiss()
          } else {
            resultNotice = "No se encontraron coincidencias para \"$findText\"."
          }
        },
      ) {
        Text("Reemplazar", color = if (findText.isNotEmpty()) WhatsAppGreen else TextSecondary, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar", color = TextSecondary)
      }
    },
  )
}

@Composable
fun SaveTemplateMetadataDialog(
  dialogTitle: String,
  onDismiss: () -> Unit,
  onConfirm: (title: String, category: String, tags: List<String>, isFavorite: Boolean) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(templateCategories.first()) }
  var tagsRaw by remember { mutableStateOf("") }
  var isFavorite by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    textContentColor = TextSecondary,
    title = { Text(dialogTitle) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(48) },
          label = { Text("Nombre de la plantilla") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
        Text("Categoría", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          templateCategories.forEach { cat ->
            val selected = selectedCategory == cat
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { selectedCategory = cat },
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
        OutlinedTextField(
          value = tagsRaw,
          onValueChange = { tagsRaw = it.take(80) },
          label = { Text("Etiquetas (separadas por coma)") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { isFavorite = !isFavorite },
        ) {
          Icon(
            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = null,
            tint = if (isFavorite) WhatsAppGreen else TextSecondary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Marcar como favorita", color = TextPrimary)
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = title.isNotBlank(),
        onClick = {
          val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
          onConfirm(title, selectedCategory, tags, isFavorite)
          onDismiss()
        },
      ) {
        Text("Guardar", color = if (title.isNotBlank()) WhatsAppGreen else TextSecondary, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar", color = TextSecondary)
      }
    },
  )
}
