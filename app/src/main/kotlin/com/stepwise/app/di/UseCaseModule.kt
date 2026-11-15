package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.GetTodayAgendaUseCase
import com.stepwise.domain.usecase.MetricResolver
import com.stepwise.domain.usecase.ResolveOccurrencesUseCase
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
}
