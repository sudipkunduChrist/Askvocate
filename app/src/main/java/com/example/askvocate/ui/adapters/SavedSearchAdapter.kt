package com.example.askvocate.ui.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.model.SavedAdvocate
import com.example.askvocate.data.model.SavedSearch

class SavedSearchAdapter(
    private val onAdvocateClick: (SavedAdvocate) -> Unit,
    private val onDeleteClick: (SavedSearch) -> Unit
) : ListAdapter<SavedSearch, SavedSearchAdapter.SavedSearchViewHolder>(SavedSearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedSearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_search_group, parent, false)
        return SavedSearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SavedSearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDomainBadge: TextView = itemView.findViewById(R.id.tv_domain_badge)
        private val tvSavedTime: TextView = itemView.findViewById(R.id.tv_saved_time)
        private val tvSearchQuery: TextView = itemView.findViewById(R.id.tv_search_query)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_search)
        private val rvAdvocates: RecyclerView = itemView.findViewById(R.id.rv_group_advocates)

        fun bind(savedSearch: SavedSearch) {
            tvDomainBadge.text = savedSearch.detectedDomain

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                savedSearch.savedTimestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            tvSavedTime.text = timeAgo

            if (savedSearch.searchQuery.isNotBlank()) {
                tvSearchQuery.visibility = View.VISIBLE
                tvSearchQuery.text = "\"${savedSearch.searchQuery}\""
            } else {
                tvSearchQuery.visibility = View.GONE
            }

            btnDelete.setOnClickListener {
                onDeleteClick(savedSearch)
            }

            // Map SavedAdvocate to Lawyer model to reuse LawyerAdapter
            val lawyers = savedSearch.advocates.map { adv ->
                Lawyer(
                    id = adv.lawyerId,
                    name = adv.name,
                    specialty = adv.specialty,
                    rating = adv.rating,
                    reviewCount = 0,
                    location = adv.location,
                    bio = adv.specialty,
                    yearsExperience = adv.yearsExperience,
                    isVerified = adv.isVerified,
                    consultationFee = adv.consultationFee
                )
            }

            val lawyerAdapter = LawyerAdapter(isHorizontal = false) { lawyer ->
                val adv = savedSearch.advocates.find { it.lawyerId == lawyer.id }
                if (adv != null) {
                    onAdvocateClick(adv)
                }
            }
            rvAdvocates.adapter = lawyerAdapter
            lawyerAdapter.submitList(lawyers)
        }
    }

    class SavedSearchDiffCallback : DiffUtil.ItemCallback<SavedSearch>() {
        override fun areItemsTheSame(oldItem: SavedSearch, newItem: SavedSearch): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SavedSearch, newItem: SavedSearch): Boolean =
            oldItem == newItem
    }
}
