package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.Lawyer

class LawyerAdapter(
    private val isHorizontal: Boolean = false,
    private val onItemClick: (Lawyer) -> Unit
) : ListAdapter<Lawyer, LawyerAdapter.LawyerViewHolder>(LawyerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LawyerViewHolder {
        val layoutId = if (isHorizontal) R.layout.item_lawyer_horizontal else R.layout.item_lawyer_card
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return LawyerViewHolder(view)
    }

    override fun onBindViewHolder(holder: LawyerViewHolder, position: Int) {
        val lawyer = getItem(position)
        holder.bind(lawyer)
    }

    inner class LawyerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvSpecialty: TextView = itemView.findViewById(R.id.tv_specialty)
        private val tvRating: TextView = itemView.findViewById(R.id.tv_rating)
        private val tvLocation: TextView? = itemView.findViewById(R.id.tv_location)
        private val ivVerified: ImageView? = itemView.findViewById(R.id.iv_verified)

        fun bind(lawyer: Lawyer) {
            tvName.text = lawyer.name
            tvSpecialty.text = lawyer.specialty
            
            if (isHorizontal) {
                tvRating.text = lawyer.rating.toString()
            } else {
                tvRating.text = "${lawyer.rating} (${lawyer.reviewCount})"
                tvLocation?.text = lawyer.location
                ivVerified?.visibility = if (lawyer.isVerified) View.VISIBLE else View.GONE
            }

            itemView.setOnClickListener {
                onItemClick(lawyer)
            }
        }
    }

    class LawyerDiffCallback : DiffUtil.ItemCallback<Lawyer>() {
        override fun areItemsTheSame(oldItem: Lawyer, newItem: Lawyer): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Lawyer, newItem: Lawyer): Boolean {
            return oldItem == newItem
        }
    }
}
