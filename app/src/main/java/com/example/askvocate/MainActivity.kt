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
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.NetworkMonitor
import com.example.askvocate.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerName: TextView
    private lateinit var drawerEmail: TextView
    private lateinit var drawerAvatar: ImageView
    private var sidebarRequest: Job? = null
    private var isCheckingSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val offlineBanner: View = findViewById(R.id.offline_banner)
        NetworkMonitor.start(this)
        lifecycleScope.launch {
            NetworkMonitor.online.collect { online ->
                offlineBanner.isVisible = !online
            }
        }
        val bottomNavContainer: View = findViewById(R.id.bottom_nav_container)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val drawerHeader = navView.getHeaderView(0)
        drawerName = drawerHeader.findViewById(R.id.user_name)
        drawerEmail = drawerHeader.findViewById(R.id.user_email)
        drawerAvatar = drawerHeader.findViewById(R.id.avatar_image)
        applyDrawerHeaderInset(drawerHeader)
        bindSidebarCached()
        refreshSidebarProfile()
        drawerHeader.findViewById<View>(R.id.btn_close_drawer)
            .setOnClickListener { animateDrawer(open = false) }
        val fabAsk: View = findViewById(R.id.fab_center_ask)
        val centerNavSlot: View = findViewById(R.id.center_nav_slot)

        fun updateRoleNavigation() {
            val role = com.example.askvocate.util.SessionManager.getUserRole(this)
            val isClient = role.equals("CLIENT", ignoreCase = true)
            val isLawyer = role.startsWith("LAWYER", ignoreCase = true)
            findViewById<TextView>(R.id.label_cases).text =
                if (isLawyer) getString(R.string.lawyer_schedule_tab) else getString(R.string.tab_cases)
            findViewById<ImageView>(R.id.icon_cases).apply {
                setImageResource(if (isLawyer) R.drawable.ic_onboard_lawyer_schedule else R.drawable.ic_case_work)
                contentDescription = if (isLawyer) getString(R.string.lawyer_schedule_tab)
                    else getString(R.string.tab_cases)
            }
            fabAsk.visibility = if (isClient) View.VISIBLE else View.GONE
            centerNavSlot.visibility = if (isClient) View.VISIBLE else View.GONE
            navView.menu.findItem(R.id.nav_find_lawyers)?.isVisible = isClient
            navView.menu.findItem(R.id.nav_search_history)?.isVisible = isClient
            navView.menu.findItem(R.id.nav_chat_history)?.isVisible = isClient
        }

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

        // Floating center AI chat button.
        fabAsk.setOnClickListener {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.nav_chat_history, null, options)
        }

        // Drawer navigation.
        navView.setupWithNavController(navController)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        // Show/hide chrome depending on the current screen.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateRoleNavigation()
            // Bottom navigation bar is accessible on all app pages, hiding only during full-screen auth/onboarding flows.
            val showBar = when (destination.id) {
                R.id.nav_splash,
                R.id.nav_role_selection,
                R.id.nav_onboarding,
                R.id.nav_get_started,
                R.id.nav_sign_in,
                R.id.nav_sign_up,
                R.id.nav_lawyer_sign_up,
                R.id.nav_legal_assistant -> false
                else -> true
            }
            val barWasShown = bottomNavContainer.visibility == View.VISIBLE
            if (showBar && !barWasShown) {
                bottomNavContainer.visibility = View.VISIBLE
                bottomNavContainer.alpha = 0f
                bottomNavContainer.animate()
                    .alpha(1f)
                    .setDuration(600)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else if (!showBar) {
                bottomNavContainer.visibility = View.GONE
            }
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            updateTabStates(destination.id)
        }
        updateRoleNavigation()
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

        bindSidebarCached()
        refreshSidebarProfile()
        isCheckingSession = true
        lifecycleScope.launch {
            val result = com.example.askvocate.util.SessionManager.validateWithServer(this@MainActivity)
            isCheckingSession = false
            if (result == com.example.askvocate.util.SessionManager.ValidationResult.INVALID) {
                com.example.askvocate.ui.documents.DocumentsCache(this@MainActivity).clear()
                com.example.askvocate.util.SessionManager.clearUser(this@MainActivity)
                com.example.askvocate.util.SessionManager.setLoggedIn(this@MainActivity, false)
                navController.navigate(
                    R.id.nav_role_selection,
                    null,
                    NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                )
            }
        }
    }

    /** Keeps the drawer header clear of the status bar and shifts it slightly downward. */
    private fun applyDrawerHeaderInset(header: View) {
        val baseHeight = resources.getDimensionPixelSize(R.dimen.nav_header_height)
        val extra = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top + extra)
            (view.layoutParams as? android.view.ViewGroup.LayoutParams)?.let { params ->
                params.height = baseHeight + top + extra
                view.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(header)
    }

    private fun bindSidebarCached() {
        if (!::drawerName.isInitialized) return
        drawerName.text = SessionManager.getUserName(this).ifBlank { "User" }
        drawerEmail.text = SessionManager.getUserEmail(this)
        val imageUrl = SessionManager.getProfileImageUrl(this)
        if (imageUrl.isNotBlank()) {
            drawerAvatar.load(imageUrl) {
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
            }
        } else {
            drawerAvatar.setImageResource(R.drawable.ic_profile)
        }
    }

    /** Pulls the latest name/email from the role-specific profile endpoint. */
    private fun refreshSidebarProfile() {
        if (!SessionManager.isLoggedIn(this)) return
        val userId = SessionManager.getUserId(this)
        val role = SessionManager.getUserRole(this)
        if (userId.isBlank()) return
        val path = when {
            role.equals("LAWYER_FRESHER", true) -> "/api/users/lawyer/fresher/$userId"
            role.equals("LAWYER_EXPERIENCED", true) -> "/api/users/lawyer/experienced/$userId"
            else -> "/api/users/client/$userId"
        }
        sidebarRequest?.cancel()
        sidebarRequest = lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) { fetchProfile(path) } ?: return@launch
            val name = user.optString("name").ifBlank { SessionManager.getUserName(this@MainActivity) }
            val email = user.optString("email").ifBlank { SessionManager.getUserEmail(this@MainActivity) }
            drawerName.text = name
            drawerEmail.text = email
            SessionManager.saveUser(
                this@MainActivity,
                userId,
                name,
                email,
                role,
                SessionManager.getAuthProvider(this@MainActivity),
                SessionManager.getProfileImageUrl(this@MainActivity).ifBlank { null },
                SessionManager.getAppToken(this@MainActivity).ifBlank { null }
            )
        }
    }

    private fun fetchProfile(path: String): JSONObject? {
        return try {
            val conn = (URL("${ApiConfig.BASE_URL}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                val token = SessionManager.getAppToken(this@MainActivity)
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) null else JSONObject(body).optJSONObject("user")
        } catch (_: Exception) {
            null
        }
    }

    /** Opens the navigation drawer from the top-left hamburger icon. */
    fun openDrawer() {
        bindSidebarCached()
        refreshSidebarProfile()
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            animateDrawer(open = false)
        } else {
            animateDrawer(open = true)
        }
    }

    private fun animateDrawer(open: Boolean) {
        val drawer = findViewById<NavigationView>(R.id.nav_view)
        val distance = (drawer.width.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.nav_drawer_width)).toFloat()
        drawer.animate().cancel()
        if (open) {
            drawerLayout.openDrawer(drawer, false)
            drawer.translationX = -distance
            drawer.animate().translationX(0f).setDuration(180L).start()
        } else {
            drawer.animate().translationX(-distance).setDuration(180L)
                .withEndAction {
                    drawerLayout.closeDrawer(drawer, false)
                    drawer.translationX = 0f
                }.start()
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

    override fun onDestroy() {
        NetworkMonitor.stop(this)
        super.onDestroy()
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
