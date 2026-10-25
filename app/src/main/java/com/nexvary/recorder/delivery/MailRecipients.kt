package com.nexvary.recorder.delivery

import java.util.Locale

/** Mailboxes only: no display names, groups or user-controlled headers. */
object MailRecipients {
    const val MAX_RECIPIENTS = 10
    const val MAX_INPUT = 4096
    fun parse(text: String): List<String>? {
        if (text.length > MAX_INPUT || text.any { (it.code < 32 && it !in listOf('\n', '\r', '\t')) || it.code == 127 }) return null
        val normalized = text.replace("\r\n", "\n")
        if ('\r' in normalized) return null
        val items = normalized.trim().split(Regex("[,;،؛\\n]+")).map(String::trim).filter(String::isNotEmpty)
        if (items.isEmpty() || items.any { it.isEmpty() || !DeliveryPolicy.email(it) }) return null
        val distinct = items.distinctBy { it.lowercase(Locale.ROOT) }
        return distinct.takeIf { it.size in 1..MAX_RECIPIENTS }
    }
    fun canonical(text: String): String? = parse(text)?.joinToString(", ")
}
