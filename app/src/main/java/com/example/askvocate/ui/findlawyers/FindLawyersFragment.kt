package com.example.askvocate.ui.findlawyers

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.CategoryAdapter
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils

class FindLawyersFragment : Fragment() {

    private val viewModel: FindLawyersViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_find_lawyers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<EditText>(R.id.et_search)
        val rvCategories = view.findViewById<RecyclerView>(R.id.rv_categories)
        val rvLawyers = view.findViewById<RecyclerView>(R.id.rv_lawyers)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        val tvResultsCount = view.findViewById<TextView>(R.id.tv_results_count)

        val categoryAdapter = CategoryAdapter { category ->
            viewModel.selectCategory(category.id)
        }
        rvCategories.adapter = categoryAdapter

        val lawyerAdapter = LawyerAdapter(isHorizontal = false) { lawyer ->
            val bundle = Bundle().apply { putString("lawyerId", lawyer.id) }
            findNavController().navigate(R.id.action_find_lawyers_to_lawyer_profile, bundle)
        }
        rvLawyers.adapter = lawyerAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        viewModel.categories.observe(viewLifecycleOwner) {
            categoryAdapter.submitList(it)
        }

        viewModel.lawyers.observe(viewLifecycleOwner) {
            lawyerAdapter.submitList(it)
            tvResultsCount.text = getString(R.string.results_count, it.size)
            AnimationUtils.runLayoutAnimation(rvLawyers)
            swipeRefresh.isRefreshing = false
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.search(etSearch.text.toString())
        }
    }
}
