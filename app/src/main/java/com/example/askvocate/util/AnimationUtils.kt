package com.example.askvocate.util

import android.view.View
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R

object AnimationUtils {
    
    fun animateViewSlideUp(view: View, delayOffset: Long = 0) {
        view.visibility = View.INVISIBLE
        view.postDelayed({
            view.visibility = View.VISIBLE
            val anim = AnimationUtils.loadAnimation(view.context, R.anim.slide_up)
            view.startAnimation(anim)
        }, delayOffset)
    }
    
    fun runLayoutAnimation(recyclerView: RecyclerView) {
        val context = recyclerView.context
        val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(context, R.anim.layout_item_fall_down)
        
        recyclerView.layoutAnimation = controller
        recyclerView.adapter?.notifyDataSetChanged()
        recyclerView.scheduleLayoutAnimation()
    }
}
