package com.example.askvocate.ui.verification

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
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
import androidx.lifecycle.lifecycleScope
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
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
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null
    private var challengeId: String = ""
    private var expectedTurn: String = ""
    private val frames = mutableListOf<File>()
    private var busy = false

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
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
        }
        instruction = TextView(this).apply {
            textSize = 18f
            text = "Preparing live selfie challenge…"
        }
        preview = PreviewView(this)
        captureButton = Button(this).apply {
            text = "Capture"
            isEnabled = false
            setOnClickListener { captureNext() }
        }
        layout.addView(instruction, LinearLayout.LayoutParams(-1, -2))
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(captureButton, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)

        cameraPermission.launch(Manifest.permission.CAMERA)
        lifecycleScope.launch {
            try {
                val challenge = withContext(Dispatchers.IO) { postChallenge() }
                challengeId = challenge.getString("challengeId")
                expectedTurn = challenge.getString("expectedTurn")
                show("Look straight at the camera, then tap Capture.")
                enableCapture()
            } catch (error: Exception) {
                show(error.message ?: "Could not start the selfie challenge.")
            }
        }
    }

    private fun startCamera() {
        val provider = ProcessCameraProvider.getInstance(this)
        provider.addListener({
            try {
                val cameraProvider = provider.get()
                val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                imageCapture = ImageCapture.Builder().build()
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
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { postFrames() }
                val status = result.optString("verificationStatus")
                if (status == "VERIFIED") {
                    challengeId = ""
                    show("Selfie verified.")
                } else {
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
                enableCapture()
            }
        }
    }

    private fun postChallenge(): JSONObject {
        val userId = URLEncoder.encode(SessionManager.getUserId(this), "UTF-8")
        val connection = URL("${ApiConfig.BASE_URL}/api/documents/selfie/challenge?userId=$userId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
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
        captureButton.isEnabled = !busy && imageCapture != null && challengeId.isNotBlank() && frames.size < 3
    }

    private fun show(message: String) { instruction.text = message }

    override fun onDestroy() {
        frames.forEach { it.delete() }
        super.onDestroy()
    }
}
