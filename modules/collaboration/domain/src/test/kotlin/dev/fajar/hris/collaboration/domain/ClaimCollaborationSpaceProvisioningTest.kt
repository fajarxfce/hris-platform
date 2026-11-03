package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.collaboration.domain.usecases.ClaimCollaborationSpaceProvisioning
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaimCollaborationSpaceProvisioningTest {
    private val owner = UUID.randomUUID()
    private val at = Instant.parse("2026-11-03T22:00:00Z")
    private val request =
        CollaborationSpaceProvisioningRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            0,
            0,
            at.minusSeconds(60),
            null,
            UUID.randomUUID(),
            ClientVersion(ClientPlatform.ANDROID, 28),
            at,
            at.plusSeconds(60),
        )
    private val lease =
        CollaborationSpaceProvisioningLease(
            request,
            1,
            owner,
            UUID.randomUUID(),
            at.plusSeconds(60),
        )
    private val head =
        CollaborationSpaceProvisioning(
            request,
            at,
            at,
            CollaborationSpaceProvisioningState.RUNNING,
            1,
            lease,
            null,
        )

    @Test
    fun disabledOrInvalidClaimsNeverTouchStorageAndValidClaimKeepsExactLeaseProof() {
        val queue = Queue(Result.Success(listOf(head)))
        val policy = CollaborationSpacePolicy(true, 28, 28)
        val matrix = CollaborationMatrixPolicy(true, "matrix.example.test")
        fun command(p: CollaborationSpacePolicy = policy, m: CollaborationMatrixPolicy = matrix) =
            ClaimCollaborationSpaceProvisioning(queue, p, m)
        assertEquals(
            Result.Success(emptyList<CollaborationSpaceProvisioningLease>()),
            command(p = policy.copy(enabled = false)).execute(owner, 1),
        )
        assertEquals(
            Result.Success(emptyList<CollaborationSpaceProvisioningLease>()),
            command(m = matrix.copy(enabled = false)).execute(owner, 1),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            command(m = matrix.copy(serverName = "invalid host")).execute(owner, 1),
        )
        for ((limit, seconds) in listOf(0 to 60, 9 to 60, 1 to 0, 1 to 61)) assertInstanceOf(
            Result.Failed::class.java,
            command().execute(owner, limit, seconds),
        )
        assertEquals(0, queue.calls)
        assertEquals(Result.Success(listOf(lease)), command().execute(owner, 1, 60))
        assertEquals(1, queue.calls)
        assertEquals(Triple(owner, 1, 60), queue.input)
    }

    @Test
    fun malformedClaimCannotSchedulePartialWorkAndTechnicalFailuresArePreserved() {
        val queue = Queue(Result.Success(emptyList()))
        val command =
            ClaimCollaborationSpaceProvisioning(
                queue,
                CollaborationSpacePolicy(true),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
            )
        for (heads in
            listOf(
                listOf(head.copy(lease = null)),
                listOf(head.copy(state = CollaborationSpaceProvisioningState.QUEUED)),
                listOf(head.copy(attempts = 2)),
                listOf(head.copy(lease = lease.copy(ownerId = UUID.randomUUID()))),
                listOf(head.copy(lease = lease.copy(request = request.copy(revision = 1)))),
                listOf(head, head),
                List(3) { head },
            )) {
            queue.value = Result.Success(heads)
            val result = command.execute(owner, 2)
            assertInstanceOf(Result.Failed::class.java, result)
            assertEquals(
                "invalid_collaboration_provisioning",
                (result as Result.Failed).failure.code,
            )
        }
        val failed = Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy"))
        queue.value = failed
        assertEquals(failed, command.execute(owner, 1))
        queue.error = CancellationException("fixture cancellation")
        assertThrows(CancellationException::class.java) { command.execute(owner, 1) }
        queue.error = InterruptedException("fixture interruption")
        assertThrows(InterruptedException::class.java) { command.execute(owner, 1) }
    }

    private class Queue(var value: Result<List<CollaborationSpaceProvisioning>>) :
        CollaborationSpaceProvisioningRepository {
        var calls = 0
        var input: Triple<UUID, Int, Int>? = null
        var error: Exception? = null

        override fun claim(
            ownerId: UUID,
            limit: Int,
            leaseSeconds: Int,
        ): Result<List<CollaborationSpaceProvisioning>> {
            calls++
            input = Triple(ownerId, limit, leaseSeconds)
            error?.let { throw it }
            return value
        }

        override fun metadata(
            spaceId: UUID,
            accountId: UUID,
            deviceId: UUID,
            lock: Boolean,
        ): Result<CollaborationSpaceProvisioning?> = error("Unexpected metadata call")

        override fun enqueue(request: CollaborationSpaceProvisioningRequest): Result<Unit> =
            error("Unexpected enqueue")

        override fun retry(
            request: CollaborationSpaceProvisioningRequest,
            expectedRevision: Long,
        ): Result<Unit> = error("Unexpected retry")

        override fun finish(
            lease: CollaborationSpaceProvisioningLease,
            failure: CollaborationSpaceProvisioningFailure?,
        ): Result<Boolean> = error("Unexpected finish")

        override fun defer(
            lease: CollaborationSpaceProvisioningLease,
            delaySeconds: Int,
        ): Result<Boolean> = error("Unexpected defer")

        override fun cancel(
            spaceId: UUID,
            accountId: UUID,
            deviceId: UUID,
            expectedRevision: Long,
        ): Result<Boolean> = error("Unexpected cancel")
    }
}
