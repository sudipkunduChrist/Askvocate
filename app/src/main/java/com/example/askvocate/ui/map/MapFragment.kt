package com.example.askvocate.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.ui.home.HomeViewModel

class MapFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvNearbyLawyers = view.findViewById<RecyclerView>(R.id.rv_nearby_lawyers)
        val lawyerAdapter = LawyerAdapter(isHorizontal = false) { lawyer ->
            val bundle = Bundle().apply { putString("lawyerId", lawyer.id) }
            findNavController().navigate(R.id.action_map_to_lawyer_profile, bundle)
        }
        rvNearbyLawyers.adapter = lawyerAdapter

        viewModel.topLawyers.observe(viewLifecycleOwner) {
            lawyerAdapter.submitList(it)
        }
    }
}
