package com.nexvary.recorder.delivery

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import java.security.MessageDigest
import javax.net.ssl.SSLException

/** SMTP has no exactly-once guarantee: uncertain DATA outcomes require an explicit manual retry. */
class MailDispatcher(private val queue: MailQueue, private val sender: RecordingMailTransport) {
    fun deliver(id: String, profile: SmtpProfile?): MailQueue.State? {
        val job = queue.claim(id) ?: return queue.get(id)?.state
        var dataStarted = false
        val state = try {
            if (profile == null || !profile.valid()) MailQueue.State.CONFIG
            else {
                val file = queue.attachment(id)
                if (!file.isFile) MailQueue.State.MISSING
                else {
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == job.digest)
                    sender.send(profile, job, file) { dataStarted = true }
                    MailQueue.State.SENT
                }
            }
        } catch (e: Exception) {
            when {
                e is MessageTooLarge -> MailQueue.State.LIMIT
                causes(e).any { it is AuthenticationFailedException } -> MailQueue.State.AUTH
                e is SMTPSendFailedException && e.returnCode >= 400 -> MailQueue.State.FAILED
                causes(e).any { it is org.eclipse.angus.mail.smtp.SMTPAddressFailedException && it.returnCode >= 400 } -> MailQueue.State.FAILED
                dataStarted -> MailQueue.State.UNCERTAIN
                causes(e).any { it is SSLException } -> MailQueue.State.TLS
                e is SendFailedException -> MailQueue.State.FAILED
                e is IllegalStateException -> MailQueue.State.MISSING
                job.attempts < 3 -> MailQueue.State.RETRY
                else -> MailQueue.State.FAILED
            }
        } finally { sender.close() }
        queue.finish(id, state)
        return state
    }
    private fun causes(error: Throwable): List<Throwable> {
        val result = mutableListOf<Throwable>(); var next: Throwable? = error
        while (next != null && result.none { it === next }) {
            result.add(next)
            next = next.cause ?: (next as? MessagingException)?.nextException
        }
        return result
    }
}
