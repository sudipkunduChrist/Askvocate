package com.example.askvocate.ui.home

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.askvocate.MainActivity
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentHomeBinding
import com.example.askvocate.ui.adapters.AppointmentAdapter
import com.example.askvocate.ui.adapters.CategoryAdapter
import com.example.askvocate.ui.adapters.KnowledgeHubAdapter
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils
import com.example.askvocate.util.SessionManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupSearch()
        setupQuickActions()
        setupCategories()
        setupTopLawyers()
        setupAppointments()
        setupKnowledgeHub()
        setupHeroSection()
        configureRoleSpecificUi()

        observeViewModel()
    }

    private fun configureRoleSpecificUi() {
        val isClient = SessionManager.getUserRole(requireContext())
            .equals("CLIENT", ignoreCase = true)

        binding.btnFindLawyerHero.isVisible = isClient
        binding.searchContainer.isVisible = isClient
        binding.actionFindLawyer.isVisible = isClient
        binding.legalCategoriesHeader.isVisible = isClient
        binding.rvCategories.isVisible = isClient
        binding.topLawyersHeader.isVisible = isClient
        binding.rvTopLawyers.isVisible = isClient
        binding.quickActionsGrid.columnCount = if (isClient) 3 else 2
        if (!isClient) {
            binding.tvHeroSubtitle.setText(R.string.lawyer_hero_subtitle)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            (requireActivity() as? MainActivity)?.openDrawer()
        }

        binding.btnNotifications.setOnClickListener {
            // Navigate to notifications if available
            Toast.makeText(requireContext(), "Notifications coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHeroSection() {
        val fullName = SessionManager.getUserName(requireContext())
        val firstName = fullName.trim().split(Regex("\\s+"))
            .firstOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "User"

        val greeting = getString(R.string.hello_user_format, firstName)
        binding.tvHeroTitle.text = HtmlCompat.fromHtml(greeting, HtmlCompat.FROM_HTML_MODE_LEGACY)

        binding.btnFindLawyerHero.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
    }

    private fun setupSearch() {
        binding.btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Filter coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotEmpty()) {
                    val bundle = Bundle().apply { putString("searchQuery", query) }
                    findNavController().navigate(R.id.nav_find_lawyers, bundle)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupQuickActions() {
        binding.actionFindLawyer.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        binding.actionAppointments.setOnClickListener {
            findNavController().navigate(R.id.nav_appointments)
        }
        binding.actionDocuments.setOnClickListener {
            Toast.makeText(requireContext(), "Legal Documents coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCategories() {
        val categoryAdapter = CategoryAdapter { category ->
            // Pass to FindLawyersFragment or filter
        }
        binding.rvCategories.adapter = categoryAdapter
        
        binding.btnViewAllCategories.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
    }

    private fun setupTopLawyers() {
        val lawyerAdapter = LawyerAdapter(isHorizontal = true) { lawyer ->
            val bundle = Bundle().apply { putString("lawyerId", lawyer.id) }
            findNavController().navigate(R.id.action_home_to_lawyer_profile, bundle)
        }
        binding.rvTopLawyers.adapter = lawyerAdapter

        binding.btnSeeAllLawyers.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
    }

    private fun setupAppointments() {
        val appointmentAdapter = AppointmentAdapter { appointment ->
            // View appointment details
        }
        binding.rvAppointments.adapter = appointmentAdapter
    }

    private fun setupKnowledgeHub() {
        val knowledgeAdapter = KnowledgeHubAdapter { item ->
            // Open knowledge detail
            Toast.makeText(requireContext(), "Article: ${item.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvKnowledgeHub.adapter = knowledgeAdapter
    }

    private fun observeViewModel() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            (binding.rvCategories.adapter as? CategoryAdapter)?.submitList(categories)
        }

        viewModel.topLawyers.observe(viewLifecycleOwner) { lawyers ->
            (binding.rvTopLawyers.adapter as? LawyerAdapter)?.submitList(lawyers)
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            binding.upcomingConsultationsContainer.isVisible = appointments.isNotEmpty()
            (binding.rvAppointments.adapter as? AppointmentAdapter)?.submitList(appointments)
        }

        viewModel.knowledgeItems.observe(viewLifecycleOwner) { items ->
            (binding.rvKnowledgeHub.adapter as? KnowledgeHubAdapter)?.submitList(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
