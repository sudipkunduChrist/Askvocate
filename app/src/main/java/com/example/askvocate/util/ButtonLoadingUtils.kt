package com.example.askvocate.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable

/**
 * Utility functions for loading states on MaterialButtons.
 */
fun MaterialButton.showLoading(context: Context, originalText: String? = null) {
    tag = originalText ?: text.toString()
    isEnabled = false

    val spec = CircularProgressIndicatorSpec(context, null, 0, com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall)
    spec.indicatorColors = intArrayOf(ContextCompat.getColor(context, R.color.white))
    spec.trackColor = ContextCompat.getColor(context, android.R.color.transparent)

    val progressDrawable = IndeterminateDrawable.createCircularDrawable(context, spec)
    icon = progressDrawable
    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    text = ""
}

fun MaterialButton.hideLoading(originalText: String? = null) {
    isEnabled = true
    icon = null
    text = originalText ?: (tag as? String ?: "")
}
