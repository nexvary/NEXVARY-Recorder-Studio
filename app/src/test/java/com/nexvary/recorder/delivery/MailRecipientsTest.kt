package com.nexvary.recorder.delivery

import org.junit.Assert.*
import org.junit.Test

class MailRecipientsTest {
    @Test fun canonicalizesSeparatorsAndRemovesDuplicates() {
        assertEquals(listOf("First@example.invalid", "second@example.invalid"),
            MailRecipients.parse(" First@example.invalid;\r\n second@example.invalid، first@example.invalid\n"))
        assertEquals("one@example.invalid, two@example.invalid", MailRecipients.canonical("one@example.invalid؛two@example.invalid"))
    }
    @Test fun rejectsInvalidMembersHeaderInjectionAndDisplayNames() {
        for (text in listOf("", " , ; \n", "one@example.invalid,broken", "Name <one@example.invalid>",
            "one@example.invalid\r\nBcc: two@example.invalid", "one@example.invalid\rX", "one@example.invalid\u0000")) {
            assertNull("Input must be rejected", MailRecipients.parse(text))
        }
    }
    @Test fun enforcesDistinctRecipientAndInputLimits() {
        assertEquals(10, MailRecipients.parse((1..10).joinToString(",") { "p$it@example.invalid" })!!.size)
        assertNull(MailRecipients.parse((1..11).joinToString(",") { "p$it@example.invalid" }))
        assertNull(MailRecipients.parse("x".repeat(MailRecipients.MAX_INPUT + 1)))
    }
    @Test fun legacySingleMailboxStillWorksAndProfileStaysRedacted() {
        assertEquals(listOf("one@example.invalid"), MailRecipients.parse("one@example.invalid"))
        val p = SmtpProfile("smtp.example.invalid", 587, false, "user", "secret", "from@example.invalid", 25,
            "one@example.invalid, two@example.invalid")
        assertTrue(p.valid()); assertEquals("SmtpProfile(redacted)", p.toString())
        assertFalse(p.copy(cameraRecipients = "bad").valid())
    }
}
