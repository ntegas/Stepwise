package com.stepwise.core.calculation

/**
 * The shared return shape every `GoalProgressMode` formula below produces
 * (`CALCULATION_ENGINE.md`'s "Goal Progress, per GoalProgressMode"). [percent] `== null` means
 * "nothing to compute from yet" (zero Milestones, zero linked Tasks, no metric readings) -
 * always distinguished from a computed `0.0`, the same "no data vs. a real zero" rule
 * [executionRate] follows.
 */
data class GoalProgressResult(
    val currentValue: Double?,
    val percent: Double?,
)

/**
 * `GoalProgressMode.METRIC` - a cumulative sum, never capped at 100% (an overachieved Goal is a
 * real fact, not a calculation error). [metricActualValues] is every `SessionMetricValue.actualValue`
 * reachable through this Goal's `GoalActivityLink` rows - the caller already did that join.
 */
fun metricProgress(
    targetValue: Double,
    metricActualValues: List<Double>,
): GoalProgressResult {
    val currentValue = metricActualValues.sum()
    return GoalProgressResult(currentValue = currentValue, percent = percentOf(currentValue, targetValue))
}

/**
 * `GoalProgressMode.TARGET_VALUE` - a "moving toward a target" reading (e.g. current weight), not
 * a sum. [mostRecentActualValue] is the `actualValue` of the most recent linked
 * `SessionMetricValue` (by `Session.date`, tie-broken by `sync.updatedAt`) - the caller already
 * picked it out; `null` when no reading exists yet.
 */
fun targetValueProgress(
    targetValue: Double,
    mostRecentActualValue: Double?,
): GoalProgressResult =
    GoalProgressResult(
        currentValue = mostRecentActualValue,
        percent = mostRecentActualValue?.let { percentOf(it, targetValue) },
    )

/**
 * `GoalProgressMode.FREQUENCY` - a period-scoped count, never an all-time one (a weekly-frequency
 * Goal's progress only makes sense within a period). [qualifyingSessionCount] is however many
 * `DONE`/`PARTIAL` Sessions the caller already counted for the period being asked about.
 */
fun frequencyProgress(
    targetFrequency: Double,
    qualifyingSessionCount: Int,
): GoalProgressResult {
    val currentValue = qualifyingSessionCount.toDouble()
    return GoalProgressResult(currentValue = currentValue, percent = percentOf(currentValue, targetFrequency))
}

/** One Milestone's contribution to `GoalProgressMode.MILESTONES`'s weighted formula. */
data class MilestoneProgressInput(
    val weight: Double,
    val isBinary: Boolean,
    val completed: Boolean,
    val progressPercent: Double?,
)

/**
 * `GoalProgressMode.MILESTONES` - `Σ(weight × milestonePercent) / Σ(weight)`. `null` (not a
 * divide-by-zero `NaN`) when the Goal has zero Milestones.
 */
fun milestonesProgress(milestones: List<MilestoneProgressInput>): GoalProgressResult {
    val totalWeight = milestones.sumOf { it.weight }
    if (totalWeight == 0.0) return GoalProgressResult(currentValue = null, percent = null)
    val weightedPercent =
        milestones.sumOf { milestone ->
            val milestonePercent =
                if (milestone.isBinary) milestone.binaryPercent() else milestone.progressPercent ?: 0.0
            milestone.weight * milestonePercent
        }
    val percent = weightedPercent / totalWeight
    return GoalProgressResult(currentValue = percent, percent = percent)
}

private fun MilestoneProgressInput.binaryPercent(): Double = if (completed) HUNDRED_PERCENT else 0.0

/**
 * `GoalProgressMode.TASKS` - equal-weighted (`CALCULATION_ENGINE.md`'s own disclosure: `Task` has
 * no completion-weight field today, unlike what `ARCHITECTURE.md` §6 describes - the honest
 * interpretation of what the schema actually supports, not a guessed weighting). `null` when the
 * Goal has zero linked Tasks.
 */
fun tasksProgress(
    completedTaskCount: Int,
    totalTaskCount: Int,
): GoalProgressResult {
    if (totalTaskCount == 0) return GoalProgressResult(currentValue = null, percent = null)
    val percent = completedTaskCount.toDouble() / totalTaskCount * HUNDRED_PERCENT
    return GoalProgressResult(currentValue = completedTaskCount.toDouble(), percent = percent)
}

private fun percentOf(
    currentValue: Double,
    targetValue: Double,
): Double? = if (targetValue == 0.0) null else currentValue / targetValue * HUNDRED_PERCENT

private const val HUNDRED_PERCENT = 100.0
