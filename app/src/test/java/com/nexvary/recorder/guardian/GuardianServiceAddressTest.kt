package com.nexvary.recorder.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianServiceAddressTest {
    @Test fun acceptsAStableHttpsOriginWithoutRequiringAnAwsHostname() {
        assertEquals("https://safety.school.example", GuardianServiceAddress.normalize(" https://safety.school.example/ "))
        assertEquals("https://guardian.example:8443", GuardianServiceAddress.normalize("https://guardian.example:8443"))
    }
    @Test fun rejectsAddressesThatCouldExposeCredentialsOrChangeTheApiRoot() {
        for (value in listOf("http://guardian.example", "https://user:secret@guardian.example", "https://guardian.example/path",
            "https://guardian.example?secret=value", "https://guardian.example#fragment", "https://guardian.example:99999", "not-a-service")) {
            assertTrue(value, runCatching { GuardianServiceAddress.normalize(value) }.isFailure)
        }
    }
}
