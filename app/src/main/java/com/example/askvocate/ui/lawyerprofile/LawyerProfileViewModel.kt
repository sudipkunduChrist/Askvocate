package com.example.askvocate.ui.lawyerprofile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.askvocate.data.model.DatasetLawyerProfile
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.model.Review
import com.example.askvocate.data.repository.LawyerRepository
import com.example.askvocate.data.repository.SavedAdvocatesRepository
import com.example.askvocate.network.ApiConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LawyerProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LawyerRepository()
    private val savedSearchRepo = SavedAdvocatesRepository(getApplication())

    private val _lawyer = MutableLiveData<Lawyer>()
    val lawyer: LiveData<Lawyer> = _lawyer

    private val _datasetProfile = MutableLiveData<DatasetLawyerProfile?>()
    val datasetProfile: LiveData<DatasetLawyerProfile?> = _datasetProfile

    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadLawyerProfile(lawyerId: String) {
        if (lawyerId.startsWith("ADV-", ignoreCase = true) || lawyerId.startsWith("adv-", ignoreCase = true) || lawyerId.contains("adv_")) {
            loadDatasetProfile(lawyerId)
            return
        }

        repository.getLawyerById(lawyerId)?.let {
            _lawyer.value = it
            _reviews.value = repository.getReviewsForLawyer(lawyerId)
        } ?: run {
            // Check if it's a saved advocate from AI recommendations
            val savedAdvocate = savedSearchRepo.getSavedSearches()
                .flatMap { it.advocates }
                .find { it.lawyerId.equals(lawyerId, ignoreCase = true) }

            if (savedAdvocate != null) {
                // If saved advocate ID is a dataset record ID, load full dataset profile!
                if (savedAdvocate.lawyerId.startsWith("ADV-", ignoreCase = true) || savedAdvocate.lawyerId.contains("adv_")) {
                    loadDatasetProfile(savedAdvocate.lawyerId)
                    return@run
                }

                _lawyer.value = Lawyer(
                    id = savedAdvocate.lawyerId,
                    name = savedAdvocate.name,
                    specialty = savedAdvocate.specialty,
                    rating = savedAdvocate.rating,
                    reviewCount = 0,
                    location = savedAdvocate.location,
                    bio = "${savedAdvocate.name} specializes in ${savedAdvocate.specialty} with ${savedAdvocate.yearsExperience} years of experience in ${savedAdvocate.location}.",
                    yearsExperience = savedAdvocate.yearsExperience,
                    isVerified = savedAdvocate.isVerified,
                    consultationFee = savedAdvocate.consultationFee
                )
                _reviews.value = emptyList()
            } else {
                // Try fetching dataset profile as final fallback for any backend record ID
                loadDatasetProfile(lawyerId)
            }
        }
    }

    private fun loadDatasetProfile(lawyerId: String) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { fetchDatasetProfile(lawyerId) }
                _datasetProfile.value = profile
                _reviews.value = emptyList()
                _lawyer.value = Lawyer(
                    id = profile.recordId,
                    name = profile.name,
                    specialty = profile.primaryPracticeArea,
                    rating = profile.rating,
                    reviewCount = 0,
                    location = listOf(profile.city, profile.state).filter { it.isNotBlank() }.joinToString(", "),
                    bio = buildString {
                        append(profile.name)
                        append(" practices in ")
                        append(profile.primaryPracticeArea.ifBlank { "law" })
                        if (profile.yearsExperience > 0) append(" with ${profile.yearsExperience} years of experience")
                        append('.')
                    },
                    yearsExperience = profile.yearsExperience,
                    isVerified = profile.verificationStatus.equals("Verified", true),
                    consultationFee = profile.consultationFee
                )
            } catch (error: Exception) {
                _error.value = error.message ?: "Unable to load advocate details."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchDatasetProfile(lawyerId: String): DatasetLawyerProfile {
        val connection = (URL("${ApiConfig.AI_BASE_URL}/lawyers/${Uri.encode(lawyerId)}")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = body.takeIf { it.isNotBlank() }?.let(::JSONObject)
            if (status !in 200..299) {
                throw IllegalStateException(json?.optString("detail") ?: "Unable to load advocate details.")
            }
            json?.toProfile() ?: throw IllegalStateException("Invalid advocate profile response.")
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toProfile(): DatasetLawyerProfile {
        fun text(key: String): String = if (isNull(key)) "" else optString(key).takeUnless { it == "null" } ?: ""
        return DatasetLawyerProfile(
            recordId = text("record_id"),
            name = text("advocate_name"),
            gender = text("gender"),
            designation = text("designation"),
            barCouncil = text("bar_council"),
            enrollmentNumber = text("enrollment_number"),
            enrollmentDate = text("enrollment_date"),
            advocateStatus = text("advocate_status"),
            yearsExperience = optDouble("years_of_experience", 0.0).toInt(),
            state = text("state"),
            district = text("district"),
            city = text("city"),
            pincode = text("pincode"),
            primaryCourt = text("primary_court"),
            otherCourts = text("other_practice_courts"),
            primaryPracticeArea = text("practice_area_primary"),
            secondaryPracticeAreas = text("practice_area_secondary"),
            practiceType = text("practice_type"),
            firm = text("organisation_or_firm"),
            barMembership = text("bar_membership"),
            lawDegree = text("law_degree"),
            lawSchool = text("law_school"),
            languages = text("languages"),
            consultationMode = text("consultation_mode"),
            availability = text("availability"),
            email = text("email"),
            phone = text("phone"),
            officeAddress = text("office_address"),
            rating = optDouble("profile_rating", 0.0),
            consultationFee = optDouble("consultation_fee_inr", 0.0).toInt(),
            casesHandled = optDouble("cases_handled_approx", 0.0).toInt(),
            verificationStatus = text("verification_status"),
            experienceLevel = text("experience_level")
        )
    }
}
