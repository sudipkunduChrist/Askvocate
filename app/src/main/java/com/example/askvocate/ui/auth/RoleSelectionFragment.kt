package com.example.askvocate.ui.auth

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class RoleSelectionFragment : Fragment() {

    private var isClientSelected = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_role_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cardClient = view.findViewById<MaterialCardView>(R.id.card_client)
        val cardLawyer = view.findViewById<MaterialCardView>(R.id.card_lawyer)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btn_continue)

        cardClient.setOnClickListener {
            if (!isClientSelected) {
                isClientSelected = true
                updateUI(view)
            }
        }

        cardLawyer.setOnClickListener {
            if (isClientSelected) {
                isClientSelected = false
                updateUI(view)
            }
        }

        // Initialize UI
        updateUI(view)

        btnContinue.setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_role_selection_to_onboarding)
        }
    }

    private fun updateUI(view: View) {
        val cardClient = view.findViewById<MaterialCardView>(R.id.card_client)
        val cardLawyer = view.findViewById<MaterialCardView>(R.id.card_lawyer)
        
        val rbClient = view.findViewById<RadioButton>(R.id.rb_client)
        val rbLawyer = view.findViewById<RadioButton>(R.id.rb_lawyer)

        val tvClientTitle = view.findViewById<TextView>(R.id.tv_client_title)
        val tvClientDesc = view.findViewById<TextView>(R.id.tv_client_desc)
        val ivClientIcon = view.findViewById<ImageView>(R.id.iv_client_icon)

        val tvLawyerTitle = view.findViewById<TextView>(R.id.tv_lawyer_title)
        val tvLawyerDesc = view.findViewById<TextView>(R.id.tv_lawyer_desc)
        val ivLawyerIcon = view.findViewById<ImageView>(R.id.iv_lawyer_icon)

        // Colors
        val colorNavy = ContextCompat.getColor(requireContext(), R.color.navy_primary)
        val colorWhite = ContextCompat.getColor(requireContext(), R.color.white)
        val colorNavyDark = ContextCompat.getColor(requireContext(), R.color.navy_dark)
        val colorTextSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val colorBorder = ContextCompat.getColor(requireContext(), R.color.border_light)
        val colorAmber = ContextCompat.getColor(requireContext(), R.color.amber_accent)
        val colorInfoBlue = ContextCompat.getColor(requireContext(), R.color.info_blue)

        if (isClientSelected) {
            // Select Client
            cardClient.setCardBackgroundColor(ColorStateList.valueOf(colorNavy))
            cardClient.strokeColor = colorNavy
            tvClientTitle.setTextColor(colorWhite)
            tvClientDesc.setTextColor(colorWhite)
            ivClientIcon.imageTintList = ColorStateList.valueOf(colorNavy)
            rbClient.isChecked = true
            rbClient.buttonTintList = ColorStateList.valueOf(colorWhite)

            // Deselect Lawyer
            cardLawyer.setCardBackgroundColor(ColorStateList.valueOf(colorWhite))
            cardLawyer.strokeColor = colorBorder
            tvLawyerTitle.setTextColor(colorNavyDark)
            tvLawyerDesc.setTextColor(colorTextSecondary)
            ivLawyerIcon.imageTintList = ColorStateList.valueOf(colorAmber)
            rbLawyer.isChecked = false
            rbLawyer.buttonTintList = ColorStateList.valueOf(colorBorder)
        } else {
            // Deselect Client
            cardClient.setCardBackgroundColor(ColorStateList.valueOf(colorWhite))
            cardClient.strokeColor = colorBorder
            tvClientTitle.setTextColor(colorNavyDark)
            tvClientDesc.setTextColor(colorTextSecondary)
            ivClientIcon.imageTintList = ColorStateList.valueOf(colorInfoBlue)
            rbClient.isChecked = false
            rbClient.buttonTintList = ColorStateList.valueOf(colorBorder)

            // Select Lawyer
            cardLawyer.setCardBackgroundColor(ColorStateList.valueOf(colorNavy))
            cardLawyer.strokeColor = colorNavy
            tvLawyerTitle.setTextColor(colorWhite)
            tvLawyerDesc.setTextColor(colorWhite)
            ivLawyerIcon.imageTintList = ColorStateList.valueOf(colorNavy)
            rbLawyer.isChecked = true
            rbLawyer.buttonTintList = ColorStateList.valueOf(colorWhite)
        }
    }
}