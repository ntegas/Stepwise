package com.stepwise.feature.activities

import com.stepwise.core.model.Activity
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId

/**
 * `:feature:activities`'s own Activity Detail state (`/ROADMAP.md` DEV-018) - metadata, linked
 * Goals (via [GoalActivityLink]), Session history, and the manual-entry/link-to-goal/unlink-
 * confirm/edit-session/delete-session-confirm dialogs. [NotFound]/[Error] carry no message -
 * `ActivityDetailScreen` resolves its own fixed, localized string for each, the same convention
 * `GoalDetailUiState` (DEV-016) already established.
 *
 * A failed action is reported through [ActivityDetailViewModel.events] (a one-shot
 * [DetailActionEvent], `:feature:today`/`:feature:plan`'s own established pattern), not a
 * persisted field here - DEV-018's first draft shipped the exact pre-fix shape DEV-017 review
 * already found and fixed once (a persisted `actionFailed: Boolean` that never reset on an
 * unrelated successful reactive re-emission, so a stale failure stayed visible indefinitely).
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
        val linkGoalDialogVisible: Boolean = false,
        val linkGoalDialog: LinkGoalDialogState = LinkGoalDialogState(),
        val manualEntryDialogVisible: Boolean = false,
        val manualEntryDialog: ManualEntryDialogState? = null,
        val unlinkConfirmLinkId: GoalActivityLinkId? = null,
        val editSessionDialog: EditSessionDialogState? = null,
        val deleteConfirmSessionId: SessionId? = null,
    ) : ActivityDetailUiState
}
