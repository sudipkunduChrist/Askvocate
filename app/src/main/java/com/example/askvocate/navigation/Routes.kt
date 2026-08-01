package com.example.askvocate.navigation

/**
 * Type-safe navigation routes for the Askvocate onboarding flow.
 *
 * Flow: Splash -> Onboarding (6-page pager) -> RoleSelection -> Login/Signup
 */
sealed class Routes(val route: String) {
    object Splash : Routes("splash")
    object Onboarding : Routes("onboarding")
    object RoleSelection : Routes("role_selection")

    object Login : Routes("login/{role}") {
        fun createRoute(role: String) = "login/$role"
    }
}