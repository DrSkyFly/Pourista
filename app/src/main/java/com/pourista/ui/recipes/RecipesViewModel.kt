package com.pourista.ui.recipes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.R
import com.pourista.data.io.RecipeJson
import com.pourista.data.model.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The result of an import or an export: the text for a snackbar with the recipe count. */
data class RecipesMessage(val textRes: Int, val count: Int = 0)

class RecipesViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<RecipesMessage?>(null)
    val message: StateFlow<RecipesMessage?> = _message.asStateFlow()

    /** What goes into the file once the system returns the chosen path. */
    private var pendingExport: List<Recipe> = emptyList()

    val recipes: StateFlow<List<Recipe>> =
        combine(container.recipes.observeRecipes(), _query) { all, query ->
            if (query.isBlank()) {
                all
            } else {
                val needle = query.trim().lowercase()
                all.filter { recipe ->
                    recipe.name.lowercase().contains(needle) ||
                        recipe.brewer.lowercase().contains(needle) ||
                        recipe.beanName?.lowercase()?.contains(needle) == true ||
                        recipe.grindSetting?.lowercase()?.contains(needle) == true ||
                        recipe.filterName?.lowercase()?.contains(needle) == true
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            container.recipes.setFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    /**
     * Deletes a recipe. A built-in one is also remembered as deleted, otherwise it would come back
     * with the next update of the preset set.
     */
    fun delete(recipe: Recipe) {
        viewModelScope.launch {
            if (recipe.isBuiltIn) container.settings.addDeletedPreset(recipe.name)
            container.recipes.delete(recipe.id)
        }
    }

    /** The order after a drag: we write the whole list at once. */
    fun reorder(ids: List<Long>) {
        viewModelScope.launch { container.recipes.reorder(ids) }
    }

    fun duplicate(recipe: Recipe, copySuffix: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = container.recipes.duplicate(recipe, "${recipe.name} $copySuffix")
            onCreated(id)
        }
    }

    /** Brewing to a recipe starts with a clean sheet: old traces are of no use. */
    fun brewWith(recipe: Recipe) {
        container.brewEngine.reset()
        container.brewEngine.selectRecipe(recipe)
        viewModelScope.launch {
            container.settings.setLastRecipe(recipe.id)
            container.recipes.markUsed(recipe.id)
        }
    }

    fun prepareExport(recipes: List<Recipe>) {
        pendingExport = recipes
    }

    fun prepareExportAll() {
        pendingExport = recipes.value
    }

    fun writePendingExport(uri: Uri) {
        val payload = pendingExport
        pendingExport = emptyList()
        if (payload.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(RecipeJson.encode(payload).toByteArray())
                    } ?: error("Could not open the file")
                }.isSuccess
            }
            _message.value = if (ok) {
                RecipesMessage(R.string.recipes_exported, payload.size)
            } else {
                RecipesMessage(R.string.recipes_export_failed)
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            importText(text)
        }
    }

    /**
     * Import from pasted text: a recipe composed by a neural network is easier to copy to the
     * clipboard than to save to a file and then look for it.
     */
    fun importText(text: String?) {
        if (text.isNullOrBlank()) {
            _message.value = RecipesMessage(R.string.recipes_clipboard_empty)
            return
        }
        viewModelScope.launch {
            val parsed = runCatching { RecipeJson.decode(text) }.getOrNull()
            _message.value = if (parsed == null) {
                RecipesMessage(R.string.recipes_import_failed)
            } else {
                RecipesMessage(R.string.recipes_imported, container.recipes.importAll(parsed).size)
            }
        }
    }
}
