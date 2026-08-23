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
        animateEntrance()
        startPulsingAnimation()
    }

    private fun animateEntrance() {
        val icons = listOf(binding.icon1, binding.icon2, binding.icon3, binding.icon4, binding.icon5, binding.icon6)

        val allItems = listOf(
            binding.item1.root, binding.item2.root, binding.item3.root,
            binding.item4.root, binding.item5.root, binding.item6.root
        )

        allItems.forEachIndexed { index, view ->
            val isLeft = index % 2 == 0
            view.alpha = 0f
            view.translationX = if (isLeft) -200f else 200f
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(600)
                .setStartDelay(index * 200L)
                .start()
        }
        
        icons.forEachIndexed { index, icon ->
            icon.alpha = 0f
            icon.scaleX = 0f
            icon.scaleY = 0f
            icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay(index * 200L + 100L)
                .start()
        }
    }

    private fun startPulsingAnimation() {
        val scaleX = ObjectAnimator.ofFloat(binding.btnStart, View.SCALE_X, 1f, 1.02f)
        val scaleY = ObjectAnimator.ofFloat(binding.btnStart, View.SCALE_Y, 1f, 1.02f)

        listOf(scaleX, scaleY).forEach {
            it.duration = 2000
            it.repeatCount = ObjectAnimator.INFINITE
            it.repeatMode = ObjectAnimator.REVERSE
            it.start()
            animators.add(it)
        }
    }

    private fun setupTimeline() {
        // Initialize items with premium content from code.html
        configureItem(binding.item1, "Discuss Privately", "Secure initial consultation to understand your needs.", true)
        configureItem(binding.item2, "AI Understands", "Advanced legal analysis to build your case profile.", false)
        configureItem(binding.item3, "Right Lawyer", "Targeted professional matching based on expertise.", true)
        configureItem(binding.item4, "Lawyers Choose", "Selective representation by top-tier legal minds.", false)
        configureItem(binding.item5, "Communicate Securely", "Encrypted dialogue for sensitive information.", true)
        configureItem(binding.item6, "Complete Journey", "Resolution and finalization of your matter.", false)

        // Show image views in cards as per code.html
        listOf(binding.item1, binding.item2, binding.item3, binding.item6).forEach { 
            it.ivItemImage.visibility = View.VISIBLE
        }
    }

    private fun configureItem(itemBinding: LayoutJourneyItemBinding, title: String, body: String, isLeft: Boolean) {
        itemBinding.tvItemTitle.text = title
        itemBinding.tvItemBody.text = body
        
        // Align text towards the timeline (Center)
        val gravity = if (isLeft) android.view.Gravity.END else android.view.Gravity.START
        itemBinding.tvItemTitle.gravity = gravity
        itemBinding.tvItemBody.gravity = gravity
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