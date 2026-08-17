package com.pourista.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.data.model.Recipe
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.components.StepsToggle
import com.pourista.ui.components.dragHandle
import com.pourista.ui.components.rememberReorderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipesViewModel,
    onEdit: (Long) -> Unit,
    onCreate: () -> Unit,
    onBrew: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val copySuffix = stringResource(R.string.recipe_copy_suffix)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var fileMenuOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Во время перетаскивания порядок живёт здесь: база узнаёт о нём один раз,
    // когда карточку отпустили.
    var dragOrder by remember { mutableStateOf<List<Recipe>?>(null) }
    val shown = dragOrder ?: recipes
    val reorder = rememberReorderState(
        listState = listState,
        scope = scope,
        onMove = { from, to ->
            val current = dragOrder ?: recipes
            val fromIndex = from - HEADER_ITEMS
            val toIndex = to - HEADER_ITEMS
            if (fromIndex in current.indices && toIndex in current.indices) {
                dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        },
        onDrop = {
            dragOrder?.let { order -> viewModel.reorder(order.map { it.id }) }
            dragOrder = null
        },
    )

    // Файл выбирает система: приложению не нужен доступ ко всему хранилищу.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME)
    ) { uri -> uri?.let(viewModel::writePendingExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(current.textRes, current.count))
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_recipes)) },
                actions = {
                    // Меню с подписями: одни стрелки читались как перестановка
                    // рецептов, хотя это обмен файлами.
                    Box {
                        IconButton(onClick = { fileMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(
                            expanded = fileMenuOpen,
                            onDismissRequest = { fileMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_import_menu)) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = {
                                    fileMenuOpen = false
                                    importLauncher.launch(IMPORT_MIME)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_import_clipboard)) },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                onClick = {
                                    fileMenuOpen = false
                                    viewModel.importText(clipboard.getText()?.text)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_export_all)) },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                                onClick = {
                                    fileMenuOpen = false
                                    viewModel.prepareExportAll()
                                    exportLauncher.launch(EXPORT_ALL_FILE)
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.recipe_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                )
            }

            if (recipes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recipe_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            }

            items(shown, key = { recipe -> recipe.id }) { recipe ->
                val dragging = reorder.draggingKey == recipe.id
                RecipeCard(
                    recipe = recipe,
                    dragging = dragging,
                    // При поиске список показан не целиком, и перестановка
                    // внутри выборки перемешала бы порядок остальных рецептов.
                    dragHandle = if (query.isBlank()) {
                        Modifier.dragHandle(reorder, recipe.id)
                    } else {
                        null
                    },
                    modifier = Modifier.graphicsLayer {
                        if (dragging) {
                            translationY = reorder.draggedOffset
                            shadowElevation = DRAG_ELEVATION
                        }
                    },
                    onOpen = { onEdit(recipe.id) },
                    onBrew = {
                        viewModel.brewWith(recipe)
                        onBrew()
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                    onDuplicate = {
                        viewModel.duplicate(recipe, copySuffix) { newId -> onEdit(newId) }
                    },
                    onDelete = { viewModel.delete(recipe) },
                    onExport = {
                        viewModel.prepareExport(listOf(recipe))
                        exportLauncher.launch("${recipe.name.take(40)}.json")
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    dragging: Boolean,
    dragHandle: Modifier?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onBrew: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Развёрнутый рецепт переживает прокрутку списка: список ключует карточки,
    // и состояние возвращается вместе с ними.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = if (dragging) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Тянуть можно только за ручку: иначе жест конфликтует с
                // прокруткой списка.
                if (dragHandle != null) {
                    // Палец должен попадать в ручку не целясь, поэтому под
                    // иконкой площадка размером с кнопку.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = dragHandle
                            .size(40.dp)
                            .padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.action_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = recipe.brewer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (recipe.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (recipe.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_export)) },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = {
                                menuOpen = false
                                onExport()
                            },
                        )
                        // Встроенные рецепты удаляются наравне со своими: чужой
                        // способ заваривания не должен занимать место навсегда.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SuggestionChip(
                    onClick = onOpen,
                    label = {
                        Text(
                            stringResource(
                                R.string.recipe_summary,
                                formatGrams(recipe.doseGrams),
                                formatGrams(recipe.waterGrams, 0),
                                formatRatio(recipe.doseGrams, recipe.waterGrams),
                            )
                        )
                    },
                )
                SuggestionChip(
                    onClick = onOpen,
                    label = { Text("${recipe.waterTempC} °C") },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.recipe_steps_summary,
                            recipe.pourCount,
                            formatClock(recipe.totalSec),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val grind = recipe.grindSetting
                    if (!grind.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.recipe_grind_value, grind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Разворот прямо в списке: чтобы понять, подходит ли рецепт,
                // открывать редактор незачем.
                if (recipe.steps.isNotEmpty()) {
                    StepsToggle(expanded = expanded, onToggle = { expanded = !expanded })
                }
                IconButton(onClick = onBrew) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_brew_this),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    RecipeStepsList(steps = recipe.steps)
                }
            }
        }
    }
}

private const val EXPORT_MIME = "application/json"
private val IMPORT_MIME = arrayOf("application/json", "text/plain", "*/*")
private const val EXPORT_ALL_FILE = "pourista-recipes.json"

/** Над списком рецептов стоит поле поиска: на него сдвинуты индексы LazyColumn. */
private const val HEADER_ITEMS = 1
private const val DRAG_ELEVATION = 12f
