package com.nexvary.recorder.delivery

import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** Loopback-only SMTP fixture. Keys and certificates are PUBLIC TEST MATERIAL. */
class LocalSmtpFixture(privateKey: ByteArray, certificate: ByteArray, val direct: Boolean, val offerTls: Boolean = true, val size: Long = 25_000_000) : AutoCloseable {
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
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                val key = java.security.KeyFactory.getInstance("RSA").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(privateKey)))
                val cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.getMimeDecoder().decode(certificate)))
                setKeyEntry("fixture", key, "test-only-password".toCharArray(), arrayOf(cert))
            }
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
