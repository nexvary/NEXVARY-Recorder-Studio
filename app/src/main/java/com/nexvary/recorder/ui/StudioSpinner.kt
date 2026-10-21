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
        adapter = object : ArrayAdapter<String>(root.context, R.layout.view_spinner_option, labels) {
            init { setDropDownViewResource(R.layout.view_spinner_option) }
            private fun style(view: View) = (view as TextView).apply {
                setTextColor(GREEN)
                setTag(R.id.studioStyled, true)
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) = style(super.getView(position, convertView, parent))
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) = style(super.getDropDownView(position, convertView, parent))
        }
        setPopupBackgroundResource(R.drawable.bg_metal_card)
        setSelection(selected.coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
        root.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
}
