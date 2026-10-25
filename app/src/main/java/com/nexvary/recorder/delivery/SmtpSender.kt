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
            setProperty("mail.smtp.sendpartial", "false")
            setProperty("mail.smtp.reportsuccess", "false")
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
    /** Structured safety alerts share the same mandatory TLS/auth transport; never request SMS. */
    fun sendAlert(profile: SmtpProfile, id: String, recipient: String, payload: String, beforeData: () -> Unit) {
        require(DeliveryPolicy.email(recipient)); java.util.UUID.fromString(id)
        val bytes = payload.toByteArray(Charsets.UTF_8)
        if (!DeliveryPolicy.fits(bytes.size.toLong()*2,profile.limitMiB)) throw MessageTooLarge()
        val session = session(profile)
        val message = object : MimeMessage(session) {
            override fun updateMessageID() { setHeader("Message-ID", "<$id@safety.nexvary.invalid>") }
        }.apply {
            setFrom(InternetAddress(profile.from,true)); setRecipient(Message.RecipientType.TO,InternetAddress(recipient,true))
            setSubject((if(org.json.JSONObject(payload).optString("eventType")=="TEST_ALERT") "TEST ALERT — " else "")+subject,"UTF-8"); sentDate = Date()
            setContent(MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText(body + "\n\n" + payload,"UTF-8") })
                addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(jakarta.mail.util.ByteArrayDataSource(bytes,"application/json; charset=UTF-8"))
                    fileName = "emergency-event.json"; disposition = "attachment"
                })
            }); saveChanges()
        }
        try {
            val connection = session.getTransport("smtp"); transport = connection
            connection.connect(profile.host,profile.port,profile.username,profile.password)
            val limit = (connection as? SMTPTransport)?.getExtensionParameter("SIZE")?.trim()?.toLongOrNull()
            if(limit != null && limit > 0 && DeliveryPolicy.messageBytes(bytes.size.toLong()*2) > limit) throw MessageTooLarge()
            beforeData(); connection.sendMessage(message,message.allRecipients)
        } finally { close() }
    }
    override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) {
        val recipients = requireNotNull(MailRecipients.parse(job.recipient)).map { InternetAddress(it, true) }.toTypedArray()
        require(file.isFile && file.length() > 0)
        if (!DeliveryPolicy.fits(file.length(), profile.limitMiB)) throw MessageTooLarge()
        val session = session(profile)
        val message = object : MimeMessage(session) {
            override fun updateMessageID() { setHeader("Message-ID", "<${job.id}@recordings.nexvary.invalid>") }
        }.apply {
            setFrom(InternetAddress(profile.from, true))
            if (recipients.size == 1) setRecipient(Message.RecipientType.TO, recipients.single())
            else {
                setRecipients(Message.RecipientType.BCC, recipients)
                setHeader("To", "undisclosed-recipients:;")
            }
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
            connection.sendMessage(message, recipients)
            // sendMessage returning means the SMTP server accepted DATA, not inbox delivery.
        } finally { close() }
    }
    override fun close() { runCatching { transport?.close() }; transport = null }
}
