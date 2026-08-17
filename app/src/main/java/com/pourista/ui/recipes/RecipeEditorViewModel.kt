package com.pourista.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.data.model.DEFAULT_POUR_FLOW_RATE
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.USER_RECIPE_SORT_ORDER
import com.pourista.data.model.StepKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val brewer: String = "",
    val dose: String = "",
    val water: String = "",
    val temp: String = "94",
    val grinder: String = "",
    val grind: String = "",
    val bean: String = "",
    val roaster: String = "",
    val notes: String = "",
    val autoStart: Boolean = true,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val sortOrder: Int = USER_RECIPE_SORT_ORDER,
    val createdAt: Long = 0,
    val lastUsedAt: Long? = null,
    val steps: List<EditableStep> = emptyList(),
) {
    val doseValue: Float get() = dose.toNumber() ?: 0f
    val waterValue: Float get() = water.toNumber() ?: 0f
    val tempValue: Int get() = temp.toIntOrNull() ?: 94
    val stepsWater: Float get() = steps.sumOf { it.deltaGrams.toDouble() }.toFloat()
    val totalSec: Int get() = steps.sumOf { it.durationSec }
    val waterMismatch: Boolean
        get() = steps.any { it.kind.isPour } && kotlin.math.abs(stepsWater - waterValue) > 1f
    val canSave: Boolean get() = name.isNotBlank() && doseValue > 0f

    /** Расписание рецепта посекундно — для предпросмотра кривой пролива. */
    fun previewSeries(): List<Float> {
        if (steps.isEmpty()) return emptyList()
        val result = mutableListOf<Float>()
        var cumulative = 0f
        steps.forEach { step ->
            val target = cumulative + step.deltaGrams
            val duration = step.durationSec.coerceAtLeast(1)
            for (second in 1..duration) {
                val fraction = second.toFloat() / duration
                result += if (step.kind.isPour) {
                    cumulative + (target - cumulative) * fraction
                } else {
                    target
                }
            }
            cumulative = target
        }
        return result
    }
}

class RecipeEditorViewModel(
    private val container: AppContainer,
    private val recipeId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var nextKey = 1L

    init {
        viewModelScope.launch {
            // Черновик из режима записи открывается как новый рецепт: он ещё не
            // в базе, и пока человек не сохранит — его там не будет.
            val draft = if (recipeId <= 0) container.recipeDraft else null
            container.recipeDraft = null
            val recipe = if (recipeId > 0) container.recipes.recipeById(recipeId) else draft
            _state.value = recipe?.toEditorState() ?: blankState()
        }
    }

    /** Заготовка нового рецепта: блуминг, два пролива и слив. */
    private fun blankState() = EditorState(
        loading = false,
        dose = "15",
        water = "250",
        temp = "94",
        steps = listOf(
            newStep(StepKind.BLOOM, duration = 45, water = 50f),
            newStep(StepKind.POUR, duration = 45, water = 100f),
            newStep(StepKind.POUR, duration = 30, water = 100f),
            newStep(StepKind.DRAWDOWN, duration = 60),
        ),
    )

    private fun newStep(kind: StepKind, duration: Int, water: Float = 0f) = EditableStep(
        key = nextKey++,
        kind = kind,
        duration = duration.toString(),
        water = if (water > 0f) trimNumber(water) else "",
    ).syncPourSeconds()

    private fun Recipe.toEditorState(): EditorState {
        var previousTarget = 0f
        val editable = steps.map { step ->
            val delta = (step.targetWaterGrams - previousTarget).coerceAtLeast(0f)
            previousTarget = maxOf(previousTarget, step.targetWaterGrams)
            EditableStep(
                key = nextKey++,
                kind = step.kind,
                title = step.title.orEmpty(),
                duration = step.durationSec.toString(),
                water = if (delta > 0f) trimNumber(delta) else "",
                flow = trimNumber(step.pourFlowRate.takeIf { it > 0f } ?: DEFAULT_POUR_FLOW_RATE),
                note = step.note.orEmpty(),
            ).syncPourSeconds()
        }
        return EditorState(
            loading = false,
            id = id,
            name = name,
            brewer = brewer,
            dose = trimNumber(doseGrams),
            water = trimNumber(waterGrams),
            temp = waterTempC.toString(),
            grinder = grinderName.orEmpty(),
            grind = grindSetting.orEmpty(),
            bean = beanName.orEmpty(),
            roaster = roaster.orEmpty(),
            notes = notes.orEmpty(),
            autoStart = autoStart,
            isBuiltIn = isBuiltIn,
            isFavorite = isFavorite,
            sortOrder = sortOrder,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            steps = editable,
        )
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setBrewer(value: String) = _state.update { it.copy(brewer = value) }
    fun setDose(value: String) = _state.update { it.copy(dose = value) }
    fun setWater(value: String) = _state.update { it.copy(water = value) }
    fun setTemp(value: String) = _state.update { it.copy(temp = value.filter(Char::isDigit)) }
    fun setGrinder(value: String) = _state.update { it.copy(grinder = value) }
    fun setGrind(value: String) = _state.update { it.copy(grind = value) }
    fun setBean(value: String) = _state.update { it.copy(bean = value) }
    fun setRoaster(value: String) = _state.update { it.copy(roaster = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }
    fun setAutoStart(value: Boolean) = _state.update { it.copy(autoStart = value) }

    /** Пропорция задаёт объём воды: доза × коэффициент. */
    fun applyRatio(ratio: Float) = _state.update { state ->
        state.copy(water = trimNumber(state.doseValue * ratio))
    }

    /** Обычный шаг всегда появляется перед сливом. */
    fun addStep() = _state.update { state ->
        state.copy(steps = state.steps.withRegularStep(newStep(StepKind.POUR, 30, 50f)))
    }

    fun addBloom() = _state.update { state ->
        state.copy(steps = state.steps.withBloom(newStep(StepKind.BLOOM, 45, 50f)))
    }

    fun addDrawdown() = _state.update { state ->
        state.copy(steps = state.steps.withDrawdown(newStep(StepKind.DRAWDOWN, 60)))
    }

    fun removeStep(key: Long) = _state.update { state ->
        state.copy(steps = state.steps.filterNot { it.key == key })
    }

    fun moveStep(key: Long, delta: Int) = _state.update { state ->
        state.copy(steps = state.steps.moved(key, delta))
    }

    fun canMoveStep(key: Long, delta: Int): Boolean = _state.value.steps.canMove(key, delta)

    fun updateStep(key: Long, transform: (EditableStep) -> EditableStep) = _state.update { state ->
        state.copy(steps = state.steps.map { if (it.key == key) transform(it) else it })
    }

    /** Растянуть объёмы проливов так, чтобы их сумма совпала с заданной водой. */
    fun distributeWater() = _state.update { state ->
        val pours = state.steps.filter { it.kind.isPour }
        if (pours.isEmpty() || state.waterValue <= 0f) return@update state
        val currentSum = pours.sumOf { it.deltaGrams.toDouble() }.toFloat()
        val updated = state.steps.map { step ->
            if (!step.kind.isPour) return@map step
            val grams = if (currentSum <= 0f) {
                state.waterValue / pours.size
            } else {
                step.deltaGrams * (state.waterValue / currentSum)
            }
            step.withWater(trimNumber(grams))
        }
        state.copy(steps = updated)
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _state.value
        if (!state.canSave) return
        viewModelScope.launch {
            val recipe = state.toRecipe()
            val id = if (state.id > 0) {
                container.recipes.save(recipe)
            } else {
                container.recipes.saveNewOnTop(recipe)
            }
            onSaved(id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val state = _state.value
        viewModelScope.launch {
            if (state.id > 0) {
                if (state.isBuiltIn) container.settings.addDeletedPreset(state.name)
                container.recipes.delete(state.id)
            }
            onDeleted()
        }
    }

    private fun EditorState.toRecipe(): Recipe {
        var start = 0
        var cumulative = 0f
        val recipeSteps = steps.map { step ->
            val target = cumulative + step.deltaGrams
            val result = RecipeStep(
                kind = step.kind,
                title = step.title.takeIf { it.isNotBlank() },
                startSec = start,
                durationSec = step.durationSec,
                targetWaterGrams = target,
                pourFlowRate = if (step.kind.isPour) step.flowRate else 0f,
                note = step.note.takeIf { it.isNotBlank() },
            )
            start += step.durationSec
            cumulative = target
            result
        }
        return Recipe(
            id = id,
            name = name.trim(),
            brewer = brewer.trim(),
            doseGrams = doseValue,
            waterGrams = if (waterValue > 0f) waterValue else cumulative,
            waterTempC = tempValue,
            grinderName = grinder.trim().takeIf { it.isNotBlank() },
            grindSetting = grind.trim().takeIf { it.isNotBlank() },
            beanName = bean.trim().takeIf { it.isNotBlank() },
            roaster = roaster.trim().takeIf { it.isNotBlank() },
            notes = notes.trim().takeIf { it.isNotBlank() },
            autoStart = autoStart,
            isBuiltIn = isBuiltIn,
            isFavorite = isFavorite,
            sortOrder = sortOrder,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            steps = recipeSteps,
        )
    }
}
