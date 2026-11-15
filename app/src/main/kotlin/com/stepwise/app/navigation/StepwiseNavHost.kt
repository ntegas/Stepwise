package com.stepwise.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
import com.stepwise.feature.goals.GoalDetailRoute
import com.stepwise.feature.goals.GoalFormRoute
import com.stepwise.feature.goals.GoalsRoute
import com.stepwise.feature.onboardingauth.SignInRoute
import com.stepwise.feature.today.TodayRoute

private const val ROUTE_SIGN_IN = "signIn"
private const val ROUTE_TODAY = "today"
private const val ROUTE_GOALS = "goals"
private const val ROUTE_GOAL_CREATE = "goalCreate"
private const val ARG_GOAL_ID = "goalId"
private const val ROUTE_GOAL_DETAIL = "goalDetail/{$ARG_GOAL_ID}"
private const val ROUTE_GOAL_EDIT = "goalEdit/{$ARG_GOAL_ID}"

private val TAB_ROUTES = setOf(ROUTE_TODAY, ROUTE_GOALS)

/**
 * `/ROADMAP.md` DEV-016 grows this graph from DEV-015's two-destination shape into Today/Goals
 * as sibling tabs under one [StepwiseBottomNavigation] (`/PRODUCT_CANON.md` §70 names
 * Today/Goals/Plan/Progress — only the first two exist before DEV-017+, so only those two tabs
 * are shown, the same "don't build chrome for destinations that don't exist yet" reasoning
 * DEV-015's own graph KDoc already states). The bottom bar is hidden below [TAB_ROUTES] — Goal
 * Detail/Create/Edit are destinations reached *from* a tab, not tabs themselves.
 * [SessionViewModel] remains the single source of truth for Sign In vs. authenticated.
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

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
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
                                icon = Icons.Filled.Flag,
                                iconContentDescription = stringResource(R.string.nav_tab_goals_content_description),
                                selected = currentRoute == ROUTE_GOALS,
                                onClick = { navController.navigateToTab(ROUTE_GOALS) },
                            ),
                        ),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_SIGN_IN,
            modifier = if (currentRoute in TAB_ROUTES) Modifier.padding(padding) else Modifier,
        ) {
            composable(ROUTE_SIGN_IN) { SignInRoute() }
            composable(ROUTE_TODAY) { TodayRoute() }
            composable(ROUTE_GOALS) {
                GoalsRoute(
                    onGoalClick = { goalId -> navController.navigate("goalDetail/${goalId.value}") },
                    onAddGoalClick = { navController.navigate(ROUTE_GOAL_CREATE) },
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
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(ROUTE_TODAY) { inclusive = false }
        launchSingleTop = true
    }
}
