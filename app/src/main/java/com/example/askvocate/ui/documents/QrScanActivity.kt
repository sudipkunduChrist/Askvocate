package com.example.askvocate.ui.documents

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.askvocate.R
import com.example.askvocate.databinding.ActivityQrScanBinding
import com.example.askvocate.util.applyStatusBarInset
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live QR scanner for Aadhaar and PAN.
 *
 * ML Kit detects a QR and the screen captures a still of that moment. The still
 * (and the raw text) are returned to the documents screen; the backend performs
 * the signature verification and decoding.
 */
class QrScanActivity : ComponentActivity() {
    private lateinit var binding: ActivityQrScanBinding
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var finished = false
    private var prompt: String = ""

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                binding.qrPrompt.text = prompt
                startCamera()
            } else {
                binding.qrPrompt.setText(R.string.pan_qr_permission_required)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.qrTopBar.applyStatusBarInset()
        binding.qrClose.setOnClickListener { finish() }
        prompt = intent.getStringExtra(EXTRA_PROMPT)
            ?: getString(R.string.qr_scan_prompt)
        binding.qrPrompt.text = prompt

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.qrPreview.surfaceProvider)
                }
                // Dense document QRs need more than the default 640x480 analysis size.
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor) { proxy -> analyzeFrame(proxy) } }
                imageAnalysis = analysis
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                try {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis, capture)
                } catch (error: Exception) {
                    // Some devices cannot bind three use cases; scanning still works
                    // without the evidence still.
                    imageCapture = null
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis)
                }
            } catch (error: Exception) {
                binding.qrPrompt.setText(R.string.pan_qr_camera_unavailable)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        val mediaImage = proxy.image
        if (finished || mediaImage == null) {
            proxy.close()
            return
        }
        val image = try {
            InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        } catch (error: Exception) {
            proxy.close()
            return
        }
        try {
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.asSequence()
                        .mapNotNull { it.rawValue?.trim() }
                        .firstOrNull { it.isNotEmpty() }
                    if (value != null) captureAndFinish(value)
                }
                .addOnCompleteListener { proxy.close() }
        } catch (error: Exception) {
            proxy.close()
        }
    }

    /**
     * Saves a still of the moment the QR was found so the backend can decode it
     * and so the image is available for selfie matching. Falls back to the QR
     * text alone if capture fails.
     */
    private fun captureAndFinish(value: String) {
        if (finished) return
        finished = true
        imageAnalysis?.clearAnalyzer()
        val capture = imageCapture
        if (capture == null) {
            finishWithText(value)
            return
        }
        val file = File.createTempFile("askvocate_qr_", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        try {
            capture.takePicture(options, ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        setResult(RESULT_OK, Intent()
                            .putExtra(EXTRA_QR_TEXT, value)
                            .putExtra(EXTRA_IMAGE_PATH, file.absolutePath))
                        finish()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        finishWithText(value)
                    }
                })
        } catch (error: Exception) {
            file.delete()
            finishWithText(value)
        }
    }

    private fun finishWithText(value: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_QR_TEXT, value))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        scanner.close()
        analysisExecutor.shutdown()
    }

    companion object {
        const val EXTRA_QR_TEXT = "qr_text"
        const val EXTRA_IMAGE_PATH = "qr_image_path"
        const val EXTRA_PROMPT = "qr_prompt"
    }
}
