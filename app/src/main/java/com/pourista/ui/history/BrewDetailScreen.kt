package com.pourista.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.ui.listSidePadding
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.core.formatTimerWithTenths
import com.pourista.data.model.BrewNotes
import com.pourista.ui.components.LabeledChart
import com.pourista.ui.components.StatTile
import com.pourista.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    viewModel: BrewDetailViewModel,
    onClose: () -> Unit,
    onOpenDraft: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val record by viewModel.brew.collectAsStateWithLifecycle()

    // Имя нового рецепта: как называлось заваривание, иначе «Запись» с датой.
    val recordedName = stringResource(R.string.recipe_recorded_name)
    val recipeName = record?.recipeName?.takeIf { it.isNotBlank() }
        ?: "$recordedName ${formatDateTime(record?.brewedAt ?: 0L)}"

    var bean by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var grinder by remember { mutableStateOf("") }
    var grind by remember { mutableStateOf("") }
    var brewer by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }

    LaunchedEffect(record?.id) {
        val notes = record?.notes ?: return@LaunchedEffect
        bean = notes.bean.orEmpty()
        roaster = notes.roaster.orEmpty()
        grinder = notes.grinder.orEmpty()
        grind = notes.grindSetting.orEmpty()
        brewer = notes.brewer.orEmpty()
        temp = notes.waterTemp.orEmpty()
        extra = notes.extra.orEmpty()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                title = { Text(stringResource(R.string.history_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveNotes(
                                BrewNotes(
                                    bean = bean.takeIf { it.isNotBlank() },
                                    roaster = roaster.takeIf { it.isNotBlank() },
                                    grinder = grinder.takeIf { it.isNotBlank() },
                                    grindSetting = grind.takeIf { it.isNotBlank() },
                                    brewer = brewer.takeIf { it.isNotBlank() },
                                    waterTemp = temp.takeIf { it.isNotBlank() },
                                    extra = extra.takeIf { it.isNotBlank() },
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                    // Рецепт из записи: пролив уже случился, повторить его
                    // проще по готовым шагам, чем восстанавливать по графику.
                    IconButton(onClick = { if (viewModel.buildRecipe(recipeName)) onOpenDraft() }) {
                        Icon(
                            Icons.Default.PlaylistAdd,
                            stringResource(R.string.history_make_recipe),
                        )
                    }
                    IconButton(onClick = { viewModel.delete(onClose) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = record
        val side = listSidePadding()
        LazyColumn(
            // Клавиатура перекрывала нижние поля: список ужимается на её
            // высоту, и поле, в которое пишут, само выезжает на видное место.
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current == null) return@LazyColumn

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = current.recipeName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.history_untitled),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = formatDateTime(current.brewedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatTile(
                                label = stringResource(R.string.readout_dose),
                                value = formatGrams(current.doseGrams),
                                unit = stringResource(R.string.unit_gram),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = stringResource(R.string.readout_weight),
                                value = formatGrams(current.weightGrams, 0),
                                unit = stringResource(R.string.unit_gram),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = stringResource(R.string.readout_ratio),
                                value = formatRatio(current.doseGrams, current.weightGrams),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = stringResource(R.string.readout_time),
                                value = formatTimerWithTenths(current.elapsedMs),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (current.weightSeries.size > 1) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            LabeledChart(
                                title = stringResource(R.string.chart_weight),
                                unit = stringResource(R.string.unit_gram),
                                values = current.weightSeries,
                            )
                        }
                    }
                }
            }

            if (current.flowSeries.size > 1) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            LabeledChart(
                                title = stringResource(R.string.chart_flow),
                                unit = stringResource(R.string.unit_gram_per_second),
                                values = current.flowSeries,
                                lineColor = AppTheme.accents.water,
                                height = 72.dp,
                                ticks = 2,
                            )
                            Text(
                                text = stringResource(
                                    R.string.chart_flow_average,
                                    formatGrams(current.flowRateAvg),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.history_notes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        NotesField(bean, { bean = it }, R.string.recipe_bean)
                        NotesField(roaster, { roaster = it }, R.string.recipe_roaster)
                        NotesField(brewer, { brewer = it }, R.string.recipe_brewer)
                        NotesField(grinder, { grinder = it }, R.string.recipe_grinder)
                        NotesField(grind, { grind = it }, R.string.recipe_grind)
                        NotesField(temp, { temp = it }, R.string.recipe_temp)
                        NotesField(extra, { extra = it }, R.string.history_extra_note)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesField(value: String, onValueChange: (String) -> Unit, labelRes: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}
