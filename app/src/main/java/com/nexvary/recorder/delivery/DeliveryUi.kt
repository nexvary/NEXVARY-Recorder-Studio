package com.nexvary.recorder.delivery

import android.content.res.ColorStateList
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import com.nexvary.recorder.R

object DeliveryUi {
    fun text(root: LinearLayout, label: Int): TextView = TextView(root.context).apply {
        setText(label); textSize = 15f; setTextColor(0xFFE2E8F0.toInt())
        val pad = (8 * resources.displayMetrics.density).toInt(); setPadding(0, pad, 0, pad)
        root.addView(this)
    }
    fun field(root: LinearLayout, label: Int, ident: Int, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        text(root, label).labelFor = ident
        return EditText(root.context).apply {
            id = ident; setText(initial); setHint(label); inputType = type; minHeight = (48 * resources.displayMetrics.density).toInt()
            setTextColor(0xFF43FF88.toInt()); setHintTextColor(0xFF9BF6B8.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF43FF88.toInt())
            textCursorDrawable?.setTint(0xFF43FF88.toInt())
            setTag(R.id.studioStyled, true)
            textDirection = View.TEXT_DIRECTION_LTR
            setSingleLine(); root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    fun button(root: LinearLayout, label: Int, ident: Int = View.NO_ID, red: Boolean = false, action: () -> Unit): AppCompatButton = AppCompatButton(root.context).apply {
        id = ident; setText(label); isAllCaps = false
        setTextColor(if (red) 0xFFFFFFFF.toInt() else 0xFF43FF88.toInt())
        ViewCompat.setBackgroundTintList(this, null)
        setBackgroundResource(if (red) R.drawable.bg_sos_button else R.drawable.bg_rescue_contact_card)
        val unit = resources.displayMetrics.density
        minHeight = (52 * unit).toInt(); val pad = (12 * unit).toInt(); setPadding(pad, pad, pad, pad)
        setTag(R.id.studioStyled, true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * unit).toInt() }
        setOnClickListener { action() }; root.addView(this)
    }
    fun section(root: LinearLayout, title: Int): LinearLayout = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        setBackgroundResource(R.drawable.bg_metal_card)
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad })
        text(this, title).apply { textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD) }
    }
}
