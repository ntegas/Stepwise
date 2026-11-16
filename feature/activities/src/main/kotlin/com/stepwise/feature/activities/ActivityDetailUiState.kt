package com.stepwise.feature.activities

import com.stepwise.core.model.Activity
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session

/**
 * `:feature:activities`'s own Activity Detail state (`/ROADMAP.md` DEV-018) - metadata, linked
 * Goals (via [GoalActivityLink]), Session history, and the manual-entry/link-to-goal dialogs.
 * [NotFound]/[Error] carry no message - `ActivityDetailScreen` resolves its own fixed, localized
 * string for each, the same convention `GoalDetailUiState` (DEV-016) already established.
 */
sealed interface ActivityDetailUiState {
    data object Loading : ActivityDetailUiState

    data object NotFound : ActivityDetailUiState

    data object Error : ActivityDetailUiState

    data class Content(
        val activity: Activity,
        val goalLinks: List<GoalActivityLink>,
        val goals: List<Goal>,
        val metrics: List<Metric>,
        val sessions: List<Session>,
        val actionFailed: Boolean = false,
        val linkGoalDialogVisible: Boolean = false,
        val linkGoalDialog: LinkGoalDialogState = LinkGoalDialogState(),
        val manualEntryDialogVisible: Boolean = false,
        val manualEntryDialog: ManualEntryDialogState? = null,
    ) : ActivityDetailUiState
}
