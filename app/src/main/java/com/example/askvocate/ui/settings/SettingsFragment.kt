package com.example.askvocate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.util.SessionManager

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            SessionManager.setLoggedIn(requireContext(), false)

            val options = NavOptions.Builder()
                .setPopUpTo(R.id.nav_home, inclusive = true)
                .setEnterAnim(R.anim.fade_in)
                .setExitAnim(R.anim.fade_out)
                .build()
            findNavController().navigate(R.id.nav_role_selection, null, options)
        }
    }
}
