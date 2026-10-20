package com.example.askvocate.ui.documents

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * ZXing capture screen locked to portrait by the manifest.
 *
 * Continuous focus keeps re-focusing while the user moves the card into frame;
 * the dense PAN Secure QR is otherwise only readable at one exact distance.
 */
class PortraitCaptureActivity : CaptureActivity() {
    private lateinit var scannerView: DecoratedBarcodeView

    override fun initializeContent(): DecoratedBarcodeView =
        super.initializeContent().also { scannerView = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CaptureManager replaces CameraSettings after initializeContent(), so this
        // must be applied after super.onCreate() and before onResume() opens the camera.
        scannerView.cameraSettings.setContinuousFocusEnabled(true)
        raisePromptText()
    }

    /** The default prompt sits flush against the bottom edge; lift it clear of the nav bar. */
    private fun raisePromptText() {
        val prompt = scannerView.findViewById<TextView>(
            com.google.zxing.client.android.R.id.zxing_status_view) ?: return
        (prompt.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = (72 * resources.displayMetrics.density).toInt()
            prompt.layoutParams = params
        }
    }
}
