package com.example.askvocate.ui.clientprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.util.SessionManager

class ClientProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_client_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            // Logging out is the only way to leave the signed-in state.
            SessionManager.setLoggedIn(requireContext(), false)

            // Home is always at the bottom of the signed-in stack (splash was
            // already popped on login), so popping up to it inclusively removes
            // EVERY signed-in page — nothing can be reached by pressing back.
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.nav_home, inclusive = true)
                .setEnterAnim(R.anim.fade_in)
                .setExitAnim(R.anim.fade_out)
                .build()
            findNavController().navigate(R.id.nav_role_selection, null, options)
        }
    }
}
