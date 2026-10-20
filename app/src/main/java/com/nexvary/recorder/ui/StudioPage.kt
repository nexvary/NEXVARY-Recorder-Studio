package com.nexvary.recorder.ui

import android.app.Activity
import android.graphics.Color
import android.widget.*
import com.nexvary.recorder.R

object StudioPage {
    fun install(activity: Activity, title: Int): LinearLayout {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad)
        }
        content.addView(Button(activity).apply {
            id = R.id.btnBack; setText(R.string.back); setOnClickListener { activity.finish() }
        })
        content.addView(TextView(activity).apply {
            setText(title); textSize = 24f; setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, pad)
        })
        val scroll = ScrollView(activity).apply {
            setBackgroundResource(R.drawable.bg_piano_gloss); addView(content)
        }
        activity.setContentView(scroll); UiInsets.apply(scroll)
        return content
    }
}
