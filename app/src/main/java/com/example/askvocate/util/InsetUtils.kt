package com.example.askvocate.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Keeps a top app bar below the system status bar when edge-to-edge is enabled. */
fun View.applyStatusBarInset() {
    val originalTopPadding = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = originalTopPadding + statusBarTop)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
