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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentClientProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

    private fun loadUserData() {
        val name = prefs.getString(KEY_NAME, "Sudip Sharma") ?: "Sudip Sharma"
        val email = prefs.getString(KEY_EMAIL, "sudip.sharma@example.com") ?: "sudip.sharma@example.com"
        val phone = prefs.getString(KEY_PHONE, "+91 98765 43210") ?: "+91 98765 43210"
        val address = prefs.getString(KEY_ADDRESS, "123 Legal Street, Justice Colony, New Delhi 110001")
            ?: "123 Legal Street, Justice Colony, New Delhi 110001"
        val language = prefs.getString(KEY_LANGUAGE, "English") ?: "English"
        val theme = prefs.getString(KEY_THEME, "System Default") ?: "System Default"
        val isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)

        binding.tvClientName.text = name
        binding.tvClientEmail.text = email
        binding.tvClientPhone.text = phone
        binding.tvClientAddress.text = address
        binding.tvCurrentLanguage.text = language
        binding.tvCurrentTheme.text = theme
        binding.switchBiometricLock.isChecked = isBiometricEnabled
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
        // Edit Profile Trigger
        val openEditDialog = View.OnClickListener { showEditProfileDialog() }
        binding.btnEditProfile.setOnClickListener(openEditDialog)
        binding.btnEditAvatar.setOnClickListener {
            Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
        }
        binding.rowPersonalInfo.setOnClickListener(openEditDialog)
        binding.btnCompleteProfile.setOnClickListener(openEditDialog)

        // Settings / Notifications icon on top right
        binding.btnProfileSettings.setOnClickListener {
            showNotificationsDialog()
        }

        // Quick Access Cards
        binding.actionAppointments.setOnClickListener {
            findNavController().navigate(R.id.nav_appointments)
        }
        binding.actionSavedAdvocates.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
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

    private fun showEditProfileDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)

        val etName = dialogView.findViewById<EditText>(R.id.et_edit_name)
        val etEmail = dialogView.findViewById<EditText>(R.id.et_edit_email)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_edit_phone)
        val etAddress = dialogView.findViewById<EditText>(R.id.et_edit_address)

        etName.setText(binding.tvClientName.text)
        etEmail.setText(binding.tvClientEmail.text)
        etPhone.setText(binding.tvClientPhone.text)
        etAddress.setText(binding.tvClientAddress.text)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.edit_profile)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = etName.text.toString().trim()
                val newEmail = etEmail.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                val newAddress = etAddress.text.toString().trim()

                if (newName.isNotEmpty()) {
                    binding.tvClientName.text = newName
                    binding.tvClientEmail.text = newEmail
                    binding.tvClientPhone.text = newPhone
                    binding.tvClientAddress.text = newAddress

                    prefs.edit()
                        .putString(KEY_NAME, newName)
                        .putString(KEY_EMAIL, newEmail)
                        .putString(KEY_PHONE, newPhone)
                        .putString(KEY_ADDRESS, newAddress)
                        .apply()

                    updateProfileCompletion()
                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                
                // Navigate to Sign In screen clearing backstack
                findNavController().navigate(
                    R.id.nav_sign_in,
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
