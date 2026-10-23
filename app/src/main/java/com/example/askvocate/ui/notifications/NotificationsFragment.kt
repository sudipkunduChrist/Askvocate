package com.example.askvocate.ui.notifications

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentNotificationsBinding
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.ui.documents.DocumentsCache
import com.example.askvocate.ui.documents.MyDocument
import com.example.askvocate.ui.documents.MyDocumentsApi
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.NetworkMonitor
import com.example.askvocate.util.applyStatusBarInset
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One place to see what still needs doing: required documents, profile gaps,
 * review updates and next-step guidance.
 */
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private data class Notice(
        val title: String,
        val detail: String,
        val action: String? = null,
        val destination: Int? = null,
        val accent: Int = R.color.navy_primary
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.notificationsAppBar.applyStatusBarInset()
        binding.notificationsBack.setOnClickListener { findNavController().navigateUp() }

        val context = requireContext()
        val role = SessionManager.getUserRole(context)
        val isLawyer = role.startsWith("LAWYER", ignoreCase = true)
        binding.notificationsLoading.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = loadProfile(context)
            val (documents, cached) = loadDocuments(context, isLawyer)
            if (_binding == null) return@launch
            binding.notificationsLoading.isVisible = false
            render(context, isLawyer, profile, documents, cached)
        }
    }

    private fun render(
        context: Context,
        isLawyer: Boolean,
        profile: JSONObject?,
        documents: List<MyDocument>?,
        cached: Boolean
    ) {
        val content = binding.notificationsContent
        content.removeAllViews()

        val notices = mutableListOf<Notice>()
        val completed = mutableListOf<String>()

        val name = profile?.optString("name").orEmpty().ifBlank {
            SessionManager.getUserName(context)
        }
        val email = profile?.optString("email").orEmpty().ifBlank {
            SessionManager.getUserEmail(context)
        }
        val phone = profile?.optString("phone").orEmpty().trim()
        val address = profile?.optString("address").orEmpty().trim()
        val incompleteProfile = mutableListOf<String>()
        if (phone.isBlank() || phone.equals("Not set", true)) incompleteProfile += "phone number"
        if (address.isBlank() || address.equals("Not set", true)) incompleteProfile += "address"
        val profileFields = listOf(name, email, phone, address)
        val completeFields = profileFields.count { it.isNotBlank() && !it.equals("Not set", true) }
        val profilePercent = completeFields * 100 / 4

        if (incompleteProfile.isNotEmpty()) {
            notices += Notice(
                "Complete your profile",
                "Add your ${incompleteProfile.joinToString(" and ")} so clients can reach you and your profile is complete.",
                "Complete profile",
                R.id.nav_personal_info,
                R.color.amber_dark
            )
        }

        var verifiedGroups = 0
        var totalGroups = 0
        if (isLawyer) {
            val groups = (documents ?: emptyList())
                .filter { it.required || it.requirementGroup != null }
                .groupBy { it.requirementGroup ?: it.type }
            totalGroups = groups.size
            groups.forEach { (_, items) ->
                val verified = items.firstOrNull { it.status == "VERIFIED" }
                if (verified != null) {
                    verifiedGroups++
                    completed += verified.title
                } else {
                    val pending = items.firstOrNull { it.status == "PENDING" }
                    val failed = items.firstOrNull { it.status == "FAILED" || it.status == "REJECTED" }
                    val item = pending ?: failed ?: items.first()
                    val title = if (item.requirementGroup == "BAR_ENROLLMENT")
                        "Bar enrollment proof" else item.title
                    val detail = when {
                        pending != null -> pending.note ?: if (
                            pending.pendingReason?.endsWith("_UNREADABLE") == true)
                            "Re-upload a clearer QR or e-document."
                        else "Waiting for manual review."
                        failed != null -> failed.note
                            ?: "Verification failed. Open the document to retry."
                        else -> "Required before you can take cases. Upload or scan its QR."
                    }
                    notices += Notice(
                        "Verify $title",
                        detail,
                        "Open documents",
                        R.id.nav_my_documents,
                        if (pending != null) R.color.amber_dark else R.color.error_red
                    )
                }
            }
            if (totalGroups == 0) {
                notices += Notice(
                    "Load your documents",
                    "Open My Documents to see the documents required for your role.",
                    "Open documents",
                    R.id.nav_my_documents,
                    R.color.navy_primary
                )
            }
        }

        val percent = if (isLawyer) {
            if (totalGroups == 0) 0 else verifiedGroups * 100 / totalGroups
        } else profilePercent

        content.addView(overviewCard(
            context,
            if (isLawyer) "Verification progress" else "Profile completion",
            if (isLawyer) "$verifiedGroups of $totalGroups required documents verified"
            else "$completeFields of 4 profile details completed",
            percent
        ), lp(context))

        if (cached || profile == null) {
            val note = card(context, R.color.doc_warning_soft)
            note.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            if (cached) {
                note.addView(label(context,
                    "Showing saved document status. Reconnect to refresh.",
                    12f, R.color.text_secondary))
            }
            if (profile == null) {
                note.addView(label(context,
                    "Profile details could not be refreshed. Check your connection.",
                    12f, R.color.text_secondary), lp(context, top = 8))
            }
            content.addView(note, lp(context, top = 12))
        }

        section(context, "Action required",
            if (notices.isEmpty()) "Nothing pending right now" else "Finish these to move forward",
            top = 28)
        if (notices.isEmpty()) {
            content.addView(noticeCard(context, Notice(
                "You're all set",
                "Your required documents and profile details are complete.",
                null, null, R.color.justitia_teal_success
            )), lp(context, top = 10))
        } else {
            notices.forEach { content.addView(noticeCard(context, it), lp(context, top = 10)) }
        }

        if (completed.isNotEmpty()) {
            section(context, "Verified", "Already complete", top = 28)
            val completedCard = card(context)
            completed.forEachIndexed { index, title ->
                if (index > 0) {
                    completedCard.addView(divider(context),
                        lp(context, height = dp(context, 1), top = 12, bottom = 12))
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(label(context, "✓", 15f, R.color.justitia_teal_success, true),
                    lp(context, width = ViewGroup.LayoutParams.WRAP_CONTENT,
                        height = ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(label(context, title, 14f, R.color.text_primary),
                    lp(context, width = 0, weight = 1f, start = 10))
                completedCard.addView(row)
            }
            content.addView(completedCard, lp(context, top = 10))
        }

        if (!isLawyer) {
            section(context, "Quick action", "Find the right legal help", top = 28)
            content.addView(noticeCard(context, Notice(
                "Find a lawyer",
                "Search verified advocates and book a consultation.",
                "Find lawyers", R.id.nav_find_lawyers, R.color.navy_primary
            )), lp(context, top = 10))
        }

        section(context, "Tips", "Good to know", top = 28)
        val tips = card(context)
        val tipText = if (isLawyer) {
            "• Verify Aadhaar first; PAN and the live selfie depend on it.\n" +
                "• If a card QR is damaged, download your free e-Aadhaar/e-PAN and upload its screenshot.\n" +
                "• Keep your profile phone and address up to date.\n" +
                "• Pending manual reviews appear here until resolved."
        } else {
            "• Keep your phone number and address up to date.\n" +
                "• Book consultations from a lawyer's profile.\n" +
                "• Your appointments and saved lawyers are in your profile."
        }
        tipText.split("\n").forEachIndexed { index, line ->
            tips.addView(label(context, line, 13f, R.color.text_secondary),
                lp(context, top = if (index == 0) 0 else 10))
        }
        content.addView(tips, lp(context, top = 10))
    }

    private fun overviewCard(
        context: Context,
        title: String,
        detail: String,
        percent: Int
    ): LinearLayout {
        val card = card(context)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label(context, title, 16f, R.color.navy_primary, true),
            lp(context, width = 0, weight = 1f))
        header.addView(label(context, "$percent%", 22f,
            if (percent >= 100) R.color.justitia_teal_success else R.color.amber_dark, true),
            lp(context, width = ViewGroup.LayoutParams.WRAP_CONTENT,
                height = ViewGroup.LayoutParams.WRAP_CONTENT, start = 12))
        card.addView(header)
        card.addView(label(context, detail, 13f, R.color.text_secondary), lp(context, top = 6))
        card.addView(LinearProgressIndicator(context).apply {
            isIndeterminate = false
            max = 100
            progress = percent.coerceIn(0, 100)
            setIndicatorColor(ContextCompat.getColor(context, R.color.amber_accent))
            trackColor = ContextCompat.getColor(context, R.color.doc_navy_soft)
            trackCornerRadius = dp(context, 4)
            trackThickness = dp(context, 6)
            trackStopIndicatorSize = 0
        }, lp(context, top = 14))
        return card
    }

    private fun noticeCard(context: Context, notice: Notice): LinearLayout {
        val card = card(context)

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        header.addView(dot(context, notice.accent),
            lp(context, width = dp(context, 9), height = dp(context, 9), top = 7))
        header.addView(label(context, notice.title, 15f, R.color.navy_primary, true),
            lp(context, width = 0, weight = 1f, start = 10))
        card.addView(header)

        card.addView(label(context, notice.detail, 13f, R.color.text_secondary), lp(context, top = 8))

        if (notice.action != null && notice.destination != null) {
            card.addView(actionButton(context, notice.action, notice.destination), lp(context, top = 16))
        }
        return card
    }

    private fun actionButton(context: Context, title: String, destination: Int) =
        MaterialButton(context).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            minHeight = dp(context, 48)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(context, 14)
            setPadding(dp(context, 18), 0, dp(context, 18), 0)
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.amber_accent))
            setTextColor(ContextCompat.getColor(context, R.color.navy_primary))
            setOnClickListener { findNavController().navigate(destination) }
        }

    private fun section(context: Context, title: String, subtitle: String, top: Int) {
        binding.notificationsContent.addView(
            label(context, title, 18f, R.color.navy_primary, true),
            lp(context, top = top)
        )
        binding.notificationsContent.addView(
            label(context, subtitle, 12f, R.color.text_secondary),
            lp(context, top = 3)
        )
    }

    private fun card(context: Context, background: Int = R.color.white): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
            this.background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(ContextCompat.getColor(context, background))
                if (background == R.color.white) {
                    setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.home_outline))
                }
            }
            elevation = dp(context, 1).toFloat()
        }

    private fun dot(context: Context, color: Int): View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, color))
        }
    }

    private fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
    }

    private fun label(
        context: Context,
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(ContextCompat.getColor(context, color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }

    private fun lp(
        context: Context,
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        weight: Float = 0f,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ) = LinearLayout.LayoutParams(width, height, weight).apply {
        topMargin = dp(context, top)
        bottomMargin = dp(context, bottom)
        marginStart = dp(context, start)
        marginEnd = dp(context, end)
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()

    private suspend fun loadProfile(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        if (!NetworkMonitor.online.value) return@withContext null
        val userId = SessionManager.getUserId(context)
        if (userId.isBlank()) return@withContext null
        val role = SessionManager.getUserRole(context)
        val path = when {
            role.equals("LAWYER_FRESHER", true) -> "/api/users/lawyer/fresher/$userId"
            role.equals("LAWYER_EXPERIENCED", true) -> "/api/users/lawyer/experienced/$userId"
            else -> "/api/users/client/$userId"
        }
        try {
            val conn = (URL("${ApiConfig.BASE_URL}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                val token = SessionManager.getAppToken(context)
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) null else JSONObject(body).optJSONObject("user")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadDocuments(
        context: Context,
        isLawyer: Boolean
    ): Pair<List<MyDocument>?, Boolean> {
        if (!isLawyer) return null to false
        if (!NetworkMonitor.online.value) return DocumentsCache(context).load() to true
        return try {
            MyDocumentsApi(context.applicationContext).list() to false
        } catch (_: Exception) {
            DocumentsCache(context).load() to true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
