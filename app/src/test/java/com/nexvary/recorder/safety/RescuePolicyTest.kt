package com.nexvary.recorder.safety

import org.junit.Assert.*
import org.junit.Test

class RescuePolicyTest {
    @Test fun supportsArabicDigitsAndPhoneFormatting() {
        assertEquals("+201065946452", RescuePolicy.normalizePhone("+٢٠ (١٠٦) ٥٩٤-٦٤٥٢"))
        assertTrue(RescuePolicy.validPhone("112"))
        assertTrue(RescuePolicy.validPhone("+201065946452"))
    }
    @Test fun rejectsInjectionAndInvalidPhoneNumbers() {
        listOf("", "+", "+12+34", "112;123", "abc", "1234567890123456").forEach { assertFalse(RescuePolicy.validPhone(it)) }
    }
    @Test fun onlyRecentMonotonicLocationFixesAreAccepted() {
        assertTrue(RescuePolicy.recentLocation(200_000_000_000L, 80_000_000_000L))
        assertFalse(RescuePolicy.recentLocation(200_000_000_001L, 80_000_000_000L))
        assertFalse(RescuePolicy.recentLocation(200L, 201L))
        assertFalse(RescuePolicy.recentLocation(200L, 0L))
    }
}
