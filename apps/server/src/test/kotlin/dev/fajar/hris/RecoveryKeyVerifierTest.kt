package dev.fajar.hris

import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.recovery.RecoveryKeyVerifier
import java.io.StringReader
import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecoveryKeyVerifierTest {
    private val first = Base64.getEncoder().encodeToString(ByteArray(32) { 17 })
    private val second = Base64.getEncoder().encodeToString(ByteArray(32) { 29 })
    private val old = parseIdentityKeyring("old", "old:$first")
    private val rotated = parseIdentityKeyring("current", "old:$first,current:$second")

    @Test
    fun verifiesStoredEnvelopesWithTheApplicationCipherIncludingRetiredKeys() {
        val purposes =
            listOf(
                "hris:totp:account",
                "hris:native:account:session:operation",
                "credential-mail:account:challenge",
                "hris:push:account:session:3",
            )
        val records =
            purposes
                .mapIndexed { index, purpose ->
                    val encrypted =
                        encryptIdentityEnvelope(
                            if (index % 2 == 0) old else rotated,
                            purpose,
                            "private fixture value",
                            SecureRandom(),
                        )
                    "$purpose\t$encrypted\n"
                }
                .joinToString("")
        assertEquals(4, RecoveryKeyVerifier.verify(rotated, StringReader(records)))
        assertEquals(0, RecoveryKeyVerifier.verify(rotated, StringReader("")))
    }

    @Test
    fun wrongKeyMissingRetiredKeyAndChangedPurposeFailWithoutADecryptionFallback() {
        val purpose = "hris:totp:account"
        val encrypted =
            encryptIdentityEnvelope(old, purpose, "private fixture value", SecureRandom())
        for (keyring in
            listOf(
                parseIdentityKeyring("old", "old:$second"),
                parseIdentityKeyring("current", "current:$second"),
            )) {
            assertThrows(Exception::class.java) {
                RecoveryKeyVerifier.verify(keyring, StringReader("$purpose\t$encrypted\n"))
            }
        }
        assertThrows(Exception::class.java) {
            RecoveryKeyVerifier.verify(rotated, StringReader("hris:totp:another\t$encrypted\n"))
        }
    }

    @Test
    fun boundsMalformedRowsAndPreservesInterruption() {
        for (row in listOf("missing column", "purpose\tinvalid\textra", "x".repeat(4_355))) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryKeyVerifier.verify(rotated, StringReader(row))
            }
        }
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) {
                RecoveryKeyVerifier.verify(rotated, StringReader(""))
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
