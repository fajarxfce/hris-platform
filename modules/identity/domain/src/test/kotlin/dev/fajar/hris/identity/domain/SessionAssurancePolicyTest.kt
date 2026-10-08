package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionAssurancePolicyTest {
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val account =
        Account(UUID.randomUUID(), "person@example.test", "Person", true, false, 0)
    private val policy = IdentitySecurityPolicy()

    @Test
    fun privilegedAndVoluntarilyEnrolledAccountsNeedAnActualProof() {
        assertTrue(requiresMfa(PermissionCatalog.manager))
        assertTrue(requiresMfa(setOf("a.future.permission")))
        assertFalse(requiresMfa(PermissionCatalog.employee))
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "mfa_setup_required")),
            validateSessionAssurance(account, setOf("people.read"), now, now, policy),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "mfa_required")),
            validateSessionAssurance(
                account.copy(mfaConfigured = true),
                PermissionCatalog.employee,
                null,
                now,
                policy,
            ),
        )
        assertEquals(
            Result.Success(Unit),
            validateSessionAssurance(
                account.copy(mfaConfigured = true),
                PermissionCatalog.manager,
                now,
                now,
                policy,
            ),
        )
    }

    @Test
    fun expirationFutureProofsAndRecentActionBoundariesFailClosed() {
        val enrolled = account.copy(mfaConfigured = true)
        for (at in listOf(now.plusSeconds(1), now.minus(policy.maximumMfaAge))) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateSessionAssurance(enrolled, PermissionCatalog.manager, at, now, policy),
            )
        }
        val actor = Actor(account.id, null, emptySet(), now, UUID.randomUUID(), now, 0)
        assertEquals(
            Result.Success(Unit),
            requireRecentMfa(actor, now.plusSeconds(599), policy.recentAuthenticationAge),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            requireRecentMfa(actor, now.plusSeconds(600), policy.recentAuthenticationAge),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateMfaCredential(
                actor,
                MfaCredential(account.copy(securityVersion = 1), null, null),
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateMfaCredential(actor, MfaCredential(account.copy(active = false), null, null)),
        )
    }
}
