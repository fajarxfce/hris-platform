package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationPushAdmissionPolicyTest {
    private val now = Instant.parse("2026-11-01T00:00:00Z")
    private val company = UUID.randomUUID()
    private val account =
        Account(
            UUID.randomUUID(),
            "fixture@example.test",
            "Fixture",
            true,
            true,
            0,
            securityVersion = 2,
        )
    private val access = AccountAccess(account, setOf(COLLABORATION_CHAT_PERMISSION), true, true)
    private val parent =
        NativeSession(
            UUID.randomUUID(),
            account.id,
            "Fixture",
            now.minusSeconds(3600),
            now.minusSeconds(3600),
            null,
            2,
            now.plusSeconds(7200),
            now.minusSeconds(1),
            now.minusSeconds(1800),
            null,
            3,
            UUID.randomUUID(),
            null,
        )
    private val device =
        CollaborationDevice(
            UUID.randomUUID(),
            company,
            account.id,
            parent.id,
            CollaborationDeviceState.ACTIVE,
            1,
            now.minusSeconds(3000),
            null,
            CollaborationPlatform.ANDROID,
        )
    private val binding =
        CollaborationPushBinding(
            company,
            account.id,
            device.id,
            parent.id,
            0,
            now.minusSeconds(2800),
            null,
        )
    private val gateway =
        CollaborationGatewaySession(
            UUID.randomUUID(),
            company,
            account.id,
            device.id,
            parent.id,
            CollaborationPlatform.ANDROID,
            10,
            2,
            now.minusSeconds(2900),
            now.minusSeconds(900),
            now.plusSeconds(3600),
            now.minusSeconds(600),
        )
    private val registration =
        NativePushRegistration(
            parent.id,
            account.id,
            PushPlatform.ANDROID,
            true,
            4,
            now.minusSeconds(3400),
            now.minusSeconds(400),
            now.plusSeconds(1800),
        )
    private val effective = EffectiveClientPolicy(null, null)

    private fun target(
        key: CollaborationPushBinding = binding,
        live: AccountAccess? = access,
        session: NativeSession? = parent,
        selected: CollaborationDevice? = device,
        head: CollaborationGatewaySession? = gateway,
        push: NativePushRegistration? = registration,
        policy: EffectiveClientPolicy = effective,
        at: Instant = now,
    ) = collaborationPushTarget(key, live, session, selected, head, push, policy, at)

    private fun ready(value: CollaborationPushTarget = target()): CollaborationPushTarget.Ready {
        assertInstanceOf(CollaborationPushTarget.Ready::class.java, value)
        return value as CollaborationPushTarget.Ready
    }

    private fun policy(value: ClientPolicy, next: Instant? = null) =
        EffectiveClientPolicy(
            CompanyClientPolicyRevision(
                0,
                now.minusSeconds(60),
                value,
                now.minusSeconds(120),
                account.id,
                "Fixture",
            ),
            next,
        )

    @Test
    fun authenticationCanPrecedeNativeSessionCreationAndRemainEligibleForOpaquePush() {
        for (authenticatedAt in
            listOf(
                parent.createdAt.minusNanos(1),
                parent.createdAt.minusSeconds(300),
                parent.createdAt.plusSeconds(1),
            )) {
            val session = parent.copy(authenticatedAt = authenticatedAt)
            assertEquals(registration, ready(target(session = session)).registration)
        }
    }

    @Test
    fun futureAuthenticationAndInvalidCreationRotationStillSuppressPush() {
        for (session in
            listOf(
                parent.copy(authenticatedAt = now.plusNanos(1)),
                parent.copy(createdAt = parent.rotatedAt.plusNanos(1)),
                parent.copy(rotatedAt = now.plusNanos(1)),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(session = session))
    }

    @Test
    fun expiredShortAccessAndMissingMfaAllowOnlyOpaqueHintsWhileInteractiveAdmissionStillDenies() {
        val admitted = ready()
        assertEquals(now.plusSeconds(30), admitted.expiresAt)
        assertEquals(registration, admitted.registration)
        val actor =
            Actor(
                account.id,
                company,
                access.permissions,
                parent.authenticatedAt,
                UUID.randomUUID(),
                credentialVersion = parent.credentialVersion,
            )
        assertInstanceOf(
            Result.Failed::class.java,
            admitCollaborationDevice(
                actor,
                access,
                parent,
                parent.version,
                device,
                now,
                IdentitySecurityPolicy(),
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            admitCollaborationDevice(
                actor,
                access,
                parent.copy(accessExpiresAt = now.plusSeconds(60)),
                parent.version,
                device,
                now,
                IdentitySecurityPolicy(),
            ),
        )
    }

    @Test
    fun parentAndDeviceRetirementArePermanentButTemporaryAccessLossIsNot() {
        for (session in
            listOf(
                null,
                parent.copy(revokedAt = now),
                parent.copy(expiresAt = now),
                parent.copy(credentialVersion = 1),
            )) assertEquals(CollaborationPushTarget.Retired, target(session = session))
        for (selected in
            listOf(
                null,
                device.copy(state = CollaborationDeviceState.REVOKED),
                device.copy(revokedAt = now),
            )) assertEquals(CollaborationPushTarget.Retired, target(selected = selected))
        for (live in
            listOf(
                null,
                access.copy(account = account.copy(active = false)),
                access.copy(companyActive = false),
                access.copy(membershipActive = false),
                access.copy(permissions = emptySet()),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(live = live))
        ready(target())
    }

    @Test
    fun inactiveExpiredOrReplacedPushTargetsSuppressWithoutRetiringTheStableIdentity() {
        for (push in
            listOf(
                null,
                registration.copy(enabled = false),
                registration.copy(expiresAt = now),
                registration.copy(version = 10000),
                registration.copy(version = -1),
                registration.copy(platform = PushPlatform.IOS),
                registration.copy(accountId = UUID.randomUUID()),
                registration.copy(sessionId = UUID.randomUUID()),
                registration.copy(registeredAt = parent.createdAt.minusSeconds(1)),
                registration.copy(updatedAt = now.plusSeconds(1)),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(push = push))
        val rotated = registration.copy(version = 5, updatedAt = now)
        assertEquals(binding, ready(target(push = rotated)).binding)
        assertEquals(rotated, ready(target(push = rotated)).registration)
    }

    @Test
    fun foreignOrInconsistentProjectionsNeverRejectAnotherOwnersValidPusher() {
        assertEquals(
            CollaborationPushTarget.Suppressed,
            target(live = access.copy(account = account.copy(id = UUID.randomUUID()))),
        )
        for (session in
            listOf(
                parent.copy(id = UUID.randomUUID()),
                parent.copy(accountId = UUID.randomUUID()),
                parent.copy(credentialVersion = 3),
                parent.copy(rotatedAt = now.plusSeconds(1)),
                parent.copy(version = -1),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(session = session))
        for (selected in
            listOf(
                device.copy(id = UUID.randomUUID()),
                device.copy(companyId = UUID.randomUUID()),
                device.copy(accountId = UUID.randomUUID()),
                device.copy(nativeSessionId = UUID.randomUUID()),
                device.copy(createdAt = now.plusSeconds(1)),
                device.copy(version = -1),
                device.copy(state = CollaborationDeviceState.PROVISIONING),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(selected = selected))
    }

    @Test
    fun pendingPublicationIsAllowedButMalformedBindingsAndGatewayHeadsAreSuppressed() {
        ready()
        ready(target(key = binding.copy(version = 1, publishedAt = now)))
        for (key in
            listOf(
                binding.copy(version = 1),
                binding.copy(publishedAt = now),
                binding.copy(version = -1),
                binding.copy(reservedAt = now.plusSeconds(1)),
                binding.copy(version = 1, publishedAt = binding.reservedAt.minusSeconds(1)),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(key = key))
        for (head in
            listOf(
                null,
                gateway.copy(companyId = UUID.randomUUID()),
                gateway.copy(accountId = UUID.randomUUID()),
                gateway.copy(deviceId = UUID.randomUUID()),
                gateway.copy(nativeSessionId = UUID.randomUUID()),
                gateway.copy(platform = CollaborationPlatform.IOS),
                gateway.copy(expiresAt = now),
                gateway.copy(revokedAt = now),
                gateway.copy(clientBuild = 0),
                gateway.copy(version = -1),
                gateway.copy(rotatedAt = now.plusSeconds(1)),
            )) assertEquals(CollaborationPushTarget.Suppressed, target(head = head))
    }

    @Test
    fun platformClientAvailabilityAndScheduledChangesBoundHints() {
        assertEquals(
            CollaborationPushTarget.Suppressed,
            target(
                policy = policy(ClientPolicy(disabledModules = setOf(CompanyModule.COLLABORATION)))
            ),
        )
        assertEquals(
            CollaborationPushTarget.Suppressed,
            target(policy = policy(ClientPolicy(minimumBuilds = MinimumClientBuilds(android = 11)))),
        )
        assertEquals(
            CollaborationPushTarget.Suppressed,
            target(policy = EffectiveClientPolicy(null, now)),
        )
        assertEquals(
            now.plusSeconds(3),
            ready(target(policy = EffectiveClientPolicy(null, now.plusSeconds(3)))).expiresAt,
        )
        assertEquals(
            now.plusSeconds(2),
            ready(target(push = registration.copy(expiresAt = now.plusSeconds(2)))).expiresAt,
        )
        assertEquals(
            now.plusSeconds(1),
            ready(target(session = parent.copy(expiresAt = now.plusSeconds(1)))).expiresAt,
        )
        val ios = device.copy(platform = CollaborationPlatform.IOS)
        ready(
            target(
                selected = ios,
                head = gateway.copy(platform = ios.platform),
                push = registration.copy(platform = PushPlatform.IOS),
            )
        )
    }

    @Test
    fun lateRegistrationRotationDisablementAndAccessLossBeatPreparedWork() {
        val prepared = ready()
        for (current in
            listOf(
                target(push = registration.copy(version = 5, updatedAt = now)),
                target(push = registration.copy(enabled = false)),
                target(live = access.copy(permissions = emptySet())),
                target(session = parent.copy(version = 4)),
                target(head = gateway.copy(version = 3)),
                target(selected = device.copy(version = 2)),
            )) assertFalse(sameCollaborationPushTarget(prepared, current, now))
        assertTrue(
            sameCollaborationPushTarget(
                prepared,
                target(at = now.plusSeconds(1)),
                now.plusSeconds(1),
            )
        )
        assertTrue(
            sameCollaborationPushTarget(
                prepared,
                target(key = binding.copy(version = 1, publishedAt = now)),
                now,
            )
        )
        assertFalse(
            sameCollaborationPushTarget(
                prepared,
                target(at = now.plusSeconds(30)),
                now.plusSeconds(30),
            )
        )
        assertFalse(sameCollaborationPushTarget(prepared, prepared, now.minusSeconds(1)))
        assertFalse(
            sameCollaborationPushTarget(
                prepared,
                ready(target()).copy(observedAt = now.minusSeconds(1)),
                now,
            )
        )
    }

    @Test
    fun onlyAnExactOwnedDirectRoomCanSupplyAnEventHint() {
        val event = CollaborationPushEvent("!room:fixture.invalid", "$" + "event")
        val peer = UUID.randomUUID()
        val room =
            CollaborationOwnedRoom(
                company,
                UUID.randomUUID(),
                account.id,
                peer,
                account.id,
                "fixture.invalid",
                event.roomId,
            )
        assertTrue(collaborationPushRoomOwned(binding, event, room, "fixture.invalid"))
        for (value in
            listOf(
                null,
                room.copy(companyId = UUID.randomUUID()),
                room.copy(serverName = "other.invalid"),
                room.copy(roomId = "!other:fixture.invalid"),
                room.copy(firstAccountId = UUID.randomUUID()),
                room.copy(secondAccountId = account.id),
                room.copy(creatorAccountId = UUID.randomUUID()),
            )) assertFalse(collaborationPushRoomOwned(binding, event, value, "fixture.invalid"))
        assertFalse(
            collaborationPushRoomOwned(
                binding,
                event.copy(eventId = "event"),
                room,
                "fixture.invalid",
            )
        )
        assertFalse(
            collaborationPushRoomOwned(
                binding,
                event.copy(eventId = "$" + "a".repeat(255)),
                room,
                "fixture.invalid",
            )
        )
    }

    @Test
    fun hintDiagnosticsAndCallerMutationCannotExposeOrReplaceCapabilities() {
        val key = CollaborationPushKey("hriscp1_" + "a".repeat(64))
        val keys = mutableListOf(key)
        val event = CollaborationPushEvent("!private:fixture.invalid", "$" + "private")
        val hint = CollaborationPushHint(event, keys)
        keys.clear()
        assertEquals(1, hint.keys.size)
        for (value in
            listOf(key.toString(), hint.toString(), event.toString(), ready().toString())) {
            assertFalse(value.contains(key.value))
            assertFalse(value.contains(event.roomId))
            assertFalse(value.contains(account.id.toString()))
        }
    }
}
