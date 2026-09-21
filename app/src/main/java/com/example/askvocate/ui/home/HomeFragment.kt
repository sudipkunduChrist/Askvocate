package com.example.askvocate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentHomeBinding
import com.example.askvocate.ui.adapters.AppointmentAdapter
import com.example.askvocate.ui.adapters.CategoryAdapter
import com.example.askvocate.ui.adapters.KnowledgeHubAdapter
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.example.askvocate.util.AnimationUtils

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

        observeViewModel()
    }

    private fun setupToolbar() {
        binding.tvGreeting.isVisible = true
        binding.tvGreeting.text = getString(R.string.hello_user_format, "Sudip")
        
        binding.btnNotifications.setOnClickListener {
            // Navigate to notifications if available
            Toast.makeText(requireContext(), "Notifications coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHeroSection() {
        binding.btnFindLawyerHero.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        binding.btnAskAiHero.setOnClickListener {
            // Navigate to AI chat
            findNavController().navigate(R.id.nav_chat)
        }
    }

    private fun setupSearch() {
        binding.btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Filter coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickActions() {
        binding.actionFindLawyer.setOnClickListener {
            findNavController().navigate(R.id.nav_find_lawyers)
        }
        binding.actionAskAi.setOnClickListener {
            findNavController().navigate(R.id.nav_chat)
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
