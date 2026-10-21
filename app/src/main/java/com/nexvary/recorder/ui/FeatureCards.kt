package com.nexvary.recorder.ui

import android.view.Gravity
import android.widget.*
import com.nexvary.recorder.R

/** Bundled realistic raster assets; no icon fonts or emoji. */
object FeatureCards {
    fun add(root: LinearLayout,asset: Int,label: Int,action: () -> Unit) {
        val density=root.resources.displayMetrics.density
        root.addView(LinearLayout(root.context).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding((16*density).toInt(),(12*density).toInt(),(16*density).toInt(),(12*density).toInt())
            setBackgroundResource(R.drawable.bg_metal_card)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(12*density).toInt() }
            addView(ImageView(context).apply { setImageResource(asset);importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams((56*density).toInt(),(56*density).toInt()))
            addView(TextView(context).apply { setText(label);textSize=18f;setPadding((16*density).toInt(),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
            isClickable=true;isFocusable=true;contentDescription=context.getString(label);setOnClickListener { action() }
        })
    }
}
