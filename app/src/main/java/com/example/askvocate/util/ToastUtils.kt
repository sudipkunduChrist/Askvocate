package com.example.askvocate.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.askvocate.R

enum class ToastType {
    SUCCESS,
    ERROR,
    INFO
}

object ToastUtils {

    private var activeCustomToastView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Shows an INSTANT custom snackbar/banner toast directly attached to the Activity view tree.
     * Unlike standard Android system Toasts, this has ZERO queueing delay and overrides previous notifications instantly!
     * Notifications are drawn in the Activity so Android does not add the app icon.
     */
    fun showToast(context: Context, message: String, type: ToastType = ToastType.INFO) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showToast(context, message, type) }
            return
        }

        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }

        val rootContent = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootContent == null) {
            return
        }

        // Cancel any active toast and pending dismiss callbacks INSTANTLY
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        activeCustomToastView?.let { oldView ->
            (oldView.parent as? ViewGroup)?.removeView(oldView)
            activeCustomToastView = null
        }

        val toastView = try {
            LayoutInflater.from(activity).inflate(R.layout.layout_custom_toast, rootContent, false)
        } catch (e: Exception) {
            return
        }

        val tvMessage = toastView.findViewById<TextView>(R.id.tv_toast_message)
        val ivIcon = toastView.findViewById<ImageView>(R.id.iv_toast_icon)

        tvMessage.text = message

        val (iconRes, tintColor) = when (type) {
            ToastType.SUCCESS -> Pair(R.drawable.ic_check_circle, ContextCompat.getColor(activity, R.color.success_green))
            ToastType.ERROR -> Pair(R.drawable.ic_close, ContextCompat.getColor(activity, R.color.error_red))
            ToastType.INFO -> Pair(R.drawable.ic_description, ContextCompat.getColor(activity, R.color.amber_accent))
        }

        ivIcon.setImageResource(iconRes)
        ivIcon.setColorFilter(tintColor)

        // Layout params for top-floating banner
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (64 * activity.resources.displayMetrics.density).toInt()
            marginStart = (20 * activity.resources.displayMetrics.density).toInt()
            marginEnd = (20 * activity.resources.displayMetrics.density).toInt()
        }

        toastView.layoutParams = params
        rootContent.addView(toastView)
        activeCustomToastView = toastView

        // Instant fade-in & pop animation
        toastView.alpha = 0f
        toastView.translationY = -40f
        toastView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Auto dismiss after 2.5 seconds
        val runnable = Runnable {
            toastView.animate()
                .alpha(0f)
                .translationY(-30f)
                .setDuration(150)
                .withEndAction {
                    rootContent.removeView(toastView)
                    if (activeCustomToastView == toastView) {
                        activeCustomToastView = null
                    }
                }
                .start()
        }

        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, if (type == ToastType.ERROR) 4000 else 2500)
    }
}

/** Small compatibility adapter for existing Toast call sites. */
class AppToast private constructor(
    private val context: Context,
    private val message: String,
    private val type: ToastType
) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        fun makeText(context: Context, message: CharSequence?, duration: Int,
                     type: ToastType = ToastType.INFO): AppToast =
            AppToast(context, message?.toString().orEmpty(), type)
    }

    fun show() = ToastUtils.showToast(context, message, type)
}

fun Context.showCustomToast(message: String, type: ToastType = ToastType.INFO) {
    ToastUtils.showToast(this, message, type)
}

