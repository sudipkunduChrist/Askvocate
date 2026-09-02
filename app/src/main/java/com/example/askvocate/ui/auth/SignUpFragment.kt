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

class SignUpFragment : Fragment() {

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPassword = view.findViewById<EditText>(R.id.et_password_signup)
        val etConfirmPassword = view.findViewById<EditText>(R.id.et_confirm_password)
        val ivTogglePassword = view.findViewById<ImageView>(R.id.iv_toggle_password)
        val ivToggleConfirm = view.findViewById<ImageView>(R.id.iv_toggle_confirm_password)

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

        // Confirm Password visibility toggle
        ivToggleConfirm.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleConfirm.setImageResource(R.drawable.ic_eye_off_filled)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleConfirm.setImageResource(R.drawable.ic_eye_filled)
            }
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        // Sign Up button
        view.findViewById<MaterialButton>(R.id.btn_sign_up).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_up_to_home)
        }

        // Already have an account? Sign In
        view.findViewById<TextView>(R.id.tv_sign_in).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_up_to_sign_in)
        }
    }
}
