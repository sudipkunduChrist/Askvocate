package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.OngoingCase

/**
 * Simple synchronous adapter (no async diffing) so the card list is fully
 * rendered on the first frame — this avoids the content appearing a moment
 * after the screen/nav bar on cold starts.
 */
class OngoingCaseAdapter(
    private val vertical: Boolean = false
) : RecyclerView.Adapter<OngoingCaseAdapter.CaseViewHolder>() {

    private var items: List<OngoingCase> = emptyList()

    /** Pastel avatar backgrounds cycled per card. */
    private val avatarColors = intArrayOf(
        R.color.avatar_pastel_blue,
        R.color.avatar_pastel_peach,
        R.color.avatar_pastel_mint,
        R.color.avatar_pastel_lilac
    )

    fun submitList(list: List<OngoingCase>?) {
        items = list.orEmpty()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaseViewHolder {
        val layoutId = if (vertical) R.layout.item_case_card_vertical else R.layout.item_ongoing_case
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return CaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CaseViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class CaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInitials: TextView = itemView.findViewById(R.id.tv_initials)
        private val tvLawyerName: TextView = itemView.findViewById(R.id.tv_lawyer_name)
        private val tvPractice: TextView = itemView.findViewById(R.id.tv_practice)
        private val tvCaseTitle: TextView = itemView.findViewById(R.id.tv_case_title)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

        fun bind(caseItem: OngoingCase, position: Int) {
            tvInitials.text = caseItem.initials
            tvLawyerName.text = caseItem.lawyerName
            tvPractice.text = caseItem.practice
            tvCaseTitle.text = caseItem.title
            tvStatus.text = caseItem.status

            val colorRes = avatarColors[position % avatarColors.size]
            tvInitials.background.mutate().setTint(
                ContextCompat.getColor(itemView.context, colorRes)
            )
        }
    }
}
