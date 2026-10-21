package com.nexvary.recorder.ui

import android.view.View
import android.widget.LinearLayout
import com.nexvary.recorder.delivery.DeliveryUi

object SettingsSections {
    fun add(root: LinearLayout, title: Int, id: Int = View.NO_ID): LinearLayout {
        val panel = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val button = DeliveryUi.button(root, title, id) {
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        androidx.core.view.ViewCompat.setAccessibilityHeading(button, true)
        root.addView(panel)
        return panel
    }
}
