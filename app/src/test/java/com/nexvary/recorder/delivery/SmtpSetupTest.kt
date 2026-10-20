package com.nexvary.recorder.delivery

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class SmtpSetupTest {
    private val profile = SmtpProfile("mail.example.com", 465, true, "account", "test-only", "sender@example.com", 25)

    @Test fun conventionalPortsChooseMatchingSecurityAndCustomPortsRemainManual() {
        assertEquals(true, DeliveryPolicy.conventionalTls(465))
        assertEquals(false, DeliveryPolicy.conventionalTls(587))
        assertNull(DeliveryPolicy.conventionalTls(2525))
        assertEquals(true, DeliveryPolicy.conventionalTls(DeliveryPolicy.digits("٤٦٥").toInt()))
    }
    @Test fun hostRejectsEmbeddedPortsSchemesAndPathsBeforeAttemptingConnection() {
        listOf("mail.example.com", "LOCALHOST", "127.0.0.1").forEach { assertTrue(DeliveryPolicy.validHost(it)) }
        listOf("", "smtp://mail.example.com", "mail.example.com:465", "https://mail.example.com/", "mail.example.com/path", "bad..example.com").forEach {
            assertFalse(it, DeliveryPolicy.validHost(it))
        }
    }
    @Test fun nestedFailuresAreClassifiedWithoutPublishingRawServerResponses() {
        val cases = listOf(
            UnknownHostException("private host") to SmtpFailure.DNS,
            ConnectException("private server") to SmtpFailure.CONNECTION,
            SocketTimeoutException("private account") to SmtpFailure.TIMEOUT,
            SSLHandshakeException("private certificate") to SmtpFailure.TLS,
            AuthenticationFailedException("private response") to SmtpFailure.AUTH,
            MessagingException("STARTTLS is required but host does not support STARTTLS") to SmtpFailure.STARTTLS
        )
        cases.forEach { (error, reason) ->
            val wrapped = MessagingException("hidden server response", error)
            assertEquals(reason, SmtpFailure.classify(wrapped))
            assertFalse(SmtpFailure.classify(wrapped).toString().contains("private"))
        }
        assertEquals(SmtpFailure.TLS, SmtpFailure.classify(AuthenticationFailedException("auth").apply { setNextException(SSLHandshakeException("TLS failed")) }))
    }
    @Test fun successfulTestSavesExactlyTheAuthenticatedProfile() {
        var authenticated: SmtpProfile? = null
        var saved: SmtpProfile? = null
        val result = SmtpSetup({ authenticated = it }, { assertEquals(authenticated, it); saved = it }).testAndSave(profile)
        assertEquals(SmtpSetup.Result.Saved, result); assertEquals(profile, saved)
    }
    @Test fun failedTestDoesNotOverwriteExistingConfigurationAndStorageFailureIsDistinct() {
        var saved = false
        val failure = SmtpSetup({ throw AuthenticationFailedException("private server response") }, { saved = true }).testAndSave(profile)
        assertEquals(SmtpSetup.Result.Failed(SmtpFailure.AUTH), failure); assertFalse(saved)
        assertEquals(SmtpSetup.Result.StorageFailed, SmtpSetup({}, { throw java.io.IOException("local storage") }).testAndSave(profile))
    }
}
