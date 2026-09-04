package com.example.askvocate.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.dummyOngoingCases
import com.example.askvocate.ui.adapters.OngoingCaseAdapter

class AppointmentsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appointments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rvAppointments = view.findViewById<RecyclerView>(R.id.rv_appointments)
        // Sample/dummy cases (mockup data) until real case APIs are wired up.
        rvAppointments.adapter = OngoingCaseAdapter(vertical = true).apply {
            submitList(dummyOngoingCases)
        }
    }
}
