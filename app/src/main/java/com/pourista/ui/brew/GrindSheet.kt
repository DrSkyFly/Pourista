package com.pourista.ui.brew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.grind.BrewUse
import com.pourista.grind.GrindMatch
import com.pourista.grind.Grinder
import com.pourista.grind.GrinderCatalog
import com.pourista.grind.convert
import kotlin.math.roundToInt

/**
 * Пересчёт помола с одной кофемолки на другую.
 *
 * Общего языка у кофемолок нет: у Comandante настройка — клики от сведённых
 * жерновов, у Timemore C5 ESP — «оборот.деление.клик». Общее только одно —
 * размер частиц, поэтому настройка сначала переводится в микроны, а из них
 * подбирается ближайшее деление на второй кофемолке.
 *
 * Кофемолка выбирается в два приёма: сначала фирма, потом её модель. В общем
 * списке их две сотни, и листать его ради одной строки незачем.
 */
@Composable
fun GrindSheetContent(
    fromId: String,
    toId: String,
    setting: String,
    onRemember: (fromId: String, toId: String, setting: String) -> Unit,
    // Вызванный из редактора рецепта пересчёт умеет подставить результат в
    // поля помола. Открытый с экрана заваривания — просто показывает ответ.
    onApply: ((grinder: Grinder, setting: String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val grinders = remember { GrinderCatalog.all(context) }
    val brands = remember { GrinderCatalog.brands(context) }

    var from by remember { mutableStateOf(grinders.firstOrNull { it.id == fromId }) }
    var to by remember { mutableStateOf(grinders.firstOrNull { it.id == toId }) }
    var fromBrand by remember { mutableStateOf(from?.brand) }
    var toBrand by remember { mutableStateOf(to?.brand) }
    var text by remember { mutableStateOf(setting) }

    // Выбор переживает закрытие окна: второй раз искать свою кофемолку незачем.
    val save = { onRemember(from?.id.orEmpty(), to?.id.orEmpty(), text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.grind_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        Block {
            GrinderRow(
                brands = brands,
                brand = fromBrand,
                model = from?.model,
                models = { GrinderCatalog.models(context, it) },
                onBrand = { fromBrand = it; from = null; save() },
                onModel = { from = it; save() },
            )
            SettingField(
                value = text,
                grinder = from,
                onValueChange = { text = it; save() },
            )
        }

        // Кнопка обмена живёт между блоками: так видно, что она меняет их
        // местами, а не что-то делает с одним из них.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = {
                    val grinder = from
                    val brand = fromBrand
                    from = to
                    fromBrand = toBrand
                    to = grinder
                    toBrand = brand
                    text = ""
                    save()
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.grind_swap))
            }
        }

        Block {
            GrinderRow(
                brands = brands,
                brand = toBrand,
                model = to?.model,
                models = { GrinderCatalog.models(context, it) },
                onBrand = { toBrand = it; to = null; save() },
                onModel = { to = it; save() },
            )
        }

        val match = from?.let { f -> to?.let { t -> f.parse(text)?.let { convert(f, it, t) } } }
        Result(from = from, to = to, text = text, match = match)

        if (onApply != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                TextButton(onClick = { onCancel?.invoke() }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { match?.let { onApply(to!!, to!!.format(it.clicks)) } },
                    enabled = match != null,
                ) {
                    Text(stringResource(R.string.grind_apply))
                }
            }
        }
    }
}

/**
 * Настройка исходной кофемолки. Поле подогнано под плашки выбора: та же
 * высота, то же скругление, та же заливка — в блоке они стоят в один ряд.
 * Справа — край шкалы, чтобы не гадать, до скольки крутить.
 */
@Composable
private fun SettingField(value: String, grinder: Grinder?, onValueChange: (String) -> Unit) {
    Surface(
        shape = PickShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.grind_setting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                        .padding(6.dp)
                        .size(18.dp),
                )
            } else if (grinder != null) {
                Text(
                    text = grinder.format(grinder.minClicks) + " – " + grinder.format(grinder.maxClicks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/** Подложка блока: что откуда и куда, видно по порядку и кнопке обмена. */
@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = BlockShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Фирма и модель в строку. Фирме места больше: её выбирают первой. */
@Composable
private fun GrinderRow(
    brands: List<String>,
    brand: String?,
    model: String?,
    models: (String) -> List<Grinder>,
    onBrand: (String) -> Unit,
    onModel: (Grinder) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown(
            value = brand,
            hint = stringResource(R.string.grind_brand),
            items = brands,
            text = { it },
            onPick = onBrand,
            modifier = Modifier.weight(1.15f),
        )
        Dropdown(
            value = model,
            hint = stringResource(R.string.grind_model),
            items = brand?.let(models).orEmpty(),
            text = { it.model },
            onPick = onModel,
            modifier = Modifier.weight(0.85f),
        )
    }
}

/**
 * Выбор из списка. Не поле ввода, а плашка: печатать тут нечего, а плашка
 * ниже поля и держит скругление заодно с блоком.
 *
 * Пока фирма не выбрана, моделей нет и список не открывается: показывать
 * пустоту — только дразнить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    value: String?,
    hint: String,
    items: List<T>,
    text: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val expanded = open && items.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (items.isNotEmpty()) open = it },
        modifier = modifier,
    ) {
        Surface(
            shape = PickShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            ) {
                Text(
                    text = value ?: hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { open = false },
            shape = BlockShape,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onPick(item)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Итог пересчёта. Микроны показываем рядом с настройкой, а под ними — для чего
 * такой помол: по этой строке сразу видно, если выбрана не та модель.
 */
@Composable
private fun Result(from: Grinder?, to: Grinder?, text: String, match: GrindMatch?) {
    val hint = when {
        from == null || to == null -> stringResource(R.string.grind_pick_both)
        text.isBlank() -> stringResource(R.string.grind_enter_setting)
        match == null -> stringResource(R.string.grind_bad_setting)
        else -> null
    }
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
        return
    }
    match!!
    to!!
    val muted = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
    Card(
        shape = BlockShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.grind_result),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = to.format(match.clicks),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.grind_microns, match.microns.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val uses = BrewUse.forMicrons(match.microns)
            if (uses.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    uses.forEach { use -> Pill(stringResource(use.labelRes)) }
                }
            }
            Text(
                text = stringResource(
                    R.string.grind_source_microns,
                    from!!.format(from.clicksFor(match.wantedMicrons)),
                    match.wantedMicrons.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            if (!match.isExact(to)) {
                Text(
                    text = stringResource(R.string.grind_nearest),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
    }
}

/** Назначение помола — короткой плашкой, а не строчкой через точку. */
@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Скругления: плашки выбора круглее полей, блоки — крупнее плашек. */
private val PickShape = RoundedCornerShape(18.dp)
private val BlockShape = RoundedCornerShape(24.dp)
