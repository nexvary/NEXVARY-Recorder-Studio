package com.nexvary.recorder.guardian

import java.net.URI

/** Same stable HTTPS origin across hosting migrations; credentials are never part of the address. */
object GuardianServiceAddress {
    fun normalize(value: String): String {
        val text = value.trim().trimEnd('/')
        val address = URI(text)
        require(address.scheme == "https" && !address.host.isNullOrBlank() && address.userInfo == null &&
            address.path.isNullOrEmpty() && address.query == null && address.fragment == null &&
            (address.port == -1 || address.port in 1..65535))
        return text
    }
}
