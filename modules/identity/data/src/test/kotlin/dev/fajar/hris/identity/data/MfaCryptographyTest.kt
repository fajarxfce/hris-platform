package dev.fajar.hris.identity.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceMfaCryptoDataSource
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.bouncycastle.util.encoders.Base32
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MfaCryptographyTest {
    private val first = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val second = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })

    @Test
    fun rfc6238Sha1VectorsMatchSixDigitAuthenticatorCodes() {
        val crypto = JceMfaCryptoDataSource(parseIdentityKeyring("v1", "v1:$first"))
        val secret = Base32.toBase32String("12345678901234567890".toByteArray(Charsets.US_ASCII))
        for ((seconds, code) in
            listOf(
                59L to "287082",
                1111111109L to "081804",
                1111111111L to "050471",
                1234567890L to "005924",
                2000000000L to "279037",
                20000000000L to "353130",
            )) {
            assertEquals(
                seconds / 30,
                crypto.matchCounter(secret, code, Instant.ofEpochSecond(seconds)),
            )
        }
        assertNull(crypto.matchCounter(secret, "not-a-code", Instant.ofEpochSecond(59)))
        assertNull(crypto.matchCounter(secret, "287082", Instant.ofEpochSecond(180)))
    }

    @Test
    fun encryptionBindsTheAccountAndSupportsExplicitKeyRotation() {
        val account = UUID.randomUUID()
        val old = JceMfaCryptoDataSource(parseIdentityKeyring("v1", "v1:$first"))
        val rotated = JceMfaCryptoDataSource(parseIdentityKeyring("v2", "v1:$first,v2:$second"))
        val secret = old.newSecret()
        val encrypted = old.encrypt(account, secret)
        assertNotEquals(encrypted, old.encrypt(account, secret))
        assertFalse(encrypted.contains(secret))
        assertEquals(secret, rotated.decrypt(account, encrypted))
        assertInstanceOf(
            Result.Failed::class.java,
            safeIdentityCall { rotated.decrypt(UUID.randomUUID(), encrypted) },
        )
        val newCiphertext = rotated.encrypt(account, secret)
        assertTrue(newCiphertext.startsWith("v2."))
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "identity_key_unavailable")),
            safeIdentityCall { old.decrypt(account, newCiphertext) },
        )
        val parts = encrypted.split('.').toMutableList()
        val bytes = Base64.getDecoder().decode(parts[2])
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        parts[2] = Base64.getEncoder().encodeToString(bytes)
        assertEquals(
            Result.Failed(Failure(FailureKind.UNEXPECTED, "identity_credential_failed")),
            safeIdentityCall { rotated.decrypt(account, parts.joinToString(".")) },
        )
        assertFalse(parseIdentityKeyring("v1", "v1:$first").toString().contains(first))
    }

    @Test
    fun unavailableOrInvalidKeysNeverUseAFallbackKey() {
        val crypto = JceMfaCryptoDataSource(parseIdentityKeyring("v1", ""))
        assertFalse(crypto.available())
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "identity_key_unavailable")),
            safeIdentityCall { crypto.encrypt(UUID.randomUUID(), crypto.newSecret()) },
        )
        for (invalid in listOf("v1:invalid-value", "v1:AA==", "v1:$first,v1:$first", "v2:$first")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseIdentityKeyring("v1", invalid)
            }
        }
    }
}
