package com.example.askvocate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.CategoryAdapter
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvCategories = view.findViewById<RecyclerView>(R.id.rv_categories)
        val categoryAdapter = CategoryAdapter { category ->
            // In a real app, maybe pass to FindLawyersFragment
        }
        rvCategories.adapter = categoryAdapter

        val rvTopLawyers = view.findViewById<RecyclerView>(R.id.rv_top_lawyers)
        val lawyerAdapter = LawyerAdapter(isHorizontal = true) { lawyer ->
            val bundle = Bundle().apply { putString("lawyerId", lawyer.id) }
            findNavController().navigate(R.id.action_home_to_lawyer_profile, bundle)
        }
        rvTopLawyers.adapter = lawyerAdapter

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.submitList(categories)
            AnimationUtils.runLayoutAnimation(rvCategories)
        }

        viewModel.topLawyers.observe(viewLifecycleOwner) { lawyers ->
            lawyerAdapter.submitList(lawyers)
            AnimationUtils.runLayoutAnimation(rvTopLawyers)
        }

        view.findViewById<View>(R.id.btn_continue).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        
        view.findViewById<View>(R.id.btn_see_all_lawyers).setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
    }
}
