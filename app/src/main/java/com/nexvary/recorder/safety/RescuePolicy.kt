package com.nexvary.recorder.safety

/** Pure validation shared by the editor and tests; short emergency numbers are valid. */
object RescuePolicy {
    fun normalizePhone(value: String): String = buildString {
        for (c in value) {
            when {
                c.isDigit() -> append(Character.digit(c, 10))
                c == '+' -> append(c)
                c == ' ' || c == '-' || c == '(' || c == ')' -> Unit
                else -> append(c)
            }
        }
    }
    fun validPhone(value: String): Boolean = value.matches(Regex("^\\+?[0-9]{2,15}$"))
    fun recentLocation(nowNanos: Long, fixNanos: Long): Boolean =
        fixNanos > 0 && nowNanos >= fixNanos && nowNanos - fixNanos <= 120_000_000_000L
}
