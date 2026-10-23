package com.nexvary.recorder.ui

import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.nexvary.recorder.R

/** Bundled realistic raster assets; no icon fonts or emoji. */
object FeatureCards {
    data class Item(val id: Int, val asset: Int, val label: Int, val description: Int, val emergency: Boolean = false, val action: () -> Unit)

    /** Two columns on normal phones; one at large font sizes so labels never clip. */
    fun grid(root: LinearLayout, items: List<Item>) {
        val density = root.resources.displayMetrics.density
        val columns = if (root.resources.configuration.screenWidthDp < 360 || root.resources.configuration.fontScale >= 1.3f) 1 else 2
        for (rowItems in items.chunked(columns)) {
            val row = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (12 * density).toInt() }
            }
            root.addView(row)
            rowItems.forEachIndexed { index, item ->
                val card = LinearLayout(root.context).apply {
                    id = item.id
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumHeight = (170 * density).toInt()
                    setPadding((12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt())
                    setBackgroundResource(if (item.emergency) R.drawable.bg_sos_card else R.drawable.bg_metal_card)
                    val selectable = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
                    foreground = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, selectable.resourceId)
                    addView(ImageView(context).apply {
                        setImageResource(item.asset)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams((64 * density).toInt(), (64 * density).toInt()))
                    addView(TextView(context).apply {
                        setText(item.label); textSize = 17f; gravity = Gravity.CENTER
                        setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, -2))
                    addView(TextView(context).apply {
                        setText(item.description); textSize = 13f; gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, -2))
                    isClickable = true; isFocusable = true
                    contentDescription = context.getString(item.label) + ". " + context.getString(item.description)
                    accessibilityDelegate = object : View.AccessibilityDelegate() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.className = Button::class.java.name
                        }
                    }
                    setOnClickListener { item.action() }
                }
                row.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (index > 0) marginStart = (12 * density).toInt()
                })
            }
            if (rowItems.size < columns) row.addView(View(root.context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = (12 * density).toInt() })
        }
    }

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
