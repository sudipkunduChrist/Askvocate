package com.example.askvocate.ui.auth

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton

class SignInFragment : Fragment() {

    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val ivTogglePassword = view.findViewById<ImageView>(R.id.iv_toggle_password)

        // Password visibility toggle
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye_off_filled)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ic_eye_filled)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        // Sign In button
        view.findViewById<MaterialButton>(R.id.btn_sign_in).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_home)
        }

        // Don't have an account? Sign Up
        view.findViewById<TextView>(R.id.tv_sign_up).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_sign_up)
        }

        // Forgot Password
        view.findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            // TODO: navigate to forgot password screen
        }
    }
}
