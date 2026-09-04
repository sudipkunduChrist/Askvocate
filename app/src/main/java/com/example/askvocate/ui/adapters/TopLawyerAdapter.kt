package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.TopLawyer
import java.util.Locale

/**
 * Simple synchronous adapter (no async diffing) so the card list is fully
 * rendered on the first frame.
 */
class TopLawyerAdapter : RecyclerView.Adapter<TopLawyerAdapter.LawyerViewHolder>() {

    private var items: List<TopLawyer> = emptyList()

    /** Solid avatar backgrounds cycled per card. */
    private val avatarColors = intArrayOf(
        R.color.navy_primary,
        R.color.home_blue,
        R.color.navy_light,
        R.color.navy_dark
    )

    fun submitList(list: List<TopLawyer>?) {
        items = list.orEmpty()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LawyerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_rated_lawyer, parent, false)
        return LawyerViewHolder(view)
    }

    override fun onBindViewHolder(holder: LawyerViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class LawyerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInitials: TextView = itemView.findViewById(R.id.tv_initials)
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvSpecialty: TextView = itemView.findViewById(R.id.tv_specialty)
        private val tvRating: TextView = itemView.findViewById(R.id.tv_rating)

        fun bind(lawyer: TopLawyer, position: Int) {
            tvInitials.text = lawyer.initials
            tvName.text = lawyer.name
            tvSpecialty.text = lawyer.specialty
            tvRating.text = String.format(Locale.US, "%.1f", lawyer.rating)

            val colorRes = avatarColors[position % avatarColors.size]
            tvInitials.background.mutate().setTint(
                ContextCompat.getColor(itemView.context, colorRes)
            )
        }
    }
}
