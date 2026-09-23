package com.example.askvocate.ui.findlawyers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.MainActivity
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.ToastType
import com.example.askvocate.util.showCustomToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FindLawyersFragment : Fragment() {

    private val viewModel: FindLawyersViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_find_lawyers, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!SessionManager.getUserRole(requireContext()).equals("CLIENT", ignoreCase = true)) {
            findNavController().navigate(
                R.id.nav_home,
                null,
                NavOptions.Builder().setPopUpTo(R.id.nav_find_lawyers, true).build()
            )
            return
        }

        val appBar = view.findViewById<View>(R.id.find_lawyers_app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { bar, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            bar.updatePadding(top = statusBarTop)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)

        view.findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            (requireActivity() as? MainActivity)?.openDrawer()
        }
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Notifications coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }

        val caseDescription = view.findViewById<EditText>(R.id.et_case_description)
        val submitIcon = view.findViewById<FloatingActionButton>(R.id.btn_submit_case)
        val submitButton = view.findViewById<MaterialButton>(R.id.btn_find_matching_lawyers)
        val loading = view.findViewById<LinearLayout>(R.id.loading_container)
        val results = view.findViewById<LinearLayout>(R.id.results_container)
        val error = view.findViewById<TextView>(R.id.tv_error)
        val domain = view.findViewById<TextView>(R.id.tv_detected_domain)
        val resultCount = view.findViewById<TextView>(R.id.tv_results_count)
        val lawyerList = view.findViewById<RecyclerView>(R.id.rv_lawyers)

        val lawyerAdapter = LawyerAdapter(isHorizontal = false) { lawyer ->
            val bundle = Bundle().apply { putString("lawyerId", lawyer.id) }
            findNavController().navigate(R.id.action_find_lawyers_to_lawyer_profile, bundle)
        }
        lawyerList.adapter = lawyerAdapter

        arguments?.getString("searchQuery")?.takeIf { it.isNotBlank() }?.let(caseDescription::setText)

        fun submit() {
            requireContext().getSystemService<InputMethodManager>()
                ?.hideSoftInputFromWindow(caseDescription.windowToken, 0)
            viewModel.findMatchingLawyers(caseDescription.text.toString())
        }

        submitIcon.setOnClickListener { submit() }
        submitButton.setOnClickListener { submit() }
        caseDescription.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            loading.isVisible = state is FindLawyersViewModel.UiState.Loading
            results.isVisible = state is FindLawyersViewModel.UiState.Success
            error.isVisible = state is FindLawyersViewModel.UiState.Error
            val isLoading = state is FindLawyersViewModel.UiState.Loading
            submitIcon.isEnabled = !isLoading
            submitButton.isEnabled = !isLoading

            when (state) {
                is FindLawyersViewModel.UiState.Success -> {
                    domain.text = state.detectedDomain
                    resultCount.text = resources.getQuantityString(
                        R.plurals.matching_lawyers_count,
                        state.lawyers.size,
                        state.lawyers.size
                    )
                    lawyerAdapter.submitList(state.lawyers)
                    AnimationUtils.runLayoutAnimation(lawyerList)

                    // Auto-save this search session to Saved Advocates
                    val savedAdvocates = state.lawyers.map { lawyer ->
                        com.example.askvocate.data.model.SavedAdvocate(
                            lawyerId = lawyer.id,
                            name = lawyer.name,
                            specialty = lawyer.specialty,
                            location = lawyer.location,
                            rating = lawyer.rating,
                            yearsExperience = lawyer.yearsExperience,
                            consultationFee = lawyer.consultationFee,
                            isVerified = lawyer.isVerified
                        )
                    }
                    val repo = com.example.askvocate.data.repository.SavedAdvocatesRepository(requireContext())
                    repo.saveSearch(
                        domain = state.detectedDomain,
                        query = caseDescription.text.toString(),
                        advocates = savedAdvocates
                    )
                }
                is FindLawyersViewModel.UiState.Error -> {
                    error.text = state.message
                    requireContext().showCustomToast(state.message, ToastType.ERROR)
                }
                else -> Unit
            }
        }
    }
}
