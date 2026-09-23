package com.example.askvocate.ui.savedadvocates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.repository.SavedAdvocatesRepository
import com.example.askvocate.ui.adapters.SavedSearchAdapter
import com.example.askvocate.util.AnimationUtils
import com.google.android.material.button.MaterialButton

class SavedAdvocatesFragment : Fragment() {

    private lateinit var repository: SavedAdvocatesRepository
    private lateinit var adapter: SavedSearchAdapter
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_saved_advocates, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = SavedAdvocatesRepository(requireContext())

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val btnFindLawyers = view.findViewById<MaterialButton>(R.id.btn_find_lawyers)
        recyclerView = view.findViewById(R.id.rv_saved_searches)
        emptyStateContainer = view.findViewById(R.id.empty_state_container)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnFindLawyers.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }

        adapter = SavedSearchAdapter(
            onAdvocateClick = { advocate ->
                val bundle = Bundle().apply { putString("lawyerId", advocate.lawyerId) }
                findNavController().navigate(R.id.action_saved_advocates_to_lawyer_profile, bundle)
            },
            onDeleteClick = { savedSearch ->
                repository.deleteSearch(savedSearch.id)
                loadSavedSearches()
            }
        )
        recyclerView.adapter = adapter

        loadSavedSearches()
    }

    private fun loadSavedSearches() {
        val searches = repository.getSavedSearches()
        if (searches.isEmpty()) {
            recyclerView.isVisible = false
            emptyStateContainer.isVisible = true
        } else {
            recyclerView.isVisible = true
            emptyStateContainer.isVisible = false
            adapter.submitList(searches)
            AnimationUtils.runLayoutAnimation(recyclerView)
        }
    }
}
