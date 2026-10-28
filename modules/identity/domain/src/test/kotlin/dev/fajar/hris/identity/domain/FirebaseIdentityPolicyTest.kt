package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validateFirebaseIdentity
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirebaseIdentityPolicyTest {
    private val now = Instant.parse("2026-10-28T00:00:00Z")
    private val policy = FirebaseSignInPolicy(true, "cmp-fajar")
    private val identity =
        FirebaseIdentity(
            policy.issuer,
            "firebase-uid",
            policy.projectId,
            now,
            now.plusSeconds(3600),
            now,
            "password",
        )

    @Test
    fun admittedIdentityPreservesItsExactIssuerAndSubjectWithoutDerivingAnHrisAccount() {
        assertEquals(Result.Success(identity), validateFirebaseIdentity(identity, policy, now))
        val boundary =
            identity.copy(subject = "A".repeat(128), authenticatedAt = now.minusSeconds(600))
        assertEquals(Result.Success(boundary), validateFirebaseIdentity(boundary, policy, now))
        assertFalse(identity.toString().contains(identity.subject))
    }

    @Test
    fun wrongIssuerAudienceAndUnusableSubjectsAreIndistinguishableRejections() {
        for (claims in
            listOf(
                identity.copy(issuer = "https://securetoken.google.com/other-project"),
                identity.copy(issuer = policy.issuer + "/"),
                identity.copy(audience = "other-project"),
                identity.copy(subject = ""),
                identity.copy(subject = " "),
                identity.copy(subject = "bad\nsubject"),
                identity.copy(subject = "a".repeat(129)),
                identity.copy(signInProvider = "anonymous"),
                identity.copy(signInProvider = "custom"),
                identity.copy(tenantId = "another-tenant"),
            )) assertRejected(claims)
        assertInstanceOf(
            Result.Failed::class.java,
            validateFirebaseIdentity(identity, policy.copy(enabled = false), now),
        )
    }

    @Test
    fun expiredFutureOldAuthenticationAndLongLivedTokensCannotEstablishANativeSession() {
        for (claims in
            listOf(
                identity.copy(expiresAt = now),
                identity.copy(issuedAt = now.plusSeconds(1)),
                identity.copy(authenticatedAt = now.plusSeconds(1)),
                identity.copy(issuedAt = now.minusSeconds(1)),
                identity.copy(expiresAt = now.plusSeconds(3601)),
                identity.copy(authenticatedAt = now.minusSeconds(601)),
            )) assertRejected(claims)
    }

    @Test
    fun enabledProjectsAndRecentAuthenticationBoundsCannotSelectArbitraryIssuers() {
        for (project in
            listOf(
                "",
                "../other-project",
                "https://other.invalid",
                "UPPER-project",
                "a".repeat(31),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                FirebaseSignInPolicy(true, project)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(recentAuthenticationAge = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(recentAuthenticationAge = Duration.ofHours(2))
        }
    }

    private fun assertRejected(claims: FirebaseIdentity) {
        val failure =
            assertInstanceOf(
                    Result.Failed::class.java,
                    validateFirebaseIdentity(claims, policy, now),
                )
                .failure
        assertEquals(FailureKind.UNAUTHENTICATED, failure.kind)
        assertEquals("firebase_sign_in_failed", failure.code)
    }
}
