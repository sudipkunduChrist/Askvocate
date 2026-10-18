package com.example.askvocate.ui.savedadvocates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.askvocate.util.AppToast as Toast
import com.example.askvocate.util.ToastType
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.repository.SearchHistoryRepository
import com.example.askvocate.ui.adapters.SavedSearchAdapter
import com.example.askvocate.util.AnimationUtils
import com.example.askvocate.util.applyStatusBarInset
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SavedAdvocatesFragment : Fragment() {

    private lateinit var repository: SearchHistoryRepository
    private lateinit var adapter: SavedSearchAdapter
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var status: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_saved_advocates, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = SearchHistoryRepository(requireContext())
        view.findViewById<View>(R.id.saved_searches_app_bar).applyStatusBarInset()

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val btnFindLawyers = view.findViewById<MaterialButton>(R.id.btn_find_lawyers)
        recyclerView = view.findViewById(R.id.rv_saved_searches)
        emptyStateContainer = view.findViewById(R.id.empty_state_container)
        status = view.findViewById(R.id.tv_saved_status)

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
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.deleteSaved(savedSearch.id) } }
                        .onSuccess { loadSavedSearches() }
                        .onFailure { Toast.makeText(requireContext(), it.message ?: getString(R.string.history_sync_failed), Toast.LENGTH_LONG, ToastType.ERROR).show() }
                }
            }
        )
        recyclerView.adapter = adapter

    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) loadSavedSearches()
    }

    private fun loadSavedSearches() {
        status.isVisible = true
        status.setText(R.string.saved_searches_loading)
        recyclerView.isVisible = false
        emptyStateContainer.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.listSaved() } }
                .onSuccess { searches ->
                    status.isVisible = false
                    recyclerView.isVisible = searches.isNotEmpty()
                    emptyStateContainer.isVisible = searches.isEmpty()
                    adapter.submitList(searches)
                    if (searches.isNotEmpty()) AnimationUtils.runLayoutAnimation(recyclerView)
                }
                .onFailure { status.text = it.message ?: getString(R.string.saved_searches_load_failed) }
        }
    }
}
