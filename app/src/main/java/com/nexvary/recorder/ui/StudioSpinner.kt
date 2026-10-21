package com.nexvary.recorder.ui

import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nexvary.recorder.R

/** Popup rows have their own window: always set their colors explicitly. */
object StudioSpinner {
    val GREEN = 0xFF43FF88.toInt()
    fun add(root: LinearLayout, labels: List<String>, id: Int = View.NO_ID, selected: Int = 0): Spinner = Spinner(root.context).apply {
        this.id = id
        adapter = object : ArrayAdapter<String>(root.context, android.R.layout.simple_spinner_item, labels) {
            private fun row(position: Int, recycled: View?): TextView = (recycled as? TextView ?: TextView(context)).apply {
                text = getItem(position); textSize = 17f
                setTextColor(GREEN); setBackgroundColor(0xFF101614.toInt())
                setTag(R.id.studioStyled, true)
                val dp = resources.displayMetrics.density
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                minHeight = (52 * dp).toInt(); gravity = android.view.Gravity.CENTER_VERTICAL
                textDirection = View.TEXT_DIRECTION_LOCALE
                layoutParams = ViewGroup.LayoutParams(-1, -2)
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) = row(position, convertView)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) = row(position, convertView)
        }
        setPopupBackgroundResource(R.drawable.bg_metal_card)
        setSelection(selected.coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
        root.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
}
