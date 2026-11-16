package com.stepwise.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CreateGoalUseCase
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
import javax.inject.Inject

private const val KEY_GOAL_ID = "goalId"

/**
 * Create/Edit Goal form (`/ROADMAP.md` DEV-016, `/PRODUCT_CANON.md`: "Goal name, Target,
 * Deadline... More Options: Life Area, description, priority, project, category"). Deadline and
 * every "More Options" field stay plain text - no date-picker/numeric-stepper design-system
 * component exists yet (a disclosed gap, the same category as `:feature:onboarding-auth`'s own
 * disclosed `StepwiseTextField` gap at DEV-015) - validated on [save] rather than per keystroke.
 * Field validation lives in [validateGoalForm]; Create/Edit command mapping (including the
 * type/progressMode default and the never-redefine-on-edit rule) lives in [toCreateGoalCommand]/
 * [toEditGoalCommand] - both extracted out of this class once it crossed detekt's
 * CyclomaticComplexMethod/TooManyFunctions thresholds.
 *
 * **Edit mode's "clear" limitation inherited, not re-solved**: `EditGoalCommand`'s own KDoc
 * states a `null` field always means "leave unchanged" - clearing the target value is the one
 * exception, since switching into `GoalProgressMode.MILESTONES` makes `EditGoalUseCase` clear
 * `targetValue`/`unit` itself. Clearing Life Area, Project, category, description, or priority
 * once set is not possible through this form, matching that command's own disclosed limitation.
 */
@Suppress("TooManyFunctions") // One on*Changed handler per form field, plus load/save - the form's genuine surface.
@HiltViewModel
class GoalFormViewModel
    @Inject
    @Suppress("LongParameterList") // One repository/Use Case per thing this form loads, picks from, or saves to.
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

        // Ownership is verified centrally by GetGoalByIdUseCase (via GoalAccess.verifyOwnedGoal)
        // now, not re-checked here - a Goal that doesn't exist and one that belongs to a
        // different user both come back as the same Failure, so the form simply stays blank for
        // both rather than disclosing which case it was.
        private suspend fun loadExistingGoal(
            goalId: GoalId,
            userId: UserId,
        ) {
            when (val result = getGoalByIdUseCase.execute(userId, goalId)) {
                is AppResult.Success -> _state.value = _state.value.copyFrom(result.value)
                is AppResult.Failure -> _state.value = _state.value.copy(hasError = true)
            }
        }

        fun onTitleChanged(value: String) {
            _state.value = _state.value.copy(title = value, titleError = false, hasError = false)
        }

        fun onTargetValueChanged(value: String) {
            _state.value =
                _state.value.copy(targetValue = value, targetValueError = false, unitError = false, hasError = false)
        }

        fun onUnitChanged(value: String) {
            _state.value = _state.value.copy(unit = value, unitError = false, hasError = false)
        }

        fun onDeadlineChanged(value: String) {
            _state.value = _state.value.copy(deadline = value, deadlineError = false, hasError = false)
        }

        fun onCategoryChanged(value: String) {
            _state.value = _state.value.copy(category = value, hasError = false)
        }

        fun onDescriptionChanged(value: String) {
            _state.value = _state.value.copy(description = value, hasError = false)
        }

        fun onPriorityChanged(value: String) {
            _state.value = _state.value.copy(priority = value, priorityError = false, hasError = false)
        }

        fun onLifeAreaSelected(id: LifeAreaId?) {
            _state.value = _state.value.copy(lifeAreaId = id)
        }

        fun onProjectSelected(id: ProjectId?) {
            _state.value = _state.value.copy(projectId = id)
        }

        fun onMoreOptionsToggled() {
            _state.value = _state.value.copy(moreOptionsExpanded = !_state.value.moreOptionsExpanded)
        }

        fun save() {
            val current = _state.value
            if (current.isSubmitting) return
            val result = validateGoalForm(current)
            val validated = result.validated
            if (validated == null) {
                _state.value =
                    current.copy(
                        titleError = result.titleError,
                        targetValueError = result.targetValueError,
                        unitError = result.unitError,
                        deadlineError = result.deadlineError,
                        priorityError = result.priorityError,
                    )
                return
            }
            _state.value =
                current.copy(
                    isSubmitting = true,
                    hasError = false,
                    titleError = false,
                    targetValueError = false,
                    unitError = false,
                    deadlineError = false,
                    priorityError = false,
                )
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first()
                if (userId == null) {
                    _state.value = _state.value.copy(isSubmitting = false, hasError = true)
                    return@launch
                }
                val saveResult =
                    when (val mode = current.mode) {
                        is GoalFormMode.Create ->
                            createGoalUseCase.execute(toCreateGoalCommand(validated, current, userId))
                        is GoalFormMode.Edit ->
                            editGoalUseCase.execute(toEditGoalCommand(validated, current, mode.goalId, userId))
                    }
                _state.value =
                    when (saveResult) {
                        is AppResult.Success -> _state.value.copy(isSubmitting = false, saved = true)
                        is AppResult.Failure -> _state.value.copy(isSubmitting = false, hasError = true)
                    }
            }
        }
    }
