package com.stepwise.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stepwise.app.R
import com.stepwise.core.designsystem.component.StepwiseBottomNavigation
import com.stepwise.core.designsystem.component.StepwiseNavigationItem
import com.stepwise.core.model.GoalId
import com.stepwise.feature.activities.ActivitiesRoute
import com.stepwise.feature.activities.ActivityDetailRoute
import com.stepwise.feature.activities.ActivityFormRoute
import com.stepwise.feature.activities.HabitDetailRoute
import com.stepwise.feature.activities.HabitFormRoute
import com.stepwise.feature.activities.HabitsRoute
import com.stepwise.feature.goals.GoalDetailRoute
import com.stepwise.feature.goals.GoalFormRoute
import com.stepwise.feature.goals.GoalsRoute
import com.stepwise.feature.onboardingauth.SignInRoute
import com.stepwise.feature.plan.PlanRoute
import com.stepwise.feature.today.TodayRoute

private const val ROUTE_SIGN_IN = "signIn"
private const val ROUTE_TODAY = "today"
private const val ROUTE_GOALS = "goals"
private const val ROUTE_PLAN = "plan"
private const val ROUTE_GOAL_CREATE = "goalCreate"
private const val ARG_GOAL_ID = "goalId"
private const val ROUTE_GOAL_DETAIL = "goalDetail/{$ARG_GOAL_ID}"
private const val ROUTE_GOAL_EDIT = "goalEdit/{$ARG_GOAL_ID}"

// DEV-018: reached from the Goals tab, not tabs themselves - see GoalsListScreen's own
// "Manage" row KDoc for why Activities/Habits have no bottom-nav tab of their own.
private const val ROUTE_ACTIVITIES = "activities"
private const val ROUTE_ACTIVITY_CREATE = "activityCreate"
private const val ARG_ACTIVITY_ID = "activityId"
private const val ROUTE_ACTIVITY_DETAIL = "activityDetail/{$ARG_ACTIVITY_ID}"
private const val ROUTE_ACTIVITY_EDIT = "activityEdit/{$ARG_ACTIVITY_ID}"
private const val ROUTE_HABITS = "habits"
private const val ROUTE_HABIT_CREATE = "habitCreate"
private const val ARG_HABIT_ID = "habitId"
private const val ROUTE_HABIT_DETAIL = "habitDetail/{$ARG_HABIT_ID}"
private const val ROUTE_HABIT_EDIT = "habitEdit/{$ARG_HABIT_ID}"

private val TAB_ROUTES = setOf(ROUTE_TODAY, ROUTE_GOALS, ROUTE_PLAN)

/**
 * `/ROADMAP.md` DEV-017 grows this graph from DEV-016's Today/Goals shape into Today/Goals/Plan
 * as sibling tabs under one [StepwiseBottomNavigation] (`/PRODUCT_CANON.md` §70 names
 * Today/Goals/Plan/Progress — only these three exist before DEV-019+, so only these three tabs
 * are shown, the same "don't build chrome for destinations that don't exist yet" reasoning
 * DEV-015's own graph KDoc already states). The bottom bar is hidden below [TAB_ROUTES] — Goal/
 * Activity/Habit Detail/Create/Edit (DEV-018 adds the latter two, reached from the Goals tab per
 * `/PRODUCT_CANON.md`'s "no separate main tab per internal entity") are destinations reached
 * *from* a tab, not tabs themselves. [SessionViewModel] remains the single source of truth for
 * Sign In vs. authenticated.
 */
@Composable
fun StepwiseNavHost(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val userId by sessionViewModel.userId.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        val destination = if (userId != null) ROUTE_TODAY else ROUTE_SIGN_IN
        navController.navigate(destination) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    val currentRoute =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                StepwiseTabBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        StepwiseNavGraph(
            navController = navController,
            modifier = if (currentRoute in TAB_ROUTES) Modifier.padding(padding) else Modifier,
        )
    }
}

@Composable
private fun StepwiseTabBar(
    navController: NavController,
    currentRoute: String?,
) {
    StepwiseBottomNavigation(
        items =
            listOf(
                StepwiseNavigationItem(
                    label = stringResource(R.string.nav_tab_today),
                    icon = Icons.Filled.Home,
                    iconContentDescription = stringResource(R.string.nav_tab_today_content_description),
                    selected = currentRoute == ROUTE_TODAY,
                    onClick = { navController.navigateToTab(ROUTE_TODAY) },
                ),
                StepwiseNavigationItem(
                    label = stringResource(R.string.nav_tab_goals),
                    icon = Icons.Filled.List,
                    iconContentDescription = stringResource(R.string.nav_tab_goals_content_description),
                    selected = currentRoute == ROUTE_GOALS,
                    onClick = { navController.navigateToTab(ROUTE_GOALS) },
                ),
                StepwiseNavigationItem(
                    label = stringResource(R.string.nav_tab_plan),
                    icon = Icons.Filled.DateRange,
                    iconContentDescription = stringResource(R.string.nav_tab_plan_content_description),
                    selected = currentRoute == ROUTE_PLAN,
                    onClick = { navController.navigateToTab(ROUTE_PLAN) },
                ),
            ),
    )
}

@Composable
private fun StepwiseNavGraph(
    navController: NavHostController,
    modifier: Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_SIGN_IN,
        modifier = modifier,
    ) {
        composable(ROUTE_SIGN_IN) { SignInRoute() }
        composable(ROUTE_TODAY) { TodayRoute() }
        composable(ROUTE_PLAN) { PlanRoute() }
        composable(ROUTE_GOALS) {
            GoalsRoute(
                onGoalClick = { goalId -> navController.navigate("goalDetail/${goalId.value}") },
                onAddGoalClick = { navController.navigate(ROUTE_GOAL_CREATE) },
                onActivitiesClick = { navController.navigate(ROUTE_ACTIVITIES) },
                onHabitsClick = { navController.navigate(ROUTE_HABITS) },
            )
        }
        composable(ROUTE_GOAL_CREATE) {
            GoalFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_GOAL_EDIT,
            arguments = listOf(navArgument(ARG_GOAL_ID) { type = NavType.StringType }),
        ) {
            GoalFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_GOAL_DETAIL,
            arguments = listOf(navArgument(ARG_GOAL_ID) { type = NavType.StringType }),
        ) {
            GoalDetailRoute(
                onEditClick = { goalId: GoalId -> navController.navigate("goalEdit/${goalId.value}") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_ACTIVITIES) {
            ActivitiesRoute(
                onActivityClick = { activityId -> navController.navigate("activityDetail/${activityId.value}") },
                onAddActivityClick = { navController.navigate(ROUTE_ACTIVITY_CREATE) },
            )
        }
        composable(ROUTE_ACTIVITY_CREATE) {
            ActivityFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_ACTIVITY_EDIT,
            arguments = listOf(navArgument(ARG_ACTIVITY_ID) { type = NavType.StringType }),
        ) {
            ActivityFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_ACTIVITY_DETAIL,
            arguments = listOf(navArgument(ARG_ACTIVITY_ID) { type = NavType.StringType }),
        ) {
            ActivityDetailRoute(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HABITS) {
            HabitsRoute(
                onHabitClick = { habitId -> navController.navigate("habitDetail/${habitId.value}") },
                onAddHabitClick = { navController.navigate(ROUTE_HABIT_CREATE) },
            )
        }
        composable(ROUTE_HABIT_CREATE) {
            HabitFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_HABIT_EDIT,
            arguments = listOf(navArgument(ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            HabitFormRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_HABIT_DETAIL,
            arguments = listOf(navArgument(ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            HabitDetailRoute(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(ROUTE_TODAY) { inclusive = false }
        launchSingleTop = true
    }
}
