package com.nexvary.recorder.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object UiInsets {
    fun apply(root: View) {
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = systemBars(windowInsets)
            view.setPadding(
                left + bars.left,
                top + bars.top,
                right + bars.right,
                bottom + bars.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun applyWithBottomBar(root: View, bottomBar: View) {
        val rootLeft = root.paddingLeft
        val rootTop = root.paddingTop
        val rootRight = root.paddingRight
        val rootBottom = root.paddingBottom

        val barLeft = bottomBar.paddingLeft
        val barTop = bottomBar.paddingTop
        val barRight = bottomBar.paddingRight
        val barBottom = bottomBar.paddingBottom
        val baseBarHeight = bottomBar.layoutParams.height

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = systemBars(windowInsets)

            view.setPadding(
                rootLeft + bars.left,
                rootTop + bars.top,
                rootRight + bars.right,
                rootBottom
            )

            bottomBar.setPadding(
                barLeft,
                barTop,
                barRight,
                barBottom + bars.bottom
            )

            bottomBar.layoutParams = bottomBar.layoutParams.apply {
                height = baseBarHeight + bars.bottom
            }

            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun systemBars(windowInsets: WindowInsetsCompat): Insets {
        return windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
        )
    }
}
