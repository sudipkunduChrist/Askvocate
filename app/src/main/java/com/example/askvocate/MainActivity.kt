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

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

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

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Push the bottom bar above the system navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        // ---- Custom bottom tabs: Home | Cases | (ask) | Messages | Profile ----
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

        // Show/hide chrome depending on the current screen.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBar = when (destination.id) {
                R.id.nav_splash,
                R.id.nav_role_selection,
                R.id.nav_onboarding,
                R.id.nav_get_started,
                R.id.nav_sign_in,
                R.id.nav_sign_up,
                R.id.nav_lawyer_sign_up,
                R.id.nav_lawyer_profile,
                R.id.nav_chat_detail,
                R.id.nav_privacy_policy -> false
                else -> true
            }
            val lockDrawer = when (destination.id) {
                R.id.nav_splash,
                R.id.nav_role_selection,
                R.id.nav_onboarding,
                R.id.nav_get_started,
                R.id.nav_sign_in,
                R.id.nav_sign_up,
                R.id.nav_lawyer_sign_up,
                R.id.nav_privacy_policy -> true
                else -> false
            }
            val barWasShown = bottomNavContainer.visibility == View.VISIBLE
            if (showBar && !barWasShown) {
                // Fade the bar in together with the destination's fragment so
                // the nav bar and page content appear at the same time.
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
            drawerLayout.setDrawerLockMode(
                if (lockDrawer) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
            )
            updateTabStates(destination.id)
        }
    }

    /** Opens the navigation drawer (used by the home screen's grid icon). */
    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun selectTab(destId: Int) {
        if (navController.currentDestination?.id == destId) return
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.nav_home, inclusive = false, saveState = true)
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .build()
        navController.navigate(destId, null, options)
    }

    private fun updateTabStates(currentDestId: Int) {
        val selectedColor = ContextCompat.getColor(this, R.color.home_blue)
        val unselectedColor = ContextCompat.getColor(this, R.color.text_tertiary)

        val tabs = listOf(
            Triple(R.id.icon_home, R.id.label_home, R.id.nav_home),
            Triple(R.id.icon_cases, R.id.label_cases, R.id.nav_appointments),
            Triple(R.id.icon_messages, R.id.label_messages, R.id.nav_chat_list),
            Triple(R.id.icon_profile, R.id.label_profile, R.id.nav_client_profile)
        )

        tabs.forEach { (iconId, labelId, destId) ->
            val selected = currentDestId == destId
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
            // Home is the signed-in root (splash was already removed), so back exits the app.
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
