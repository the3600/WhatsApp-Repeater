package com.whatsapprepeater.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
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
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.SectionCard
import com.whatsapprepeater.app.ui.components.StatusBadge
import com.whatsapprepeater.app.ui.messages.templateCategories
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
import com.whatsapprepeater.core.model.SavedMessageList

@Composable
fun SmartLibraryScreen(
  viewModel: SmartLibraryViewModel,
  onBack: () -> Unit,
  onOpenTemplateInEditor: (isMultipleMode: Boolean) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var editingMetaFor by remember { mutableStateOf<SavedMessageList?>(null) }
  var deletingId by remember { mutableStateOf<Long?>(null) }

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
        Text("Centro Inteligente de Mensajes", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Favoritas, fijadas, recientes y clonado seguro", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.feedback.isNullOrBlank()) {
        item {
          SectionCard(borderColor = WhatsAppGreen, containerColor = WhatsAppGreenContainer) {
            Text(state.feedback ?: "", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      // Búsqueda instantánea
      item {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = viewModel::onSearchChanged,
          placeholder = { Text("Buscar por título, mensaje, categoría o etiqueta...", color = TextSecondary) },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = WhatsAppGreen) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
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

      // Sección Favoritas y Recientes en acceso rápido cuando no hay búsqueda filtrada
      if (state.searchQuery.isBlank() && state.favoritesSection.isNotEmpty()) {
        item {
          Text("FAVORITAS Y FIJADAS", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.favoritesSection, key = { "fav_${it.id}" }) { fav ->
              QuickHighlightCard(
                item = fav,
                onUse = {
                  viewModel.applyTemplateToDraft(fav)
                  onOpenTemplateInEditor(fav.isMultipleMode)
                },
              )
            }
          }
        }
      }

      // Filtros de vista y categoría
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          LibraryFilterTab.entries.forEach { tab ->
            val selected = state.selectedTab == tab
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurface,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { viewModel.onTabSelected(tab) },
            ) {
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) WhatsAppGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          state.availableCategories.forEach { cat ->
            val selected = state.selectedCategory == cat
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (selected) CardSurfaceElevated else CardSurface,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { viewModel.onCategorySelected(cat) },
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) WhatsAppGreen else TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        }
      }

      if (state.templates.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.Bookmarks,
            title = "Sin plantillas coincidentes",
            description = "Guarda mensajes o secuencias desde el Editor para organizarlos por categorías, etiquetas y favoritos.",
          )
        }
      } else {
        items(state.templates, key = { it.id }) { template ->
          TemplateLibraryCard(
            template = template,
            onToggleFavorite = { viewModel.toggleFavorite(template) },
            onTogglePinned = { viewModel.togglePinned(template) },
            onDuplicate = { viewModel.duplicateWithoutMutating(template) },
            onEditMetadata = { editingMetaFor = template },
            onDelete = { deletingId = template.id },
            onUse = {
              viewModel.applyTemplateToDraft(template)
              onOpenTemplateInEditor(template.isMultipleMode)
            },
          )
        }
      }
    }
  }

  val targetEdit = editingMetaFor
  if (targetEdit != null) {
    EditTemplateMetadataDialog(
      item = targetEdit,
      onDismiss = { editingMetaFor = null },
      onSave = { title, cat, tags ->
        viewModel.updateMetadata(targetEdit, title, cat, tags)
        editingMetaFor = null
      },
    )
  }

  val targetDelete = deletingId
  if (targetDelete != null) {
    AlertDialog(
      onDismissRequest = { deletingId = null },
      containerColor = CardSurface,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Eliminar plantilla") },
      text = { Text("¿Eliminar permanentemente esta plantilla de la Biblioteca local?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteTemplate(targetDelete)
            deletingId = null
          },
        ) {
          Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { deletingId = null }) {
          Text("Cancelar", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun QuickHighlightCard(
  item: SavedMessageList,
  onUse: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = CardSurface,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier
      .width(200.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onUse),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = item.category,
          style = MaterialTheme.typography.labelSmall,
          color = WhatsAppGreen,
        )
        Text(
          text = "${item.useCount} usos",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = item.title,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val preview = if (item.isMultipleMode) item.messages.firstOrNull().orEmpty() else item.singleMessage
      Text(
        text = preview,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TemplateLibraryCard(
  template: SavedMessageList,
  onToggleFavorite: () -> Unit,
  onTogglePinned: () -> Unit,
  onDuplicate: () -> Unit,
  onEditMetadata: () -> Unit,
  onDelete: () -> Unit,
  onUse: () -> Unit,
) {
  SectionCard(
    borderColor = if (template.isPinned) WhatsAppGreen.copy(alpha = 0.7f) else OutlineSubtle,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = template.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
          )
          StatusBadge(template.category, CardSurfaceElevated, WhatsAppGreen)
        }
        val modeInfo = if (template.isMultipleMode) {
          "Secuencia (${template.messages.size} mensajes) • ${template.useCount} usos"
        } else {
          "Repetir ${template.repeatCount}x • ${template.useCount} usos"
        }
        Text(modeInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
      Row {
        IconButton(onClick = onTogglePinned) {
          Icon(
            Icons.Rounded.PushPin,
            contentDescription = if (template.isPinned) "Desfijar" else "Fijar",
            tint = if (template.isPinned) WhatsAppGreen else TextSecondary,
          )
        }
        IconButton(onClick = onToggleFavorite) {
          Icon(
            imageVector = if (template.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = if (template.isFavorite) "Quitar favorito" else "Favorito",
            tint = if (template.isFavorite) WarningAmber else TextSecondary,
          )
        }
      }
    }

    val previewText = if (template.isMultipleMode) {
      template.messages.joinToString(" • ")
    } else {
      template.singleMessage
    }
    if (previewText.isNotBlank()) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = previewText,
        style = MaterialTheme.typography.bodySmall,
        color = TextPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (template.tags.isNotEmpty()) {
      Spacer(modifier = Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        template.tags.forEach { tag ->
          Surface(shape = RoundedCornerShape(999.dp), color = CardSurfaceElevated) {
            Text(
              text = "#$tag",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row {
        IconButton(onClick = onDuplicate) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicar sin modificar original", tint = TextPrimary)
        }
        IconButton(onClick = onEditMetadata) {
          Icon(Icons.Rounded.Edit, contentDescription = "Editar categoría y etiquetas", tint = TextSecondary)
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = DangerRed)
        }
      }
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = WhatsAppGreenContainer,
        border = BorderStroke(1.dp, WhatsAppGreen),
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onUse),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reutilizar copia", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun EditTemplateMetadataDialog(
  item: SavedMessageList,
  onDismiss: () -> Unit,
  onSave: (title: String, category: String, tags: List<String>) -> Unit,
) {
  var title by remember { mutableStateOf(item.title) }
  var category by remember { mutableStateOf(item.category) }
  var tagsRaw by remember { mutableStateOf(item.tags.joinToString(", ")) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    titleContentColor = TextPrimary,
    textContentColor = TextSecondary,
    title = { Text("Organizar plantilla") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(48) },
          label = { Text("Nombre") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WhatsAppGreen,
            unfocusedBorderColor = OutlineSubtle,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          templateCategories.forEach { cat ->
            val selected = category == cat
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) WhatsAppGreenContainer else CardSurfaceElevated,
              border = BorderStroke(1.dp, if (selected) WhatsAppGreen else OutlineSubtle),
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { category = cat },
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelSmall,
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
      }
    },
    confirmButton = {
      TextButton(
        enabled = title.isNotBlank(),
        onClick = {
          onSave(title, category, tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        },
      ) {
        Text("Guardar", color = WhatsAppGreen, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar", color = TextSecondary)
      }
    },
  )
}
