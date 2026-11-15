package com.stepwise.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stepwise.feature.onboardingauth.SignInRoute
import com.stepwise.feature.today.TodayRoute

private const val ROUTE_SIGN_IN = "signIn"
private const val ROUTE_TODAY = "today"

/**
 * Deliberately simple two-destination graph (`/ROADMAP.md` DEV-015's checkpoint scope) - no
 * bottom navigation yet, since `/PRODUCT_CANON.md` §70 names Goals/Plan/Progress as Today's
 * sibling tabs and none of those screens exist before DEV-016+; building tab chrome for
 * destinations that don't exist would be the exact speculative infrastructure `/ROADMAP.md` warns
 * against.
 * [SessionViewModel] is the single source of truth for which destination is current - neither
 * [SignInRoute] nor [TodayRoute] navigates on its own authority.
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

    NavHost(navController = navController, startDestination = ROUTE_SIGN_IN) {
        composable(ROUTE_SIGN_IN) { SignInRoute() }
        composable(ROUTE_TODAY) { TodayRoute() }
    }
}
