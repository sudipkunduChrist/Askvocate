package com.example.askvocate.ui.home

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.MainActivity
import com.example.askvocate.R
import com.example.askvocate.data.model.dummyOngoingCases
import com.example.askvocate.data.model.dummyTopLawyers
import com.example.askvocate.ui.adapters.OngoingCaseAdapter
import com.example.askvocate.ui.adapters.TopLawyerAdapter
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val greeting = view.findViewById<android.widget.TextView>(R.id.tv_greeting)
        greeting.text = getString(R.string.greeting_line, getString(R.string.greeting_name))

        // --- Carousels -------------------------------------------------
        // Sample content is submitted synchronously so the very first frame of
        // Home already shows the cards (no empty-pop-in after the nav bar).
        val rvCases = view.findViewById<RecyclerView>(R.id.rv_ongoing_cases)
        val caseAdapter = OngoingCaseAdapter()
        rvCases.adapter = caseAdapter
        caseAdapter.submitList(dummyOngoingCases)

        val rvTopLawyers = view.findViewById<RecyclerView>(R.id.rv_top_lawyers)
        val topLawyerAdapter = TopLawyerAdapter()
        rvTopLawyers.adapter = topLawyerAdapter
        topLawyerAdapter.submitList(dummyTopLawyers)

        // --- Filter chips (All | Business | Marriage | Criminal | Employment) ---
        val chips = listOf(
            view.findViewById<View>(R.id.chip_all),
            view.findViewById<View>(R.id.chip_business),
            view.findViewById<View>(R.id.chip_marriage),
            view.findViewById<View>(R.id.chip_criminal),
            view.findViewById<View>(R.id.chip_employment)
        )
        var selectedChip = 0
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                if (selectedChip != index) {
                    selectedChip = index
                    chips.forEachIndexed { i, c -> renderChip(c, i == selectedChip) }
                }
            }
        }
        chips.forEachIndexed { i, c -> renderChip(c, i == selectedChip) }

        // --- Category pills (single select) ---
        val categories = listOf(
            view.findViewById<View>(R.id.cat_family),
            view.findViewById<View>(R.id.cat_property),
            view.findViewById<View>(R.id.cat_employment),
            view.findViewById<View>(R.id.cat_civil)
        )
        var selectedCategory = 0
        categories.forEachIndexed { index, pill ->
            pill.setOnClickListener {
                if (selectedCategory != index) {
                    selectedCategory = index
                    categories.forEachIndexed { i, p -> renderCategory(p, i == selectedCategory) }
                }
            }
        }
        categories.forEachIndexed { i, p -> renderCategory(p, i == selectedCategory) }

        // --- Header / CTA actions --------------------------------------
        view.findViewById<View>(R.id.search_container).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        view.findViewById<View>(R.id.btn_view_all).setOnClickListener {
            findNavController().navigate(R.id.nav_appointments)
        }
        view.findViewById<View>(R.id.btn_see_all).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        view.findViewById<View>(R.id.card_verified_lawyers).setOnClickListener {
            findNavController().navigate(R.id.nav_map)
        }
        view.findViewById<View>(R.id.btn_find_matching).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        view.findViewById<View>(R.id.btn_send_desc).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        view.findViewById<View>(R.id.btn_menu_open).setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener { v ->
            Snackbar.make(v, R.string.no_new_notifications, Snackbar.LENGTH_SHORT).show()
        }

        // --- System bar insets ------------------------------------------
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val topBar = view.findViewById<View>(R.id.top_bar)
            topBar.updatePadding(top = systemBars.top + 6)
            topBar.layoutParams = topBar.layoutParams.apply {
                height = (56 * resources.displayMetrics.density).toInt() + systemBars.top + 6
            }
            val bottomPad = (96 * resources.displayMetrics.density).toInt()
            view.findViewById<View>(R.id.home_scroll).updatePadding(bottom = bottomPad + systemBars.bottom)
            insets
        }
    }

    private fun renderChip(chip: View, selected: Boolean) {
        val tv = chip as android.widget.TextView
        tv.setBackgroundResource(
            if (selected) R.drawable.bg_home_chip_selected else R.drawable.bg_home_chip_unselected
        )
        tv.setTextColor(
            if (selected) resources.getColor(R.color.surface_white, null)
            else resources.getColor(R.color.navy_primary, null)
        )
    }

    private fun renderCategory(pill: View, selected: Boolean) {
        val tv = pill as android.widget.TextView
        tv.setBackgroundResource(
            if (selected) R.drawable.bg_category_pill_selected else R.drawable.bg_category_pill_unselected
        )
        tv.setTextColor(
            if (selected) resources.getColor(R.color.pill_selected_text, null)
            else resources.getColor(R.color.pill_unselected_text, null)
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
