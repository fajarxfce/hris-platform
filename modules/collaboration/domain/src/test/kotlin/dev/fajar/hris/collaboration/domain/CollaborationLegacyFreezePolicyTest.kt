package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationLegacyFreezePolicyTest {
    private val at = Instant.parse("2026-11-04T02:00:00Z")
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val native = UUID.randomUUID()
    private val legacy = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
    private val fingerprint = "a".repeat(64)
    private val user = "@${collaborationMatrixLocalpart(legacy.companyId, account)}:fixture.test"
    private val providerDevice = collaborationMatrixDeviceId(legacy.deviceId)
    private val identity =
        CollaborationSpaceIdentity(
            space,
            account,
            "fixture.test",
            user,
            CollaborationSpaceIdentityOrigin.LEGACY,
            legacy,
            at.minusSeconds(20),
        )
    private val admission =
        CollaborationSpaceAdmission(
            space,
            account,
            native,
            0,
            0,
            ClientVersion(ClientPlatform.ANDROID, 1),
            at,
            at.plusSeconds(50),
        )
    private val key = CollaborationLegacyParentKey(fingerprint, 0, at.minusSeconds(15), null)
    private val source =
        CollaborationLegacyParent(
            CollaborationLegacyDevice(
                legacy,
                account,
                user,
                providerDevice,
                CollaborationPlatform.ANDROID,
                CollaborationDeviceState.ACTIVE,
                1,
                0,
                at.minusSeconds(30),
                at.minusSeconds(25),
                null,
                null,
            ),
            native,
            at.minusSeconds(29),
            key,
        )
    private val target =
        CollaborationSpaceDevice(
            UUID.randomUUID(),
            space,
            account,
            user,
            providerDevice,
            CollaborationSpaceDeviceOrigin.LEGACY,
            legacy,
            fingerprint,
            native,
            CollaborationPlatform.ANDROID,
            "Fictional retained device",
            CollaborationDeviceState.PROVISIONING,
            0,
            at.minusSeconds(10),
            at.minusSeconds(10),
            null,
        )
    private val owner =
        CollaborationLegacyRuntimeOwner(legacy.companyId, legacy.deviceId, account, native)
    private val destination = CollaborationLegacyRuntimeTarget(space, target.id)
    private val runtime =
        CollaborationLegacyRuntime(
            owner,
            CollaborationLegacyRuntimePhase.LEGACY,
            0,
            at.minusSeconds(1),
            null,
            null,
        )

    @Test
    fun currentReservedOrPublishedMetadataSelectsOnlyItsExactOriginalOwner() {
        assertEquals(Result.Success(owner), validate())
        val published = source.copy(key = key.copy(version = 1, publishedAt = at.minusSeconds(14)))
        assertEquals(Result.Success(owner), validate(source = published))
        // Freeze metadata accepts a reservation; provider/private-key possession is not asserted.
        for (wrong in
            listOf(
                source.copy(nativeSessionId = UUID.randomUUID()),
                source.copy(device = source.device.copy(accountId = UUID.randomUUID())),
                source.copy(
                    device =
                        source.device.copy(position = legacy.copy(companyId = UUID.randomUUID()))
                ),
                source.copy(
                    device =
                        source.device.copy(
                            state = CollaborationDeviceState.REVOKED,
                            deviceRevokedAt = at,
                        )
                ),
                source.copy(device = source.device.copy(credentialRetiredAt = at)),
                source.copy(key = key.copy(fingerprint = "b".repeat(64))),
                source.copy(key = null),
                null,
            )) assertInstanceOf(Result.Failed::class.java, validate(source = wrong))
    }

    @Test
    fun groupScopeNativeTargetVersionAndCurrentAdmissionCannotBeSubstituted() {
        for (wrong in
            listOf(
                target.copy(spaceId = UUID.randomUUID()),
                target.copy(accountId = UUID.randomUUID()),
                target.copy(originNativeSessionId = UUID.randomUUID()),
                target.copy(matrixDeviceId = "HRISG_other"),
                target.copy(matrixUserId = "@other:fixture.test"),
                target.copy(legacyKeyFingerprint = "b".repeat(64)),
                target.copy(platform = CollaborationPlatform.IOS),
                target.copy(state = CollaborationDeviceState.ACTIVE),
                target.copy(version = 1),
            )) assertInstanceOf(Result.Failed::class.java, validate(target = wrong))
        for (time in listOf(at.minusNanos(1), admission.expiresAt)) assertInstanceOf(
            Result.Failed::class.java,
            validate(at = time),
        )
        assertInstanceOf(Result.Failed::class.java, validate(fingerprint = "a".repeat(63)))
        assertEquals(
            Result.Success(owner),
            validate(target = target.copy(version = 999998), expectedVersion = 999998),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validate(target = target.copy(version = 999999), expectedVersion = 999999),
        )
    }

    @Test
    fun sameTargetReplayNeverRetargetsUnfreezesOrTreatsIncoherentMetadataAsAFreeze() {
        assertEquals(Result.Success(CollaborationLegacyFreezeAction.FREEZE), action(runtime))
        val frozen =
            runtime.copy(
                phase = CollaborationLegacyRuntimePhase.DRAINING,
                version = 1,
                frozenAt = at,
                target = destination,
            )
        assertEquals(Result.Success(CollaborationLegacyFreezeAction.REPLAY), action(frozen))
        for (wrong in
            listOf(
                runtime.copy(owner = owner.copy(accountId = UUID.randomUUID())),
                runtime.copy(createdAt = at.plusSeconds(1)),
                runtime.copy(version = 1),
                runtime.copy(target = destination),
                frozen.copy(version = 0),
                frozen.copy(frozenAt = null),
                frozen.copy(frozenAt = runtime.createdAt.minusNanos(1)),
                frozen.copy(frozenAt = at.plusSeconds(1)),
                frozen.copy(target = destination.copy(spaceId = UUID.randomUUID())),
                frozen.copy(target = destination.copy(deviceId = UUID.randomUUID())),
            )) assertInstanceOf(Result.Failed::class.java, action(wrong))
    }

    private fun action(value: CollaborationLegacyRuntime) =
        collaborationLegacyFreezeAction(value, owner, destination, at)

    private fun validate(
        target: CollaborationSpaceDevice = this.target,
        source: CollaborationLegacyParent? = this.source,
        expectedVersion: Long = 0,
        fingerprint: String = this.fingerprint,
        at: Instant = this.at,
    ) =
        collaborationLegacyFreezeOwner(
            identity,
            target,
            source,
            admission,
            expectedVersion,
            fingerprint,
            at,
        )
}
