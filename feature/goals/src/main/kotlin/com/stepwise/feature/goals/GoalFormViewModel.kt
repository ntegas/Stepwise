package com.stepwise.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CreateGoalCommand
import com.stepwise.domain.usecase.CreateGoalUseCase
import com.stepwise.domain.usecase.EditGoalCommand
import com.stepwise.domain.usecase.EditGoalUseCase
import com.stepwise.domain.usecase.GetGoalByIdUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.GetProjectsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject

private const val KEY_GOAL_ID = "goalId"

/**
 * Create/Edit Goal form (`/ROADMAP.md` DEV-016, `/PRODUCT_CANON.md`: "Goal name, Target,
 * Deadline... More Options: Life Area, description, priority, project, category"). Deadline and
 * every "More Options" field stay plain text — no date-picker/numeric-stepper design-system
 * component exists yet (a disclosed gap, the same category as `:feature:onboarding-auth`'s own
 * disclosed `StepwiseTextField` gap at DEV-015) — validated on [save] rather than per keystroke.
 *
 * **[GoalType]/[GoalProgressMode] default, disclosed rather than invented**: `/ARCHITECTURE.md`
 * §6/B6 only describes UI-level *suggestions* per [GoalType], not a hard mapping, and no
 * canonical doc says what a type/progressMode-picker-free form should default to. This form uses
 * [GoalProgressMode.TARGET_VALUE]/[GoalType.QUANTITY] when a target value and unit are both
 * given, else [GoalProgressMode.MILESTONES]/[GoalType.CUMULATIVE] (progress tracked entirely via
 * Goal Detail's Milestones list) — a narrow default a later task is free to replace with an
 * actual picker.
 *
 * **Edit mode's "clear" limitation inherited, not re-solved**: [EditGoalCommand]'s own KDoc
 * states a `null` field always means "leave unchanged" — clearing the target value is the one
 * exception, since switching into [GoalProgressMode.MILESTONES] makes [EditGoalUseCase] clear
 * `targetValue`/`unit` itself. Clearing Life Area, Project, category, description, or priority
 * once set is not possible through this form, matching that command's own disclosed limitation.
 */
@HiltViewModel
class GoalFormViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val getLifeAreasUseCase: GetLifeAreasUseCase,
        private val getProjectsUseCase: GetProjectsUseCase,
        private val getGoalByIdUseCase: GetGoalByIdUseCase,
        private val createGoalUseCase: CreateGoalUseCase,
        private val editGoalUseCase: EditGoalUseCase,
    ) : ViewModel() {
        private val editingGoalId: GoalId? = savedStateHandle.get<String>(KEY_GOAL_ID)?.let { GoalId(it) }

        private val _state =
            MutableStateFlow(
                GoalFormUiState(mode = editingGoalId?.let { GoalFormMode.Edit(it) } ?: GoalFormMode.Create),
            )
        val state: StateFlow<GoalFormUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                loadPickerOptions(userId)
                editingGoalId?.let { loadExistingGoal(it, userId) }
            }
        }

        private suspend fun loadPickerOptions(userId: UserId) {
            val lifeAreas = (getLifeAreasUseCase.execute(userId).first() as? AppResult.Success)?.value.orEmpty()
            val projects = (getProjectsUseCase.execute(userId).first() as? AppResult.Success)?.value.orEmpty()
            _state.value = _state.value.copy(lifeAreas = lifeAreas, projects = projects)
        }

        // Scoped to [userId] the same way GoalDetailViewModel derives its own Goal from the
        // user-scoped GetGoalsUseCase list, rather than trusting a bare-by-id lookup - a Goal
        // belonging to a different user is treated the same as "not found" (the form simply
        // stays blank) instead of ever being populated into this form.
        private suspend fun loadExistingGoal(
            goalId: GoalId,
            userId: UserId,
        ) {
            when (val result = getGoalByIdUseCase.execute(goalId)) {
                is AppResult.Success -> {
                    val goal = result.value
                    if (goal != null && goal.userId == userId) {
                        _state.value = _state.value.copyFrom(goal)
                    }
                }
                is AppResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }

        private fun GoalFormUiState.copyFrom(goal: Goal): GoalFormUiState =
            copy(
                title = goal.title,
                targetValue = goal.targetValue?.toString() ?: "",
                unit = goal.unit ?: "",
                deadline = goal.deadline?.toString() ?: "",
                lifeAreaId = goal.lifeAreaId,
                projectId = goal.projectId,
                category = goal.category ?: "",
                description = goal.description ?: "",
                priority = goal.priority?.toString() ?: "",
            )

        fun onTitleChanged(value: String) {
            _state.value = _state.value.copy(title = value, titleError = false, error = null)
        }

        fun onTargetValueChanged(value: String) {
            _state.value = _state.value.copy(targetValue = value, targetValueError = false, error = null)
        }

        fun onUnitChanged(value: String) {
            _state.value = _state.value.copy(unit = value, error = null)
        }

        fun onDeadlineChanged(value: String) {
            _state.value = _state.value.copy(deadline = value, deadlineError = false, error = null)
        }

        fun onCategoryChanged(value: String) {
            _state.value = _state.value.copy(category = value, error = null)
        }

        fun onDescriptionChanged(value: String) {
            _state.value = _state.value.copy(description = value, error = null)
        }

        fun onPriorityChanged(value: String) {
            _state.value = _state.value.copy(priority = value, priorityError = false, error = null)
        }

        fun onLifeAreaSelected(id: com.stepwise.core.model.LifeAreaId?) {
            _state.value = _state.value.copy(lifeAreaId = id)
        }

        fun onProjectSelected(id: com.stepwise.core.model.ProjectId?) {
            _state.value = _state.value.copy(projectId = id)
        }

        fun onMoreOptionsToggled() {
            _state.value = _state.value.copy(moreOptionsExpanded = !_state.value.moreOptionsExpanded)
        }

        fun save() {
            val current = _state.value
            if (current.isSubmitting) return
            val validated = validate(current) ?: return
            _state.value =
                current.copy(
                    isSubmitting = true,
                    error = null,
                    titleError = false,
                    targetValueError = false,
                    deadlineError = false,
                    priorityError = false,
                )
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first()
                if (userId == null) {
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                val result =
                    when (val mode = current.mode) {
                        is GoalFormMode.Create -> createGoalUseCase.execute(toCreateCommand(validated, current, userId))
                        is GoalFormMode.Edit ->
                            editGoalUseCase.execute(toEditCommand(validated, current, mode.goalId, userId))
                    }
                _state.value =
                    when (result) {
                        is AppResult.Success -> _state.value.copy(isSubmitting = false, saved = true)
                        is AppResult.Failure -> _state.value.copy(isSubmitting = false, error = result.error.message)
                    }
            }
        }

        private fun validate(current: GoalFormUiState): ValidatedInput? {
            val title = current.title.trim()
            val targetValueText = current.targetValue.trim()
            val targetValue = targetValueText.toDoubleOrNull()
            val targetValueInvalid =
                targetValueText.isNotBlank() && (targetValue == null || !targetValue.isFinite() || targetValue <= 0.0)
            val deadlineText = current.deadline.trim()
            val parsedDeadline = runCatching { LocalDate.parse(deadlineText) }.getOrNull()
            val deadline = if (deadlineText.isBlank()) null else parsedDeadline
            val deadlineInvalid = deadlineText.isNotBlank() && deadline == null
            val priorityText = current.priority.trim()
            val priority = priorityText.toIntOrNull()
            val priorityInvalid = priorityText.isNotBlank() && priority == null
            if (title.isBlank() || targetValueInvalid || deadlineInvalid || priorityInvalid) {
                _state.value =
                    current.copy(
                        titleError = title.isBlank(),
                        targetValueError = targetValueInvalid,
                        deadlineError = deadlineInvalid,
                        priorityError = priorityInvalid,
                    )
                return null
            }
            return ValidatedInput(
                title = title,
                targetValue = if (targetValueText.isBlank()) null else targetValue,
                unit = current.unit.trim().ifBlank { null },
                deadline = deadline,
                priority = priority,
            )
        }

        private fun inferTypeAndMode(validated: ValidatedInput): Pair<GoalType, GoalProgressMode> =
            if (validated.targetValue != null && validated.unit != null) {
                GoalType.QUANTITY to GoalProgressMode.TARGET_VALUE
            } else {
                GoalType.CUMULATIVE to GoalProgressMode.MILESTONES
            }

        private fun toCreateCommand(
            validated: ValidatedInput,
            formState: GoalFormUiState,
            userId: UserId,
        ): CreateGoalCommand {
            val (type, progressMode) = inferTypeAndMode(validated)
            return CreateGoalCommand(
                userId = userId,
                title = validated.title,
                type = type,
                progressMode = progressMode,
                lifeAreaId = formState.lifeAreaId,
                projectId = formState.projectId,
                targetValue = validated.targetValue,
                unit = validated.unit,
                deadline = validated.deadline,
                priority = validated.priority,
                category = formState.category.trim().ifBlank { null },
                description = formState.description.trim().ifBlank { null },
            )
        }

        private fun toEditCommand(
            validated: ValidatedInput,
            formState: GoalFormUiState,
            goalId: GoalId,
            userId: UserId,
        ): EditGoalCommand {
            val (_, progressMode) = inferTypeAndMode(validated)
            return EditGoalCommand(
                userId = userId,
                goalId = goalId,
                title = validated.title,
                progressMode = progressMode,
                lifeAreaId = formState.lifeAreaId,
                projectId = formState.projectId,
                targetValue = validated.targetValue,
                unit = validated.unit,
                deadline = validated.deadline,
                priority = validated.priority,
                category = formState.category.trim().ifBlank { null },
                description = formState.description.trim().ifBlank { null },
            )
        }

        private data class ValidatedInput(
            val title: String,
            val targetValue: Double?,
            val unit: String?,
            val deadline: LocalDate?,
            val priority: Int?,
        )
    }
