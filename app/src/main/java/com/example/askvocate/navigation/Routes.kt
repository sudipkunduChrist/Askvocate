package com.example.askvocate.navigation

/**
 * Type-safe navigation routes for the Askvocate onboarding flow.
 */
sealed class Routes(val route: String) {
    object Splash : Routes("splash")
    object Welcome : Routes("welcome")
    object Onboarding : Routes("onboarding")
    object RoleSelection : Routes("role_selection")
    object Login : Routes("login")
}
