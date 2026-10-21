package com.nexvary.recorder.delivery

/** Limits apply to the MIME message, including base64 line wrapping and header allowance. */
object DeliveryPolicy {
    const val MAX_DURATION_MS = 86_400_000L
    fun digits(text: String) = text.trim().map { c -> Character.digit(c, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: c }.joinToString("")
    fun duration(minutes: String): Long? = digits(minutes).toLongOrNull()?.takeIf { it in 1..1440 }?.times(60_000)
    fun conventionalTls(port: Int): Boolean? = when (port) { 465 -> true; 587 -> false; else -> null }
    fun validHost(host: String): Boolean = host.length in 1..253 &&
        host.split('.').all { label -> label.length in 1..63 &&
            Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?").matches(label) }
    fun validDuration(ms: Long) = ms == 0L || ms in 1_000..MAX_DURATION_MS
    fun email(text: String): Boolean = text.length in 3..254 &&
        Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?").matches(text) &&
        !text.contains("..") && text.substringBefore('@').let { it.length <= 64 && !it.startsWith('.') && !it.endsWith('.') } &&
        text.substringAfter('@').split('.').all { it.length in 1..63 && !it.startsWith('-') && !it.endsWith('-') }
    fun messageBytes(bytes: Long): Long {
        require(bytes in 0..(1L shl 40))
        val encoded = ((bytes + 2) / 3) * 4
        return encoded + ((encoded + 75) / 76) * 2 + 16_384
    }
    fun fits(bytes: Long, limitMiB: Int) = limitMiB in 1..500 && messageBytes(bytes) <= limitMiB * 1024L * 1024L
}

data class SmtpProfile(val host: String, val port: Int, val directTls: Boolean,
    val username: String, val password: String, val from: String, val limitMiB: Int) {
    fun valid() = DeliveryPolicy.validHost(host) &&
        port in 1..65535 && username.isNotBlank() && username.length <= 320 &&
        username.none { it == '\r' || it == '\n' } && password.length in 1..4096 &&
        DeliveryPolicy.email(from) && limitMiB in 1..500
    override fun toString() = "SmtpProfile(redacted)"
}
