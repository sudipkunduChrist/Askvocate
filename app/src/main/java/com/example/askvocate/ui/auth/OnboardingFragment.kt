package com.example.askvocate.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton

class OnboardingFragment : Fragment() {

    private var isLawyer: Boolean = false
    private lateinit var dots: Array<View>

    // When the user leaves the onboarding for Sign Up / Sign In, remember the page they
    // were on (the final "Get Started" page) so that pressing Back returns them to that
    // page instead of restarting at the first page.
    private var pageToRestoreOnReturn = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLawyer = arguments?.getBoolean("isLawyer", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager    = view.findViewById<ViewPager2>(R.id.viewPager)
        val dotContainer = view.findViewById<LinearLayout>(R.id.dot_container)
        val btnNext      = view.findViewById<ImageButton>(R.id.btn_next)
        val tvSkip       = view.findViewById<TextView>(R.id.tv_skip)

        val btnBack      = view.findViewById<ImageButton>(R.id.btn_back_onboarding)

        val totalSlides = 5

        val adapter = OnboardingAdapter(isLawyer) { navigateToSignUp() }
        viewPager.adapter = adapter

        // Build 5 circular dot indicators
        setupDots(dotContainer, totalSlides)

        // Custom back button handling: step back page by page through the ViewPager
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) {
                    viewPager.currentItem -= 1
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        btnBack.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = 0
            } else {
                NavHostFragment.findNavController(this).navigateUp()
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val isLastSlide = position == totalSlides - 1
                // Hide next arrow & skip pill on the last slide
                btnNext.visibility = if (isLastSlide) View.GONE else View.VISIBLE
                tvSkip.visibility  = if (isLastSlide) View.GONE else View.VISIBLE

                if (isLastSlide) {
                    btnBack.imageTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.navy_primary)
                    )
                    btnBack.background = ContextCompat.getDrawable(
                        requireContext(), R.drawable.bg_pill_button_outline
                    )
                } else {
                    btnBack.imageTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.white)
                    )
                    btnBack.background = ContextCompat.getDrawable(
                        requireContext(), R.drawable.bg_skip_pill
                    )
                }
            }
        })

        btnNext.setOnClickListener {
            val next = viewPager.currentItem + 1
            if (next < totalSlides) viewPager.currentItem = next else navigateToSignUp()
        }

        // Skip button jumps to slide 5 (Let's start exploring)
        tvSkip.setOnClickListener { viewPager.currentItem = totalSlides - 1 }

        // Coming back from Sign Up / Sign In: land on the "Get Started" page the user
        // left from (the ViewPager would otherwise restart at the first page).
        if (pageToRestoreOnReturn >= 0) {
            viewPager.setCurrentItem(pageToRestoreOnReturn, false)
            pageToRestoreOnReturn = -1
        }
    }

    // ── Dot helpers ──────────────────────────────────────────────────────────

    private fun setupDots(container: LinearLayout, count: Int) {
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val dotPx = (8 * dp).toInt()   // 8 dp diameter
        val gapPx = (6 * dp).toInt()   // 6 dp spacing

        dots = Array(count) { i ->
            View(requireContext()).apply {
                val lp = LinearLayout.LayoutParams(dotPx, dotPx)
                if (i > 0) lp.marginStart = gapPx
                layoutParams = lp
                background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.bg_onboarding_dot
                )
                isSelected = i == 0
            }.also { container.addView(it) }
        }
    }

    private fun updateDots(activePosition: Int) {
        dots.forEachIndexed { i, dot -> dot.isSelected = i == activePosition }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun navigateToSignUp() {
        pageToRestoreOnReturn =
            view?.findViewById<ViewPager2>(R.id.viewPager)?.currentItem ?: -1
        val actionId = if (isLawyer)
            R.id.action_onboarding_to_lawyer_sign_up
        else
            R.id.action_onboarding_to_sign_up
        NavHostFragment.findNavController(this).navigate(actionId)
    }

    // ── Data Class ───────────────────────────────────────────────────────────

    data class OnboardingSlide(
        val title: String,
        val description: String,
        val imageResId: Int,
        val isFinal: Boolean
    )

    // ── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_FINAL  = 1
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    inner class OnboardingAdapter(
        private val isLawyer: Boolean,
        private val onFinish: () -> Unit
    ) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        private val slides: List<OnboardingSlide> by lazy {
            if (isLawyer) listOf(
                OnboardingSlide(
                    getString(R.string.lawyer_slide_1_title),
                    getString(R.string.lawyer_slide_1_desc),
                    R.drawable.ic_onboard_welcome_lawyer,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.lawyer_slide_2_title),
                    getString(R.string.lawyer_slide_2_desc),
                    R.drawable.ic_onboard_lawyer_practice,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.lawyer_slide_3_title),
                    getString(R.string.lawyer_slide_3_desc),
                    R.drawable.ic_onboard_lawyer_schedule,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.lawyer_slide_4_title),
                    getString(R.string.lawyer_slide_4_desc),
                    R.drawable.ic_onboard_lawyer_verification,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.onboarding_final_title),
                    "",
                    0,
                    true
                )
            ) else listOf(
                OnboardingSlide(
                    getString(R.string.client_slide_1_title),
                    getString(R.string.client_slide_1_desc),
                    R.drawable.ic_onboard_welcome_client,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.client_slide_2_title),
                    getString(R.string.client_slide_2_desc),
                    R.drawable.ic_onboard_find_lawyer,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.client_slide_3_title),
                    getString(R.string.client_slide_3_desc),
                    R.drawable.ic_onboard_consultation,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.client_slide_4_title),
                    getString(R.string.client_slide_4_desc),
                    R.drawable.ic_onboard_security,
                    false
                ),
                OnboardingSlide(
                    getString(R.string.onboarding_final_title),
                    "",
                    0,
                    true
                )
            )
        }

        override fun getItemViewType(position: Int) =
            if (slides[position].isFinal) VIEW_TYPE_FINAL else VIEW_TYPE_NORMAL

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutRes = if (viewType == VIEW_TYPE_FINAL)
                R.layout.item_onboarding_final else R.layout.item_onboarding
            val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(slides[position])

        override fun getItemCount() = slides.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(slide: OnboardingSlide) {
                // Title exists on both layouts
                itemView.findViewById<TextView>(R.id.tv_title)?.text = slide.title

                // Description and Illustration exist on normal slides
                itemView.findViewById<TextView>(R.id.tv_description)?.text = slide.description

                itemView.findViewById<ImageView>(R.id.iv_illustration)?.apply {
                    if (slide.imageResId != 0) {
                        setImageResource(slide.imageResId)
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                // Wire Get Started button on the final slide
                if (slide.isFinal) {
                    itemView.findViewById<MaterialButton>(R.id.btn_get_started_final)
                        ?.setOnClickListener { onFinish() }
                }
            }
        }
    }
}