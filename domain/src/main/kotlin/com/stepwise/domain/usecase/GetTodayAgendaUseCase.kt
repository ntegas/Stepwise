package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Habit
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.Task
import com.stepwise.core.model.TodayAgenda
import com.stepwise.core.model.TodayItem
import com.stepwise.core.model.TodayItemKind
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * The canonical Today Aggregator (`/ROADMAP.md` DEV-014, `/ARCHITECTURE.md` §14) — the one place
 * a [Task]'s plain [Task.date], a resolved recurring occurrence ([ResolveOccurrencesUseCase],
 * DEV-010, reused here rather than re-derived — "нет дублирующих engines",
 * `/docs/dev-plan-detailed.md`'s own DEV-010 acceptance criterion, extended to this task), and
 * Overdue Tasks are merged into one ordered [TodayAgenda]. `:feature:today`'s ViewModel and the
 * future Widget (`/ARCHITECTURE.md` §19.7) both consume this same read model so the two can never
 * disagree about what "today" contains (`/ARCHITECTURE.md` §14's own "App and Widget show
 * identical Today state", AC4).
 *
 * **Returns a [Flow], not a one-shot suspend result** — a deliberate departure from every other
 * Use Case in this codebase (none return `Flow` yet). `/ARCHITECTURE.md` §14/AC4 explicitly
 * describes `:feature:today`'s ViewModel and `:feature:widget`'s `WidgetRefreshCoordinator` as
 * both "collecting the same emission" from this exact Use Case — language only a `Flow`-returning
 * API supports — and §15 separately lists "Today's agenda" under "Cached... Invalidated and
 * recomputed whenever a relevant local write happens," which [combine]-ing every underlying
 * repository's own `observeAll` gives for free, the same reactivity Room's `Flow` queries already
 * provide everywhere else in this codebase.
 *
 * **A recurring owner's own [Task.date]/[Activity]/[Habit] has no independent "today" meaning
 * once a [RecurrenceRule] exists for it** — a genuine, previously undocumented gap found while
 * scoping this task: `/DATA_MODEL.md`'s "tasks 1─* schedules" relationship and its "Inbox = tasks
 * where `date IS NULL`" filter are both true at once, but neither doc states which one governs a
 * Task that has *both* a `date` and an attached [RecurrenceRule]. Resolved here, not left
 * ambiguous: a Task with at least one live [RecurrenceRule] is treated as fully recurrence-driven
 * for Today purposes — its own [Task.date] is ignored for scheduling (the rule's resolved
 * occurrences are the only source of its "today" dates) — since `CreateRecurrenceRuleUseCase`
 * (DEV-013) already supports and tests [ExecutableKind.TASK] owners, so this is a real, exercised
 * path, not a hypothetical. A Task with no [RecurrenceRule] at all keeps the simpler, fully
 * documented path: [Task.date] directly, with Overdue computed exactly as `/DATA_MODEL.md`
 * states it (`date < today AND status NOT IN (done, cancelled)`).
 *
 * **Activities/Habits only ever appear via a [RecurrenceRule]** — neither type carries a `date`
 * field of its own (`/DATA_MODEL.md`'s own field lists), so an Activity/Habit with no recurrence
 * rule attached has no "today" occurrence to resolve and simply never appears on the agenda; it
 * remains fully usable through ad hoc Session logging (DEV-009), just not through this screen.
 *
 * **Overdue is scoped to one-off Tasks only, not recurring occurrences** — `/DATA_MODEL.md`
 * defines Overdue purely as a `tasks` filter; no canonical doc defines what "overdue" would even
 * mean for a missed recurring Habit/Activity occurrence (most habits reset per period rather than
 * accumulating backlog). Disclosed as a deliberate scope boundary, not silently assumed — revisit
 * if product design later wants a "missed recurring occurrence" surfaced here too.
 *
 * **`Task.deadline` is not surfaced as a Today source at all** — `/ARCHITECTURE.md` §14 names it
 * as one of five Today sources, but no canonical doc specifies a deadline-only Task's session-
 * date semantics (what date a completion keys to when `date` is null), its interaction with an
 * already-set `date`, or whether a lead-time window applies — found at DEV-014 review
 * (architecture reviewer, HIGH). Not invented unilaterally; tracked in `/PROJECT_STATE.md` for a
 * product-owner decision, consistent with how DEV-010's own disclosed-not-resolved product
 * defaults (the `Monthly` clamp, `Custom`'s grammar) were carried forward rather than guessed at.
 *
 * **A Task whose latest Session status is PARTIAL/MISSED/IN_PROGRESS still counts as Overdue**,
 * per `/DATA_MODEL.md`'s own literal `status NOT IN (done, cancelled)` text — found at DEV-014
 * review (logic reviewer, HIGH). Tracked in `/PROJECT_STATE.md` for a product-owner decision on
 * whether `/DATA_MODEL.md`'s Overdue exclusion set should widen; not fixed here, since this is a
 * genuinely open product question, not a mechanical gap. **RESCHEDULED is no longer part of this
 * same disclosure** — DEV-017 (`/ROADMAP.md`, Plan/Calendar UI) closed the mechanical half of it:
 * [CompleteOccurrenceUseCase] now actually moves a one-off Task's [Task.date] to
 * `rescheduledToDate` on a RESCHEDULED completion (previously accepted but silently dropped for
 * [com.stepwise.domain.usecase.CompletionTarget.OneOff], only ever applied for
 * [com.stepwise.domain.usecase.CompletionTarget.Recurring]'s own occurrence row) — once `date`
 * itself moves, this aggregator's ordinary `date < today` Overdue test naturally stops matching
 * the old date, with no change needed in this file.
 *
 * **[TodayItem.isCompleted] is coarser than [ExecutionStatus]** — "has any Session been recorded"
 * (or, for a recurring occurrence, "is it already marked skipped"), not "is its status
 * specifically DONE." Nothing downstream needs the finer distinction yet (no UI exists before
 * DEV-015), and threading PARTIAL/MISSED/IN_PROGRESS-aware semantics through this aggregator
 * ahead of an actual product requirement for it would be exactly the kind of speculative
 * complexity `/DEVELOPMENT_PROTOCOL.md` warns against. The one place this type *is* distinguished
 * precisely is Overdue inclusion, because `/DATA_MODEL.md` states that rule in exact terms
 * (`status NOT IN (done, cancelled)`) rather than leaving it to a coarser approximation.
 *
 * **Ranking** uses each item's [TodayItem.goalImpactScore] where present (`/ARCHITECTURE.md`
 * §14), descending, then by [TodayItem.time] (untimed items last within the same score), then by
 * title — deterministic, per `/docs/dev-plan-detailed.md`'s own DEV-014 criterion, rather than
 * relying on whatever incidental order the underlying repositories happen to emit in.
 * [TodayAgenda.focus] is simply the first not-yet-completed item in that order, matching
 * `/PRODUCT_CANON.md` §9's "Focus: <single highest-impact item>" mockup. Neither [Activity] nor
 * [Habit] carries an impact-score field yet (confirmed absent from both types), so they always
 * rank by the time/title tiebreakers alone relative to each other — a real asymmetry with Task,
 * disclosed rather than invented a product rule to paper over.
 */
@Suppress("LongParameterList") // Genuinely needs all 5 read sources plus the shared resolver/clock - see KDoc.
class GetTodayAgendaUseCase(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val sessionRepository: SessionRepository,
    private val resolveOccurrencesUseCase: ResolveOccurrencesUseCase,
    private val clock: Clock,
) {
    fun execute(
        userId: UserId,
        timeZone: TimeZone,
    ): Flow<AppResult<TodayAgenda>> =
        combine(
            taskRepository.observeAll(userId),
            activityRepository.observeAll(userId),
            habitRepository.observeAll(userId),
            recurrenceRuleRepository.observeAll(userId),
            sessionRepository.observeAll(userId),
        ) { tasks, activities, habits, rules, sessions ->
            buildAgenda(userId, clock.todayIn(timeZone), tasks, activities, habits, rules, sessions)
        }.catch { cause ->
            // A repository's own Flow throwing (a real Room exception, not the ordinary
            // AppResult.Failure path ResolveOccurrencesUseCase already returns) would otherwise
            // terminate this Flow uncaught instead of surfacing the graceful Failure this type's
            // own signature promises - a real gap found at DEV-014 review (logic reviewer,
            // MEDIUM), consistent with every other repository-boundary catch in this codebase
            // (SyncEngine/DataErrorMapping's own "never let a platform exception escape" rule).
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load Today's agenda", cause)))
        }

    private suspend fun buildAgenda(
        userId: UserId,
        today: LocalDate,
        tasks: List<Task>,
        activities: List<Activity>,
        habits: List<Habit>,
        rules: List<RecurrenceRule>,
        sessions: List<Session>,
    ): AppResult<TodayAgenda> {
        val rulesByOwner = rules.filterNot { it.sync.isDeleted }.groupBy { it.ownerType to it.ownerId }
        val sessionsById = sessions.filterNot { it.sync.isDeleted }.associateBy { it.id }
        val liveTasks = tasks.filterNot { it.sync.isDeleted || it.archived }
        val (tasksWithRule, tasksWithoutRule) =
            liveTasks.partition { (ExecutableKind.TASK to it.id.value) in rulesByOwner }
        val (todayTaskItems, overdueItems) = buildOneOffTaskItems(today, tasksWithoutRule, sessionsById)

        val recurringTasksResult =
            resolveScheduledItems(
                userId,
                today..today,
                TodayItemKind.TASK,
                ExecutableKind.TASK,
                tasksWithRule.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to it.goalImpactScore }
        val activityItemsResult =
            resolveScheduledItems(
                userId,
                today..today,
                TodayItemKind.ACTIVITY,
                ExecutableKind.ACTIVITY,
                activities.filterNot { it.sync.isDeleted || it.archived }.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to null }
        val habitItemsResult =
            resolveScheduledItems(
                userId,
                today..today,
                TodayItemKind.HABIT,
                ExecutableKind.HABIT,
                habits.filterNot { it.sync.isDeleted || it.archived }.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to null }

        return recurringTasksResult.andThen { recurringTaskItems ->
            activityItemsResult.andThen { activityItems ->
                habitItemsResult.map { habitItems ->
                    assembleAgenda(todayTaskItems + recurringTaskItems + activityItems + habitItems, overdueItems)
                }
            }
        }
    }

    private fun buildOneOffTaskItems(
        today: LocalDate,
        tasksWithoutRule: List<Task>,
        sessionsById: Map<SessionId, Session>,
    ): Pair<List<TodayItem>, List<TodayItem>> {
        val todayItems = mutableListOf<TodayItem>()
        val overdueItems = mutableListOf<TodayItem>()
        for (task in tasksWithoutRule) {
            val date = task.date ?: continue
            val session = sessionsById[SessionIdentity.deterministicId(ExecutableKind.TASK, task.id.value, date)]
            val item =
                TodayItem(
                    kind = if (date == today) TodayItemKind.TASK else TodayItemKind.OVERDUE,
                    sourceType = ExecutableKind.TASK,
                    sourceId = task.id.value,
                    title = task.title,
                    date = date,
                    time = task.time,
                    isCompleted = session != null,
                    goalImpactScore = task.goalImpactScore,
                    recurrenceRuleId = null,
                    occurrenceDate = null,
                )
            val isDoneOrCancelled = session != null && session.status in DONE_OR_CANCELLED
            when {
                date == today -> todayItems += item
                date < today && !isDoneOrCancelled -> overdueItems += item
                else -> Unit
            }
        }
        return todayItems to overdueItems
    }

    private fun assembleAgenda(
        items: List<TodayItem>,
        overdue: List<TodayItem>,
    ): TodayAgenda {
        val ordered = items.sortedWith(TODAY_ITEM_ORDER)
        return TodayAgenda(
            focus = ordered.firstOrNull { !it.isCompleted },
            items = ordered,
            overdue = overdue.sortedWith(TODAY_ITEM_ORDER),
            completedCount = items.count { it.isCompleted },
            totalCount = items.size,
        )
    }

    companion object {
        private val DONE_OR_CANCELLED = setOf(ExecutionStatus.DONE, ExecutionStatus.CANCELLED)
        private const val NO_IMPACT_SCORE_RANK = -1
        private val TODAY_ITEM_ORDER =
            compareByDescending<TodayItem> { it.goalImpactScore ?: NO_IMPACT_SCORE_RANK }
                .thenBy { it.time ?: LocalTime(hour = 23, minute = 59) }
                .thenBy { it.title }
    }
}
