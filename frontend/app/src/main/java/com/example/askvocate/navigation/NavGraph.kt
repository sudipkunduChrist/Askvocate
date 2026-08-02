package com.example.askvocate.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.askvocate.ui.screens.LoginScreen
import com.example.askvocate.ui.screens.OnboardingScreen
import com.example.askvocate.ui.screens.RoleSelectionScreen
import com.example.askvocate.ui.screens.SplashScreen
import com.example.askvocate.ui.screens.UserRole

/**
 * Main Navigation Graph
 *
 * Flow: Splash -> Onboarding (6-page pager, skippable) -> RoleSelection -> Login/Signup
 *
 * Configures transitions between screens:
 * - slideInHorizontally/slideOutHorizontally for standard forward/back navigation
 * - fadeIn/fadeOut for Splash -> Onboarding transition
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
                onNavigateToOnboarding = {
                    navController.navigate(Routes.Onboarding.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Pager Screen
        composable(
            route = Routes.Onboarding.route,
            enterTransition = { fadeIn(animationSpec = tween(500)) },
            exitTransition = {
                // Slide out left AND fade out together so no peek-through during RoleSelection slide-in
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeIn(animationSpec = tween(300))
            }
        ) {
            OnboardingScreen(
                onNavigateToRoleSelection = {
                    navController.navigate(Routes.RoleSelection.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Role Selection Screen
        composable(
            route = Routes.RoleSelection.route,
            enterTransition = {
                // Slide in from right + fade in so the incoming screen is opaque immediately
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    val roleParam =
                        if (role == UserRole.LAWYER) "lawyer" else "user"

                    navController.navigate(
                        Routes.Login.createRoute(roleParam)
                    ) {
                        popUpTo(Routes.RoleSelection.route) {
                            inclusive = false
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Login / Signup Screen
        composable(
            route = Routes.Login.route,
            arguments = listOf(
                navArgument("role") {
                    type = NavType.StringType
                }
            ),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(450, easing = EaseInOut)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val roleParam =
                backStackEntry.arguments?.getString("role") ?: "user"

            val role =
                if (roleParam == "lawyer")
                    UserRole.LAWYER
                else
                    UserRole.USER

            LoginScreen(
                role = role,
                onLoginSuccess = {
                    // Navigate to main app content (placeholder)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
