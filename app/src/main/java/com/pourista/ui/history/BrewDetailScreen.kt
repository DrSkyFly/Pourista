package com.pourista.ui.history

import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.ui.listSidePadding
import com.pourista.ui.share.BrewImage
import com.pourista.ui.share.BrewImageColors
import com.pourista.ui.share.BrewImageContent
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.core.formatTimerWithTenths
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import com.pourista.ui.components.FLOW_AXIS_STEPS
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

    // Всё для картинки собираем здесь: в отрисовке нет ни ресурсов, ни темы.
    val context = LocalContext.current
    val current = record
    val imageColors = BrewImageColors(
        background = MaterialTheme.colorScheme.surface,
        onBackground = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        weightLine = MaterialTheme.colorScheme.primary,
        flowLine = AppTheme.accents.water,
        grid = MaterialTheme.colorScheme.outlineVariant,
    )
    val texts = BrewShareTexts(
        title = recipeName,
        subtitle = formatDateTime(current?.brewedAt ?: 0L),
        facts = stringResource(
            R.string.history_summary,
            formatGrams(current?.doseGrams ?: 0f),
            formatGrams(current?.weightGrams ?: 0f, 0),
            formatRatio(current?.doseGrams ?: 0f, current?.weightGrams ?: 0f),
        ) + " · " + formatTimerWithTenths(current?.elapsedMs ?: 0L),
        details = listOfNotNull(
            current?.notes?.bean?.takeIf { it.isNotBlank() },
            current?.notes?.roaster?.takeIf { it.isNotBlank() },
            current?.notes?.grinder?.takeIf { it.isNotBlank() },
            current?.notes?.grindSetting?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.recipe_grind_value, it) },
            current?.notes?.filterName?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.recipe_filter_value, it) },
        ).joinToString(" · ").takeIf { it.isNotBlank() },
        weightTitle = stringResource(R.string.chart_weight),
        flowTitle = stringResource(R.string.chart_flow),
        footer = stringResource(R.string.history_share_footer),
        chooser = stringResource(R.string.history_share),
    )

    var bean by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var grinder by remember { mutableStateOf("") }
    var grind by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var brewer by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }

    LaunchedEffect(record?.id) {
        val notes = record?.notes ?: return@LaunchedEffect
        bean = notes.bean.orEmpty()
        roaster = notes.roaster.orEmpty()
        grinder = notes.grinder.orEmpty()
        grind = notes.grindSetting.orEmpty()
        filter = notes.filterName.orEmpty()
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
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Кнопка гаснет, когда менять нечего: так видно, что
                    // сохранять нечего, а не что она сломана.
                    val edited = current?.notes?.let { saved ->
                        bean != saved.bean.orEmpty() ||
                            roaster != saved.roaster.orEmpty() ||
                            grinder != saved.grinder.orEmpty() ||
                            grind != saved.grindSetting.orEmpty() ||
                            filter != saved.filterName.orEmpty() ||
                            brewer != saved.brewer.orEmpty() ||
                            temp != saved.waterTemp.orEmpty() ||
                            extra != saved.extra.orEmpty()
                    } ?: false
                    IconButton(
                        enabled = edited,
                        onClick = {
                            viewModel.saveNotes(
                                BrewNotes(
                                    bean = bean.takeIf { it.isNotBlank() },
                                    roaster = roaster.takeIf { it.isNotBlank() },
                                    grinder = grinder.takeIf { it.isNotBlank() },
                                    grindSetting = grind.takeIf { it.isNotBlank() },
                                    filterName = filter.takeIf { it.isNotBlank() },
                                    brewer = brewer.takeIf { it.isNotBlank() },
                                    waterTemp = temp.takeIf { it.isNotBlank() },
                                    extra = extra.takeIf { it.isNotBlank() },
                                )
                            )
                            // Сохранили — и сразу назад к списку: держать
                            // человека в карточке после этого незачем.
                            onClose()
                        }
                    ) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.action_save))
                    }
                    // Картинку собираем заново, а не снимаем экран: снимок
                    // обрезан по высоте телефона и тащит поля с кнопками.
                    IconButton(
                        onClick = { current?.let { shareBrew(context, it, imageColors, texts) } },
                        enabled = current?.weightSeries.orEmpty().size > 1,
                    ) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.history_share))
                    }
                    IconButton(onClick = { viewModel.delete(onClose) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete))
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
                                // В истории экран листается: графику можно
                                // отдать высоту, на которой цифры не жмутся.
                                height = 200.dp,
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
                                height = 130.dp,
                                axisSteps = FLOW_AXIS_STEPS,
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

            // Рецепт из записи: пролив уже случился, повторить его проще по
            // готовым шагам. Значка в шапке для этого мало — нужна подпись.
            if (current.weightSeries.size > 1) {
                item {
                    Button(
                        onClick = { if (viewModel.buildRecipe(recipeName)) onOpenDraft() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.history_make_recipe))
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
                        NotesField(filter, { filter = it }, R.string.recipe_filter)
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

/** Тексты картинки: собираются из ресурсов на экране, отрисовке они приходят готовыми. */
private data class BrewShareTexts(
    val title: String,
    val subtitle: String,
    val facts: String,
    val details: String?,
    val weightTitle: String,
    val flowTitle: String,
    val footer: String,
    val chooser: String,
)

/** Рисует картинку заваривания, кладёт в кэш и отдаёт системе «поделиться». */
private fun shareBrew(
    context: Context,
    record: BrewRecord,
    colors: BrewImageColors,
    texts: BrewShareTexts,
) {
    val bitmap = BrewImage.render(
        context = context,
        content = BrewImageContent(
            title = texts.title,
            subtitle = texts.subtitle,
            facts = texts.facts,
            details = texts.details,
            weightSeries = record.weightSeries,
            flowSeries = record.flowSeries,
            weightTitle = texts.weightTitle,
            flowTitle = texts.flowTitle,
            footer = texts.footer,
        ),
        colors = colors,
    )
    val file = BrewImage.save(context, bitmap, "pourista-${record.id}.png") ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, texts.chooser)) }
}
