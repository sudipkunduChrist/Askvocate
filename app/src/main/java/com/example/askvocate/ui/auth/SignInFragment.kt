package com.example.askvocate.ui.auth

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.askvocate.R
import com.example.askvocate.util.ToastType
import com.example.askvocate.util.hideLoading
import com.example.askvocate.util.showCustomToast
import com.example.askvocate.util.showLoading
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SignInFragment : Fragment() {

    private var isPasswordVisible = false

    private val BASE_URL = com.example.askvocate.network.ApiConfig.BASE_URL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val ivTogglePassword = view.findViewById<ImageView>(R.id.iv_toggle_password)
        val btnSignIn = view.findViewById<MaterialButton>(R.id.btn_sign_in)

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
        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                requireContext().showCustomToast("Please fill in all fields", ToastType.ERROR)
                return@setOnClickListener
            }
            if (!com.example.askvocate.util.EmailValidator.isValidEmail(email)) {
                requireContext().showCustomToast("Please enter a valid email address", ToastType.ERROR)
                return@setOnClickListener
            }

            btnSignIn.showLoading(requireContext())
            login(email, password) { success ->
                btnSignIn.hideLoading()
                if (success) {
                    NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_home)
                }
            }
        }

        // Don't have an account? Sign Up
        view.findViewById<TextView>(R.id.tv_sign_up).setOnClickListener {
            val hasArg = arguments?.containsKey("isLawyer") == true
            if (hasArg) {
                val isLawyer = arguments?.getBoolean("isLawyer", false) ?: false
                val actionId = if (isLawyer) R.id.action_sign_in_to_lawyer_sign_up else R.id.action_sign_in_to_sign_up
                NavHostFragment.findNavController(this).navigate(actionId)
            } else {
                NavHostFragment.findNavController(this).navigate(R.id.action_sign_in_to_role_selection)
            }
        }

        // Forgot Password
        view.findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            // TODO: navigate to forgot password screen
        }
    }

    private fun login(emailOrPhone: String, password: String, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$BASE_URL/users/login")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val body = JSONObject().apply {
                    put("emailOrPhone", emailOrPhone)
                    put("password", password)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader(Charsets.UTF_8).readText()

                Log.d("SignIn", "[$responseCode] /login → $responseText")

                withContext(Dispatchers.Main) {
                    val json = runCatching { JSONObject(responseText) }.getOrNull()
                    val isSuccess = responseCode in 200..299 && json?.optBoolean("success", false) == true

                    if (isSuccess) {
                        requireContext().showCustomToast("Signed in successfully!", ToastType.SUCCESS)
                        onResult(true)
                    } else {
                        val errorMsg = json?.optString("error", "Invalid credentials") ?: "Invalid credentials"
                        requireContext().showCustomToast(errorMsg, ToastType.ERROR)
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("SignIn", "Network error", e)
                withContext(Dispatchers.Main) {
                    requireContext().showCustomToast("Network error: ${e.message}", ToastType.ERROR)
                    onResult(false)
                }
            }
        }
    }
}
