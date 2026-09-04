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
import com.example.askvocate.util.SessionManager
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

/**
 * Signup screen exclusively for clients.
 * No role arg needed — always calls POST /api/users/register/client.
 */
class SignUpFragment : Fragment() {

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private val BASE_URL = com.example.askvocate.network.ApiConfig.BASE_URL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName            = view.findViewById<EditText>(R.id.et_name)
        val etEmail           = view.findViewById<EditText>(R.id.et_email_signup)
        val etPassword        = view.findViewById<EditText>(R.id.et_password_signup)
        val etConfirmPassword = view.findViewById<EditText>(R.id.et_confirm_password)

        val ivTogglePassword  = view.findViewById<ImageView>(R.id.iv_toggle_password)
        val ivToggleConfirm   = view.findViewById<ImageView>(R.id.iv_toggle_confirm_password)

        val btnSignUp         = view.findViewById<MaterialButton>(R.id.btn_sign_up)

        // ── Password visibility toggles ───────────────────────────────────────
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.transformationMethod = if (isPasswordVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            ivTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_off_filled else R.drawable.ic_eye_filled
            )
            etPassword.setSelection(etPassword.text.length)
        }

        ivToggleConfirm.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            etConfirmPassword.transformationMethod = if (isConfirmPasswordVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            ivToggleConfirm.setImageResource(
                if (isConfirmPasswordVisible) R.drawable.ic_eye_off_filled else R.drawable.ic_eye_filled
            )
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        // ── Sign Up button ────────────────────────────────────────────────────
        btnSignUp.setOnClickListener {
            val name    = etName.text.toString().trim()
            val email   = etEmail.text.toString().trim()
            val pass    = etPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                requireContext().showCustomToast("Please fill in all fields", ToastType.ERROR)
                return@setOnClickListener
            }
            if (!com.example.askvocate.util.EmailValidator.isValidEmail(email)) {
                requireContext().showCustomToast("Please enter a valid email address", ToastType.ERROR)
                return@setOnClickListener
            }
            if (pass != confirm) {
                requireContext().showCustomToast("Passwords do not match", ToastType.ERROR)
                return@setOnClickListener
            }

            btnSignUp.showLoading(requireContext())
            register(name, email, pass, confirm) { success ->
                btnSignUp.hideLoading()
                if (success) {
                    SessionManager.setLoggedIn(requireContext(), true)
                    NavHostFragment.findNavController(this)
                        .navigate(R.id.action_sign_up_to_home)
                }
            }
        }

        // ── Already have an account? → Sign In ───────────────────────────────
        view.findViewById<TextView>(R.id.tv_sign_in).setOnClickListener {
            NavHostFragment.findNavController(this).navigate(R.id.action_sign_up_to_sign_in)
        }
    }

    // ── API call — always client ──────────────────────────────────────────────

    private fun register(
        name: String,
        emailOrPhone: String,
        password: String,
        confirmPassword: String,
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$BASE_URL/users/register/client")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000

                val body = JSONObject().apply {
                    put("name",            name)
                    put("emailOrPhone",    emailOrPhone)
                    put("password",        password)
                    put("confirmPassword", confirmPassword)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader(Charsets.UTF_8).readText()

                Log.d("SignUp", "[$responseCode] /register/client → $responseText")

                withContext(Dispatchers.Main) {
                    val json = runCatching { JSONObject(responseText) }.getOrNull()
                    val isSuccess = responseCode in 200..299 && json?.optBoolean("success", false) == true

                    if (isSuccess) {
                        requireContext().showCustomToast("Account created! Welcome.", ToastType.SUCCESS)
                        onResult(true)
                    } else {
                        val errorMsg = json?.optString("error", "Registration failed") ?: "Registration failed"
                        requireContext().showCustomToast(errorMsg, ToastType.ERROR)
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("SignUp", "Network error", e)
                withContext(Dispatchers.Main) {
                    requireContext().showCustomToast("Network error: ${e.message}", ToastType.ERROR)
                    onResult(false)
                }
            }
        }
    }
}
