package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.askvocate.R
import com.example.askvocate.data.model.KnowledgeItem
import com.example.askvocate.databinding.ItemKnowledgeCardBinding

class KnowledgeHubAdapter(private val onClick: (KnowledgeItem) -> Unit) :
    ListAdapter<KnowledgeItem, KnowledgeHubAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemKnowledgeCardBinding, private val onClick: (KnowledgeItem) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KnowledgeItem) {
            binding.tvTitle.text = item.title
            binding.tvDesc.text = item.description
            binding.tvCategory.text = item.category.uppercase()
            binding.ivThumbnail.load(item.imageUrl) {
                placeholder(R.drawable.bg_journey_hero)
                error(R.drawable.bg_journey_hero)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemKnowledgeCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<KnowledgeItem>() {
        override fun areItemsTheSame(oldItem: KnowledgeItem, newItem: KnowledgeItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: KnowledgeItem, newItem: KnowledgeItem): Boolean =
            oldItem == newItem
    }
}
