package com.nexvary.recorder.delivery

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.Properties
import java.util.UUID

/** Only loopback is used. This committed key/certificate is PUBLIC TEST MATERIAL, never a server credential. */
class SmtpSenderTest {
    @Test fun multipleRecipientsUsePrivateEnvelopeAndOneUnchangedAttachment() {
        for (direct in listOf(true, false)) Server(direct).use { server ->
            val file = File.createTempFile("smtp-multi", ".mp4").apply { writeBytes(ByteArray(8011) { it.toByte() }) }
            try {
                val job = job().copy(recipient = "one@example.invalid; two@example.invalid; ONE@example.invalid")
                SmtpSender("Recording", "Local only", server.factory).send(profile(server), job, file) { }
                assertEquals(listOf("one@example.invalid", "two@example.invalid"), server.recipients.toList())
                assertEquals(1, server.dataCommands)
                val raw = String(server.received, Charsets.US_ASCII).substringBefore("\r\n\r\n")
                assertFalse(raw.contains("one@example.invalid")); assertFalse(raw.contains("two@example.invalid"))
                val message = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(server.received))
                assertNull(message.getHeader("Bcc"))
                assertArrayEquals(file.readBytes(), (message.content as MimeMultipart).getBodyPart(1).inputStream.readBytes())
            } finally { file.delete() }
        }
    }
    @Test fun rejectedRecipientAbortsBeforeDataForEveryone() {
        LocalSmtpFixture(javaClass.getResourceAsStream("/smtp-test-key.b64")!!.readBytes(),
            javaClass.getResourceAsStream("/smtp-test-cert.b64")!!.readBytes(), true,
            rejectRecipient = "bad@example.invalid").use { server ->
            val file = File.createTempFile("smtp-reject", ".mp4").apply { writeBytes(ByteArray(512)) }
            try {
                val error = runCatching { SmtpSender("", "", server.factory).send(profile(server),
                    job().copy(recipient = "one@example.invalid,bad@example.invalid"), file) { } }.exceptionOrNull()
                assertTrue(error is jakarta.mail.SendFailedException)
                assertEquals(0, server.dataCommands); assertTrue(server.received.isEmpty()); assertTrue(file.exists())
            } finally { file.delete() }
        }
    }
    private fun Server(direct: Boolean, offerTls: Boolean = true, size: Long = 25_000_000) = LocalSmtpFixture(
        javaClass.getResourceAsStream("/smtp-test-key.b64")!!.readBytes(),
        javaClass.getResourceAsStream("/smtp-test-cert.b64")!!.readBytes(), direct, offerTls, size)
    private fun profile(server: LocalSmtpFixture) = SmtpProfile("localhost", server.port, server.direct, "fixture-user", "fixture-password", "sender@example.invalid", 25)
    private fun job() = MailQueue.Job(UUID.randomUUID().toString(), "recording.mp4", "recipient@example.invalid", "", 1_790_000_000_000, MailQueue.State.QUEUED)
    @Test fun directTlsAndStartTlsBothDeliverIdenticalBinaryAttachment() {
        for (direct in listOf(true, false)) Server(direct).use { server ->
            val file = File.createTempFile("smtp-test", ".mp4")
            try {
                val bytes = ByteArray(90_001) { (it % 251).toByte() }; file.writeBytes(bytes)
                val job = job(); var dataStarted = false
                SmtpSender("Recording", "Local test only", server.factory).send(profile(server), job, file) { dataStarted = true }
                assertTrue(dataStarted); assertTrue(server.authenticated); assertEquals(1, server.dataCommands)
                val received = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(server.received))
                assertEquals("recipient@example.invalid", received.allRecipients.single().toString())
                assertEquals("<${job.id}@recordings.nexvary.invalid>", received.messageID)
                val multipart = received.content as MimeMultipart
                assertArrayEquals(bytes, multipart.getBodyPart(1).inputStream.readBytes())
            } finally { file.delete() }
        }
    }
    @Test fun untrustedCertificateIsRejectedBeforeCredentials() {
        Server(true).use { server ->
            val error = runCatching { SmtpSender("", "").test(profile(server)) }.exceptionOrNull()!!
            assertEquals(SmtpFailure.TLS, SmtpFailure.classify(error))
            assertFalse(server.authenticated); assertEquals(0, server.dataCommands)
        }
    }
    @Test fun missingStartTlsIsNeverDowngradedToPlaintextAuthentication() {
        Server(false, offerTls = false).use { server ->
            val error = runCatching { SmtpSender("", "", server.factory).test(profile(server)) }.exceptionOrNull()!!
            assertEquals(SmtpFailure.STARTTLS, SmtpFailure.classify(error))
            assertFalse(server.authenticated)
        }
    }
    @Test fun advertisedServerSizeStopsBeforeDataAndRetainsFile() {
        Server(true, size = 1_024).use { server ->
            val file = File.createTempFile("smtp-size", ".mp4").apply { writeBytes(ByteArray(4_000)) }
            try {
                val error = runCatching { SmtpSender("", "", server.factory).send(profile(server), job(), file) { fail("DATA must not start") } }.exceptionOrNull()
                assertTrue(error is MessageTooLarge); assertEquals(0, server.dataCommands); assertTrue(file.isFile)
            } finally { file.delete() }
        }
    }
    @Test fun successfulConnectionTestAuthenticatesBothModesWithoutSubmittingAnyMessage() {
        for (direct in listOf(true, false)) Server(direct).use { server ->
            var saved = false
            val result = SmtpSetup({ SmtpSender("", "", server.factory).test(it) }, { saved = true }).testAndSave(profile(server))
            assertEquals(SmtpSetup.Result.Saved, result)
            assertTrue(saved); assertTrue(server.authenticated); assertEquals(0, server.dataCommands)
        }
    }
}
