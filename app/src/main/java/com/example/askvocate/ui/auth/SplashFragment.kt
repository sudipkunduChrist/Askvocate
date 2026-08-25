package com.example.askvocate.ui.auth

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.askvocate.databinding.FragmentSplashBinding
import com.example.askvocate.R

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    
    private val animators = mutableListOf<Animator>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startAnimations()

        // 4 second delay to allow animations to play before navigating
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded && _binding != null) {
                NavHostFragment.findNavController(this).navigate(R.id.action_splash_to_role_selection)
            }
        }, 4000)
    }

    private fun startAnimations() {
        if (_binding == null) return

        // 1. Logo Animation (Fade & Scale)
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.95f
        binding.ivLogo.scaleY = 0.95f

        val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, View.ALPHA, 0f, 0.9f)
        val logoScaleX = ObjectAnimator.ofFloat(binding.ivLogo, View.SCALE_X, 0.95f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, View.SCALE_Y, 0.95f, 1f)

        val logoAnimation = AnimatorSet().apply {
            playTogether(logoAlpha, logoScaleX, logoScaleY)
            duration = 1500
            interpolator = DecelerateInterpolator()
        }
        animators.add(logoAnimation)

        // 2. Brand Name Animation (Tracking & Fade)
        binding.tvBrandName.alpha = 0f
        val brandAlpha = ObjectAnimator.ofFloat(binding.tvBrandName, View.ALPHA, 0f, 1f).apply {
            startDelay = 500
            duration = 1500
        }
        animators.add(brandAlpha)

        val brandTracking = ValueAnimator.ofFloat(0.3f, 0.05f).apply {
            startDelay = 500
            duration = 1500
            addUpdateListener { animator ->
                _binding?.tvBrandName?.letterSpacing = animator.animatedValue as Float
            }
        }
        animators.add(brandTracking)

        // 3. Tagline Animation (Slide Up & Fade)
        binding.taglineContainer.translationY = 15f
        binding.taglineContainer.alpha = 0f
        val taglineAlpha = ObjectAnimator.ofFloat(binding.taglineContainer, View.ALPHA, 0f, 0.6f)
        val taglineSlide = ObjectAnimator.ofFloat(binding.taglineContainer, View.TRANSLATION_Y, 15f, 0f)

        val taglineAnimation = AnimatorSet().apply {
            playTogether(taglineAlpha, taglineSlide)
            startDelay = 1000
            duration = 1200
            interpolator = DecelerateInterpolator()
        }
        animators.add(taglineAnimation)

        // 4. Progress Sheen Animation
        binding.progressContainer.alpha = 0f
        val progressAlpha = ObjectAnimator.ofFloat(binding.progressContainer, View.ALPHA, 0f, 1f).apply {
            startDelay = 1200
            duration = 1200
        }
        animators.add(progressAlpha)

        // Infinite sheen loop
        val progressSheen = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val currentBinding = _binding ?: return@addUpdateListener
                val progress = animator.animatedValue as Float
                val containerWidth = currentBinding.progressContainer.width.toFloat()
                if (containerWidth > 0) {
                    currentBinding.progressSheen.translationX = containerWidth * progress
                }
            }
        }
        animators.add(progressSheen)

        // Start all animations
        animators.forEach { it.start() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animators.forEach { it.cancel() }
        animators.clear()
        _binding = null
    }
}