package com.example.askvocate.ui.auth

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentLegalJourneyBinding
import com.example.askvocate.databinding.LayoutJourneyItemBinding

class LegalJourneyFragment : Fragment() {

    private var _binding: FragmentLegalJourneyBinding? = null
    private val binding get() = _binding!!
    
    private val animators = mutableListOf<Animator>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLegalJourneyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTimeline()
        setupListeners()
    }

    private fun setupTimeline() {
        // Initialize items
        configureItem(binding.item1, "Discuss Privately", "Start by detailing your situation in a completely secure, encrypted environment.", R.drawable.ic_lock)
        configureItem(binding.item2, "AI Understands Your Case", "Our advanced Legal AI analyzes your input instantly and structures your narrative.", R.drawable.ic_psychology)
        configureItem(binding.item3, "Find the Right Lawyer", "Match with specialized attorneys whose expertise aligns with your legal needs.", R.drawable.ic_group)
        configureItem(binding.item4, "Lawyers Choose Cases", "Review curated proposals and select the counsel that best fits your budget.", R.drawable.ic_description)
        configureItem(binding.item5, "Communicate Securely", "All communication and document sharing happens within our encrypted vault.", R.drawable.ic_forum)
        configureItem(binding.item6, "Complete Your Legal Journey", "Experience a structured and transparent process powered by AI.", R.drawable.ic_verified_user)

        // Scroll listener for progress line and active states
        binding.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val childHeight = v.getChildAt(0).height
            val maxScroll = childHeight - v.height
            if (maxScroll > 0) {
                updateTimelineProgress(scrollY, maxScroll)
            }
        })
    }

    private fun configureItem(itemBinding: LayoutJourneyItemBinding, title: String, body: String, iconRes: Int) {
        itemBinding.tvItemTitle.text = title
        itemBinding.tvItemBody.text = body
        itemBinding.ivIcon.setImageResource(iconRes)
    }

    private fun updateTimelineProgress(scrollY: Int, maxScroll: Int) {
        val progress = scrollY.toFloat() / maxScroll
        val params = binding.timelineProgress.layoutParams
        params.height = (binding.timelineLine.height * progress).toInt()
        binding.timelineProgress.layoutParams = params

        // Update active states based on position (approximate points)
        updateActiveState(binding.item1, scrollY, 0)
        updateActiveState(binding.item2, scrollY, 300)
        updateActiveState(binding.item3, scrollY, 600)
        updateActiveState(binding.item4, scrollY, 900)
        updateActiveState(binding.item5, scrollY, 1200)
        updateActiveState(binding.item6, scrollY, 1500)
    }

    private fun updateActiveState(itemBinding: LayoutJourneyItemBinding, scrollY: Int, activationPoint: Int) {
        val isActive = scrollY >= activationPoint
        val targetScale = if (isActive) 1.15f else 1.0f
        val targetColor = if (isActive) R.color.journey_gold else R.color.white
        val targetIconColor = if (isActive) R.color.white else R.color.journey_brass

        if (itemBinding.iconContainer.tag != isActive) {
            itemBinding.iconContainer.tag = isActive
            
            // Animate scale and rotation
            val scaleX = ObjectAnimator.ofFloat(itemBinding.iconContainer, View.SCALE_X, targetScale)
            val scaleY = ObjectAnimator.ofFloat(itemBinding.iconContainer, View.SCALE_Y, targetScale)
            scaleX.start()
            scaleY.start()
            animators.add(scaleX)
            animators.add(scaleY)

            if (isActive) {
                val rotation = ObjectAnimator.ofFloat(itemBinding.iconContainer, View.ROTATION, 0f, 360f)
                rotation.start()
                animators.add(rotation)
            }

            // Update colors
            itemBinding.iconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), targetColor))
            itemBinding.ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), targetIconColor))
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnStart.setOnClickListener {
            findNavController().navigate(R.id.action_legal_journey_to_role_selection)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animators.forEach { it.cancel() }
        animators.clear()
        _binding = null
    }
}