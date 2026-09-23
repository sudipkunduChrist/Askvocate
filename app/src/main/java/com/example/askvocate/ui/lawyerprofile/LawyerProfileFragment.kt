package com.example.askvocate.ui.lawyerprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.askvocate.R
import com.example.askvocate.data.model.DatasetLawyerProfile
import com.example.askvocate.ui.adapters.ReviewAdapter
import com.example.askvocate.util.AnimationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class LawyerProfileFragment : Fragment() {

    private val viewModel: LawyerProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_lawyer_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lawyerId = arguments?.getString("lawyerId") ?: return
        val rvReviews = view.findViewById<RecyclerView>(R.id.rv_reviews)
        val reviewsTitle = view.findViewById<TextView>(R.id.tv_reviews_title)
        val detailsTitle = view.findViewById<TextView>(R.id.tv_dataset_details_title)
        val detailsCard = view.findViewById<MaterialCardView>(R.id.card_dataset_details)
        val detailsContainer = view.findViewById<LinearLayout>(R.id.dataset_details_container)
        val progress = view.findViewById<ProgressBar>(R.id.progress_profile)
        val errorText = view.findViewById<TextView>(R.id.tv_profile_error)
        val messageButton = view.findViewById<MaterialButton>(R.id.btn_message)
        val bookButton = view.findViewById<MaterialButton>(R.id.btn_book)

        val reviewAdapter = ReviewAdapter()
        rvReviews.adapter = reviewAdapter

        view.findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.lawyer.observe(viewLifecycleOwner) { lawyer ->
            view.findViewById<TextView>(R.id.tv_lawyer_name).text = lawyer.name
            view.findViewById<TextView>(R.id.tv_lawyer_specialty).text =
                "${lawyer.specialty} • ${lawyer.yearsExperience} Yrs Exp"
            view.findViewById<TextView>(R.id.tv_rating).text = lawyer.rating.toString()
            view.findViewById<TextView>(R.id.tv_review_count).text =
                if (lawyer.id.startsWith("ADV-")) "Dataset rating" else "${lawyer.reviewCount} reviews"
            view.findViewById<TextView>(R.id.tv_location).text = lawyer.location
            view.findViewById<TextView>(R.id.tv_bio).text = lawyer.bio

            view.findViewById<ImageView>(R.id.iv_lawyer_avatar).load(lawyer.imageResId ?: lawyer.imageUrl) {
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                fallback(R.drawable.ic_profile)
            }
        }

        viewModel.datasetProfile.observe(viewLifecycleOwner) { profile ->
            val hasDatasetProfile = profile != null
            detailsTitle.isVisible = hasDatasetProfile
            detailsCard.isVisible = hasDatasetProfile
            reviewsTitle.isVisible = !hasDatasetProfile
            rvReviews.isVisible = !hasDatasetProfile
            if (profile != null) populateDatasetDetails(detailsContainer, profile)
        }

        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewAdapter.submitList(reviews)
            if (reviews.isNotEmpty()) AnimationUtils.runLayoutAnimation(rvReviews)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { progress.isVisible = it }
        viewModel.error.observe(viewLifecycleOwner) { message ->
            errorText.isVisible = !message.isNullOrBlank()
            errorText.text = message.orEmpty()
        }

        messageButton.setOnClickListener {
            if (viewModel.datasetProfile.value != null) {
                Toast.makeText(requireContext(), "Messaging will be available when this advocate registers on Askvocate.", Toast.LENGTH_SHORT).show()
            } else {
                val bundle = Bundle().apply { putString("chatId", "chat_new") }
                findNavController().navigate(R.id.action_lawyer_profile_to_chat_detail, bundle)
            }
        }
        bookButton.setOnClickListener {
            Toast.makeText(
                requireContext(),
                if (viewModel.datasetProfile.value != null)
                    "Booking will be available when this advocate registers on Askvocate."
                else "Booking flow coming soon!",
                Toast.LENGTH_SHORT
            ).show()
        }

        viewModel.loadLawyerProfile(lawyerId)
    }

    private fun populateDatasetDetails(container: LinearLayout, profile: DatasetLawyerProfile) {
        container.removeAllViews()

        addSection(container, "Practice")
        addDetail(container, "Advocate ID", profile.recordId)
        addDetail(container, "Designation", profile.designation)
        addDetail(container, "Primary practice area", profile.primaryPracticeArea)
        addDetail(container, "Other practice areas", readable(profile.secondaryPracticeAreas))
        addDetail(container, "Practice type", profile.practiceType)
        addDetail(container, "Experience", profile.experienceLevel)
        addDetail(container, "Years of experience", profile.yearsExperience.takeIf { it > 0 }?.toString().orEmpty())
        addDetail(container, "Cases handled", profile.casesHandled.takeIf { it > 0 }?.toString().orEmpty())
        addDetail(container, "Firm / organisation", profile.firm)

        addSection(container, "Enrollment & education")
        addDetail(container, "Verification", profile.verificationStatus)
        addDetail(container, "Advocate status", profile.advocateStatus)
        addDetail(container, "Bar council", profile.barCouncil)
        addDetail(container, "Enrollment number", profile.enrollmentNumber)
        addDetail(container, "Enrollment date", profile.enrollmentDate)
        addDetail(container, "Bar membership", profile.barMembership)
        addDetail(container, "Law degree", profile.lawDegree)
        addDetail(container, "Law school", profile.lawSchool)

        addSection(container, "Courts & location")
        addDetail(container, "Primary court", profile.primaryCourt)
        addDetail(container, "Other courts", readable(profile.otherCourts))
        addDetail(container, "Office address", profile.officeAddress)
        addDetail(container, "City", profile.city)
        addDetail(container, "District", profile.district)
        addDetail(container, "State", profile.state)
        addDetail(container, "PIN code", profile.pincode)

        addSection(container, "Consultation & contact")
        addDetail(container, "Languages", readable(profile.languages))
        addDetail(container, "Consultation mode", readable(profile.consultationMode))
        addDetail(container, "Availability", profile.availability)
        addDetail(
            container,
            "Consultation fee",
            profile.consultationFee.takeIf { it > 0 }?.let {
                "₹${NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(it)}"
            }.orEmpty()
        )
        addDetail(container, "Email", profile.email)
        addDetail(container, "Phone", profile.phone)
        addDetail(container, "Gender", profile.gender)
    }

    private fun addSection(container: LinearLayout, title: String) {
        val heading = TextView(requireContext()).apply {
            text = title
            setTextAppearance(R.style.TextAppearance_Askvocate_Title)
            setTextColor(requireContext().getColor(R.color.navy_primary))
            setPadding(0, dp(14), 0, dp(6))
        }
        container.addView(heading)
    }

    private fun addDetail(container: LinearLayout, label: String, value: String) {
        if (value.isBlank() || value.equals("nan", true)) return
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        row.addView(TextView(requireContext()).apply {
            text = label
            setTextAppearance(R.style.TextAppearance_Askvocate_Caption)
            setTextColor(requireContext().getColor(R.color.text_tertiary))
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            setTextAppearance(R.style.TextAppearance_Askvocate_Body)
            setTextColor(requireContext().getColor(R.color.text_primary))
            setPadding(0, dp(2), 0, 0)
            setTextIsSelectable(true)
        })
        container.addView(row)
        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(requireContext().getColor(R.color.divider))
        })
    }

    private fun readable(value: String): String = value.replace(";", " • ")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
