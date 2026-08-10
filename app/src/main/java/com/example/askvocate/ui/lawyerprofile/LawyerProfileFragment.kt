package com.example.askvocate.ui.lawyerprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.ReviewAdapter
import com.example.askvocate.util.AnimationUtils
import com.google.android.material.button.MaterialButton

class LawyerProfileFragment : Fragment() {

    private val viewModel: LawyerProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_lawyer_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lawyerId = arguments?.getString("lawyerId") ?: return

        val rvReviews = view.findViewById<RecyclerView>(R.id.rv_reviews)
        val reviewAdapter = ReviewAdapter()
        rvReviews.adapter = reviewAdapter

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.lawyer.observe(viewLifecycleOwner) { lawyer ->
            view.findViewById<TextView>(R.id.tv_lawyer_name).text = lawyer.name
            view.findViewById<TextView>(R.id.tv_lawyer_specialty).text = "${lawyer.specialty} • ${lawyer.yearsExperience} Yrs Exp"
            view.findViewById<TextView>(R.id.tv_rating).text = lawyer.rating.toString()
            view.findViewById<TextView>(R.id.tv_review_count).text = "${lawyer.reviewCount} reviews"
            view.findViewById<TextView>(R.id.tv_location).text = lawyer.location
            view.findViewById<TextView>(R.id.tv_bio).text = lawyer.bio
        }

        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewAdapter.submitList(reviews)
            AnimationUtils.runLayoutAnimation(rvReviews)
        }

        view.findViewById<MaterialButton>(R.id.btn_message).setOnClickListener {
            val bundle = Bundle().apply { putString("chatId", "chat_new") }
            findNavController().navigate(R.id.action_lawyer_profile_to_chat_detail, bundle)
        }

        viewModel.loadLawyerProfile(lawyerId)
    }
}
