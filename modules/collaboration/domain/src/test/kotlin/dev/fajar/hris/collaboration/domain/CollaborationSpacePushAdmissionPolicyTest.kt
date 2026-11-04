package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpacePushAdmissionPolicyTest {
    private val now = Instant.parse("2026-11-04T19:00:00Z")
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val native = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()
    private val user = "@${collaborationSpaceMatrixLocalpart(space, account)}:matrix.example.test"
    private val device =
        CollaborationSpaceDevice(
            deviceId,
            space,
            account,
            user,
            "HRISG_" + deviceId.toString().replace("-", ""),
            CollaborationSpaceDeviceOrigin.SPACE,
            null,
            null,
            native,
            CollaborationPlatform.ANDROID,
            "Fictional",
            CollaborationDeviceState.ACTIVE,
            1,
            now.minusSeconds(100),
            now.minusSeconds(90),
            null,
        )
    private val binding =
        CollaborationSpacePushBinding(
            space,
            account,
            deviceId,
            native,
            user,
            device.matrixDeviceId,
            0,
            now.minusSeconds(10),
            null,
        )
    private val parent =
        NativeSession(
            native,
            account,
            "Fictional",
            now.minusSeconds(200),
            now.minusSeconds(4000),
            now.minusSeconds(4000),
            2,
            now.plusSeconds(3600),
            now.minusSeconds(60),
            now.minusSeconds(200),
            null,
            3,
            UUID.randomUUID(),
            null,
        )
    private val access =
        AccountAccess(
            Account(account, "fictional@example.test", "Fictional", true, true, 0, 2),
            emptySet(),
            false,
            false,
        )
    private val gateway =
        CollaborationSpaceGatewaySession(
            UUID.randomUUID(),
            space,
            account,
            deviceId,
            native,
            CollaborationPlatform.ANDROID,
            28,
            2,
            now.minusSeconds(80),
            now.minusSeconds(70),
            now.plusSeconds(1800),
            now.minusSeconds(30),
        )
    private val identity =
        CollaborationSpaceIdentity(
            space,
            account,
            "matrix.example.test",
            user,
            CollaborationSpaceIdentityOrigin.SPACE,
            null,
            now.minusSeconds(150),
        )
    private val selection = CollaborationSpaceIdentityInput(space, account, identity, null)
    private val provider =
        CollaborationSpaceProviderState(
            space,
            account,
            deviceId,
            native,
            user,
            device.matrixDeviceId,
            1,
            now.minusSeconds(90),
            now.minusSeconds(80),
            now.minusSeconds(40),
        )
    private val registration =
        NativePushRegistration(
            native,
            account,
            PushPlatform.ANDROID,
            true,
            3,
            now.minusSeconds(90),
            now.minusSeconds(60),
            now.plusSeconds(120),
        )

    private fun target(
        binding: CollaborationSpacePushBinding = this.binding,
        parent: NativeSession? = this.parent,
        access: AccountAccess? = this.access,
        device: CollaborationSpaceDevice? = this.device,
        bound: CollaborationSpaceDevice? = device,
        gateway: CollaborationSpaceGatewaySession? = this.gateway,
        selection: CollaborationSpaceIdentityInput? = this.selection,
        provider: CollaborationSpaceProviderState? = this.provider,
        registration: NativePushRegistration? = this.registration,
        current: UUID? = space,
        policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 28, 28),
        at: Instant = now,
    ) =
        collaborationSpacePushTarget(
            binding,
            access,
            parent,
            device,
            bound,
            gateway,
            registration,
            current,
            selection,
            provider,
            null,
            null,
            null,
            policy,
            "matrix.example.test",
            at,
        )

    @Test
    fun shortAssuranceAndProviderExpiryDoNotStandInForCurrentGroupEligibility() {
        val ready = target() as CollaborationSpacePushTarget.Ready
        assertEquals(now.plusSeconds(30), ready.expiresAt)
        assertEquals(CollaborationSpacePushTarget.Suppressed, target(current = null))
        assertEquals(CollaborationSpacePushTarget.Suppressed, target(current = UUID.randomUUID()))
        assertEquals(
            CollaborationSpacePushTarget.Suppressed,
            target(policy = CollaborationSpacePolicy(false)),
        )
        assertEquals(
            CollaborationSpacePushTarget.Suppressed,
            target(policy = CollaborationSpacePolicy(true, 29, 29)),
        )
        assertEquals(
            CollaborationSpacePushTarget.Suppressed,
            target(access = access.copy(account = access.account.copy(active = false))),
        )
    }

    @Test
    fun permanentRetirementRequiresTheExactOwnerWhileForeignProjectionsOnlySuppress() {
        for (result in
            listOf(
                target(parent = null),
                target(device = null),
                target(parent = parent.copy(revokedAt = now)),
                target(parent = parent.copy(expiresAt = now)),
                target(device = device.copy(state = CollaborationDeviceState.REVOKED)),
                target(access = access.copy(account = access.account.copy(securityVersion = 3))),
            )) assertEquals(CollaborationSpacePushTarget.Retired, result)
        for (result in
            listOf(
                target(parent = parent.copy(accountId = UUID.randomUUID(), revokedAt = now)),
                target(
                    device =
                        device.copy(
                            accountId = UUID.randomUUID(),
                            state = CollaborationDeviceState.REVOKED,
                        )
                ),
                target(
                    access =
                        access.copy(
                            account =
                                access.account.copy(id = UUID.randomUUID(), securityVersion = 3)
                        )
                ),
            )) assertEquals(CollaborationSpacePushTarget.Suppressed, result)
    }

    @Test
    fun malformedOrUnboundMetadataCannotDeliverEvenIfAllIdsLookOwned() {
        for (result in
            listOf(
                target(bound = null),
                target(parent = parent.copy(version = -1)),
                target(parent = parent.copy(rotatedAt = now.plusSeconds(1))),
                target(parent = parent.copy(mfaVerifiedAt = now.plusSeconds(1))),
                target(device = device.copy(version = 1000000)),
                target(device = device.copy(matrixUserId = "@foreign:matrix.example.test")),
                target(device = device.copy(legacyKeyFingerprint = "a".repeat(64))),
                target(selection = selection.copy(selected = null)),
                target(selection = selection.copy(selected = identity.copy(version = 1))),
                target(provider = provider.copy(clearedAt = now)),
                target(provider = provider.copy(matrixDeviceId = "foreign")),
                target(provider = provider.copy(updatedAt = now.plusSeconds(1))),
                target(provider = provider.copy(generation = -1)),
                target(gateway = gateway.copy(expiresAt = now)),
                target(gateway = gateway.copy(revokedAt = now)),
                target(gateway = gateway.copy(nativeSessionId = UUID.randomUUID())),
                target(gateway = gateway.copy(clientBuild = 0)),
                target(registration = registration.copy(enabled = false)),
                target(registration = registration.copy(expiresAt = now)),
                target(registration = registration.copy(version = 10000)),
                target(registration = registration.copy(platform = PushPlatform.IOS)),
            )) assertEquals(CollaborationSpacePushTarget.Suppressed, result)
    }

    @Test
    fun immutablePublishedCapabilityCannotChangeTimestampAndPendingMayAdvanceOnlyOnce() {
        val published = binding.copy(version = 1, publishedAt = now.minusSeconds(5))
        assertTrue(sameCollaborationSpacePushBinding(binding, published))
        assertTrue(sameCollaborationSpacePushBinding(published, published))
        assertFalse(sameCollaborationSpacePushBinding(published, binding))
        assertFalse(
            sameCollaborationSpacePushBinding(
                published,
                published.copy(publishedAt = now.minusSeconds(4)),
            )
        )
        assertFalse(
            sameCollaborationSpacePushBinding(binding, published.copy(deviceId = UUID.randomUUID()))
        )
        assertEquals(
            CollaborationSpacePushTarget.Suppressed,
            target(binding = published.copy(publishedAt = now.plusSeconds(1))),
        )
    }

    @Test
    fun tokenProviderGatewayAndParentRotationsFenceOldExternalResults() {
        val before = target() as CollaborationSpacePushTarget.Ready
        val later = now.plusSeconds(1)
        assertTrue(sameCollaborationSpacePushTarget(before, target(at = later), later))
        for (after in
            listOf(
                target(registration = registration.copy(version = 4), at = later),
                target(provider = provider.copy(generation = 2), at = later),
                target(gateway = gateway.copy(version = 3), at = later),
                target(parent = parent.copy(version = 4), at = later),
                target(current = null, at = later),
                target(at = now.plusSeconds(31)),
            )) assertFalse(sameCollaborationSpacePushTarget(before, after, later))
        assertFalse(sameCollaborationSpacePushTarget(before, before, before.expiresAt))
    }
}
