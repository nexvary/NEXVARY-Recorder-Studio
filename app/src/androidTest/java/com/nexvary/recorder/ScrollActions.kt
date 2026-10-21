package com.nexvary.recorder

import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher

/** Deterministic immediate reveal for API 35; click actions still require real visibility. */
fun reveal(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isDescendantOfA(isAssignableFrom(ScrollView::class.java))
    override fun getDescription() = "reveal the full target inside its scrolling viewport"
    override fun perform(ui: UiController, view: View) {
        var ancestor = view.parent
        while (ancestor != null && ancestor !is ScrollView) ancestor = ancestor.parent
        val scroll = ancestor as? ScrollView ?: error("No ScrollView ancestor")
        scroll.isSmoothScrollingEnabled = false
        val rect = Rect()
        view.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        scroll.scrollTo(0, (rect.top - scroll.paddingTop).coerceAtLeast(0))
        ui.loopMainThreadUntilIdle()
        check(isDisplayed().matches(view)) { "Target remains outside the viewport" }
    }
}
