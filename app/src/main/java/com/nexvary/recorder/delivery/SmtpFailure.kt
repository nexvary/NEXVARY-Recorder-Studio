package com.nexvary.recorder.delivery

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Only fixed categories reach the UI: server responses can contain account data. */
enum class SmtpFailure {
    DNS, CONNECTION, TIMEOUT, TLS, AUTH, STARTTLS, OTHER;

    companion object {
        fun classify(error: Throwable): SmtpFailure {
            val errors = mutableListOf<Throwable>()
            fun visit(item: Throwable?) {
                if (item == null || errors.any { it === item }) return
                errors.add(item)
                visit(item.cause)
                visit((item as? MessagingException)?.nextException)
            }
            visit(error)
            return when {
                errors.any { it is SSLException || it is java.security.cert.CertificateException } -> TLS
                errors.any { it is AuthenticationFailedException } -> AUTH
                errors.any { it is UnknownHostException } -> DNS
                errors.any { it is SocketTimeoutException } -> TIMEOUT
                errors.any { it is ConnectException } -> CONNECTION
                errors.any { it is MessagingException && it.message?.contains("STARTTLS is required", ignoreCase = true) == true } -> STARTTLS
                else -> OTHER
            }
        }
    }
}
