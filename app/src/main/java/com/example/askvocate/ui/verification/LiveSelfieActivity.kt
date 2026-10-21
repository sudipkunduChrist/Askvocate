package com.example.askvocate.ui.verification

import android.Manifest
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.askvocate.R
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.applyStatusBarInset
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Camera-only center / random-turn / center capture for a verified Aadhaar holder. */
class LiveSelfieActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var instruction: TextView
    private lateinit var captureButton: MaterialButton
    private lateinit var step: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var loadingIcon: ImageView
    private var loadingAnimator: ValueAnimator? = null
    private var imageCapture: ImageCapture? = null
    private var challengeId: String = ""
    private var expectedTurn: String = ""
    private val frames = mutableListOf<File>()
    private var busy = false
    private var verificationComplete = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else show("Camera permission is required for live selfie verification.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val role = SessionManager.getUserRole(this).uppercase()
        if (role != "LAWYER_FRESHER" && role != "LAWYER_EXPERIENCED") {
            finish()
            return
        }
        cacheDir.listFiles()?.filter { it.name.startsWith("askvocate_selfie_") }?.forEach { it.delete() }
        setContentView(R.layout.activity_live_selfie)
        findViewById<View>(R.id.selfie_app_bar).applyStatusBarInset()
        findViewById<View>(R.id.selfie_back).setOnClickListener { if (!busy) finish() }
        instruction = findViewById(R.id.selfie_instruction)
        step = findViewById(R.id.selfie_step)
        progress = findViewById(R.id.selfie_progress)
        preview = findViewById(R.id.selfie_preview)
        captureButton = findViewById<MaterialButton>(R.id.selfie_capture).apply {
            setOnClickListener { captureNext() }
        }
        loadingOverlay = findViewById(R.id.selfie_loading_overlay)
        loadingText = findViewById(R.id.selfie_loading_text)
        loadingIcon = findViewById(R.id.selfie_loading_icon)
        setLoading(true, "Preparing secure challenge…")

        cameraPermission.launch(Manifest.permission.CAMERA)
        lifecycleScope.launch {
            try {
                val challenge = withContext(Dispatchers.IO) { postChallenge() }
                challengeId = challenge.getString("challengeId")
                expectedTurn = challenge.getString("expectedTurn")
                show("Look straight at the camera, then tap Capture.")
                setLoading(false)
                enableCapture()
            } catch (error: Exception) {
                show(error.message ?: "Could not start the selfie challenge.")
                setLoading(false)
            }
        }
    }

    private fun startCamera() {
        val provider = ProcessCameraProvider.getInstance(this)
        provider.addListener({
            try {
                val cameraProvider = provider.get()
                val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                imageCapture = ImageCapture.Builder().setJpegQuality(85).build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA,
                    cameraPreview, imageCapture)
                enableCapture()
            } catch (error: Exception) {
                show("Front camera is unavailable: ${error.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureNext() {
        val camera = imageCapture ?: return
        if (busy || challengeId.isBlank() || frames.size >= 3) return
        busy = true
        captureButton.isEnabled = false
        captureButton.text = "Capturing…"
        val file = File.createTempFile("askvocate_selfie_", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        camera.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    frames.add(file)
                    busy = false
                    when (frames.size) {
                        1 -> show("Turn your head to your $expectedTurn, then tap Capture.")
                        2 -> show("Look straight at the camera again, then tap Capture.")
                        3 -> submitFrames()
                    }
                    enableCapture()
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    busy = false
                    show("Camera capture failed. Please try again.")
                    enableCapture()
                }
            })
    }

    private fun submitFrames() {
        busy = true
        captureButton.isEnabled = false
        show("Checking your live selfie…")
        setLoading(true, "Checking your live selfie…")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { postFrames() }
                val status = result.optString("verificationStatus")
                if (status == "VERIFIED") {
                    challengeId = ""
                    verificationComplete = true
                    show("Selfie verified.")
                } else {
                    verificationComplete = false
                    val failure = result.optString("failureReason", "Selfie did not pass face and liveness checks.")
                    val next = withContext(Dispatchers.IO) { postChallenge() }
                    challengeId = next.getString("challengeId")
                    expectedTurn = next.getString("expectedTurn")
                    show("$failure Look straight at the camera to try again.")
                }
            } catch (error: Exception) {
                challengeId = ""
                show(error.message ?: "Selfie verification is unavailable. Reopen this screen to retry.")
            } finally {
                frames.forEach { it.delete() }
                frames.clear()
                busy = false
                setLoading(false)
                enableCapture()
            }
        }
    }

    private fun postChallenge(): JSONObject {
        val userId = URLEncoder.encode(SessionManager.getUserId(this), "UTF-8")
        val connection = URL("${ApiConfig.BASE_URL}/api/documents/selfie/challenge?userId=$userId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${SessionManager.getAppToken(this)}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        return readJson(connection)
    }

    private fun postFrames(): JSONObject {
        val userId = URLEncoder.encode(SessionManager.getUserId(this), "UTF-8")
        val boundary = "askvocate-${UUID.randomUUID()}"
        val connection = URL("${ApiConfig.BASE_URL}/api/documents/selfie/verify?userId=$userId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${SessionManager.getAppToken(this)}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 45_000
        connection.doOutput = true
        connection.setChunkedStreamingMode(8192)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(connection.outputStream).use { output ->
            output.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"challengeId\"\r\n\r\n")
            output.writeBytes("$challengeId\r\n")
            listOf("center", "turned", "returned").forEachIndexed { index, name ->
                output.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$name.jpg\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                frames[index].inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n")
            }
            output.writeBytes("--$boundary--\r\n")
        }
        return readJson(connection)
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        try {
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optBoolean("success", true).not()) throw IllegalStateException(json.optString("error"))
            if (connection.responseCode !in 200..299) throw IllegalStateException(json.optString("error", "Server error"))
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun enableCapture() {
        captureButton.isEnabled = !busy && !verificationComplete && imageCapture != null &&
            challengeId.isNotBlank() && frames.size < 3
        captureButton.text = when {
            verificationComplete -> "Verified"
            frames.size >= 3 -> "Checking…"
            else -> "Capture step ${frames.size + 1}"
        }
        step.text = when {
            verificationComplete -> "Verification complete"
            frames.size >= 3 -> "Verifying"
            else -> "Step ${frames.size + 1} of 3"
        }
        progress.setProgressCompat(if (verificationComplete) 3 else frames.size, true)
    }

    private fun show(message: String) { instruction.text = message }

    private fun setLoading(visible: Boolean, message: String = "") {
        loadingOverlay.isVisible = visible
        if (visible) {
            loadingText.text = message
            loadingOverlay.alpha = 0f
            loadingOverlay.animate().alpha(1f).setDuration(160).start()
            if (ValueAnimator.areAnimatorsEnabled()) {
                loadingAnimator?.cancel()
                loadingAnimator = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
                    duration = 1100
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener {
                        loadingIcon.scaleX = it.animatedValue as Float
                        loadingIcon.scaleY = it.animatedValue as Float
                    }
                    start()
                }
            }
        } else {
            loadingAnimator?.cancel()
            loadingAnimator = null
            loadingIcon.scaleX = 1f
            loadingIcon.scaleY = 1f
        }
    }

    override fun onDestroy() {
        loadingAnimator?.cancel()
        frames.forEach { it.delete() }
        super.onDestroy()
    }
}
