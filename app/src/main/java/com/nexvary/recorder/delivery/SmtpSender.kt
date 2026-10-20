package com.nexvary.recorder.delivery

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.eclipse.angus.mail.smtp.SMTPTransport
import java.io.File
import java.util.Date
import java.util.Properties
import javax.net.ssl.SSLSocketFactory

interface RecordingMailTransport {
    fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit)
    fun close()
}
class MessageTooLarge : Exception()

/** Maintained Jakarta Mail implementation; no plaintext mode or trust-all certificate switch. */
class SmtpSender(private val subject: String, private val body: String,
    private val socketFactory: SSLSocketFactory? = null) : RecordingMailTransport {
    @Volatile private var transport: Transport? = null
    private fun session(profile: SmtpProfile): Session {
        require(profile.valid())
        val props = Properties().apply {
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.auth.mechanisms", "LOGIN PLAIN")
            setProperty("mail.smtp.ssl.enable", profile.directTls.toString())
            setProperty("mail.smtp.starttls.enable", (!profile.directTls).toString())
            setProperty("mail.smtp.starttls.required", (!profile.directTls).toString())
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2")
            setProperty("mail.smtp.connectiontimeout", "20000")
            setProperty("mail.smtp.timeout", "60000")
            setProperty("mail.smtp.writetimeout", "60000")
            setProperty("mail.smtp.localhost", "android.nexvary.invalid")
            setProperty("mail.smtp.quitwait", "false")
            // Authentication traces must never enter Logcat or CI output.
            setProperty("mail.debug", "false")
            if (socketFactory != null) put("mail.smtp.ssl.socketFactory", socketFactory)
        }
        return Session.getInstance(props)
    }
    fun test(profile: SmtpProfile) {
        try {
            val connection = session(profile).getTransport("smtp")
            transport = connection
            connection.connect(profile.host, profile.port, profile.username, profile.password)
        } finally { close() }
    }
    override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) {
        require(DeliveryPolicy.email(job.recipient) && file.isFile && file.length() > 0)
        if (!DeliveryPolicy.fits(file.length(), profile.limitMiB)) throw MessageTooLarge()
        val session = session(profile)
        val message = object : MimeMessage(session) {
            override fun updateMessageID() { setHeader("Message-ID", "<${job.id}@recordings.nexvary.invalid>") }
        }.apply {
            setFrom(InternetAddress(profile.from, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(job.recipient, true))
            setSubject(subject, "UTF-8")
            sentDate = Date(job.created)
            setContent(MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
                addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(file))
                    fileName = job.name
                    setHeader("Content-Type", "video/mp4")
                    disposition = "attachment"
                })
            })
            saveChanges()
        }
        try {
            val connection = session.getTransport("smtp")
            transport = connection
            connection.connect(profile.host, profile.port, profile.username, profile.password)
            val serverLimit = (connection as? SMTPTransport)?.getExtensionParameter("SIZE")?.trim()?.toLongOrNull()
            if (serverLimit != null && serverLimit > 0 && DeliveryPolicy.messageBytes(file.length()) > serverLimit) throw MessageTooLarge()
            beforeData()
            connection.sendMessage(message, message.allRecipients)
            // sendMessage returning means the SMTP server accepted DATA, not inbox delivery.
        } finally { close() }
    }
    override fun close() { runCatching { transport?.close() }; transport = null }
}
