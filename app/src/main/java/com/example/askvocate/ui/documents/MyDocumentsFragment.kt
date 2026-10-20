package com.example.askvocate.ui.documents

import android.content.Intent
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.askvocate.util.AppToast as Toast
import com.example.askvocate.util.ToastType
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.askvocate.R
import com.example.askvocate.databinding.FragmentMyDocumentsBinding
import com.example.askvocate.ui.verification.LiveSelfieActivity
import com.example.askvocate.util.applyStatusBarInset
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MyDocumentsFragment : Fragment() {
    private var _binding: FragmentMyDocumentsBinding? = null
    private val binding get() = _binding!!
    private val documentState: MyDocumentsState by viewModels()
    private lateinit var api: MyDocumentsApi
    private var selectedType: String? = null
    private var selected: MyDocument? = null
    private var front: Uri?
        get() = documentState.front
        set(value) { documentState.front = value }
    private var back: Uri?
        get() = documentState.back
        set(value) { documentState.back = value }
    private var pickingBack: Boolean
        get() = documentState.pickingBack
        set(value) { documentState.pickingBack = value }
    private var cameraUri: Uri?
        get() = documentState.cameraUri
        set(value) { documentState.cameraUri = value }
    private var downloadBytes: ByteArray? = null
    private var aadhaarNumberVisible = false
    private var revealedAadhaarNumber: String? = null
    private var aadhaarValueView: TextView? = null
    private var aadhaarRevealButton: ImageButton? = null
    private var panNumberVisible = false
    private var revealedPanNumber: String? = null
    private var panValueView: TextView? = null
    private var panRevealButton: ImageButton? = null
    private var busy = false
    private var loadingAnimator: ValueAnimator? = null
    private var loadingStage: Job? = null
    private var skeletonAnimator: ValueAnimator? = null
    private var submitProgressAnimator: ValueAnimator? = null
    private var checkStatusButton: MaterialButton? = null

    private val chooseImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { selectedImage(uri) }
            .onFailure { message(it.message ?: "Could not read the selected image.") }
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let(::selectedImage)
        else {
            documentState.discardPendingCapture()
            message("Photo was not captured. Try again or choose an image from files.")
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { launchCamera() }
            .onFailure { message(it.message ?: "Could not open the camera.") }
        else message("Camera permission is needed to take a photo.")
    }
    private val saveImage = registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val bytes = downloadBytes ?: return@registerForActivityResult
        downloadBytes = null
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true, "Saving image", "Writing the document image to your device…")
            runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Could not save the image.")
            }.onSuccess { message("Document image saved.", ToastType.SUCCESS) }
                .onFailure { message(it.message ?: "Download failed.") }
            setBusy(false)
        }
    }
    private val liveQrScanner = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents?.trim()
        if (contents.isNullOrEmpty()) return@registerForActivityResult
        if (!contents.all(Char::isDigit) || contents.length % 4 != 0) {
            message("That QR is not the signed Enhanced PAN QR.")
            return@registerForActivityResult
        }
        scanPanQrText(contents)
    }
    private val qrPhotoCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) runCatching { launchQrCamera() }
                .onFailure { message(it.message ?: "Could not open the camera.") }
            else message("Camera permission is needed to take a photo.")
        }
    private val qrPhotoCamera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = documentState.qrCameraUri
        if (success && uri != null) submitQrPhoto(uri)
        else {
            documentState.discardQrCapture()
            message("Photo was not captured. Try again or choose an image from files.")
        }
    }
    private val qrPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { submitQrPhoto(uri) }
            .onFailure { message(it.message ?: "Could not read the selected image.") }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        api = MyDocumentsApi(requireContext().applicationContext)
        binding.documentsAppBar.applyStatusBarInset()
        binding.documentsBack.setOnClickListener { goBack() }
        binding.documentsRefresh.setOnClickListener { if (!busy) refresh() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        selectedType = savedInstanceState?.getString("type")
        savedInstanceState?.let { state ->
            front = state.getString("front_uri")?.let(Uri::parse) ?: front
            back = state.getString("back_uri")?.let(Uri::parse) ?: back
            cameraUri = state.getString("camera_uri")?.let(Uri::parse) ?: cameraUri
            documentState.qrCameraUri = state.getString("qr_camera_uri")?.let(Uri::parse)
                ?: documentState.qrCameraUri
            pickingBack = state.getBoolean("picking_back", pickingBack)
        }
        val cached = documentState.documents
        if (cached == null) refresh(showInitialOverlay = true)
        else showCached(cached)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("type", selectedType)
        outState.putString("front_uri", front?.toString())
        outState.putString("back_uri", back?.toString())
        outState.putString("camera_uri", cameraUri?.toString())
        outState.putString("qr_camera_uri", documentState.qrCameraUri?.toString())
        outState.putBoolean("picking_back", pickingBack)
    }

    private fun goBack() {
        if (busy) return
        if (selectedType != null) {
            selectedType = null; selected = null; front = null; back = null
            aadhaarNumberVisible = false; revealedAadhaarNumber = null
            panNumberVisible = false; revealedPanNumber = null
            documentState.clearCapturedFiles()
            documentState.documents?.let(::renderList)
            binding.documentsScroll.post { binding.documentsScroll.smoothScrollTo(0, 0) }
        } else findNavController().navigateUp()
    }

    private fun setBusy(value: Boolean, title: String = "Loading documents", detail: String = "Getting the latest status…") {
        busy = value
        val current = _binding ?: return
        current.documentsLoadingOverlay.isVisible = value
        current.documentsInlineProgress.isVisible = false
        if (value) {
            stopSkeletons()
            current.documentsProgress.isIndeterminate = true
            current.documentsProgress.progress = 0
            current.documentsLoadingTitle.text = title
            current.documentsLoadingDetail.text = detail
            current.documentsLoadingOverlay.alpha = 0f
            current.documentsLoadingOverlay.animate().alpha(1f).setDuration(160).start()
            if (ValueAnimator.areAnimatorsEnabled()) {
                loadingAnimator?.cancel()
                loadingAnimator = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
                    duration = 1100
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener {
                        current.documentsLoadingIcon.scaleX = it.animatedValue as Float
                        current.documentsLoadingIcon.scaleY = it.animatedValue as Float
                    }
                    start()
                }
            }
        } else {
            loadingStage?.cancel()
            loadingStage = null
            loadingAnimator?.cancel()
            loadingAnimator = null
            submitProgressAnimator?.cancel()
            submitProgressAnimator = null
            current.documentsProgress.isIndeterminate = true
            current.documentsProgress.progress = 0
            current.documentsLoadingIcon.scaleX = 1f
            current.documentsLoadingIcon.scaleY = 1f
        }
    }

    private fun renderSkeletons() {
        stopSkeletons()
        val content = binding.documentsContent
        content.removeAllViews()
        content.addView(card().apply {
            addView(skeletonBlock(-1, 12, 8))
            addView(skeletonBlock(-1, 20, 8, 12))
            addView(skeletonBlock(160, 12, 8, 10))
        }, marginParams(0))
        content.addView(label("Your documents", 18f, R.color.navy_primary, true), marginParams(24))
        repeat(3) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(skeletonBlock(48, 48, 14))
            val lines = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            lines.addView(skeletonBlock(170, 14, 6))
            lines.addView(skeletonBlock(110, 10, 5, 8))
            row.addView(lines, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(14) })
            content.addView(card().apply { addView(row) }, marginParams(12))
        }
        if (ValueAnimator.areAnimatorsEnabled()) {
            content.alpha = 0.55f
            skeletonAnimator = ValueAnimator.ofFloat(0.45f, 0.9f).apply {
                duration = 850
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    if (_binding != null) binding.documentsContent.alpha = it.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun stopSkeletons() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        if (_binding != null) binding.documentsContent.alpha = 1f
    }

    private fun skeletonBlock(widthDp: Int, heightDp: Int, radiusDp: Int, topDp: Int = 0) =
        View(requireContext()).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(radiusDp).toFloat()
                setColor(color(R.color.doc_navy_soft))
            }
            layoutParams = if (widthDp > 0)
                LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply { topMargin = dp(topDp) }
            else
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
                    .apply { topMargin = dp(topDp) }
        }

    private fun animateIn(view: View, index: Int) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.alpha = 0f
        view.translationY = dp(10).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((index * 45L).coerceAtMost(320L))
            .setDuration(240)
            .start()
    }

    private fun startSubmitProgress() {
        val bar = _binding?.documentsProgress ?: return
        bar.isIndeterminate = false
        bar.progress = 0
        submitProgressAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            bar.progress = 45
            return
        }
        submitProgressAnimator = ValueAnimator.ofInt(0, 90).apply {
            duration = 9000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (_binding != null) bar.progress = it.animatedValue as Int
            }
            start()
        }
    }

    private fun showCached(docs: List<MyDocument>) {
        val type = selectedType
        val doc = docs.firstOrNull { it.type == type }
        if (type != null && doc != null) renderDetail(doc)
        else {
            selectedType = null
            aadhaarNumberVisible = false
            revealedAadhaarNumber = null
            renderList(docs)
        }
    }

    private fun refresh(showInitialOverlay: Boolean = false) {
        if (busy) return
        val type = selectedType
        if (showInitialOverlay) {
            binding.documentsContent.removeAllViews()
            renderSkeletons()
        }
        busy = true
        binding.documentsRefresh.isVisible = false
        binding.documentsRefreshProgress.isVisible = true
        binding.documentsInlineProgress.isVisible = true
        checkStatusButton?.apply {
            isEnabled = false
            text = "Checking…"
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                if (showInitialOverlay || type == null) {
                    val docs = api.list()
                    documentState.documents = docs
                    if (_binding != null) showCached(docs)
                } else {
                    val doc = api.detail(type)
                    val previous = selected
                    documentState.documents = documentState.documents?.map {
                        if (it.type == type) doc else it
                    }
                    if (_binding != null && selectedType == type) {
                        renderDetail(doc)
                        if ((doc.status == "FAILED" || doc.status == "REJECTED") &&
                            (previous?.status != doc.status || previous.note != doc.note)) {
                            scrollToStatusError()
                        }
                    }
                }
            }.onFailure {
                if (_binding != null) {
                    if (showInitialOverlay) binding.documentsContent.removeAllViews()
                    message(it.message ?: "Could not load documents.")
                }
            }
            if (_binding != null) {
                stopSkeletons()
                busy = false
                binding.documentsInlineProgress.isVisible = false
                binding.documentsRefreshProgress.isVisible = false
                binding.documentsRefresh.isVisible = true
                checkStatusButton?.apply {
                    isEnabled = true
                    text = "Check verification status"
                }
            }
        }
    }

    private fun renderList(docs: List<MyDocument>) {
        stopSkeletons()
        binding.documentsContent.removeAllViews()
        aadhaarValueView = null
        aadhaarRevealButton = null
        panValueView = null
        panRevealButton = null
        checkStatusButton = null
        binding.documentsTitle.text = "My Documents"
        binding.documentsSubtitle.text = "Your secure document vault"
        val verified = docs.count { it.status == "VERIFIED" }
        val hero = card(R.color.navy_primary).apply {
            addView(label("YOUR VAULT", 12f, R.color.amber_light, true))
            addView(label("Documents, all in one place", 21f, R.color.white, true), marginParams(8))
            addView(label("$verified of ${docs.size} verified  •  Select a document to view its status.",
                13f, R.color.white), marginParams(8))
        }
        binding.documentsContent.addView(hero, marginParams(0))
        sectionTitle("Your documents", "View, upload and track verification")
        docs.forEachIndexed { index, doc ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ImageView(requireContext()).apply {
                setImageResource(if (doc.liveCapture) R.drawable.ic_profile else R.drawable.ic_description)
                imageTintList = ColorStateList.valueOf(color(R.color.navy_primary))
                setBackgroundResource(R.drawable.bg_document_icon)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val copy = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(label(doc.title, 16f, R.color.text_primary, true))
            copy.addView(statusChip(doc.status), marginParams(8, false))
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            })
            row.addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ColorStateList.valueOf(color(R.color.text_secondary))
                contentDescription = "Open ${doc.title}"
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            val item = card().apply {
                addView(row)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedType = doc.type; selected = doc; front = null; back = null
                    aadhaarNumberVisible = false; revealedAadhaarNumber = null
                    panNumberVisible = false; revealedPanNumber = null
                    binding.documentsScroll.scrollTo(0, 0)
                    renderDetail(doc)
                }
            }
            binding.documentsContent.addView(item, marginParams(12))
            animateIn(item, index)
        }
        revealContent()
    }

    private fun renderDetail(doc: MyDocument) {
        stopSkeletons()
        binding.documentsContent.removeAllViews()
        aadhaarValueView = null
        aadhaarRevealButton = null
        panValueView = null
        panRevealButton = null
        checkStatusButton = null
        selected = doc
        binding.documentsTitle.text = doc.title
        binding.documentsSubtitle.text = "Status, images and details"
        val statusNote = doc.note?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true)
        } ?: when (doc.status) {
            "VERIFIED" -> "Verification complete."
            "PENDING" -> "Verification is in progress."
            "NOT_UPLOADED" -> "Upload a clear image to begin."
            else -> "Review the document and try again if needed."
        }
        val statusCard = card(statusBackground(doc.status)).apply {
            val heading = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(ImageView(requireContext()).apply {
                setImageResource(statusIcon(doc.status))
                imageTintList = ColorStateList.valueOf(color(statusColor(doc.status)))
                setBackgroundResource(R.drawable.bg_document_icon)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            heading.addView(label(statusText(doc.status), 19f, statusColor(doc.status), true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
            addView(heading)
            addView(label(statusNote, 14f, R.color.text_secondary), marginParams(14, false))
            if (doc.status == "PENDING") {
                addView(LinearProgressIndicator(requireContext()).apply {
                    isIndeterminate = true
                    setIndicatorColor(color(R.color.amber_accent))
                    trackColor = color(R.color.doc_warning_soft)
                }, marginParams(18, false))
            }
        }
        statusCard.tag = "document_status_error"
        binding.documentsContent.addView(statusCard, marginParams(0))
        if (doc.status == "PENDING") {
            val check = actionButton("Check verification status", false) { refresh() }
            checkStatusButton = check
            binding.documentsContent.addView(check, marginParams(12))
        }
        if (doc.uploadOnly) {
            val note = card(R.color.doc_navy_soft).apply {
                addView(label("Stored securely", 15f, R.color.navy_primary, true))
                addView(label("Automatic verification is not available for this document yet.",
                    13f, R.color.text_secondary), marginParams(6, false))
            }
            binding.documentsContent.addView(note, marginParams(14))
        }
        if (doc.media.isNotEmpty()) {
            sectionTitle("Uploaded images", "Tap an image to view it")
            doc.media.forEach { side ->
                val sideName = side.replaceFirstChar { it.uppercase() }
                val imageCard = card()
                imageCard.addView(label("$sideName image", 16f, R.color.text_primary, true))
                val previewHolder = FrameLayout(requireContext()).apply {
                    setBackgroundColor(color(R.color.doc_navy_soft))
                    setOnClickListener { showMedia(doc.type, side) }
                }
                val preview = ImageView(requireContext()).apply {
                    contentDescription = "$sideName document image"
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                previewHolder.addView(preview, FrameLayout.LayoutParams(-1, -1))
                val previewProgress = com.google.android.material.progressindicator.CircularProgressIndicator(requireContext()).apply {
                    isIndeterminate = true
                    setIndicatorColor(color(R.color.amber_accent))
                }
                previewHolder.addView(previewProgress, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
                imageCard.addView(previewHolder, LinearLayout.LayoutParams(-1, dp(156)).apply { topMargin = dp(12) })
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        val bytes = api.media(doc.type, side)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot read image preview.")
                    }.onSuccess { bitmap ->
                        if (_binding != null && selectedType == doc.type && preview.parent != null) {
                            preview.setImageBitmap(bitmap)
                            previewProgress.isVisible = false
                        }
                    }.onFailure {
                        previewProgress.isVisible = false
                        if (_binding != null && selectedType == doc.type)
                            message(it.message ?: "Could not load image preview.")
                    }
                }
                val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(actionButton("View", false) { showMedia(doc.type, side) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                actions.addView(actionButton("Download", false) { startDownload(doc.type, side) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
                imageCard.addView(actions, marginParams(8))
                binding.documentsContent.addView(imageCard, marginParams(12))
            }
        }
        val useQrDetails = doc.type == "AADHAAR" && doc.status == "VERIFIED" && doc.qrDetails.isNotEmpty()
        val displayDetails = if (useQrDetails) doc.qrDetails else doc.details
        if (doc.maskedNumber != null || displayDetails.isNotEmpty() || doc.submittedAt != null) {
            sectionTitle("Document details",
                if (useQrDetails) "Information from your signed Aadhaar QR"
                else "Information from your saved document")
            val detailCard = card()
            doc.maskedNumber?.let {
                val fullNumberAvailable = doc.aadhaarNumber?.let { number ->
                    number.length == 12 && number.all(Char::isDigit)
                } == true
                val fullPanAvailable = doc.panNumber?.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]")) == true
                when {
                    doc.type == "AADHAAR" && doc.status == "VERIFIED" && fullNumberAvailable ->
                        aadhaarNumberRow(detailCard, it)
                    doc.type == "PAN" && doc.status == "VERIFIED" && fullPanAvailable ->
                        panNumberRow(detailCard, it)
                    else -> detailRow(detailCard, "Number", it)
                }
            }
            displayDetails.forEach { (key, value) ->
                if (value.isNotBlank()) detailRow(detailCard,
                    key.replace(Regex("([a-z])([A-Z])"), "$1 $2"), value)
            }
            doc.submittedAt?.let { detailRow(detailCard, "Uploaded", date(it)) }
            doc.verifiedAt?.let { detailRow(detailCard, "Verified", date(it)) }
            binding.documentsContent.addView(detailCard, marginParams(12))
        }
        val showQrPhoto = !doc.qrPhotoUrl.isNullOrBlank() && when (doc.type) {
            "AADHAAR" -> useQrDetails
            // The uploaded card photo takes priority; the QR photo is the fallback
            // for live scans, where no card image was ever uploaded.
            "PAN" -> doc.status == "VERIFIED" && doc.media.isEmpty()
            else -> false
        }
        if (showQrPhoto) {
            val qrName = if (doc.type == "PAN") "PAN" else "Aadhaar"
            sectionTitle("$qrName QR photo", "Photo embedded in your signed $qrName QR")
            val photoCard = card()
            photoCard.addView(ImageView(requireContext()).apply {
                contentDescription = "$qrName QR photo"
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(doc.qrPhotoUrl) { crossfade(true) }
            }, LinearLayout.LayoutParams(-1, dp(180)))
            binding.documentsContent.addView(photoCard, marginParams(12))
        }
        if (doc.type == "PAN") {
            sectionTitle("PAN QR", "Use this if the card text is difficult to read")
            val qrCard = card()
            qrCard.addView(label("Scan only the QR code", 16f, R.color.text_primary, true))
            qrCard.addView(label("Open the live scanner and point the camera at the QR code on the PAN card.",
                13f, R.color.text_secondary), marginParams(6, false))
            addAction(qrCard, "Open live QR scanner", true) { launchLiveQrScanner() }
            addAction(qrCard, "Use a photo of the QR", false) { chooseQrPhotoSource() }
            binding.documentsContent.addView(qrCard, marginParams(12))
        }
        if (doc.liveCapture) {
            if (doc.status != "VERIFIED") addAction(binding.documentsContent, "Start live selfie verification", true) {
                runCatching { startActivity(Intent(requireContext(), LiveSelfieActivity::class.java)) }
                    .onFailure { message(it.message ?: "Could not start selfie verification.") }
            }
            revealContent()
            return
        }
        if (doc.status != "VERIFIED" && doc.status != "PENDING") {
            sectionTitle(if (doc.uploadOnly) "Upload image" else "Upload and verify",
                "Use a clear, well-lit photo with every corner visible")
            val uploadCard = card()
            uploadCard.addView(label("Document photos", 16f, R.color.text_primary, true))
            uploadCard.addView(label(getString(R.string.document_upload_limits), 13f,
                R.color.text_secondary), marginParams(6, false))
            addAction(uploadCard, if (front == null) "Choose front image" else "✓ Front image ready · Change", false) {
                chooseSource(false)
            }
            if (doc.requiresBack) addAction(uploadCard,
                if (back == null) "Choose back image" else "✓ Back image ready · Change", false) {
                chooseSource(true)
            }
            addAction(uploadCard, if (doc.uploadOnly) "Upload document" else "Submit for verification", true) {
                val first = front
                if (first == null) { message("Choose the front image first."); return@addAction }
                if (doc.requiresBack && back == null) { message("Choose the back image too."); return@addAction }
                submit(doc.type, first, back)
            }
            binding.documentsContent.addView(uploadCard, marginParams(12))
        }
        revealContent()
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun card(background: Int = R.color.white) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        this.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(color(background))
            if (background == R.color.white) setStroke(dp(1), color(R.color.home_outline))
        }
        elevation = dp(2).toFloat()
    }

    private fun label(value: String, size: Float, tone: Int, bold: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(color(tone))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun statusColor(status: String) = when (status) {
        "VERIFIED" -> R.color.justitia_teal_success
        "PENDING", "UPLOADED" -> R.color.amber_dark
        "FAILED", "REJECTED" -> R.color.error_red
        else -> R.color.navy_primary
    }

    private fun statusBackground(status: String) = when (status) {
        "VERIFIED" -> R.color.doc_success_soft
        "PENDING", "UPLOADED" -> R.color.doc_warning_soft
        "FAILED", "REJECTED" -> R.color.doc_error_soft
        else -> R.color.doc_navy_soft
    }

    private fun statusIcon(status: String) = when (status) {
        "VERIFIED" -> R.drawable.ic_check_circle
        "PENDING" -> R.drawable.ic_history
        else -> R.drawable.ic_description
    }

    private fun statusChip(status: String) = label(statusText(status), 12f, statusColor(status), true).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(100).toFloat()
            setColor(color(statusBackground(status)))
        }
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }

    private fun sectionTitle(title: String, subtitle: String) {
        binding.documentsContent.addView(label(title, 18f, R.color.navy_primary, true), marginParams(24))
        binding.documentsContent.addView(label(subtitle, 13f, R.color.text_secondary), marginParams(3))
    }

    private fun detailRow(container: LinearLayout, name: String, value: String) {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        row.addView(label(name, 12f, R.color.text_secondary, true))
        row.addView(label(value, 14f, R.color.text_primary), marginParams(3, false))
        container.addView(row, marginParams(12))
    }

    private fun aadhaarNumberRow(container: LinearLayout, maskedNumber: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = if (aadhaarNumberVisible) revealedAadhaarNumber ?: maskedNumber else maskedNumber
        val valueView = label(value, 14f, R.color.text_primary)
        aadhaarValueView = valueView
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Aadhaar number", 12f, R.color.text_secondary, true))
            addView(valueView, marginParams(3, false))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val revealButton = ImageButton(requireContext()).apply {
            setImageResource(if (aadhaarNumberVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
            imageTintList = ColorStateList.valueOf(color(R.color.navy_primary))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(R.color.doc_navy_soft))
            }
            contentDescription = if (aadhaarNumberVisible) "Hide Aadhaar number" else "Show Aadhaar number"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                if (aadhaarNumberVisible) {
                    aadhaarNumberVisible = false
                    revealedAadhaarNumber = null
                    renderDetail(selected ?: return@setOnClickListener)
                } else if (revealedAadhaarNumber != null) {
                    aadhaarNumberVisible = true
                    renderDetail(selected ?: return@setOnClickListener)
                } else {
                    isEnabled = false
                    setBusy(true, "Revealing Aadhaar number", "Fetching your verified number…")
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { api.revealAadhaarNumber() }
                            .onSuccess { number ->
                                if (_binding != null && selectedType == "AADHAAR" &&
                                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    revealedAadhaarNumber = number
                                    aadhaarNumberVisible = true
                                    renderDetail(selected ?: return@onSuccess)
                                }
                                if (_binding != null) setBusy(false)
                            }
                            .onFailure {
                                isEnabled = true
                                if (_binding != null &&
                                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    message(it.message ?: "Could not reveal Aadhaar number.")
                                }
                                if (_binding != null) setBusy(false)
                            }
                    }
                }
            }
        }
        aadhaarRevealButton = revealButton
        row.addView(revealButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(row, marginParams(12))
    }

    private fun panNumberRow(container: LinearLayout, maskedNumber: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = if (panNumberVisible) revealedPanNumber ?: maskedNumber else maskedNumber
        val valueView = label(value, 14f, R.color.text_primary)
        panValueView = valueView
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("PAN number", 12f, R.color.text_secondary, true))
            addView(valueView, marginParams(3, false))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val revealButton = ImageButton(requireContext()).apply {
            setImageResource(if (panNumberVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
            imageTintList = ColorStateList.valueOf(color(R.color.navy_primary))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(R.color.doc_navy_soft))
            }
            contentDescription = if (panNumberVisible) "Hide PAN number" else "Show PAN number"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                if (panNumberVisible) {
                    panNumberVisible = false
                    revealedPanNumber = null
                    renderDetail(selected ?: return@setOnClickListener)
                } else if (revealedPanNumber != null) {
                    panNumberVisible = true
                    renderDetail(selected ?: return@setOnClickListener)
                } else {
                    isEnabled = false
                    setBusy(true, "Revealing PAN number", "Fetching your verified PAN…")
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { api.revealPanNumber() }
                            .onSuccess { number ->
                                if (_binding != null && selectedType == "PAN" &&
                                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    revealedPanNumber = number
                                    panNumberVisible = true
                                    renderDetail(selected ?: return@onSuccess)
                                }
                                if (_binding != null) setBusy(false)
                            }
                            .onFailure {
                                isEnabled = true
                                if (_binding != null &&
                                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    message(it.message ?: "Could not reveal PAN number.")
                                }
                                if (_binding != null) setBusy(false)
                            }
                    }
                }
            }
        }
        panRevealButton = revealButton
        row.addView(revealButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(row, marginParams(12))
    }

    private fun actionButton(title: String, primary: Boolean, action: () -> Unit) =
        MaterialButton(requireContext()).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(14)
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(color(if (primary) R.color.amber_accent else R.color.white))
            setTextColor(color(R.color.navy_primary))
            if (!primary) {
                strokeWidth = dp(1)
                strokeColor = ColorStateList.valueOf(color(R.color.home_outline))
            }
            setOnClickListener { if (!busy) action() }
        }

    private fun addAction(container: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        container.addView(actionButton(title, primary, action), marginParams(12))
    }

    private fun revealContent() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        binding.documentsContent.animate().cancel()
        binding.documentsContent.alpha = 0f
        binding.documentsContent.translationY = dp(8).toFloat()
        binding.documentsContent.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun date(raw: String): String = runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
            .withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
    }.getOrDefault(raw)

    private fun statusText(status: String): String = when (status) {
        "VERIFIED" -> "Verified"
        "UPLOADED" -> "Uploaded · Not verified"
        "PENDING" -> "Verification pending"
        "FAILED" -> "Verification failed"
        "REJECTED" -> "Rejected"
        else -> "Not uploaded"
    }

    private fun chooseSource(isBack: Boolean) {
        pickingBack = isBack
        MaterialAlertDialogBuilder(requireContext()).setTitle("Add ${if (isBack) "back" else "front"} image")
            .setItems(arrayOf("Take photo", "Choose from files")) { _, which ->
                if (which == 0) cameraPermission.launch(android.Manifest.permission.CAMERA)
                else runCatching { chooseImage.launch("image/*") }
                    .onFailure { message(it.message ?: "Could not open image files.") }
            }.show()
    }

    private fun launchCamera() {
        val dir = File(requireContext().cacheDir, "document_captures").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", dir)
        documentState.capturedFiles.add(file)
        cameraUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.documents", file)
        takePhoto.launch(cameraUri!!)
    }

    private fun launchLiveQrScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Align the PAN QR inside the frame")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setBarcodeImageEnabled(false)
            .setCaptureActivity(PortraitCaptureActivity::class.java)
        liveQrScanner.launch(options)
    }

    private fun chooseQrPhotoSource() {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Add a photo of the PAN QR")
            .setItems(arrayOf("Take photo", "Choose from files")) { _, which ->
                if (which == 0) qrPhotoCameraPermission.launch(android.Manifest.permission.CAMERA)
                else runCatching { qrPhotoPicker.launch("image/*") }
                    .onFailure { message(it.message ?: "Could not open image files.") }
            }.show()
    }

    private fun launchQrCamera() {
        val dir = File(requireContext().cacheDir, "document_captures").apply { mkdirs() }
        val file = File.createTempFile("pan_qr_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(requireContext(),
            "${requireContext().packageName}.documents", file)
        documentState.qrCameraFile = file
        documentState.qrCameraUri = uri
        qrPhotoCamera.launch(uri)
    }

    private fun submitQrPhoto(uri: Uri) {
        setBusy(true, "Verifying PAN QR", "Reading the QR code from your photo…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { api.scanPanQr(uri) }
            documentState.discardQrCapture()
            result.onSuccess { doc -> if (_binding != null) applyPanDocument(doc) }
                .onFailure { if (_binding != null) message(it.message ?: "Could not verify the PAN QR.") }
            if (_binding != null) setBusy(false)
        }
    }

    private fun scanPanQrText(qrText: String) {
        setBusy(true, "Verifying PAN QR", "Decoding the signed PAN QR…")
        viewLifecycleOwner.lifecycleScope.launch {
            var scannedDoc: MyDocument? = null
            val scanError = runCatching { api.scanPanQrText(qrText) }
                .onSuccess { scannedDoc = it }
                .exceptionOrNull()?.message
            // The backend may already have saved the QR data even if the scan response
            // failed. Re-read the authoritative detail so the UI never stays stale.
            runCatching { api.detail("PAN") }
                .onSuccess { doc -> applyPanDocument(doc) }
                .onFailure {
                    val fallback = scannedDoc
                    if (fallback != null) applyPanDocument(fallback)
                    else if (_binding != null) {
                        message(scanError ?: it.message ?: "Could not verify the PAN QR.")
                    }
                }
            if (_binding != null) setBusy(false)
        }
    }

    private fun applyPanDocument(doc: MyDocument) {
        documentState.documents = documentState.documents?.map {
            if (it.type == "PAN") doc else it
        }
        selectedType = "PAN"
        selected = doc
        front = null
        back = null
        if (_binding == null) return
        renderDetail(doc)
        when (doc.status) {
            "VERIFIED" -> message("PAN verified successfully.", ToastType.SUCCESS)
            "FAILED", "REJECTED" -> scrollToStatusError()
        }
    }

    private fun selectedImage(uri: Uri) {
        if (pickingBack) back = uri else front = uri
        selected?.let(::renderSelection)
    }

    private fun renderSelection(doc: MyDocument) {
        renderDetail(doc)
    }

    private fun submit(type: String, first: Uri, second: Uri?) {
        setBusy(true, "Submitting document", "Preparing your image for secure upload…")
        startSubmitProgress()
        loadingStage = viewLifecycleOwner.lifecycleScope.launch {
            delay(1300)
            if (busy && _binding != null)
                binding.documentsLoadingDetail.text = "Uploading securely and checking the document…"
            delay(3500)
            if (busy && _binding != null)
                binding.documentsLoadingDetail.text = "Still working. Please keep this page open."
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { api.upload(type, first, second) }
                .onSuccess {
                    documentState.documents = documentState.documents?.map { existing ->
                        if (existing.type == type) it else existing
                    }
                    front = null; back = null; documentState.clearCapturedFiles(); renderDetail(it)
                    if (it.status == "FAILED" || it.status == "REJECTED") {
                        scrollToStatusError()
                    } else if (it.status == "VERIFIED") {
                        message("${it.title} verified successfully.", ToastType.SUCCESS)
                    } else if (it.uploadOnly) {
                        message("${it.title} uploaded. Verification is not available yet.", ToastType.SUCCESS)
                    }
                }
                .onFailure {
                    if (_binding != null) message(it.message ?: "Upload failed. Please try again.")
                }
            setBusy(false)
        }
    }

    private fun showMedia(type: String, side: String) {
        setBusy(true, "Opening image", "Loading the saved document photo…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val bytes = api.media(type, side)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot open image.")
            }.onSuccess { bitmap ->
                if (isAdded) MaterialAlertDialogBuilder(requireContext())
                    .setTitle("${side.replaceFirstChar { it.uppercase() }} image")
                    .setView(ImageView(requireContext()).apply {
                        setImageBitmap(bitmap); adjustViewBounds = true; setPadding(16, 16, 16, 16)
                    }).setPositiveButton("Close", null).show()
            }.onFailure { message(it.message ?: "Could not open image.") }
            setBusy(false)
        }
    }

    private fun startDownload(type: String, side: String) {
        setBusy(true, "Preparing download", "Getting the saved document image…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { api.media(type, side) }
                .onSuccess { bytes ->
                    downloadBytes = bytes
                    val extension = when {
                        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
                        bytes.size > 12 && String(bytes, 0, 4) == "RIFF" -> "webp"
                        else -> "jpg"
                    }
                    saveImage.launch("${type.lowercase()}_$side.$extension")
                }.onFailure { message(it.message ?: "Download failed.") }
            setBusy(false)
        }
    }

    private fun scrollToError(target: View) {
        binding.documentsScroll.post {
            if (_binding != null && target.parent != null) {
                binding.documentsScroll.smoothScrollTo(0, (target.top - dp(12)).coerceAtLeast(0))
            }
        }
    }

    private fun scrollToTaggedError(tag: String) {
        binding.documentsContent.findViewWithTag<View>(tag)?.let(::scrollToError)
    }

    private fun scrollToStatusError() = scrollToTaggedError("document_status_error")

    private fun marginParams(top: Int, fullWidth: Boolean = true) = LinearLayout.LayoutParams(
        if (fullWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = (top * resources.displayMetrics.density).toInt() }

    private fun message(text: String, type: ToastType = ToastType.ERROR) {
        if (isAdded) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG, type).show()
    }

    override fun onStop() {
        aadhaarNumberVisible = false
        revealedAadhaarNumber = null
        selected?.maskedNumber?.let { aadhaarValueView?.text = it }
        aadhaarRevealButton?.apply {
            isEnabled = true
            setImageResource(R.drawable.ic_visibility)
            contentDescription = "Show Aadhaar number"
        }
        panNumberVisible = false
        revealedPanNumber = null
        selected?.maskedNumber?.let { panValueView?.text = it }
        panRevealButton?.apply {
            isEnabled = true
            setImageResource(R.drawable.ic_visibility)
            contentDescription = "Show PAN number"
        }
        super.onStop()
    }

    override fun onDestroyView() {
        loadingStage?.cancel()
        loadingAnimator?.cancel()
        skeletonAnimator?.cancel()
        submitProgressAnimator?.cancel()
        super.onDestroyView()
        _binding = null
        aadhaarValueView = null
        aadhaarRevealButton = null
        panValueView = null
        panRevealButton = null
    }
}

internal class MyDocumentsState : ViewModel() {
    var documents: List<MyDocument>? = null
    var front: Uri? = null
    var back: Uri? = null
    var pickingBack = false
    var cameraUri: Uri? = null
    var qrCameraUri: Uri? = null
    var qrCameraFile: File? = null
    val capturedFiles = mutableSetOf<File>()

    fun discardPendingCapture() {
        capturedFiles.lastOrNull()?.let { file ->
            file.delete()
            capturedFiles.remove(file)
        }
        cameraUri = null
    }

    fun discardQrCapture() {
        qrCameraFile?.delete()
        qrCameraFile = null
        qrCameraUri = null
    }

    fun clearCapturedFiles() {
        capturedFiles.forEach(File::delete)
        capturedFiles.clear()
        discardQrCapture()
        cameraUri = null
    }

    override fun onCleared() {
        clearCapturedFiles()
        super.onCleared()
    }
}
