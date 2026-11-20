package com.whatsapprepeater.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.core.data.backup.TemplateBundleCodec
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryFilterTab(val label: String) {
  ALL("Todas"),
  FAVORITES("Favoritas"),
  PINNED("Fijadas"),
  FREQUENT("Más usadas"),
  RECENT("Recientes"),
}

enum class LibrarySortOrder(val label: String) {
  RECENT("Recientes"),
  MOST_USED("Más usadas"),
  TITLE_ASC("A → Z"),
  TITLE_DESC("Z → A"),
  FAVORITES_FIRST("Favoritas"),
}

data class SmartLibraryUiState(
  val searchQuery: String = "",
  val selectedTab: LibraryFilterTab = LibraryFilterTab.ALL,
  val selectedCategory: String = "Todas",
  val selectedSort: LibrarySortOrder = LibrarySortOrder.RECENT,
  val availableCategories: List<String> = listOf("Todas"),
  val templates: List<SavedMessageList> = emptyList(),
  val favoritesSection: List<SavedMessageList> = emptyList(),
  val recentsSection: List<SavedMessageList> = emptyList(),
  val feedback: String? = null,
  val isErrorFeedback: Boolean = false,
)

class SmartLibraryViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedTab = MutableStateFlow(LibraryFilterTab.ALL)
  private val selectedCategory = MutableStateFlow("Todas")
  private val selectedSort = MutableStateFlow(LibrarySortOrder.RECENT)
  private val feedback = MutableStateFlow<String?>(null)
  private val isErrorFeedback = MutableStateFlow(false)

  private val filtersFlow = combine(
    searchQuery,
    selectedTab,
    selectedCategory,
    selectedSort,
  ) { q, tab, cat, sort ->
    LibraryFilters(q, tab, cat, sort)
  }

  val uiState: StateFlow<SmartLibraryUiState> = combine(
    container.savedConfigRepository.observeSavedMessageLists(),
    filtersFlow,
    feedback,
    isErrorFeedback,
  ) { allTemplates, filters, msg, err ->
    val categories = (listOf("Todas") + allTemplates.map { it.category.ifBlank { "General" } }).distinct()

    val favorites = allTemplates.filter { it.isFavorite || it.isPinned }.take(6)
    val recents = allTemplates
      .filter { it.lastUsedAtMillis > 0L || it.updatedAtMillis > 0L }
      .sortedByDescending { maxOf(it.lastUsedAtMillis, it.updatedAtMillis) }
      .take(6)

    val byTab = when (filters.tab) {
      LibraryFilterTab.ALL -> allTemplates
      LibraryFilterTab.FAVORITES -> allTemplates.filter { it.isFavorite }
      LibraryFilterTab.PINNED -> allTemplates.filter { it.isPinned }
      LibraryFilterTab.FREQUENT -> allTemplates.sortedByDescending { it.useCount }
      LibraryFilterTab.RECENT -> allTemplates.sortedByDescending { maxOf(it.lastUsedAtMillis, it.updatedAtMillis) }
    }

    val byCategory = if (filters.category == "Todas") byTab else byTab.filter { it.category == filters.category }

    val cleanQuery = filters.query.trim().lowercase()
    val filtered = if (cleanQuery.isEmpty()) {
      byCategory
    } else {
      byCategory.filter { item ->
        item.title.lowercase().contains(cleanQuery) ||
          item.category.lowercase().contains(cleanQuery) ||
          item.singleMessage.lowercase().contains(cleanQuery) ||
          item.tags.any { it.lowercase().contains(cleanQuery) } ||
          item.messages.any { it.lowercase().contains(cleanQuery) }
      }
    }

    val sorted = when (filters.sort) {
      LibrarySortOrder.RECENT -> filtered.sortedByDescending { maxOf(it.lastUsedAtMillis, it.updatedAtMillis) }
      LibrarySortOrder.MOST_USED -> filtered.sortedByDescending { it.useCount }
      LibrarySortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
      LibrarySortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
      LibrarySortOrder.FAVORITES_FIRST -> filtered.sortedWith(
        compareByDescending<SavedMessageList> { it.isPinned }
          .thenByDescending { it.isFavorite }
          .thenByDescending { it.updatedAtMillis },
      )
    }

    SmartLibraryUiState(
      searchQuery = filters.query,
      selectedTab = filters.tab,
      selectedCategory = filters.category,
      selectedSort = filters.sort,
      availableCategories = categories,
      templates = sorted,
      favoritesSection = favorites,
      recentsSection = recents,
      feedback = msg,
      isErrorFeedback = err,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SmartLibraryUiState(),
  )

  private data class LibraryFilters(
    val query: String,
    val tab: LibraryFilterTab,
    val category: String,
    val sort: LibrarySortOrder,
  )

  fun onSearchChanged(q: String) {
    searchQuery.value = q
    feedback.value = null
  }

  fun onTabSelected(tab: LibraryFilterTab) {
    selectedTab.value = tab
  }

  fun onCategorySelected(category: String) {
    selectedCategory.value = category
  }

  fun onSortSelected(sort: LibrarySortOrder) {
    selectedSort.value = sort
  }

  fun buildExportJson(): String = TemplateBundleCodec.exportJson(uiState.value.templates)

  fun notifyExportResult(success: Boolean, detail: String) {
    isErrorFeedback.value = !success
    feedback.value = detail
  }

  fun importTemplatesJson(rawJson: String) {
    viewModelScope.launch {
      try {
        val parsed = TemplateBundleCodec.importJson(rawJson)
        val existingKeys = uiState.value.templates.map {
          "${it.title.trim().lowercase()}|${it.isMultipleMode}|${it.singleMessage.trim()}|${it.messages.joinToString("\n")}"
        }.toSet()

        var importedCount = 0
        withContext(NonCancellable) {
          parsed.forEach { tpl ->
            val key = "${tpl.title.trim().lowercase()}|${tpl.isMultipleMode}|${tpl.singleMessage.trim()}|${tpl.messages.joinToString("\n")}"
            if (key !in existingKeys) {
              container.savedConfigRepository.saveMessageList(tpl)
              importedCount++
            }
          }
        }
        isErrorFeedback.value = false
        feedback.value = if (importedCount > 0) {
          "Se importaron $importedCount plantilla(s) validadas (sin datos de contactos)."
        } else {
          "Las plantillas del archivo ya existían en tu Biblioteca."
        }
      } catch (e: Exception) {
        isErrorFeedback.value = true
        feedback.value = e.message ?: "No se pudo validar el archivo de plantillas."
      }
    }
  }

  fun toggleFavorite(item: SavedMessageList) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.setFavorite(item.id, !item.isFavorite)
      }
    }
  }

  fun togglePinned(item: SavedMessageList) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.setPinned(item.id, !item.isPinned)
      }
    }
  }

  fun duplicateWithoutMutating(item: SavedMessageList) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        val newId = container.savedConfigRepository.duplicateMessageList(item.id)
        isErrorFeedback.value = newId == null
        feedback.value = if (newId != null) {
          "Duplicada como \"${item.title} (copia)\" sin alterar la original."
        } else {
          "No se pudo duplicar la plantilla."
        }
      }
    }
  }

  fun updateMetadata(
    item: SavedMessageList,
    newTitle: String,
    newCategory: String,
    newTags: List<String>,
  ) {
    val clean = newTitle.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.saveMessageList(
          item.copy(
            title = clean,
            category = newCategory.trim().ifBlank { "General" },
            tags = newTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            updatedAtMillis = System.currentTimeMillis(),
          ),
        )
        isErrorFeedback.value = false
        feedback.value = "Plantilla actualizada."
      }
    }
  }

  fun applyTemplateToDraft(item: SavedMessageList) {
    container.loadTemplateIntoDraft(item)
  }

  fun deleteTemplate(id: Long) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        container.savedConfigRepository.deleteMessageList(id)
        isErrorFeedback.value = false
        feedback.value = "Plantilla eliminada."
      }
    }
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SmartLibraryViewModel(container) as T
      }
  }
}
