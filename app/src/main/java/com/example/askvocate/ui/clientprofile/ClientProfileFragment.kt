package com.example.askvocate.ui.clientprofile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ClientProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_client_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindSessionData(view)
        fetchLatestProfile(view)

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

    private fun bindSessionData(view: View) {
        val nameTv = view.findViewById<TextView>(R.id.tv_client_name)
        val emailTv = view.findViewById<TextView>(R.id.tv_client_email)
        val phoneTv = view.findViewById<TextView>(R.id.tv_client_phone)
        val addressTv = view.findViewById<TextView>(R.id.tv_client_address)

        val userName = SessionManager.getUserName(requireContext())
        val userEmail = SessionManager.getUserEmail(requireContext())

        nameTv.text = userName
        emailTv.text = userEmail
        phoneTv.text = userEmail
        addressTv.text = "Not added yet"
    }

    private fun fetchLatestProfile(view: View) {
        val userId = SessionManager.getUserId(requireContext())
        val role = SessionManager.getUserRole(requireContext())
        if (userId.isBlank()) return

        val endpoint = when (role.uppercase()) {
            "CLIENT" -> "client"
            "LAWYER_FRESHER" -> "lawyer/fresher"
            "LAWYER_EXPERIENCED" -> "lawyer/experienced"
            else -> return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("${ApiConfig.BASE_URL}/api/users/$endpoint/$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                Log.d("ClientProfile", "[$responseCode] $endpoint/$userId -> $responseText")

                val json = runCatching { JSONObject(responseText) }.getOrNull() ?: return@launch
                val userObj = json.optJSONObject("user") ?: return@launch

                val name = userObj.optString("name", SessionManager.getUserName(requireContext()))
                val email = userObj.optString("emailOrPhone", SessionManager.getUserEmail(requireContext()))
                val userRole = userObj.optString("role", role)

                withContext(Dispatchers.Main) {
                    view.findViewById<TextView>(R.id.tv_client_name).text = name
                    view.findViewById<TextView>(R.id.tv_client_email).text = email
                    view.findViewById<TextView>(R.id.tv_client_phone).text = email
                    SessionManager.saveUser(requireContext(), userId, name, email, userRole)
                }
            } catch (e: Exception) {
                Log.e("ClientProfile", "Failed to fetch profile", e)
            }
        }
    }
}
