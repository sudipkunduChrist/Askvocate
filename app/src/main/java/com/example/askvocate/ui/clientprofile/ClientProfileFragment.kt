package com.example.askvocate.ui.clientprofile

import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import coil.load
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentClientProfileBinding
import com.example.askvocate.network.GoogleAuthHelper
import com.example.askvocate.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ClientProfileFragment : Fragment() {

    private var _binding: FragmentClientProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "client_profile_prefs"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_ADDRESS = "user_address"
        private const val KEY_LANGUAGE = "user_language"
        private const val KEY_THEME = "user_theme"
        private const val KEY_BIOMETRIC = "user_biometric"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadUserData()
        setupAppVersion()
        setupClickListeners()
        updateProfileCompletion()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized && _binding != null) {
            loadUserData()
            updateProfileCompletion()
        }
    }

    private fun loadUserData() {
        val name = prefs.getString(KEY_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: SessionManager.getUserName(requireContext()).takeIf { it.isNotBlank() }
            ?: getString(R.string.not_set)
        val email = prefs.getString(KEY_EMAIL, null)
            ?.takeIf { it.isNotBlank() }
            ?: SessionManager.getUserEmail(requireContext()).takeIf { it.isNotBlank() }
            ?: getString(R.string.not_set)
        val phone = prefs.getString(KEY_PHONE, null)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_set)
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_set)
        val language = prefs.getString(KEY_LANGUAGE, "English") ?: "English"
        val theme = prefs.getString(KEY_THEME, "System Default") ?: "System Default"
        val isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)

        binding.tvClientName.text = name
        binding.tvClientEmail.text = email
        binding.tvClientPhone.text = phone
        binding.tvClientAddress.text = address
        binding.tvAccountBadge.text = when (SessionManager.getUserRole(requireContext()).uppercase()) {
            "LAWYER_FRESHER" -> getString(R.string.fresher_lawyer_account)
            "LAWYER_EXPERIENCED" -> getString(R.string.experienced_lawyer_account)
            "ADMIN" -> getString(R.string.admin_account)
            else -> getString(R.string.client_account)
        }
        updateAvatar(name)
        binding.tvCurrentLanguage.text = language
        binding.tvCurrentTheme.text = theme
        binding.switchBiometricLock.isChecked = isBiometricEnabled
    }

    private fun updateAvatar(name: String) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvAvatarInitial.text = initial

        val isGoogleUser = SessionManager.getAuthProvider(requireContext())
            .equals("GOOGLE", ignoreCase = true)
        val profileImageUrl = SessionManager.getProfileImageUrl(requireContext())

        if (isGoogleUser && profileImageUrl.isNotBlank()) {
            binding.tvAvatarInitial.isVisible = false
            binding.ivClientAvatar.isVisible = true
            binding.ivClientAvatar.load(profileImageUrl) {
                crossfade(true)
                listener(
                    onError = { _, _ ->
                        binding.ivClientAvatar.isVisible = false
                        binding.tvAvatarInitial.isVisible = true
                    }
                )
            }
        } else {
            binding.ivClientAvatar.setImageDrawable(null)
            binding.ivClientAvatar.isVisible = false
            binding.tvAvatarInitial.isVisible = true
        }
    }

    private fun updateProfileCompletion() {
        val name = binding.tvClientName.text.toString()
        val email = binding.tvClientEmail.text.toString()
        val phone = binding.tvClientPhone.text.toString()
        val address = binding.tvClientAddress.text.toString()

        var completedFields = 0
        val totalFields = 4

        if (name.isNotEmpty()) completedFields++
        if (email.isNotEmpty()) completedFields++
        if (phone.isNotEmpty() && phone != "Not set") completedFields++
        if (address.isNotEmpty() && address != "Not set") completedFields++

        val percentage = ((completedFields.toFloat() / totalFields) * 100).toInt()

        binding.tvCompletionPercentage.text = String.format(java.util.Locale.getDefault(), "%d%%", percentage)
        
        ObjectAnimator.ofInt(binding.progressProfileCompletion, "progress", percentage).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            start()
        }

        binding.cardProfileCompletion.isVisible = percentage < 100
    }

    private fun setupAppVersion() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = getString(R.string.app_version_format, packageInfo.versionName)
        } catch (_: Exception) {
            binding.tvAppVersion.text = getString(R.string.app_version_format, "1.0.0")
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            (requireActivity() as? com.example.askvocate.MainActivity)?.openDrawer()
        }

        binding.btnProfileSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Edit Profile Trigger
        binding.btnEditAvatar.setOnClickListener {
            Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
        }
        
        val navigateToPersonalInfo = View.OnClickListener {
            findNavController().navigate(R.id.action_client_profile_to_personal_info)
        }
        binding.rowPersonalInfo.setOnClickListener(navigateToPersonalInfo)
        binding.btnCompleteProfile.setOnClickListener(navigateToPersonalInfo)

        // Settings / Notifications icon on top right
        binding.btnProfileSettings.setOnClickListener {
            showNotificationsDialog()
        }

        // Quick Access Cards
        binding.actionAppointments.setOnClickListener {
            findNavController().navigate(R.id.nav_appointments)
        }
        binding.actionSavedAdvocates.setOnClickListener {
            findNavController().navigate(R.id.action_client_profile_to_saved_advocates)
        }
        binding.actionDocuments.setOnClickListener {
            showDocumentsDialog()
        }
        binding.actionRecentActivity.setOnClickListener {
            showRecentActivityDialog()
        }

        // Account & Preferences
        binding.rowNotifications.setOnClickListener {
            showNotificationsDialog()
        }
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }
        binding.rowAppearance.setOnClickListener {
            showAppearanceDialog()
        }

        // Security & Privacy
        binding.rowPrivacySecurity.setOnClickListener {
            findNavController().navigate(R.id.nav_privacy_policy)
        }
        binding.switchBiometricLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_BIOMETRIC, isChecked).apply()
            val msg = if (isChecked) "Biometric App Lock enabled" else "Biometric App Lock disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Help & Support
        binding.rowHelpCenter.setOnClickListener {
            showHelpCenterDialog()
        }
        binding.rowContactSupport.setOnClickListener {
            showContactSupportDialog()
        }
        binding.rowFeedback.setOnClickListener {
            showFeedbackDialog()
        }

        // Footer links
        binding.btnTermsOfService.setOnClickListener {
            findNavController().navigate(R.id.nav_privacy_policy)
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            findNavController().navigate(R.id.nav_privacy_policy)
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }



    private fun showNotificationsDialog() {
        val options = arrayOf("Appointment Reminders", "Chat Alerts", "Legal Updates", "Promotions")
        val checked = booleanArrayOf(true, true, true, false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notifications)
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.save) { _, _ ->
                Toast.makeText(requireContext(), "Notification settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi (हिंदी)", "Bengali (বাংলা)")
        val currentLang = binding.tvCurrentLanguage.text.toString()
        val selectedIndex = languages.indexOfFirst { it.startsWith(currentLang) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val chosen = languages[which].split(" ")[0]
                binding.tvCurrentLanguage.text = chosen
                prefs.edit().putString(KEY_LANGUAGE, chosen).apply()
                
                val languageTag = when (which) {
                    1 -> "hi"
                    2 -> "bn"
                    else -> "en"
                }
                val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(appLocale)

                Toast.makeText(requireContext(), "Language set to $chosen", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAppearanceDialog() {
        val themes = arrayOf("System Default", "Light", "Dark")
        val currentTheme = binding.tvCurrentTheme.text.toString()
        val selectedIndex = themes.indexOf(currentTheme).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val chosen = themes[which]
                binding.tvCurrentTheme.text = chosen
                prefs.edit().putString(KEY_THEME, chosen).apply()

                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }

                Toast.makeText(requireContext(), "Appearance set to $chosen", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDocumentsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.legal_documents)
            .setMessage("No legal documents uploaded yet.\n\nDocuments shared during advocate consultations will securely appear in this section.")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showRecentActivityDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recent_activity)
            .setMessage("• Searched for Family Law Advocates\n• Viewed profile of Adv. Anjali Sharma\n• Consultation scheduled for tomorrow")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showHelpCenterDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.help_center)
            .setMessage("1. How do I book a consultation?\nSearch for an advocate and tap 'Book Consultation'.\n\n2. Are my consultations private?\nYes, all communications are end-to-end encrypted.\n\n3. Can I cancel a booking?\nYes, you can manage appointments in 'My Appointments'.")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showContactSupportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.contact_support)
            .setMessage("Need help?\n\nEmail: support@askvocate.com\nHelpline: +91 1800 123 4567\nHours: Mon - Sat (9 AM - 7 PM)")
            .setPositiveButton("Email Support") { _, _ ->
                Toast.makeText(requireContext(), "Opening email client...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFeedbackDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Tell us how we can improve Askvocate..."
            minLines = 3
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.feedback)
            .setView(editText)
            .setPositiveButton("Submit") { _, _ ->
                Toast.makeText(requireContext(), "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_msg)
            .setPositiveButton(R.string.nav_logout) { _, _ ->
                // Clear profile session prefs
                prefs.edit().clear().apply()
                
                // Clear global session state
                SessionManager.setLoggedIn(requireContext(), false)
                
                // Clear Google Auth credential state
                viewLifecycleOwner.lifecycleScope.launch {
                    GoogleAuthHelper.clearCredentialState(requireContext())
                }

                // Return to the first role selection screen and clear authenticated history.
                findNavController().navigate(
                    R.id.nav_role_selection,
                    null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
