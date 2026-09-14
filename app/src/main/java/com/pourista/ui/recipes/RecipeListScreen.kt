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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.ui.listSidePadding
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.data.model.Recipe
import com.pourista.ui.components.EmptyState
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.components.SearchField
import com.pourista.ui.components.StepsToggle
import com.pourista.ui.components.reorderByLongPress
import com.pourista.ui.theme.AppTheme
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
    // A card taken for rearranging — that has to be felt rather than merely seen: at that moment
    // the finger is covering the card itself.
    val haptics = LocalHapticFeedback.current
    var fileMenuOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // During a drag the order lives here: the database learns about it once, when the card is let
    // go.
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

    // The file is picked by the system: the app needs no access to the whole storage.
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

    // The header moves away on scroll: the screen is long, and the header holds a single word.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.tab_recipes)) },
                actions = {
                    // A menu with labels: arrows alone read as rearranging recipes, though this is
                    // exchanging files.
                    Box {
                        IconButton(onClick = { fileMenuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(
                            expanded = fileMenuOpen,
                            onDismissRequest = { fileMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_import_menu)) },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                                onClick = {
                                    fileMenuOpen = false
                                    importLauncher.launch(IMPORT_MIME)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_import_clipboard)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                                onClick = {
                                    fileMenuOpen = false
                                    viewModel.importText(clipboard.getText()?.text)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_export_all)) },
                                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
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
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.recipe_new)) },
            )
        },
    ) { padding ->
        val side = listSidePadding()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchField(value = query, onValueChange = viewModel::setQuery)
            }

            if (recipes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        text = stringResource(R.string.recipe_empty),
                        actionText = stringResource(R.string.recipe_new),
                        onAction = onCreate,
                    )
                }
            }

            items(shown, key = { recipe -> recipe.id }) { recipe ->
                val dragging = reorder.draggingKey == recipe.id
                RecipeCard(
                    recipe = recipe,
                    dragging = dragging,
                    // During a search the list is not shown whole, and rearranging inside the
                    // selection would shuffle the order of the other recipes.
                    reorderModifier = if (query.isBlank()) {
                        Modifier.reorderByLongPress(reorder, recipe.id) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    } else {
                        null
                    },
                    // The dragged card is moved by the finger, the rest by the list: their
                    // rearrangement has to be smooth, otherwise the neighbours jump to their new place
                    // in one lurch.
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .graphicsLayer {
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
                        exportLauncher.launch("${recipe.name.take(40)}.pour")
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
    /** Dragging a card, if the order can be changed right now. */
    reorderModifier: Modifier?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onBrew: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // An expanded recipe outlives the scrolling of the list: the list keys the cards, and the state
    // comes back together with them.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        // A long press shorter than usual: the card is taken for rearranging, while a short press
        // still opens the recipe.
        modifier = modifier
            .fillMaxWidth()
            .then(reorderModifier ?: Modifier)
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
                        imageVector = if (recipe.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
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
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_export)) },
                            leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                            onClick = {
                                menuOpen = false
                                onExport()
                            },
                        )
                        // Built-in recipes are deleted on a par with one's own: someone else's way of
                        // brewing should not take up room forever.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
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
                // Expanding right in the list: there is no need to open the editor to see whether a
                // recipe fits.
                if (recipe.steps.isNotEmpty()) {
                    StepsToggle(expanded = expanded, onToggle = { expanded = !expanded })
                }
                // Brewing is the main action of the card, and it should look like a button rather
                // than an icon in a row with the star and the menu.
                FilledTonalIconButton(onClick = onBrew) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_brew_this),
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

/**
 * A file type of our own: by it the system recognises a recipe and offers to open it in the app.
 * Inside it is the same JSON — it is read and written by hand.
 */
private const val EXPORT_MIME = "application/vnd.pourista.recipe"

/** Picking a file: our own type, and then everything it could have been called on the way. */
private val IMPORT_MIME =
    arrayOf(EXPORT_MIME, "application/json", "text/plain", "*/*")

private const val EXPORT_ALL_FILE = "pourista-recipes.pour"

/** A search field stands above the recipe list: the LazyColumn indices are shifted for it. */
private const val HEADER_ITEMS = 1
private const val DRAG_ELEVATION = 12f
