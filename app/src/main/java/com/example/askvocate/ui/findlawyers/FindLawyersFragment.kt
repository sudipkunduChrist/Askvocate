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
import androidx.core.widget.NestedScrollView
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.ToastType
import com.example.askvocate.util.applyStatusBarInset
import com.example.askvocate.util.showCustomToast
import com.example.askvocate.util.AppToast as Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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

        view.findViewById<View>(R.id.find_lawyers_app_bar).applyStatusBarInset()

        view.findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            if (!findNavController().navigateUp()) findNavController().navigate(R.id.nav_home)
        }
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener {
            findNavController().navigate(R.id.nav_notifications)
        }

        val caseDescription = view.findViewById<EditText>(R.id.et_case_description)
        val submitIcon = view.findViewById<FloatingActionButton>(R.id.btn_submit_case)
        val submitButton = view.findViewById<MaterialButton>(R.id.btn_find_matching_lawyers)
        val loading = view.findViewById<LinearLayout>(R.id.loading_container)
        val clarification = view.findViewById<View>(R.id.clarification_container)
        val clarificationQuestion = view.findViewById<TextView>(R.id.tv_clarification_question)
        val clarificationAnswerLayout = view.findViewById<TextInputLayout>(R.id.til_clarification_answer)
        val clarificationAnswer = view.findViewById<TextInputEditText>(R.id.et_clarification_answer)
        val clarificationSubmit = view.findViewById<MaterialButton>(R.id.btn_submit_clarification)
        val scroll = view.findViewById<NestedScrollView>(R.id.find_lawyers_scroll)
        val baseBottomPadding = scroll.paddingBottom
        fun showClarificationAboveKeyboard() {
            scroll.post {
                if (clarification.isVisible) {
                    val visibleHeight = scroll.height - scroll.paddingBottom
                    val margin = (16 * resources.displayMetrics.density).toInt()
                    scroll.smoothScrollTo(0, (clarification.bottom - visibleHeight + margin).coerceAtLeast(0))
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { target, insets ->
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            target.updatePadding(bottom = baseBottomPadding + maxOf(keyboard, navigation))
            if (keyboard > 0) showClarificationAboveKeyboard()
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
        clarificationAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showClarificationAboveKeyboard()
        }
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
        clarificationSubmit.setOnClickListener {
            val answer = clarificationAnswer.text?.toString().orEmpty()
            if (answer.trim().length < 2) {
                clarificationAnswerLayout.error = "Please answer this question to continue"
                return@setOnClickListener
            }
            clarificationAnswerLayout.error = null
            requireContext().getSystemService<InputMethodManager>()
                ?.hideSoftInputFromWindow(clarificationAnswer.windowToken, 0)
            viewModel.submitClarification(answer)
        }
        clarificationAnswer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                clarificationSubmit.performClick()
                true
            } else false
        }
        caseDescription.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            loading.isVisible = state is FindLawyersViewModel.UiState.Loading
            clarification.isVisible = state is FindLawyersViewModel.UiState.Clarification
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

                }
                is FindLawyersViewModel.UiState.Error -> {
                    error.text = state.message
                    requireContext().showCustomToast(state.message, ToastType.ERROR)
                }
                is FindLawyersViewModel.UiState.Clarification -> {
                    clarificationQuestion.text = state.question
                    clarificationAnswerLayout.error = null
                    clarificationAnswer.text?.clear()
                    clarificationAnswer.requestFocus()
                    showClarificationAboveKeyboard()
                }
                else -> Unit
            }
        }
    }
}
