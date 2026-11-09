package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceReadinessAdmissionPolicyTest {
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
            now.plusSeconds(300),
        )

    private val keys =
        CollaborationSpaceKeyPublication(
            space,
            account,
            deviceId,
            native,
            user,
            device.matrixDeviceId,
            "a".repeat(64),
            1,
            now.minusSeconds(20),
            now.minusSeconds(10),
        )
    private val lease =
        CollaborationSpaceReadinessLease(
            UUID.randomUUID(),
            CollaborationSpaceReadinessChannel.MATRIX,
            space,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            now.plusSeconds(60),
        )
    private val checkpoint =
        CollaborationSpaceReadinessCheckpoint(
            lease,
            7,
            now.minusSeconds(60),
            now.minusSeconds(5),
            now.minusSeconds(60).plusSeconds(86400),
            now.minusSeconds(5),
            1,
            null,
        )
    private val target =
        CollaborationReadinessTarget(CollaborationReadinessPosition(account, deviceId), native)
    private val snapshot =
        CollaborationSpaceReadinessSnapshot(
            space,
            access,
            parent,
            device,
            device,
            gateway,
            selection,
            keys,
            provider,
        )
    private val policy = CollaborationSpacePolicy(true, 28, 28)

    private fun admit(
        value: CollaborationSpaceReadinessSnapshot = snapshot,
        checkpoint: CollaborationSpaceReadinessCheckpoint = this.checkpoint,
        target: CollaborationReadinessTarget = this.target,
        policy: CollaborationSpacePolicy = this.policy,
        at: Instant = now,
    ) =
        collaborationSpaceReadinessAdmission(
            checkpoint,
            target,
            value,
            policy,
            "matrix.example.test",
            at,
        )

    @Test
    fun currentOpaqueWakeNeedsNeitherPushRegistrationNorShortInteractiveFreshness() {
        val ready = requireNotNull(admit())
        assertEquals(now.plusSeconds(20), ready.expiresAt)
        assertEquals(
            CollaborationSpaceHistoryOwner(
                space,
                account,
                deviceId,
                "matrix.example.test",
                user,
                device.matrixDeviceId,
            ),
            ready.owner,
        )
        assertTrue(parent.accessExpiresAt < now && gateway.accessExpiresAt < now)
        assertTrue(
            collaborationSpaceReadinessCredential(
                ready,
                CollaborationSpaceProviderSession(provider, "fixture-only"),
                now,
            )
        )
        val expired = snapshot.copy(provider = provider.copy(expiresAt = now.minusSeconds(40)))
        val admission = requireNotNull(admit(expired))
        assertFalse(
            collaborationSpaceReadinessCredential(
                admission,
                CollaborationSpaceProviderSession(requireNotNull(expired.provider), "fixture-only"),
                now,
            )
        )
        assertFalse(collaborationSpaceReadinessCredential(ready, null, now))
    }

    @Test
    fun targetPublicationUsesCapturedCommitBoundaryRatherThanIssuersEarlierObservationTime() {
        assertTrue(requireNotNull(keys.publishedAt) > checkpoint.publicationCutoff)
        assertNotNull(admit())
        assertNull(
            admit(
                snapshot.copy(keys = keys.copy(publishedAt = checkpoint.capturedAt.plusNanos(1000)))
            )
        )
        assertNull(admit(snapshot.copy(keys = keys.copy(version = 0, publishedAt = null))))
        assertNull(admit(snapshot.copy(keys = keys.copy(fingerprint = "invalid"))))
    }

    @Test
    fun currentScopeParentSecurityAndDeviceCannotBeSubstituted() {
        for (wrong in
            listOf(
                snapshot.copy(currentSpaceId = null),
                snapshot.copy(currentSpaceId = UUID.randomUUID()),
                snapshot.copy(access = null),
                snapshot.copy(access = access.copy(account = access.account.copy(active = false))),
                snapshot.copy(
                    access =
                        access.copy(
                            account =
                                access.account.copy(securityVersion = parent.credentialVersion + 1)
                        )
                ),
                snapshot.copy(parent = null),
                snapshot.copy(parent = parent.copy(accountId = UUID.randomUUID())),
                snapshot.copy(parent = parent.copy(id = UUID.randomUUID())),
                snapshot.copy(parent = parent.copy(revokedAt = now)),
                snapshot.copy(parent = parent.copy(expiresAt = now)),
                snapshot.copy(device = device.copy(spaceId = UUID.randomUUID())),
                snapshot.copy(device = device.copy(accountId = UUID.randomUUID())),
                snapshot.copy(bound = null),
                snapshot.copy(
                    device = device.copy(state = CollaborationDeviceState.REVOKED, revokedAt = now)
                ),
                snapshot.copy(keys = keys.copy(nativeSessionId = UUID.randomUUID())),
                snapshot.copy(
                    keys =
                        keys.copy(
                            matrixDeviceId =
                                "HRISG_${UUID.randomUUID().toString().replace("-", "")}"
                        )
                ),
                snapshot.copy(gateway = gateway.copy(accountId = UUID.randomUUID())),
                snapshot.copy(gateway = gateway.copy(revokedAt = now)),
                snapshot.copy(
                    provider = provider.copy(matrixUserId = "@foreign:matrix.example.test")
                ),
                snapshot.copy(provider = provider.copy(clearedAt = now)),
            )) assertNull(admit(wrong), wrong.toString())
    }

    @Test
    fun leaseCursorRolloutAndNativePlatformMinimumsRemainCurrent() {
        assertNull(admit(checkpoint = checkpoint.copy(lease = lease.copy(until = now))))
        assertNull(admit(at = checkpoint.capturedAt.minusNanos(1)))
        assertNull(admit(checkpoint = checkpoint.copy(after = target.position)))
        assertNull(
            admit(
                target =
                    target.copy(
                        position = target.position.copy(accountId = lease.publisherAccountId)
                    )
            )
        )
        assertNull(admit(target = target.copy(nativeSessionId = UUID.randomUUID())))
        assertNull(admit(policy = policy.copy(enabled = false)))
        assertNull(admit(policy = policy.copy(minimumAndroidBuild = 29)))
        val ios = device.copy(platform = CollaborationPlatform.IOS)
        val iosSnapshot =
            snapshot.copy(
                device = ios,
                bound = ios,
                gateway = gateway.copy(platform = CollaborationPlatform.IOS),
            )
        assertNotNull(admit(iosSnapshot))
        assertNull(admit(iosSnapshot, policy = policy.copy(minimumIosBuild = 29)))
    }

    @Test
    fun transportResponseCannotAcknowledgeAChangedCredentialOrAdmission() {
        val before = requireNotNull(admit())
        val after = requireNotNull(admit(at = now.plusSeconds(1)))
        assertTrue(sameCollaborationSpaceReadinessAdmission(before, after, now.plusSeconds(1)))
        for (wrong in
            listOf(
                after.copy(nativeSessionVersion = after.nativeSessionVersion + 1),
                after.copy(credentialVersion = after.credentialVersion + 1),
                after.copy(deviceVersion = after.deviceVersion + 1),
                after.copy(gateway = after.gateway.copy(version = after.gateway.version + 1)),
                after.copy(keys = after.keys.copy(fingerprint = "b".repeat(64))),
                after.copy(
                    provider = after.provider.copy(generation = after.provider.generation + 1)
                ),
                after.copy(observedAt = now.minusNanos(1)),
                after.copy(expiresAt = now),
            )) assertFalse(
            sameCollaborationSpaceReadinessAdmission(before, wrong, now.plusSeconds(1))
        )
        assertFalse(sameCollaborationSpaceReadinessAdmission(before, after, before.expiresAt))
        for (token in
            listOf("", "x".repeat(2049), "line\nbreak", "space token", "unicode-ñ")) assertFalse(
            collaborationSpaceReadinessCredential(
                before,
                CollaborationSpaceProviderSession(provider, token),
                now,
            )
        )
        assertFalse(
            collaborationSpaceReadinessCredential(
                before,
                CollaborationSpaceProviderSession(
                    provider.copy(generation = provider.generation + 1),
                    "fixture",
                ),
                now,
            )
        )
    }

    @Test
    fun originalPhysicalIdentityAndFreshDeviceOnOriginalUserRemainDistinct() {
        val legacy = legacySnapshot()
        val retained = requireNotNull(admit(legacy))
        assertEquals(requireNotNull(legacy.device).matrixUserId, retained.owner.matrixUserId)
        assertEquals(requireNotNull(legacy.device).matrixDeviceId, retained.owner.matrixDeviceId)
        assertNotNull(retained.owner.legacy)
        val fresh =
            requireNotNull(legacy.device)
                .copy(
                    origin = CollaborationSpaceDeviceOrigin.SPACE,
                    legacy = null,
                    legacyKeyFingerprint = null,
                    matrixDeviceId = device.matrixDeviceId,
                )
        val changed =
            legacy.copy(
                device = fresh,
                bound = fresh,
                keys =
                    requireNotNull(legacy.keys)
                        .copy(matrixDeviceId = fresh.matrixDeviceId, fingerprint = "b".repeat(64)),
                provider =
                    requireNotNull(legacy.provider).copy(matrixDeviceId = fresh.matrixDeviceId),
                legacyParent = null,
                completion = null,
                provisioning = null,
            )
        val newDevice = requireNotNull(admit(changed))
        assertEquals(retained.owner.matrixUserId, newDevice.owner.matrixUserId)
        assertEquals(device.matrixDeviceId, newDevice.owner.matrixDeviceId)
        assertNull(newDevice.owner.legacy)
    }

    @Test
    fun retainedReadinessRequiresExactCompletedOriginalPublication() {
        val legacy = legacySnapshot()
        assertNotNull(admit(legacy))
        val done = requireNotNull(legacy.completion)
        val old = requireNotNull(legacy.legacyParent)
        val currentKeys = requireNotNull(legacy.keys)
        for (wrong in
            listOf(
                legacy.copy(legacyParent = null),
                legacy.copy(completion = null),
                legacy.copy(provisioning = null),
                legacy.copy(completion = done.copy(fingerprint = "b".repeat(64))),
                legacy.copy(
                    completion = done.copy(target = done.target.copy(deviceId = UUID.randomUUID()))
                ),
                legacy.copy(
                    completion = done.copy(keysObservedAt = done.frozenAt.minusNanos(1000))
                ),
                legacy.copy(
                    legacyParent =
                        old.copy(device = old.device.copy(state = CollaborationDeviceState.ACTIVE))
                ),
                legacy.copy(legacyParent = old.copy(nativeSessionId = UUID.randomUUID())),
                legacy.copy(keys = currentKeys.copy(fingerprint = "b".repeat(64))),
                legacy.copy(
                    keys = currentKeys.copy(reservedAt = done.completedAt.minusNanos(1000))
                ),
                legacy.copy(
                    provider =
                        requireNotNull(legacy.provider)
                            .copy(generation = done.targetProviderGeneration - 1)
                ),
            )) assertNull(admit(wrong))
    }

    private fun legacySnapshot(): CollaborationSpaceReadinessSnapshot {
        val original = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
        val matrixUser =
            "@${collaborationMatrixLocalpart(original.companyId, account)}:matrix.example.test"
        val physical = collaborationMatrixDeviceId(original.deviceId)
        val retained =
            device.copy(
                matrixUserId = matrixUser,
                matrixDeviceId = physical,
                origin = CollaborationSpaceDeviceOrigin.LEGACY,
                legacy = original,
                legacyKeyFingerprint = keys.fingerprint,
            )
        val selected =
            identity.copy(
                matrixUserId = matrixUser,
                origin = CollaborationSpaceIdentityOrigin.LEGACY,
                legacy = original,
            )
        val completed = now.minusSeconds(85)
        val completion =
            CollaborationLegacyRuntimeCompletion(
                CollaborationLegacyRuntimeOwner(
                    original.companyId,
                    original.deviceId,
                    account,
                    native,
                ),
                CollaborationLegacyRuntimeTarget(space, deviceId),
                matrixUser,
                physical,
                keys.fingerprint,
                parent.version,
                0,
                2,
                0,
                null,
                device.version,
                1,
                now.minusSeconds(90),
                now.minusSeconds(86),
                completed,
            )
        val old =
            CollaborationLegacyParent(
                CollaborationLegacyDevice(
                    original,
                    account,
                    matrixUser,
                    physical,
                    CollaborationPlatform.ANDROID,
                    CollaborationDeviceState.REVOKED,
                    2,
                    0,
                    now.minusSeconds(400),
                    now.minusSeconds(180),
                    completed,
                    completed,
                ),
                native,
                now.minusSeconds(190),
                CollaborationLegacyParentKey(
                    keys.fingerprint,
                    1,
                    now.minusSeconds(180),
                    now.minusSeconds(170),
                ),
            )
        val request =
            CollaborationSpaceProvisioningRequest(
                space,
                account,
                deviceId,
                native,
                0,
                parent.version,
                parent.credentialVersion,
                parent.authenticatedAt,
                parent.mfaVerifiedAt,
                UUID.randomUUID(),
                ClientVersion(ClientPlatform.ANDROID, 28),
                now.minusSeconds(95),
                now.plusSeconds(100),
            )
        val head =
            CollaborationSpaceProvisioning(
                request,
                now.minusSeconds(100),
                now.minusSeconds(95),
                CollaborationSpaceProvisioningState.SUCCEEDED,
                1,
                null,
                CollaborationSpaceProvisioningOutcome(
                    0,
                    CollaborationSpaceProvisioningState.SUCCEEDED,
                    completed,
                    null,
                ),
            )
        return snapshot.copy(
            device = retained,
            bound = retained,
            selection = selection.copy(selected = selected),
            keys = keys.copy(matrixUserId = matrixUser, matrixDeviceId = physical),
            provider = provider.copy(matrixUserId = matrixUser, matrixDeviceId = physical),
            legacyParent = old,
            completion = completion,
            provisioning = head,
        )
    }
}
