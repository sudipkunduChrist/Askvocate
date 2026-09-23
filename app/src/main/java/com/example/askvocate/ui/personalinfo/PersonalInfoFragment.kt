package com.example.askvocate.ui.personalinfo

import android.os.Bundle
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.ToastType
import com.example.askvocate.util.showCustomToast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class PersonalInfoFragment : Fragment() {

    companion object {
        private const val PROFILE_PREFS = "client_profile_prefs"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_ADDRESS = "user_address"
    }

    // View Mode UI
    private lateinit var layoutViewMode: LinearLayout
    private lateinit var tvDisplayName: TextView
    private lateinit var tvDisplayEmail: TextView
    private lateinit var tvDisplayPhone: TextView
    private lateinit var tvDisplayAddress: TextView
    private lateinit var containerDisplaySpecialization: LinearLayout
    private lateinit var tvDisplaySpecialization: TextView
    private lateinit var tvDisplayRole: TextView
    private lateinit var tvDisplayUniversity: TextView
    private lateinit var tvDisplayGraduationYear: TextView
    private lateinit var tvDisplayVerificationStatus: TextView
    private lateinit var containerDisplayExperienced: LinearLayout
    private lateinit var tvDisplayBarCouncilId: TextView
    private lateinit var tvDisplayPracticeAreas: TextView
    private lateinit var tvDisplayCurrentFirm: TextView
    private lateinit var btnEditProfile: MaterialButton

    // Edit Mode UI
    private lateinit var layoutEditMode: LinearLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etSpecialization: TextInputEditText
    private lateinit var tilSpecialization: TextInputLayout
    private lateinit var containerEditLawyerDetails: LinearLayout
    private lateinit var containerEditExperienced: LinearLayout
    private lateinit var etUniversity: TextInputEditText
    private lateinit var etGraduationYear: TextInputEditText
    private lateinit var etBarCouncilId: TextInputEditText
    private lateinit var etPracticeAreas: TextInputEditText
    private lateinit var etCurrentFirm: TextInputEditText
    private lateinit var btnCancelEdit: MaterialButton
    private lateinit var btnSave: MaterialButton

    private var userId: String = ""
    private var userRole: String = ""
    private var isLawyer: Boolean = false
    private var isExperienced: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_personal_info, container, false)
        
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // View Mode Bindings
        layoutViewMode = view.findViewById(R.id.layout_view_mode)
        tvDisplayName = view.findViewById(R.id.tv_display_name)
        tvDisplayEmail = view.findViewById(R.id.tv_display_email)
        tvDisplayPhone = view.findViewById(R.id.tv_display_phone)
        tvDisplayAddress = view.findViewById(R.id.tv_display_address)
        containerDisplaySpecialization = view.findViewById(R.id.container_display_specialization)
        tvDisplaySpecialization = view.findViewById(R.id.tv_display_specialization)
        tvDisplayRole = view.findViewById(R.id.tv_display_role)
        tvDisplayUniversity = view.findViewById(R.id.tv_display_university)
        tvDisplayGraduationYear = view.findViewById(R.id.tv_display_graduation_year)
        tvDisplayVerificationStatus = view.findViewById(R.id.tv_display_verification_status)
        containerDisplayExperienced = view.findViewById(R.id.container_display_experienced)
        tvDisplayBarCouncilId = view.findViewById(R.id.tv_display_bar_council_id)
        tvDisplayPracticeAreas = view.findViewById(R.id.tv_display_practice_areas)
        tvDisplayCurrentFirm = view.findViewById(R.id.tv_display_current_firm)
        btnEditProfile = view.findViewById(R.id.btn_edit_profile)

        // Edit Mode Bindings
        layoutEditMode = view.findViewById(R.id.layout_edit_mode)
        etName = view.findViewById(R.id.et_name)
        etEmail = view.findViewById(R.id.et_email)
        etPhone = view.findViewById(R.id.et_phone)
        etAddress = view.findViewById(R.id.et_address)
        etSpecialization = view.findViewById(R.id.et_specialization)
        tilSpecialization = view.findViewById(R.id.til_specialization)
        containerEditLawyerDetails = view.findViewById(R.id.container_edit_lawyer_details)
        containerEditExperienced = view.findViewById(R.id.container_edit_experienced)
        etUniversity = view.findViewById(R.id.et_university)
        etGraduationYear = view.findViewById(R.id.et_graduation_year)
        etBarCouncilId = view.findViewById(R.id.et_bar_council_id)
        etPracticeAreas = view.findViewById(R.id.et_practice_areas)
        etCurrentFirm = view.findViewById(R.id.et_current_firm)
        btnCancelEdit = view.findViewById(R.id.btn_cancel_edit)
        btnSave = view.findViewById(R.id.btn_save_profile)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = SessionManager.getUserId(requireContext())
        userRole = SessionManager.getUserRole(requireContext())
        
        // Exact matching so clients named "Lawyer" or whatever bug don't trigger it
        isLawyer = userRole.equals("LAWYER_FRESHER", ignoreCase = true) || 
                   userRole.equals("LAWYER_EXPERIENCED", ignoreCase = true)
        isExperienced = userRole.equals("LAWYER_EXPERIENCED", ignoreCase = true)

        tvDisplayRole.text = formatRole(userRole)

        if (isLawyer) {
            containerDisplaySpecialization.isVisible = true
            tilSpecialization.isVisible = true
            containerEditLawyerDetails.isVisible = true
        }
        containerDisplayExperienced.isVisible = isExperienced
        containerEditExperienced.isVisible = isExperienced

        fetchProfile()

        btnEditProfile.setOnClickListener {
            toggleEditMode(true)
        }

        btnCancelEdit.setOnClickListener {
            toggleEditMode(false)
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }
    
    private fun toggleEditMode(isEditing: Boolean) {
        if (isEditing) {
            layoutViewMode.isVisible = false
            layoutEditMode.isVisible = true
        } else {
            layoutViewMode.isVisible = true
            layoutEditMode.isVisible = false
        }
    }

    private fun getApiEndpoint(): String {
        return when {
            userRole.equals("CLIENT", ignoreCase = true) -> "${ApiConfig.BASE_URL}/api/users/client/$userId"
            userRole.equals("LAWYER_FRESHER", ignoreCase = true) -> "${ApiConfig.BASE_URL}/api/users/lawyer/fresher/$userId"
            userRole.equals("LAWYER_EXPERIENCED", ignoreCase = true) -> "${ApiConfig.BASE_URL}/api/users/lawyer/experienced/$userId"
            else -> "${ApiConfig.BASE_URL}/api/users/client/$userId" // fallback
        }
    }

    private fun fetchProfile() {
        btnEditProfile.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(getApiEndpoint())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                
                if (responseCode in 200..299) {
                    val json = JSONObject(responseText)
                    val userObj = json.optJSONObject("user")
                    if (userObj != null) {
                        val name = if (userObj.isNull("name")) "" else userObj.optString("name", "")
                        val email = (if (userObj.isNull("email")) "" else userObj.optString("email", ""))
                            .ifBlank { SessionManager.getUserEmail(requireContext()) }
                        val phone = if (userObj.isNull("phone")) "" else userObj.optString("phone", "")
                        val address = if (userObj.isNull("address")) "" else userObj.optString("address", "")
                        val specialization = if (userObj.isNull("specialization")) "" else userObj.optString("specialization", "")
                        val university = userObj.optString("university", "")
                        val graduationYear = userObj.optInt("graduationYear", 0)
                        val verificationStatus = userObj.optString("verificationStatus", "")
                        val barCouncilId = userObj.optString("barCouncilId", "")
                        val currentFirm = userObj.optString("currentFirm", "")
                        val practiceAreas = userObj.optJSONArray("practiceAreas")?.let { array ->
                            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                        } ?: emptyList()

                        withContext(Dispatchers.Main) {
                            // Populate View Mode
                            tvDisplayName.text = name.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayEmail.text = email.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayPhone.text = phone.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayAddress.text = address.takeIf { it.isNotEmpty() } ?: "Not Set"
                            if (isLawyer) {
                                tvDisplaySpecialization.text = specialization.takeIf { it.isNotEmpty() } ?: "Not Set"
                                tvDisplayUniversity.text = university.takeIf { it.isNotEmpty() } ?: "Not Set"
                                tvDisplayGraduationYear.text = graduationYear.takeIf { it > 0 }?.toString() ?: "Not Set"
                                tvDisplayVerificationStatus.text = verificationStatus.takeIf { it.isNotEmpty() }?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not Set"
                            }
                            if (isExperienced) {
                                tvDisplayBarCouncilId.text = barCouncilId.takeIf { it.isNotEmpty() } ?: "Not Set"
                                tvDisplayPracticeAreas.text = practiceAreas.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not Set"
                                tvDisplayCurrentFirm.text = currentFirm.takeIf { it.isNotEmpty() } ?: "Not Set"
                            }

                            // Populate Edit Mode
                            etName.setText(name)
                            etEmail.setText(email)
                            etPhone.setText(phone)
                            etAddress.setText(address)
                            if (isLawyer) {
                                etSpecialization.setText(specialization)
                                etUniversity.setText(university)
                                etGraduationYear.setText(graduationYear.takeIf { it > 0 }?.toString().orEmpty())
                            }
                            if (isExperienced) {
                                etBarCouncilId.setText(barCouncilId)
                                etPracticeAreas.setText(practiceAreas.joinToString(", "))
                                etCurrentFirm.setText(currentFirm)
                            }

                            cacheProfile(name, email, phone, address)
                            
                            btnEditProfile.isEnabled = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        requireContext().showCustomToast("Failed to fetch profile", ToastType.ERROR)
                        btnEditProfile.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("PersonalInfo", "Network error fetching profile", e)
                withContext(Dispatchers.Main) {
                    requireContext().showCustomToast("Network error", ToastType.ERROR)
                    btnEditProfile.isEnabled = true
                }
            }
        }
    }

    private fun saveProfile() {
        btnSave.isEnabled = false
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val specialization = etSpecialization.text.toString().trim()
        val university = etUniversity.text.toString().trim()
        val graduationYear = etGraduationYear.text.toString().trim().toIntOrNull()
        val barCouncilId = etBarCouncilId.text.toString().trim()
        val practiceAreas = etPracticeAreas.text.toString().split(",").map(String::trim).filter(String::isNotEmpty)
        val currentFirm = etCurrentFirm.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(getApiEndpoint())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000

                val body = JSONObject().apply {
                    put("name", name)
                    put("email", email)
                    put("phone", phone)
                    put("address", address)
                    if (isLawyer) {
                        put("specialization", specialization)
                        put("university", university)
                        if (graduationYear != null) put("graduationYear", graduationYear)
                    }
                    if (isExperienced) {
                        put("barCouncilId", barCouncilId)
                        put("practiceAreas", JSONArray(practiceAreas))
                        put("currentFirm", currentFirm)
                    }
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""

                val json = runCatching { JSONObject(responseText) }.getOrNull()
                val isSuccess = responseCode in 200..299 && json?.optBoolean("success", false) == true

                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        requireContext().showCustomToast("Profile updated successfully", ToastType.SUCCESS)
                        // Also update SessionManager cached name
                        SessionManager.saveUser(
                            requireContext(),
                            userId,
                            name,
                            email,
                            userRole
                        )
                        cacheProfile(name, email, phone, address)
                        // Update View Mode with new data
                        tvDisplayName.text = name.takeIf { it.isNotEmpty() } ?: "Not Set"
                        tvDisplayEmail.text = email.takeIf { it.isNotEmpty() } ?: "Not Set"
                        tvDisplayPhone.text = phone.takeIf { it.isNotEmpty() } ?: "Not Set"
                        tvDisplayAddress.text = address.takeIf { it.isNotEmpty() } ?: "Not Set"
                        if (isLawyer) {
                            tvDisplaySpecialization.text = specialization.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayUniversity.text = university.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayGraduationYear.text = graduationYear?.toString() ?: "Not Set"
                        }
                        if (isExperienced) {
                            tvDisplayBarCouncilId.text = barCouncilId.takeIf { it.isNotEmpty() } ?: "Not Set"
                            tvDisplayPracticeAreas.text = practiceAreas.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not Set"
                            tvDisplayCurrentFirm.text = currentFirm.takeIf { it.isNotEmpty() } ?: "Not Set"
                        }
                        
                        btnSave.isEnabled = true
                        toggleEditMode(false)
                    } else {
                        val errorMsg = json?.optString("error") ?: "Update failed"
                        requireContext().showCustomToast(errorMsg, ToastType.ERROR)
                        btnSave.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("PersonalInfo", "Network error updating profile", e)
                withContext(Dispatchers.Main) {
                    requireContext().showCustomToast("Network error", ToastType.ERROR)
                    btnSave.isEnabled = true
                }
            }
        }
    }

    private fun cacheProfile(name: String, email: String, phone: String, address: String) {
        requireContext().getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ADDRESS, address)
            .apply()
    }

    private fun formatRole(role: String): String = when (role.uppercase()) {
        "LAWYER_EXPERIENCED" -> "Experienced Lawyer"
        "LAWYER_FRESHER" -> "Fresher Lawyer"
        "ADMIN" -> "Administrator"
        else -> "Client"
    }
}
