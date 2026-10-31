package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationAdmissionPolicyTest {
    private val now = Instant.parse("2026-10-31T00:00:00Z")
    private val account =
        Account(UUID.randomUUID(), "fixture@example.test", "Fixture", true, false, 0)
    private val actor =
        Actor(
            account.id,
            UUID.randomUUID(),
            setOf(COLLABORATION_CHAT_PERMISSION),
            now.minusSeconds(60),
            UUID.randomUUID(),
            credentialVersion = 0,
        )
    private val access = AccountAccess(account, actor.permissions, true, true)
    private val session =
        NativeSession(
            UUID.randomUUID(),
            account.id,
            "Fictional Android",
            now.minusSeconds(60),
            actor.authenticatedAt,
            null,
            0,
            now.plusSeconds(3600),
            now.plusSeconds(900),
            now.minusSeconds(30),
            null,
            1,
            UUID.randomUUID(),
            null,
        )
    private val device =
        CollaborationDevice(
            UUID.randomUUID(),
            requireNotNull(actor.companyId),
            account.id,
            session.id,
            CollaborationDeviceState.ACTIVE,
            2,
            now.minusSeconds(30),
            null,
        )
    private val security = IdentitySecurityPolicy()

    private fun admit(
        request: Actor = actor,
        live: AccountAccess? = access,
        parent: NativeSession? = session,
        selected: CollaborationDevice? = device,
        at: Instant = now,
        expectedSessionVersion: Long = session.version,
    ) =
        admitCollaborationDevice(
            request,
            live,
            parent,
            expectedSessionVersion,
            selected,
            at,
            security,
        )

    private fun success(result: Result<CollaborationAdmission>): CollaborationAdmission {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    @Test
    fun employeeChatDoesNotAddPrivilegedMfaAndAdmissionIsBoundToOneDevice() {
        assertTrue(COLLABORATION_CHAT_PERMISSION in PermissionCatalog.employee)
        val admitted = success(admit())
        assertEquals(actor.accountId, admitted.accountId)
        assertEquals(actor.companyId, admitted.companyId)
        assertEquals(device.id, admitted.deviceId)
        assertEquals(session.id, admitted.nativeSessionId)
        assertEquals(now.plusSeconds(60), admitted.expiresAt)
    }

    @Test
    fun currentAndCapturedPermissionsMustBothAdmitChat() {
        assertInstanceOf(
            Result.Failed::class.java,
            admit(live = access.copy(permissions = emptySet())),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            admit(request = actor.copy(permissions = emptySet())),
        )
        assertInstanceOf(Result.Failed::class.java, admit(live = null))
    }

    @Test
    fun disabledAccountCompanyOrMembershipCannotAcquireAProviderSession() {
        for (live in
            listOf(
                access.copy(account = account.copy(active = false)),
                access.copy(companyActive = false),
                access.copy(membershipActive = false),
                access.copy(account = account.copy(securityVersion = 1)),
            )) {
            assertInstanceOf(Result.Failed::class.java, admit(live = live))
        }
    }

    @Test
    fun companyAccountAndParentSessionPartitionsCannotBeSubstituted() {
        for (selected in
            listOf(
                device.copy(companyId = UUID.randomUUID()),
                device.copy(accountId = UUID.randomUUID()),
                device.copy(nativeSessionId = UUID.randomUUID()),
            )) {
            assertInstanceOf(Result.Failed::class.java, admit(selected = selected))
        }
        assertInstanceOf(Result.Failed::class.java, admit(request = actor.copy(companyId = null)))
        assertInstanceOf(
            Result.Failed::class.java,
            admit(parent = session.copy(accountId = UUID.randomUUID())),
        )
    }

    @Test
    fun pendingRevokedMissingAndFutureDevicesNeverGainAdmission() {
        for (selected in
            listOf(
                null,
                device.copy(state = CollaborationDeviceState.PROVISIONING),
                device.copy(state = CollaborationDeviceState.REVOKED),
                device.copy(revokedAt = now),
                device.copy(createdAt = now.plusSeconds(1)),
                device.copy(version = -1),
            )) {
            assertInstanceOf(Result.Failed::class.java, admit(selected = selected))
        }
    }

    @Test
    fun expiredRevokedRotatedAndFutureNativeSessionsFailClosed() {
        for (parent in
            listOf(
                null,
                session.copy(revokedAt = now),
                session.copy(accessExpiresAt = now),
                session.copy(expiresAt = now),
                session.copy(version = 2),
                session.copy(credentialVersion = 1),
                session.copy(createdAt = now.plusSeconds(1)),
            )) {
            assertInstanceOf(Result.Failed::class.java, admit(parent = parent))
        }
        assertInstanceOf(Result.Failed::class.java, admit(expectedSessionVersion = -1))
    }

    @Test
    fun nearNativeDeadlinesCannotBeExtendedByAGrant() {
        assertEquals(
            now.plusSeconds(4),
            success(admit(parent = session.copy(accessExpiresAt = now.plusSeconds(4)))).expiresAt,
        )
        assertEquals(
            now.plusSeconds(3),
            success(admit(parent = session.copy(expiresAt = now.plusSeconds(3)))).expiresAt,
        )
    }

    @Test
    fun enrolledAndPrivilegedAccountsRetainMfaAndItsDeadline() {
        val enrolled = access.copy(account = account.copy(mfaConfigured = true))
        assertInstanceOf(Result.Failed::class.java, admit(live = enrolled))
        val proof = now.minus(security.maximumMfaAge).plusSeconds(7)
        val verified = actor.copy(mfaVerifiedAt = proof)
        assertEquals(
            now.plusSeconds(7),
            success(admit(request = verified, live = enrolled)).expiresAt,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            admit(request = verified, live = enrolled, at = now.plusSeconds(7)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            admit(live = access.copy(securityPermissions = setOf("identity.manage"))),
        )
    }

    @Test
    fun exchangeExpiresAtTheExactHrisDeadlineDespiteProviderClockLeeway() {
        val prepared = success(admit())
        for (seconds in listOf(60L, 61L, 119L, 120L)) {
            val at = now.plusSeconds(seconds)
            val current = success(admit(at = at))
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationExchange(prepared, current, at),
            )
        }
        val before = now.plusSeconds(59)
        assertEquals(
            Result.Success(Unit),
            validateCollaborationExchange(prepared, success(admit(at = before)), before),
        )
    }

    @Test
    fun resultsFromAReplacedDeviceOrSessionCannotPublishAfterTheProviderReturns() {
        val prepared = success(admit())
        val current = success(admit(at = now.plusSeconds(1)))
        for (changed in
            listOf(
                current.copy(companyId = UUID.randomUUID()),
                current.copy(accountId = UUID.randomUUID()),
                current.copy(deviceId = UUID.randomUUID()),
                current.copy(deviceVersion = current.deviceVersion + 1),
                current.copy(nativeSessionId = UUID.randomUUID()),
                current.copy(nativeSessionVersion = current.nativeSessionVersion + 1),
                current.copy(credentialVersion = 1),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationExchange(prepared, changed, now.plusSeconds(1)),
            )
        }
    }

    @Test
    fun clockReversalExpiredCurrentAuthorityAndOlderObservationsAreRejected() {
        val prepared = success(admit())
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationExchange(prepared, prepared, now.minusSeconds(1)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationExchange(
                prepared,
                prepared.copy(admittedAt = now.minusSeconds(1)),
                now,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationExchange(prepared, prepared.copy(expiresAt = now), now),
        )
    }
}
