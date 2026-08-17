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

/** Итог импорта или экспорта: текст для снекбара со счётчиком рецептов. */
data class RecipesMessage(val textRes: Int, val count: Int = 0)

class RecipesViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<RecipesMessage?>(null)
    val message: StateFlow<RecipesMessage?> = _message.asStateFlow()

    /** Что уходит в файл, когда система вернёт выбранный путь. */
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
                        recipe.grindSetting?.lowercase()?.contains(needle) == true
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
     * Удаляет рецепт. Встроенный ещё и запоминается удалённым, иначе он вернулся
     * бы при следующем обновлении набора пресетов.
     */
    fun delete(recipe: Recipe) {
        viewModelScope.launch {
            if (recipe.isBuiltIn) container.settings.addDeletedPreset(recipe.name)
            container.recipes.delete(recipe.id)
        }
    }

    /** Порядок после перетаскивания: пишем разом весь список. */
    fun reorder(ids: List<Long>) {
        viewModelScope.launch { container.recipes.reorder(ids) }
    }

    fun duplicate(recipe: Recipe, copySuffix: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = container.recipes.duplicate(recipe, "${recipe.name} $copySuffix")
            onCreated(id)
        }
    }

    /** Заваривание по рецепту начинается с чистого листа: старые следы ни к чему. */
    fun brewWith(recipe: Recipe) {
        container.brewEngine.reset()
        container.brewEngine.selectRecipe(recipe)
        viewModelScope.launch {
            container.settings.setLastRecipeId(recipe.id)
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
                    } ?: error("Не удалось открыть файл")
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
     * Импорт из вставленного текста: рецепт, составленный нейросетью, проще
     * скопировать в буфер, чем сохранять в файл и потом искать его.
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
                RecipesMessage(R.string.recipes_imported, container.recipes.importAll(parsed))
            }
        }
    }
}
