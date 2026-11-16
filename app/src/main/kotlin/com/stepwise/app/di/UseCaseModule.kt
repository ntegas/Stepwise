package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import com.stepwise.domain.usecase.AddMilestoneUseCase
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.CreateGoalUseCase
import com.stepwise.domain.usecase.EditGoalUseCase
import com.stepwise.domain.usecase.EditMilestoneUseCase
import com.stepwise.domain.usecase.GetGoalByIdUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.GetMilestonesUseCase
import com.stepwise.domain.usecase.GetProjectsUseCase
import com.stepwise.domain.usecase.GetTodayAgendaUseCase
import com.stepwise.domain.usecase.MetricResolver
import com.stepwise.domain.usecase.ObserveGoalUseCase
import com.stepwise.domain.usecase.ResolveOccurrencesUseCase
import com.stepwise.domain.usecase.UpdateGoalStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The exact two Use Cases DEV-015's own "Today → item → execute → canonical Session" flow needs
 * (`/ROADMAP.md`): [GetTodayAgendaUseCase] (DEV-014) to read the agenda,
 * [CompleteOccurrenceUseCase] (DEV-009) to execute a tap on one of its items. [MetricResolver]/
 * [ResolveOccurrencesUseCase] are only provided because those two Use Cases need them as
 * constructor dependencies - never injected into a ViewModel directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideMetricResolver(
        taskRepository: TaskRepository,
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
    ): MetricResolver = MetricResolver(taskRepository, activityRepository, habitRepository)

    @Provides
    fun provideResolveOccurrencesUseCase(
        recurrenceRuleRepository: RecurrenceRuleRepository,
        recurrenceOccurrenceRepository: RecurrenceOccurrenceRepository,
    ): ResolveOccurrencesUseCase = ResolveOccurrencesUseCase(recurrenceRuleRepository, recurrenceOccurrenceRepository)

    @Provides
    @Suppress("LongParameterList") // One parameter per GetTodayAgendaUseCase constructor arg - see its own KDoc.
    fun provideGetTodayAgendaUseCase(
        taskRepository: TaskRepository,
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
        recurrenceRuleRepository: RecurrenceRuleRepository,
        sessionRepository: SessionRepository,
        resolveOccurrencesUseCase: ResolveOccurrencesUseCase,
        clock: Clock,
    ): GetTodayAgendaUseCase =
        GetTodayAgendaUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            recurrenceRuleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
            clock,
        )

    @Provides
    @Suppress("LongParameterList") // One parameter per CompleteOccurrenceUseCase constructor arg - see its own KDoc.
    fun provideCompleteOccurrenceUseCase(
        metricResolver: MetricResolver,
        recurrenceRuleRepository: RecurrenceRuleRepository,
        recurrenceOccurrenceRepository: RecurrenceOccurrenceRepository,
        sessionRepository: SessionRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CompleteOccurrenceUseCase =
        CompleteOccurrenceUseCase(
            metricResolver,
            recurrenceRuleRepository,
            recurrenceOccurrenceRepository,
            sessionRepository,
            idGenerator,
            clock,
        )

    @Provides
    fun provideGetGoalsUseCase(goalRepository: GoalRepository): GetGoalsUseCase = GetGoalsUseCase(goalRepository)

    @Provides
    fun provideGetGoalByIdUseCase(goalRepository: GoalRepository): GetGoalByIdUseCase =
        GetGoalByIdUseCase(goalRepository)

    @Provides
    fun provideObserveGoalUseCase(goalRepository: GoalRepository): ObserveGoalUseCase =
        ObserveGoalUseCase(goalRepository)

    @Provides
    fun provideGetMilestonesUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
    ): GetMilestonesUseCase = GetMilestonesUseCase(goalRepository, milestoneRepository)

    @Provides
    fun provideGetLifeAreasUseCase(lifeAreaRepository: LifeAreaRepository): GetLifeAreasUseCase =
        GetLifeAreasUseCase(lifeAreaRepository)

    @Provides
    fun provideGetProjectsUseCase(projectRepository: ProjectRepository): GetProjectsUseCase =
        GetProjectsUseCase(projectRepository)

    @Provides
    fun provideCreateGoalUseCase(
        goalRepository: GoalRepository,
        lifeAreaRepository: LifeAreaRepository,
        projectRepository: ProjectRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateGoalUseCase = CreateGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, idGenerator, clock)

    @Provides
    fun provideEditGoalUseCase(
        goalRepository: GoalRepository,
        lifeAreaRepository: LifeAreaRepository,
        projectRepository: ProjectRepository,
        clock: Clock,
    ): EditGoalUseCase = EditGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, clock)

    @Provides
    fun provideUpdateGoalStatusUseCase(
        goalRepository: GoalRepository,
        clock: Clock,
    ): UpdateGoalStatusUseCase = UpdateGoalStatusUseCase(goalRepository, clock)

    @Provides
    fun provideAddMilestoneUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): AddMilestoneUseCase = AddMilestoneUseCase(goalRepository, milestoneRepository, idGenerator, clock)

    @Provides
    fun provideEditMilestoneUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
        clock: Clock,
    ): EditMilestoneUseCase = EditMilestoneUseCase(goalRepository, milestoneRepository, clock)
}
