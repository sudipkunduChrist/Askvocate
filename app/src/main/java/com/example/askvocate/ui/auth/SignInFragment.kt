package com.example.askvocate.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton

class SignInFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_sign_in).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_home)
        }

        view.findViewById<TextView>(R.id.tv_sign_up).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_sign_up)
        }
    }
}