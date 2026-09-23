package com.example.askvocate

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    private lateinit var drawerLayout: DrawerLayout
    private var isCheckingSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val bottomNavContainer: View = findViewById(R.id.bottom_nav_container)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val fabAsk: View = findViewById(R.id.fab_center_ask)
        val centerNavSlot: View = findViewById(R.id.center_nav_slot)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Push the bottom bar above the system navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        // ---- Custom bottom tabs: Home | Cases | (ask center) | Messages | Profile ----
        val tabDestinations = mapOf(
            R.id.tab_home to R.id.nav_home,
            R.id.tab_cases to R.id.nav_appointments,
            R.id.tab_messages to R.id.nav_chat_list,
            R.id.tab_profile to R.id.nav_client_profile
        )
        tabDestinations.forEach { (tabId, destId) ->
            findViewById<View>(tabId).setOnClickListener { selectTab(destId) }
        }

        // Floating center "ask / find your lawyer" button.
        fabAsk.setOnClickListener {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.nav_find_lawyers, null, options)
        }

        // Drawer navigation.
        navView.setupWithNavController(navController)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        // Show/hide chrome depending on the current screen.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Bottom navigation bar is accessible on all app pages, hiding only during full-screen auth/onboarding flows.
            val showBar = when (destination.id) {
                R.id.nav_splash,
                R.id.nav_role_selection,
                R.id.nav_onboarding,
                R.id.nav_get_started,
                R.id.nav_sign_in,
                R.id.nav_sign_up,
                R.id.nav_lawyer_sign_up -> false
                else -> true
            }
            val barWasShown = bottomNavContainer.visibility == View.VISIBLE
            if (showBar && !barWasShown) {
                bottomNavContainer.visibility = View.VISIBLE
                bottomNavContainer.alpha = 0f
                bottomNavContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else if (!showBar) {
                bottomNavContainer.visibility = View.GONE
            }
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            updateTabStates(destination.id)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::navController.isInitialized || !com.example.askvocate.util.SessionManager.isLoggedIn(this)) return

        val destination = navController.currentDestination?.id
        val authDestinations = setOf(
            R.id.nav_splash, R.id.nav_role_selection, R.id.nav_onboarding,
            R.id.nav_get_started, R.id.nav_sign_in, R.id.nav_sign_up, R.id.nav_lawyer_sign_up
        )
        if (destination in authDestinations || isCheckingSession) return

        isCheckingSession = true
        lifecycleScope.launch {
            val result = com.example.askvocate.util.SessionManager.validateWithServer(this@MainActivity)
            isCheckingSession = false
            if (result == com.example.askvocate.util.SessionManager.ValidationResult.INVALID) {
                com.example.askvocate.util.SessionManager.setLoggedIn(this@MainActivity, false)
                navController.navigate(
                    R.id.nav_role_selection,
                    null,
                    NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                )
            }
        }
    }

    /** Opens the navigation drawer from the top-left hamburger icon. */
    fun openDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun selectTab(destId: Int) {
        if (navController.currentDestination?.id == destId) return
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.nav_home, inclusive = (destId == R.id.nav_home), saveState = false)
            .setLaunchSingleTop(true)
            .build()
        navController.navigate(destId, null, options)
    }

    private fun updateTabStates(currentDestId: Int) {
        val selectedColor = ContextCompat.getColor(this, R.color.home_blue)
        val unselectedColor = ContextCompat.getColor(this, R.color.text_tertiary)

        val isProfileTabActive = currentDestId == R.id.nav_client_profile || currentDestId == R.id.nav_personal_info
        val isHomeTabActive = currentDestId == R.id.nav_home
        val isCasesTabActive = currentDestId == R.id.nav_appointments
        val isMessagesTabActive = currentDestId == R.id.nav_chat_list

        val tabs = listOf(
            Triple(R.id.icon_home, R.id.label_home, isHomeTabActive),
            Triple(R.id.icon_cases, R.id.label_cases, isCasesTabActive),
            Triple(R.id.icon_messages, R.id.label_messages, isMessagesTabActive),
            Triple(R.id.icon_profile, R.id.label_profile, isProfileTabActive)
        )

        tabs.forEach { (iconId, labelId, selected) ->
            val icon = findViewById<ImageView>(iconId)
            val label = findViewById<TextView>(labelId)
            icon.imageTintList = ColorStateList.valueOf(if (selected) selectedColor else unselectedColor)
            label.setTextColor(if (selected) selectedColor else unselectedColor)
            label.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (navController.currentDestination?.id == R.id.nav_home) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
