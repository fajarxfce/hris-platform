package dev.fajar.hris.identity.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.firebase.datasources.*
import dev.fajar.hris.identity.data.firebase.repositories.VerifiedFirebaseIdentityRepository
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirebaseTokenVerificationTest {
    private val source = JcaFirebaseTokenDataSource()
    private var reads = 0
    private val repository =
        VerifiedFirebaseIdentityRepository(
            FirebaseKeyDataSource {
                reads++
                mapOf("test-key" to key.public as RSAPublicKey)
            },
            source,
        )
    private val header = """{"alg":"RS256","kid":"test-key","typ":"JWT"}"""
    private val claims =
        """{"iss":"https://securetoken.google.com/cmp-fajar","sub":"test-uid","aud":"cmp-fajar","iat":1900000000,"exp":1900003600,"auth_time":1900000000,"firebase":{"sign_in_provider":"password"}}"""

    @Test
    fun aRealRsaSignatureMapsOnlyVerifiedTypedFirebaseClaims() {
        val result =
            assertInstanceOf(Result.Success::class.java, repository.verify(sign(header, claims)))
        val identity = result.value as dev.fajar.hris.identity.domain.entities.FirebaseIdentity
        assertEquals("test-uid", identity.subject)
        assertEquals("cmp-fajar", identity.audience)
        assertEquals(1900000000L, identity.authenticatedAt.epochSecond)
        assertEquals("password", identity.signInProvider)
        assertNull(identity.tenantId)
        assertEquals(1, reads)
    }

    @Test
    fun algorithmKeyUrlsDuplicateHeaderFieldsAndOversizedInputsFailBeforeKeyAcquisition() {
        for (badHeader in
            listOf(
                header.replace("RS256", "HS256"),
                """{"alg":"none","kid":"test-key"}""",
                header.dropLast(1) + ",\"jku\":\"https://untrusted.invalid\"}",
                header.dropLast(1) + ",\"crit\":[\"other\"]}",
                header.dropLast(1) + ",\"alg\":\"RS256\"}",
                header + "{}",
            )) assertRejected(sign(badHeader, claims))
        assertRejected("a".repeat(8193))
        assertRejected(sign(header, claims) + ".extra")
        assertEquals(0, reads)
    }

    @Test
    fun unknownKeysTamperingAndMalformedSignedClaimsNeverProduceAnIdentity() {
        assertRejected(sign(header.replace("test-key", "unknown-key"), claims))
        val valid = sign(header, claims)
        val changed = valid.split('.').toMutableList()
        changed[1] = encode(claims.replace("test-uid", "other-uid").toByteArray())
        assertRejected(changed.joinToString("."))
        for (payload in
            listOf(
                claims.replace("\"aud\":\"cmp-fajar\"", "\"aud\":[\"cmp-fajar\"]"),
                claims.replace("\"iat\":1900000000", "\"iat\":1900000000.5"),
                claims.replace("\"iat\":1900000000", "\"iat\":9999999999999999999999999"),
                claims.dropLast(1) + ",\"sub\":\"second-subject\"}",
                claims + "{}",
            )) assertRejected(sign(header, payload))
    }

    @Test
    fun unavailableKeysAndInterruptionKeepTheirTechnicalClassification() {
        val unavailable =
            VerifiedFirebaseIdentityRepository(
                FirebaseKeyDataSource { throw IOException("private diagnostics") },
                source,
            )
        val failure =
            assertInstanceOf(Result.Failed::class.java, unavailable.verify(sign(header, claims)))
                .failure
        assertEquals(FailureKind.UNAVAILABLE, failure.kind)
        assertEquals("firebase_sign_in_unavailable", failure.code)
        val interrupted =
            VerifiedFirebaseIdentityRepository(
                FirebaseKeyDataSource { throw InterruptedException() },
                source,
            )
        assertThrows(InterruptedException::class.java) { interrupted.verify(sign(header, claims)) }
    }

    private fun assertRejected(token: String) {
        val failure = assertInstanceOf(Result.Failed::class.java, repository.verify(token)).failure
        assertEquals(FailureKind.UNAUTHENTICATED, failure.kind)
        assertEquals("firebase_sign_in_failed", failure.code)
    }

    private fun sign(header: String, payload: String): String {
        val input = "${encode(header.toByteArray())}.${encode(payload.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(key.private)
        signature.update(input.toByteArray())
        return "$input.${encode(signature.sign())}"
    }

    private fun encode(value: ByteArray) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    companion object {
        private val key =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
