package com.nexvary.recorder

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.anything

/** Wait for the actual focused window before Espresso binds a root during async dialog swaps.
 * No action is retried, and a missing/blocked window still fails the test after a bounded wait.
 */
fun awaitUi(matcher: Matcher<View>) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + 20_000
    val focused = object : org.hamcrest.TypeSafeMatcher<View>() {
        override fun describeTo(description: org.hamcrest.Description) { description.appendText("window has focus") }
        override fun matchesSafely(view: View) = view.hasWindowFocus()
    }
    fun contains(view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (matcher.matches(view)) return true
        return view is ViewGroup && (0 until view.childCount).any { contains(view.getChildAt(it)) }
    }
    while (SystemClock.uptimeMillis() < deadline) {
        var ready = false
        instrumentation.runOnMainSync {
            ready = WindowInspector.getGlobalWindowViews().any { it.hasWindowFocus() && contains(it) }
        }
        if (ready) {
            onView(matcher).inRoot(withDecorView(focused)).check(matches(anything()))
            return
        }
        SystemClock.sleep(50)
    }
    throw AssertionError("No focused window containing $matcher within 20 seconds")
}
