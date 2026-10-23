package com.example.askvocate.ui.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.askvocate.R
import com.example.askvocate.util.applyStatusBarInset

/** Free e-Aadhaar / e-PAN guidance for damaged physical card QRs. */
class DocumentHelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_help)
        findViewById<View>(R.id.help_top_bar).applyStatusBarInset()
        findViewById<View>(R.id.help_back).setOnClickListener { finish() }
        findViewById<View>(R.id.help_aadhaar_site).setOnClickListener {
            open("https://myaadhaar.uidai.gov.in/")
        }
        findViewById<View>(R.id.help_aadhaar_digilocker).setOnClickListener {
            open("https://www.digilocker.gov.in/")
        }
        findViewById<View>(R.id.help_pan_site).setOnClickListener {
            open("https://www.incometax.gov.in/")
        }
        findViewById<View>(R.id.help_pan_digilocker).setOnClickListener {
            open("https://www.digilocker.gov.in/")
        }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                Toast.makeText(this, "No browser found to open this link.", Toast.LENGTH_SHORT).show()
            }
    }
}
