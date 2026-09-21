package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.askvocate.R
import com.example.askvocate.data.model.Appointment
import com.example.askvocate.databinding.ItemAppointmentCardBinding

class AppointmentAdapter(private val onClick: (Appointment) -> Unit) :
    ListAdapter<Appointment, AppointmentAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemAppointmentCardBinding, private val onClick: (Appointment) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(appointment: Appointment) {
            binding.tvLawyerName.text = appointment.lawyerName
            binding.tvAppointmentType.text = appointment.type
            binding.tvDate.text = appointment.date
            binding.tvTime.text = appointment.time
            binding.ivLawyer.load(appointment.lawyerImageUrl) {
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
            }
            binding.root.setOnClickListener { onClick(appointment) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppointmentCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Appointment>() {
        override fun areItemsTheSame(oldItem: Appointment, newItem: Appointment): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Appointment, newItem: Appointment): Boolean =
            oldItem == newItem
    }
}
