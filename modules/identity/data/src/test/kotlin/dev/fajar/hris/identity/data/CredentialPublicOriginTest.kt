package dev.fajar.hris.identity.data

import dev.fajar.hris.identity.data.di.credentialPublicOrigin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CredentialPublicOriginTest {
    @Test
    fun onlyTrustedAbsoluteOriginsAreAcceptedWithoutEmbeddedCredentialsOrPaths() {
        assertEquals(
            "https://hris.example.test",
            credentialPublicOrigin("https://hris.example.test/", false),
        )
        assertEquals("http://127.0.0.1:5173", credentialPublicOrigin("http://127.0.0.1:5173", true))
        for (value in
            listOf(
                "http://hris.example.test",
                "https://user:secret@hris.example.test",
                "https://hris.example.test/redirect",
                "https://hris.example.test?to=elsewhere",
                "https://hris.example.test#fragment",
                "javascript:alert(1)",
            )) assertThrows(IllegalArgumentException::class.java) {
            credentialPublicOrigin(value, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            credentialPublicOrigin("http://127.0.0.1:5173", false)
        }
    }
}
