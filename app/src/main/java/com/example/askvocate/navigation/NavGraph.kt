package com.example.askvocate.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.askvocate.ui.screens.LoginScreen
import com.example.askvocate.ui.screens.OnboardingScreen
import com.example.askvocate.ui.screens.RoleSelectionScreen
import com.example.askvocate.ui.screens.SplashScreen
import com.example.askvocate.ui.screens.WelcomeScreen

/**
 * Main Navigation Graph
 *
 * Configures transitions between screens:
 * - slideInHorizontally/slideOutHorizontally for standard forward/back navigation
 * - fadeIn/fadeOut for Splash -> Welcome transition
 */
@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route
    ) {
        // Splash Screen
        composable(
            route = Routes.Splash.route,
            exitTransition = { fadeOut(animationSpec = tween(500)) }
        ) {
            SplashScreen(
                onNavigateToWelcome = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Welcome Screen
        composable(
            route = Routes.Welcome.route,
            enterTransition = { fadeIn(animationSpec = tween(500)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) }
        ) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Routes.Onboarding.route) },
                onSkip = { navController.navigate(Routes.RoleSelection.route) },
                onLogin = { navController.navigate(Routes.Login.route) }
            )
        }

        // Onboarding Journey Screen
        composable(
            route = Routes.Onboarding.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) }
        ) {
            OnboardingScreen(
                onNavigateToRoleSelection = { navController.navigate(Routes.RoleSelection.route) },
                onBack = { navController.popBackStack() }
            )
        }

        // Role Selection Screen
        composable(
            route = Routes.RoleSelection.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) }
        ) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    // For now, navigate to login after role selection
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Login Screen
        composable(
            route = Routes.Login.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(400)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    // Navigate to main app content (placeholder)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
