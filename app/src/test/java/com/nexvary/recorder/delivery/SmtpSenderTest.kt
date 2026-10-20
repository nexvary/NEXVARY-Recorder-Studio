package com.nexvary.recorder.delivery

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** Only loopback is used. This committed key/certificate is PUBLIC TEST MATERIAL, never a server credential. */
class SmtpSenderTest {
    private class Server(val direct: Boolean, val offerTls: Boolean = true, val size: Long = 25_000_000) : AutoCloseable {
        val ssl: SSLContext
        val factory: SSLSocketFactory
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port get() = listener.localPort
        @Volatile var received = ByteArray(0)
        @Volatile var authenticated = false
        @Volatile var dataCommands = 0
        private val pool = Executors.newSingleThreadExecutor()
        private val task: java.util.concurrent.Future<*>
        init {
            val ks = KeyStore.getInstance("PKCS12")
            val key = Base64.getMimeDecoder().decode(SmtpSenderTest::class.java.getResourceAsStream("/smtp-test-keystore.b64")!!.readBytes())
            ks.load(ByteArrayInputStream(key), "test-only-password".toCharArray())
            val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, "test-only-password".toCharArray()) }
            val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
            factory = ssl.socketFactory
            listener.soTimeout = 15_000
            task = pool.submit { runCatching { listener.accept().use { run(it) } } }
        }
        private fun secure(raw: Socket): Socket = (ssl.socketFactory.createSocket(raw, "localhost", port, true) as SSLSocket).apply {
            useClientMode = false; soTimeout = 15_000; startHandshake()
        }
        private fun run(raw: Socket) {
            raw.soTimeout = 15_000
            var socket = if (direct) secure(raw) else raw
            var input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            var out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            fun say(s: String) { out.write(s + "\r\n"); out.flush() }
            say("220 localhost test-only")
            var auth = 0
            while (true) {
                val line = input.readLine() ?: break
                when {
                    line.startsWith("EHLO") -> {
                        say("250-localhost")
                        if (!direct && socket === raw && offerTls) say("250-STARTTLS")
                        say("250-AUTH LOGIN PLAIN"); say("250 SIZE $size")
                    }
                    line == "STARTTLS" -> {
                        say("220 Ready")
                        socket = secure(raw); input = socket.getInputStream().bufferedReader(Charsets.US_ASCII); out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                    }
                    line.startsWith("AUTH LOGIN") -> {
                        check(socket is SSLSocket)
                        val initial = line.removePrefix("AUTH LOGIN").trim()
                        if (initial.isNotEmpty()) { check(String(Base64.getDecoder().decode(initial)) == "fixture-user"); auth = 2; say("334 UGFzc3dvcmQ6") }
                        else { auth = 1; say("334 VXNlcm5hbWU6") }
                    }
                    auth == 1 -> { check(String(Base64.getDecoder().decode(line)) == "fixture-user"); auth = 2; say("334 UGFzc3dvcmQ6") }
                    auth == 2 -> { check(String(Base64.getDecoder().decode(line)) == "fixture-password"); authenticated = true; auth = 0; say("235 Authenticated") }
                    line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:") -> { check(authenticated); say("250 OK") }
                    line == "DATA" -> {
                        check(authenticated); dataCommands++; say("354 End with dot")
                        val message = ByteArrayOutputStream()
                        while (true) { val part = input.readLine() ?: error("Dropped DATA"); if (part == ".") break
                            message.write((part.removePrefix(if (part.startsWith("..")) "." else "") + "\r\n").toByteArray(Charsets.US_ASCII)) }
                        received = message.toByteArray(); say("250 Queued by local fixture")
                    }
                    line == "QUIT" -> { say("221 Bye"); break }
                    else -> say("250 OK")
                }
            }
            socket.close()
        }
        override fun close() { listener.close(); runCatching { task.get(16, TimeUnit.SECONDS) }; pool.shutdownNow() }
    }
    private fun profile(server: Server) = SmtpProfile("localhost", server.port, server.direct, "fixture-user", "fixture-password", "sender@example.invalid", 25)
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
            assertTrue(runCatching { SmtpSender("", "").test(profile(server)) }.isFailure)
            assertFalse(server.authenticated); assertEquals(0, server.dataCommands)
        }
    }
    @Test fun missingStartTlsIsNeverDowngradedToPlaintextAuthentication() {
        Server(false, offerTls = false).use { server ->
            assertTrue(runCatching { SmtpSender("", "", server.factory).test(profile(server)) }.isFailure)
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
}
