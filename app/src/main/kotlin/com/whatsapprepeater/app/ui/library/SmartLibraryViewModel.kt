package com.whatsapprepeater.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
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

data class SmartLibraryUiState(
  val searchQuery: String = "",
  val selectedTab: LibraryFilterTab = LibraryFilterTab.ALL,
  val selectedCategory: String = "Todas",
  val availableCategories: List<String> = listOf("Todas"),
  val templates: List<SavedMessageList> = emptyList(),
  val favoritesSection: List<SavedMessageList> = emptyList(),
  val recentsSection: List<SavedMessageList> = emptyList(),
  val feedback: String? = null,
)

class SmartLibraryViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedTab = MutableStateFlow(LibraryFilterTab.ALL)
  private val selectedCategory = MutableStateFlow("Todas")
  private val feedback = MutableStateFlow<String?>(null)

  val uiState: StateFlow<SmartLibraryUiState> = combine(
    container.savedConfigRepository.observeSavedMessageLists(),
    searchQuery,
    selectedTab,
    selectedCategory,
    feedback,
  ) { allTemplates, query, tab, category, msg ->
    val categories = (listOf("Todas") + allTemplates.map { it.category.ifBlank { "General" } }).distinct()

    val favorites = allTemplates.filter { it.isFavorite || it.isPinned }.take(6)
    val recents = allTemplates
      .filter { it.lastUsedAtMillis > 0L || it.updatedAtMillis > 0L }
      .sortedByDescending { maxOf(it.lastUsedAtMillis, it.updatedAtMillis) }
      .take(6)

    val byTab = when (tab) {
      LibraryFilterTab.ALL -> allTemplates
      LibraryFilterTab.FAVORITES -> allTemplates.filter { it.isFavorite }
      LibraryFilterTab.PINNED -> allTemplates.filter { it.isPinned }
      LibraryFilterTab.FREQUENT -> allTemplates.sortedByDescending { it.useCount }
      LibraryFilterTab.RECENT -> allTemplates.sortedByDescending { maxOf(it.lastUsedAtMillis, it.updatedAtMillis) }
    }

    val byCategory = if (category == "Todas") byTab else byTab.filter { it.category == category }

    val cleanQuery = query.trim().lowercase()
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

    SmartLibraryUiState(
      searchQuery = query,
      selectedTab = tab,
      selectedCategory = category,
      availableCategories = categories,
      templates = filtered,
      favoritesSection = favorites,
      recentsSection = recents,
      feedback = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SmartLibraryUiState(),
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
