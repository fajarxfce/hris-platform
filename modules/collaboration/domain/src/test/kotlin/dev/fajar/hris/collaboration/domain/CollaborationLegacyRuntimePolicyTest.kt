package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationLegacyRuntimePolicyTest {
    private val at = Instant.parse("2026-11-04T00:10:00Z")
    private val admission =
        CollaborationAdmission(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            UUID.randomUUID(),
            0,
            0,
            at,
            at.plusSeconds(55),
        )
    private val owner = collaborationLegacyRuntimeOwner(admission)
    private val runtime =
        CollaborationLegacyRuntime(
            owner,
            CollaborationLegacyRuntimePhase.LEGACY,
            0,
            at.minusSeconds(1),
            null,
            null,
        )
    private val lease =
        CollaborationLegacyRuntimeLease(
            owner,
            0,
            0,
            UUID.randomUUID(),
            at.plusSeconds(1),
            at.plusSeconds(31),
            null,
        )

    @Test
    fun publicationRequiresExactStillLiveLeaseAndCurrentOriginalOwnerWithinAdmissionDeadline() {
        assertEquals(Result.Success(Unit), validate(lease))
        assertEquals(
            Result.Success(Unit),
            validateCollaborationLegacyRuntimeLease(
                runtime,
                lease,
                lease,
                admission.copy(admittedAt = at.plusSeconds(2)),
                at.plusSeconds(3),
            ),
        )
        val stale =
            listOf(
                lease.copy(owner = owner.copy(companyId = UUID.randomUUID())),
                lease.copy(owner = owner.copy(accountId = UUID.randomUUID())),
                lease.copy(owner = owner.copy(deviceId = UUID.randomUUID())),
                lease.copy(owner = owner.copy(nativeSessionId = UUID.randomUUID())),
                lease.copy(slot = 1),
                lease.copy(runtimeVersion = 1),
                lease.copy(token = UUID.randomUUID()),
                lease.copy(startedAt = at),
                lease.copy(expiresAt = at.plusSeconds(32)),
                lease.copy(releasedAt = at.plusSeconds(2)),
                null,
            )
        for (current in stale) assertInstanceOf(Result.Failed::class.java, validate(current))
        for (expected in
            listOf(
                lease.copy(slot = 8),
                lease.copy(runtimeVersion = 1),
                lease.copy(startedAt = at.minusSeconds(2)),
                lease.copy(startedAt = at.plusSeconds(4)),
                lease.copy(expiresAt = at.plusSeconds(80)),
                lease.copy(releasedAt = at.plusSeconds(2)),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationLegacyRuntimeLease(
                    runtime,
                    expected,
                    expected,
                    admission,
                    at.plusSeconds(3),
                ),
            )
        }
        for (time in
            listOf(at.minusSeconds(1), lease.expiresAt, admission.expiresAt)) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationLegacyRuntimeLease(runtime, lease, lease, admission, time),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationLegacyRuntimeLease(
                runtime,
                lease,
                lease,
                admission.copy(expiresAt = lease.expiresAt.minusNanos(1000)),
                at.plusSeconds(3),
            ),
        )
    }

    @Test
    fun freezeStopsBothOldAdmissionAndPublicationWithoutMasqueradingAsAccountRevocation() {
        val frozen =
            runtime.copy(
                phase = CollaborationLegacyRuntimePhase.DRAINING,
                version = 1,
                frozenAt = at.plusSeconds(2),
                target = CollaborationLegacyRuntimeTarget(UUID.randomUUID(), UUID.randomUUID()),
            )
        val result = admitCollaborationLegacyRuntime(frozen, admission, at.plusSeconds(3))
        assertEquals("collaboration_runtime_transferring", (result as Result.Failed).failure.code)
        assertEquals(
            result,
            validateCollaborationLegacyRuntimeLease(
                frozen,
                lease,
                lease,
                admission,
                at.plusSeconds(3),
            ),
        )
        for (current in
            listOf(
                frozen.copy(frozenAt = null),
                frozen.copy(frozenAt = at.minusSeconds(2)),
                frozen.copy(frozenAt = at.plusSeconds(4)),
                frozen.copy(target = null),
                frozen.copy(version = 0),
                runtime.copy(version = 1),
                runtime.copy(createdAt = at.plusSeconds(4)),
                runtime.copy(owner = owner.copy(accountId = UUID.randomUUID())),
            )) {
            assertEquals(
                "collaboration_runtime_changed",
                (admitCollaborationLegacyRuntime(current, admission, at.plusSeconds(3))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    private fun validate(current: CollaborationLegacyRuntimeLease?) =
        validateCollaborationLegacyRuntimeLease(
            runtime,
            lease,
            current,
            admission,
            at.plusSeconds(3),
        )
}
