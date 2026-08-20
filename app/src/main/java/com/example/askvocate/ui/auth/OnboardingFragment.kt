package com.example.askvocate.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.askvocate.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_next)
        val tvSkip = view.findViewById<TextView>(R.id.tv_skip)

        val adapter = OnboardingAdapter {
            navigateToGetStarted()
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        btnNext.setOnClickListener {
            if (viewPager.currentItem < 2) {
                viewPager.currentItem += 1
            } else {
                navigateToGetStarted()
            }
        }

        tvSkip.setOnClickListener {
            navigateToGetStarted()
        }
    }

    private fun navigateToGetStarted() {
        NavHostFragment.findNavController(this).navigate(R.id.action_onboarding_to_get_started)
    }

    inner class OnboardingAdapter(private val onFinish: () -> Unit) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = 3

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(position: Int) {
                val tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
                val tvDesc = itemView.findViewById<TextView>(R.id.tv_description)
                val btn = itemView.findViewById<View>(R.id.btn_get_started_onboarding)

                when (position) {
                    0 -> {
                        tvTitle.text = getString(R.string.welcome_askvocate_title)
                        tvDesc.text = getString(R.string.onboarding_step_1_desc)
                    }
                    1 -> {
                        tvTitle.text = getString(R.string.onboarding_step_2_title)
                        tvDesc.text = getString(R.string.onboarding_step_2_desc)
                    }
                    2 -> {
                        tvTitle.text = getString(R.string.lets_start_exploring)
                        tvDesc.text = getString(R.string.onboarding_step_3_desc)
                        btn.visibility = View.VISIBLE
                        btn.setOnClickListener { onFinish() }
                    }
                }
            }
        }
    }
}