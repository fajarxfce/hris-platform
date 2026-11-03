package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.spaceProvisioningRequestRow
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceProvisioningRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProvisioningRecord
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationSpaceProvisioningMappingTest {
    private val at = Instant.parse("2026-11-03T22:00:00.123456Z")
    private val request =
        CollaborationSpaceProvisioningRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            0,
            0,
            at.minusSeconds(30),
            null,
            UUID.randomUUID(),
            ClientVersion(ClientPlatform.ANDROID, 28),
            at,
            at.plusSeconds(300),
        )
    private val owner = UUID.randomUUID()

    private fun metadata(source: Source) =
        StoredCollaborationSpaceProvisioningRepository(source)
            .metadata(request.spaceId, request.accountId, request.deviceId)

    private fun running(): CollaborationSpaceProvisioningRecord =
        spaceProvisioningRequestRow(request).apply {
            state = "RUNNING"
            attempts = 1
            leaseOwner = owner
            leaseToken = UUID.randomUUID()
            leaseUntil = requestedAt.plusSeconds(60)
        }

    @Test
    fun malformedForeignAndMixedLifecycleRowsCannotLeaveTheRepositoryBoundary() {
        for (change in
            listOf<(CollaborationSpaceProvisioningRecord) -> Unit>(
                { it.accountId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.nativeSessionId = null },
                { it.nativeSessionVersion = -1 },
                { it.clientBuild = 0 },
                { it.clientPlatform = "WEB" },
                { it.expiresAt = it.requestedAt.plusSeconds(301) },
                { it.createdAt = it.requestedAt.plusSeconds(1) },
                { it.leaseOwner = owner },
                { it.state = "RUNNING" },
                { it.attempts = 8 },
                { it.lastOutcomeRevision = 0 },
                { it.completedAt = it.requestedAt },
                { it.requestedAt = it.requestedAt.plusNanos(1) },
            )) {
            val source = Source().apply { row = spaceProvisioningRequestRow(request).also(change) }
            assertInstanceOf(Result.Failed::class.java, metadata(source))
        }
        for (change in
            listOf<(CollaborationSpaceProvisioningRecord) -> Unit>(
                { it.leaseUntil = null },
                { it.leaseToken = null },
                { it.attempts = 0 },
                { it.leaseUntil = it.expiresAt.plusSeconds(1) },
                { it.leaseOwner = UUID.randomUUID() },
            )) {
            val source = Source().apply { claims = listOf(running().also(change)) }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationSpaceProvisioningRepository(source).claim(owner, 1, 60),
            )
        }
        val duplicate = running()
        for (limit in listOf(1, 2)) {
            val source = Source().apply { claims = listOf(duplicate, duplicate) }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationSpaceProvisioningRepository(source).claim(owner, limit, 60),
            )
        }
    }

    @Test
    fun canonicalTimestampsAndPreviousOutcomeStayDistinctFromCurrentPendingOrCompletedWork() {
        val source = Source()
        val repo = StoredCollaborationSpaceProvisioningRepository(source)
        val nanos =
            request.copy(
                requestedAt = at.plusNanos(999),
                expiresAt = request.expiresAt.plusNanos(999),
            )
        success(repo.enqueue(nanos))
        val queued = requireNotNull(success(metadata(source)))
        assertEquals(request, queued.request)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, queued.state)
        assertNull(queued.lastOutcome)
        assertTrue(queued.toString().contains("redacted"))
        assertFalse(request.toString().contains(request.accountId.toString()))
        val next =
            request.copy(
                revision = 1,
                requestedAt = at.plusSeconds(2),
                expiresAt = at.plusSeconds(302),
            )
        source.row =
            spaceProvisioningRequestRow(next).apply {
                createdAt = requestedAt.minusSeconds(2)
                lastOutcomeRevision = 0
                lastOutcomeState = "FAILED"
                lastOutcomeAt = requestedAt.minusSeconds(1)
                lastFailureCode = "PROVIDER_UNAVAILABLE"
            }
        val pending = requireNotNull(success(metadata(source)))
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, pending.state)
        assertEquals(0L, pending.lastOutcome?.revision)
        assertEquals(
            CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
            pending.lastOutcome?.failure,
        )
        requireNotNull(source.row).apply {
            state = "SUCCEEDED"
            attempts = 1
            completedAt = requestedAt.plusSeconds(1)
            lastOutcomeRevision = revision
            lastOutcomeState = state
            lastOutcomeAt = completedAt
            lastFailureCode = null
        }
        val completed = requireNotNull(success(metadata(source)))
        assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, completed.state)
        assertEquals(1L, completed.lastOutcome?.revision)
        assertNull(completed.lastOutcome?.failure)
        source.row = null
        assertNull(success(metadata(source)))
    }

    @Test
    fun acquisitionMappingBoundsAndCancellationRemainWithinOneFailureBoundary() {
        fun failed(error: Exception) =
            metadata(
                object : Source() {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                        lock: Boolean,
                    ): CollaborationSpaceProvisioningRecord? = throw error
                }
            )
        assertInstanceOf(Result.Failed::class.java, failed(QueryTimeoutException("fixture")))
        assertThrows(CancellationException::class.java) { failed(CancellationException("fixture")) }
        assertThrows(InterruptedException::class.java) { failed(InterruptedException("fixture")) }
        val source = Source()
        val repo = StoredCollaborationSpaceProvisioningRepository(source)
        for (changed in
            listOf(
                request.copy(revision = 1),
                request.copy(client = ClientVersion(ClientPlatform.WEB, 28)),
                request.copy(authenticatedAt = request.authenticatedAt.plusNanos(1)),
                request.copy(expiresAt = request.requestedAt.plusNanos(1)),
            )) assertInstanceOf(Result.Failed::class.java, repo.enqueue(changed))
        assertNull(source.row)
        for ((limit, seconds) in listOf(0 to 30, 9 to 30, 1 to 0, 1 to 61)) assertInstanceOf(
            Result.Failed::class.java,
            repo.claim(owner, limit, seconds),
        )
        source.claims = listOf(running())
        val lease = requireNotNull(success(repo.claim(owner, 1, 60)).single().lease)
        for (seconds in listOf(0, 301)) assertInstanceOf(
            Result.Failed::class.java,
            repo.defer(lease, seconds),
        )
        assertInstanceOf(Result.Failed::class.java, repo.defer(lease.copy(attempt = 8), 1))
        assertInstanceOf(
            Result.Failed::class.java,
            repo.finish(lease.copy(until = lease.until.plusNanos(1))),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.finish(lease.copy(request = request.copy(requestedAt = at.plusNanos(1)))),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.cancel(request.spaceId, request.accountId, request.deviceId, -1),
        )
    }

    private open class Source : CollaborationSpaceProvisioningDataSource {
        var row: CollaborationSpaceProvisioningRecord? = null
        var claims = emptyList<CollaborationSpaceProvisioningRecord>()

        override fun find(spaceId: UUID, accountId: UUID, deviceId: UUID, lock: Boolean) = row

        override fun insert(row: CollaborationSpaceProvisioningRecord) {
            this.row = row
        }

        override fun retry(
            row: CollaborationSpaceProvisioningRecord,
            expectedRevision: Long,
        ): Boolean = error("Unexpected retry")

        override fun claim(ownerId: UUID, limit: Int, leaseSeconds: Int) = claims

        override fun finish(
            lease: CollaborationSpaceProvisioningLeaseData,
            failure: String?,
        ): Boolean = error("Unexpected completion")

        override fun defer(
            lease: CollaborationSpaceProvisioningLeaseData,
            delaySeconds: Int,
        ): Boolean = error("Unexpected deferral")

        override fun cancel(
            spaceId: UUID,
            accountId: UUID,
            deviceId: UUID,
            expectedRevision: Long,
        ): Boolean = error("Unexpected cancellation")
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
