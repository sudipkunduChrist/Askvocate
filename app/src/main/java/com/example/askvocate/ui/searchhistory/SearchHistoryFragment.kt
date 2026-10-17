package com.example.askvocate.ui.searchhistory

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.model.SearchHistoryEntry
import com.example.askvocate.data.repository.SearchHistoryRepository
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.applyStatusBarInset
import com.google.android.material.button.MaterialButton
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchHistoryFragment : Fragment(R.layout.fragment_search_history) {
    private lateinit var repository: SearchHistoryRepository
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var status: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SearchHistoryRepository(requireContext())
        view.findViewById<View>(R.id.search_history_app_bar).applyStatusBarInset()
        view.findViewById<ImageButton>(R.id.btn_history_back).setOnClickListener {
            if (!findNavController().navigateUp()) findNavController().navigate(R.id.nav_home)
        }
        view.findViewById<View>(R.id.btn_history_find_lawyers).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        list = view.findViewById(R.id.rv_search_history)
        empty = view.findViewById(R.id.history_empty_state)
        status = view.findViewById(R.id.tv_history_status)
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) refresh()
    }

    private fun refresh() {
        status.isVisible = true
        status.setText(R.string.history_loading)
        empty.isVisible = false
        list.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.list() to repository.savedHistoryIds() } }
                .onSuccess { (entries, savedIds) ->
                    status.isVisible = false
                    empty.isVisible = entries.isEmpty()
                    list.isVisible = entries.isNotEmpty()
                    list.adapter = HistoryAdapter(entries, savedIds,
                        onDelete = { id -> mutate { repository.delete(id) } },
                        onSave = { id -> mutate { repository.saveChoice(id) } },
                        onLawyerClick = { id ->
                            findNavController().navigate(R.id.nav_lawyer_profile, Bundle().apply { putString("lawyerId", id) })
                        })
                }
                .onFailure { status.text = it.message ?: getString(R.string.history_load_failed) }
        }
    }

    private fun mutate(action: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { refresh() }
                .onFailure { Toast.makeText(requireContext(), it.message ?: getString(R.string.history_sync_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private class HistoryAdapter(
        private val entries: List<SearchHistoryEntry>,
        private val savedIds: Set<String>,
        private val onDelete: (String) -> Unit,
        private val onSave: (String) -> Unit,
        private val onLawyerClick: (String) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_search_history, parent, false)
        )

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val domain: TextView = view.findViewById(R.id.tv_history_domain)
            private val query: TextView = view.findViewById(R.id.tv_history_query)
            private val time: TextView = view.findViewById(R.id.tv_history_time)
            private val clarification: TextView = view.findViewById(R.id.tv_history_clarification)
            private val matchCount: TextView = view.findViewById(R.id.tv_history_match_count)
            private val noMatches: TextView = view.findViewById(R.id.tv_history_no_matches)
            private val lawyers: RecyclerView = view.findViewById(R.id.rv_history_lawyers)
            private val delete: ImageButton = view.findViewById(R.id.btn_delete_history)
            private val save: MaterialButton = view.findViewById(R.id.btn_save_history)

            fun bind(entry: SearchHistoryEntry) {
                val context = itemView.context
                domain.text = entry.detectedDomain.ifBlank { context.getString(R.string.history_default_domain) }
                query.text = entry.query
                time.text = DateFormat.getMediumDateFormat(context).format(Date(entry.createdAt)) + " · " +
                    DateFormat.getTimeFormat(context).format(Date(entry.createdAt))
                clarification.isVisible = entry.needsClarification && entry.clarificationQuestion.isNotBlank()
                clarification.text = context.getString(R.string.history_follow_up, entry.clarificationQuestion)
                matchCount.text = context.resources.getQuantityString(
                    R.plurals.history_matched_advocates, entry.lawyers.size, entry.lawyers.size
                )
                noMatches.isVisible = entry.lawyers.isEmpty()
                lawyers.isVisible = entry.lawyers.isNotEmpty()
                if (entry.lawyers.isNotEmpty()) {
                    lawyers.adapter = LawyerAdapter(isHorizontal = false) { onLawyerClick(it.id) }.also { adapter ->
                        adapter.submitList(entry.lawyers.map { advocate -> Lawyer(
                            id = advocate.lawyerId, name = advocate.name,
                            specialty = advocate.specialty, rating = advocate.rating,
                            reviewCount = 0, location = advocate.location, bio = advocate.specialty,
                            yearsExperience = advocate.yearsExperience, isVerified = advocate.isVerified,
                            consultationFee = advocate.consultationFee
                        ) })
                    }
                } else {
                    lawyers.adapter = null
                }
                delete.setOnClickListener { onDelete(entry.id) }
                save.isEnabled = entry.id !in savedIds
                save.setText(if (entry.id in savedIds) R.string.history_saved else R.string.history_save)
                save.setOnClickListener { onSave(entry.id) }
            }
        }
    }
}
