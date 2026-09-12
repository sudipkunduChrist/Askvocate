package com.example.askvocate.util

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.askvocate.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable

private data class ButtonLoadingState(
    val originalText: String,
    val originalIcon: Drawable?
)

/**
 * Utility functions for loading states on MaterialButtons.
 */
fun MaterialButton.showLoading(context: Context, originalText: String? = null, isDarkSpinner: Boolean = false) {
    if (tag !is ButtonLoadingState) {
        tag = ButtonLoadingState(
            originalText = originalText ?: text.toString(),
            originalIcon = icon
        )
    }
    isEnabled = false

    val colorRes = if (isDarkSpinner) R.color.navy_primary else R.color.white
    val spec = CircularProgressIndicatorSpec(context, null, 0, com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall)
    spec.indicatorColors = intArrayOf(ContextCompat.getColor(context, colorRes))
    spec.trackColor = ContextCompat.getColor(context, android.R.color.transparent)

    val progressDrawable = IndeterminateDrawable.createCircularDrawable(context, spec)
    icon = progressDrawable
    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    text = ""
}

fun MaterialButton.hideLoading(originalText: String? = null) {
    isEnabled = true
    val state = tag as? ButtonLoadingState
    if (state != null) {
        text = originalText ?: state.originalText
        icon = state.originalIcon
        tag = null
    } else {
        if (originalText != null) {
            text = originalText
        }
        icon = null
    }
}

