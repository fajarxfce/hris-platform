package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.resolveCollaborationSpaceRoomFailure
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRoomRepository
import dev.fajar.hris.collaboration.domain.usecases.ClaimCollaborationSpaceRooms
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomWorkerPolicyTest {
    private val owner = UUID.randomUUID()
    private val at = Instant.parse("2026-11-04T12:00:00Z")
    private val request =
        CollaborationSpaceRoomRequest(
            spaceId = UUID.randomUUID(),
            conversationId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            deviceId = UUID.randomUUID(),
            deviceVersion = 1,
            providerGeneration = 1,
            nativeSessionId = UUID.randomUUID(),
            revision = 0,
            nativeSessionVersion = 0,
            credentialVersion = 0,
            authenticatedAt = at.minusSeconds(60),
            mfaVerifiedAt = null,
            correlationId = UUID.randomUUID(),
            client = ClientVersion(ClientPlatform.ANDROID, 28),
            requestedAt = at,
            expiresAt = at.plusSeconds(60),
        )
    private val lease =
        CollaborationSpaceRoomLease(request, 1, owner, UUID.randomUUID(), at.plusSeconds(60))
    private val head =
        CollaborationSpaceRoomProvisioning(
            request,
            at,
            at,
            CollaborationSpaceProvisioningState.RUNNING,
            1,
            lease,
            null,
        )
    private val policy = CollaborationSpacePolicy(true, 28, 28)
    private val provider = CollaborationMatrixPolicy(true, "matrix.example.test")

    @Test
    fun disabledAndInvalidClaimsDoNotReachStorageAndTechnicalFailuresArePreserved() {
        val queue = Queue(Result.Success(listOf(head)))
        assertEquals(
            Result.Success(emptyList<CollaborationSpaceRoomLease>()),
            ClaimCollaborationSpaceRooms(queue, policy.copy(enabled = false), provider)
                .execute(owner, 1),
        )
        val command = ClaimCollaborationSpaceRooms(queue, policy, provider)
        for ((limit, seconds) in listOf(0 to 60, 9 to 60, 1 to 0, 1 to 61)) assertInstanceOf(
            Result.Failed::class.java,
            command.execute(owner, limit, seconds),
        )
        assertInstanceOf(Result.Failed::class.java, command.execute(UUID(0, 0), 1))
        assertEquals(0, queue.calls)
        assertEquals(Result.Success(listOf(lease)), command.execute(owner, 1))
        assertEquals(Triple(owner, 1, 60), queue.input)
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy"))
        queue.value = failure
        assertEquals(failure, command.execute(owner, 1))
        queue.thrown = CancellationException("fixture claim cancelled")
        assertThrows(CancellationException::class.java) { command.execute(owner, 1) }
        queue.thrown = InterruptedException("fixture claim interrupted")
        assertThrows(InterruptedException::class.java) { command.execute(owner, 1) }
    }

    @Test
    fun claimBatchDeduplicatesConversationsAcrossParticipantsButAllowsDifferentRoomsOnOneDevice() {
        val queue = Queue(Result.Success(emptyList()))
        val command = ClaimCollaborationSpaceRooms(queue, policy, provider)
        val otherRequest = request.copy(conversationId = UUID.randomUUID())
        val otherLease = lease.copy(request = otherRequest, token = UUID.randomUUID())
        val other = head.copy(request = otherRequest, lease = otherLease)
        queue.value = Result.Success(listOf(head, other))
        assertEquals(Result.Success(listOf(lease, otherLease)), command.execute(owner, 2))
        val peerRequest = request.copy(accountId = UUID.randomUUID())
        val peer =
            head.copy(
                request = peerRequest,
                lease = lease.copy(request = peerRequest, token = UUID.randomUUID()),
            )
        for (invalid in
            listOf(
                listOf(head, peer),
                listOf(head, head),
                List(3) { head },
                listOf(head.copy(lease = null)),
                listOf(head.copy(attempts = 2)),
                listOf(head.copy(lease = lease.copy(ownerId = UUID.randomUUID()))),
                listOf(head.copy(lease = lease.copy(token = UUID(0, 0)))),
                listOf(head.copy(lease = lease.copy(until = request.expiresAt.plusSeconds(1)))),
                listOf(head.copy(lease = lease.copy(request = request.copy(revision = 1)))),
            )) {
            queue.value = Result.Success(invalid)
            assertInstanceOf(Result.Failed::class.java, command.execute(owner, 2))
        }
    }

    @Test
    fun retryStaysWithinOriginalLeaseAndProofAndNeverStoresRawProviderParameters() {
        val failure = Failure(FailureKind.UNAVAILABLE, "collaboration_space_request_busy")
        for (attempt in 1..7) {
            val current = lease.copy(attempt = attempt)
            val resolution = resolveCollaborationSpaceRoomFailure(current, failure, at)
            assertTrue(requireNotNull(resolution.delaySeconds) in 1..30)
            assertEquals(
                resolution,
                resolveCollaborationSpaceRoomFailure(
                    current,
                    failure.copy(
                        parameters =
                            mapOf("retryAfterSeconds" to "99999", "token" to "fixture-private")
                    ),
                    at,
                ),
            )
        }
        for (attempt in listOf(0, 8, 9, Int.MIN_VALUE, Int.MAX_VALUE)) assertNull(
            resolveCollaborationSpaceRoomFailure(lease.copy(attempt = attempt), failure, at)
                .delaySeconds
        )
        for (time in listOf(at.minusNanos(1), lease.until, lease.until.plusSeconds(1))) assertNull(
            resolveCollaborationSpaceRoomFailure(lease, failure, time).delaySeconds
        )
        assertNull(
            resolveCollaborationSpaceRoomFailure(
                    lease.copy(request = request.copy(expiresAt = at.plusSeconds(1))),
                    failure,
                    at,
                )
                .delaySeconds
        )
        val denied =
            resolveCollaborationSpaceRoomFailure(
                lease,
                failure.copy(kind = FailureKind.FORBIDDEN),
                at,
            )
        assertEquals(CollaborationSpaceRoomFailure.ACCESS_DENIED, denied.failure)
        assertNull(denied.delaySeconds)
        for (code in
            listOf("collaboration_room_mismatch", "collaboration_room_recovery_requires_creator")) {
            val mismatch =
                resolveCollaborationSpaceRoomFailure(lease, Failure(FailureKind.CONFLICT, code), at)
            assertEquals(CollaborationSpaceRoomFailure.ROOM_MISMATCH, mismatch.failure)
            assertNull(mismatch.delaySeconds)
        }
        assertNull(
            resolveCollaborationSpaceRoomFailure(
                    lease,
                    Failure(FailureKind.UNEXPECTED, "unknown"),
                    at,
                )
                .delaySeconds
        )
    }

    private class Queue(var value: Result<List<CollaborationSpaceRoomProvisioning>>) :
        CollaborationSpaceRoomRepository {
        var calls = 0
        var input: Triple<UUID, Int, Int>? = null
        var thrown: Exception? = null

        override fun claim(
            ownerId: UUID,
            limit: Int,
            leaseSeconds: Int,
        ): Result<List<CollaborationSpaceRoomProvisioning>> {
            calls++
            input = Triple(ownerId, limit, leaseSeconds)
            thrown?.let { throw it }
            return value
        }

        override fun lockOriginalPair(companyId: UUID, first: UUID, second: UUID): Result<Unit> =
            error("Unexpected claim operation")

        override fun lock(spaceId: UUID, conversationId: UUID): Result<Unit> =
            error("Unexpected claim operation")

        override fun intent(
            spaceId: UUID,
            accountId: UUID,
            conversationId: UUID,
        ): Result<CollaborationSpaceRoomIntent?> = error("Unexpected claim operation")

        override fun createIntent(value: CollaborationSpaceRoomIntent): Result<Unit> =
            error("Unexpected claim operation")

        override fun binding(
            spaceId: UUID,
            accountId: UUID,
            conversationId: UUID,
        ): Result<CollaborationSpaceRoomBinding?> = error("Unexpected claim operation")

        override fun status(
            spaceId: UUID,
            accountId: UUID,
            conversationId: UUID,
        ): Result<CollaborationSpaceRoomStatus?> = error("Unexpected claim operation")

        override fun provisioning(
            spaceId: UUID,
            accountId: UUID,
            conversationId: UUID,
            lock: Boolean,
        ): Result<CollaborationSpaceRoomProvisioning?> = error("Unexpected claim operation")

        override fun enqueue(request: CollaborationSpaceRoomRequest): Result<Unit> =
            error("Unexpected claim operation")

        override fun retry(
            request: CollaborationSpaceRoomRequest,
            expectedRevision: Long,
        ): Result<Unit> = error("Unexpected claim operation")

        override fun finish(
            lease: CollaborationSpaceRoomLease,
            failure: CollaborationSpaceRoomFailure?,
        ): Result<Boolean> = error("Unexpected claim operation")

        override fun defer(lease: CollaborationSpaceRoomLease, delaySeconds: Int): Result<Boolean> =
            error("Unexpected claim operation")

        override fun cancel(
            spaceId: UUID,
            accountId: UUID,
            conversationId: UUID,
            expectedRevision: Long,
        ): Result<Boolean> = error("Unexpected claim operation")

        override fun publish(value: CollaborationSpaceRoomBinding): Result<Unit> =
            error("Unexpected claim operation")
    }
}
