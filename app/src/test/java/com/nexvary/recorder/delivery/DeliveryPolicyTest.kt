package com.nexvary.recorder.delivery

import org.junit.Assert.*
import org.junit.Test

class DeliveryPolicyTest {
    @Test fun timerAcceptsArabicPersianDigitsAndRejectsOverflow() {
        assertEquals(600_000L, DeliveryPolicy.duration("١٠")); assertEquals(120_000L, DeliveryPolicy.duration("۲"))
        assertEquals(86_400_000L, DeliveryPolicy.duration("1440"))
        listOf("0", "-1", "1.5", "1441", "999999999999999999999999999").forEach { assertNull(DeliveryPolicy.duration(it)) }
    }
    @Test fun serviceRejectsNegativeAndUnboundedTimers() {
        assertTrue(DeliveryPolicy.validDuration(0)); assertTrue(DeliveryPolicy.validDuration(3_000))
        assertFalse(DeliveryPolicy.validDuration(-1)); assertFalse(DeliveryPolicy.validDuration(999)); assertFalse(DeliveryPolicy.validDuration(86_400_001))
    }
    @Test fun oneRecipientCannotInjectHeadersOrMultipleRecipients() {
        assertTrue(DeliveryPolicy.email("user+recordings@example.com"))
        listOf("a@b.com\r\nBcc: c@d.com", "a@b.com,c@d.com", "Name <a@b.com>", "@", "a..b@c.com").forEach { assertFalse(it, DeliveryPolicy.email(it)) }
    }
    @Test fun attachmentLimitIncludesBase64LineWrappingAndHeaders() {
        assertTrue(DeliveryPolicy.fits(10_000, 1)); assertFalse(DeliveryPolicy.fits(900_000, 1))
        assertTrue(DeliveryPolicy.messageBytes(3_000) > 4_000)
        assertFalse(DeliveryPolicy.fits(10, 0)); assertFalse(DeliveryPolicy.fits(10, 501))
    }
    @Test fun credentialsAreNotIncludedInDiagnosticStringAndInvalidProfilesCannotConnect() {
        val valid = SmtpProfile("smtp.example.com", 587, false, "test", "not-a-real-secret", "from@example.com", 25)
        assertTrue(valid.valid()); assertFalse(valid.toString().contains(valid.password))
        assertFalse(valid.copy(host = "smtp://bad").valid()); assertFalse(valid.copy(password = "").valid())
        assertFalse(valid.copy(port = 0).valid()); assertFalse(valid.copy(from = "bad").valid())
    }
}
