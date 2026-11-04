package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyRuntimeRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeLeasesRecord
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationLegacyRuntimeMappingTest {
    private val owner =
        CollaborationLegacyRuntimeOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
    private val at = Instant.parse("2026-11-03T23:50:00Z")
    private val lease =
        CollaborationLegacyRuntimeLease(
            owner,
            0,
            0,
            UUID.randomUUID(),
            at,
            at.plusSeconds(30),
            null,
        )

    @Test
    fun exactLeaseLookupCarriesItsGuardAndRejectsAnUnexpectedSlot() {
        var guarded = false
        var answer = legacyRuntimeLeaseRow(lease)
        val source =
            object : Source() {
                override fun lease(
                    owner: CollaborationLegacyRuntimeCoordinatesData,
                    slot: Int,
                    lock: Boolean,
                ): CollaborationLegacyRuntimeLeasesRecord {
                    guarded = lock
                    return answer
                }
            }
        val repository = StoredCollaborationLegacyRuntimeRepository(source)
        assertEquals(lease, success(repository.lease(owner, 0, true)))
        assertTrue(guarded)
        answer = legacyRuntimeLeaseRow(lease.copy(slot = 1))
        assertInstanceOf(Result.Failed::class.java, repository.lease(owner, 0, false))
        assertInstanceOf(Result.Failed::class.java, repository.lease(owner, 8, true))
    }

    @Test
    fun ensureReturnsTheRetainedFreezeAndMalformedOwnersOrStatesNeverEscape() {
        val frozen =
            row().apply {
                phase = "DRAINING"
                version = 1
                frozenAt = at.plusSeconds(1).atOffset(UTC)
                targetSpaceId = UUID.randomUUID()
                targetDeviceId = UUID.randomUUID()
            }
        var inserted = false
        var locked = false
        val source =
            object : Source() {
                override fun insertIfAbsent(row: CollaborationLegacyRuntimeRecord) {
                    inserted = true
                    assertEquals("LEGACY", row.phase)
                }

                override fun find(
                    owner: CollaborationLegacyRuntimeCoordinatesData,
                    lock: Boolean,
                ): CollaborationLegacyRuntimeRecord {
                    locked = lock
                    return frozen
                }
            }
        val retained =
            success(
                StoredCollaborationLegacyRuntimeRepository(source).ensure(owner, at.plusSeconds(2))
            )
        assertTrue(inserted && locked)
        assertEquals(CollaborationLegacyRuntimePhase.DRAINING, retained.phase)
        assertEquals(at, retained.createdAt)
        val changes =
            listOf<(CollaborationLegacyRuntimeRecord) -> Unit>(
                { it.companyId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.phase = "OTHER" },
                { it.version = 2 },
                { it.createdAt = null },
                { it.frozenAt = at.atOffset(UTC) },
                { it.targetSpaceId = UUID.randomUUID() },
                { it.targetDeviceId = UUID.randomUUID() },
                { it.createdAt = at.plusNanos(1).atOffset(UTC) },
            )
        for (change in changes) {
            val malformed =
                object : Source() {
                    override fun find(
                        owner: CollaborationLegacyRuntimeCoordinatesData,
                        lock: Boolean,
                    ) = row().also(change)
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeRepository(malformed).find(owner, false),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            legacyRuntimeFromRow(
                frozen.apply { frozenAt = at.minusSeconds(1).atOffset(UTC) },
                owner,
            )
        }
    }

    @Test
    fun leaseRowsAreBoundedUniqueUnreleasedOnClaimAndPreserveEveryReleaseCoordinate() {
        assertEquals(lease, legacyRuntimeLeaseFromRow(legacyRuntimeLeaseRow(lease), owner))
        assertFalse(lease.toString().contains(lease.token.toString()))
        val changes =
            listOf<(CollaborationLegacyRuntimeLeasesRecord) -> Unit>(
                { it.companyId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.slot = -1 },
                { it.slot = 8 },
                { it.runtimeVersion = 1 },
                { it.token = null },
                { it.expiresAt = it.startedAt },
                { it.expiresAt = it.startedAt.plusSeconds(61) },
                { it.releasedAt = it.startedAt.minusSeconds(1) },
                { it.startedAt = it.startedAt.plusNanos(1) },
            )
        for (change in changes) {
            val malformed =
                object : Source() {
                    override fun slots(owner: CollaborationLegacyRuntimeCoordinatesData) =
                        listOf(legacyRuntimeLeaseRow(lease).also(change))
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeRepository(malformed).slots(owner),
            )
        }
        for (rows in
            listOf(
                List(9) { legacyRuntimeLeaseRow(lease) },
                List(2) { legacyRuntimeLeaseRow(lease) },
            )) {
            val malformed =
                object : Source() {
                    override fun slots(owner: CollaborationLegacyRuntimeCoordinatesData) = rows
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeRepository(malformed).slots(owner),
            )
        }
        for (rows in
            listOf(
                List(2) { legacyRuntimeLeaseRow(lease) },
                listOf(legacyRuntimeLeaseRow(lease.copy(releasedAt = at))),
                listOf(legacyRuntimeLeaseRow(lease.copy(expiresAt = at.plusSeconds(31)))),
            )) {
            val malformed =
                object : Source() {
                    override fun claim(
                        owner: CollaborationLegacyRuntimeCoordinatesData,
                        until: OffsetDateTime,
                    ) = rows
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeRepository(malformed).claim(owner, lease.expiresAt),
            )
        }
        var captured: CollaborationLegacyRuntimeLeasesRecord? = null
        val source =
            object : Source() {
                override fun release(row: CollaborationLegacyRuntimeLeasesRecord): Boolean {
                    captured = row
                    return true
                }
            }
        assertTrue(success(StoredCollaborationLegacyRuntimeRepository(source).release(lease)))
        assertEquals(lease, legacyRuntimeLeaseFromRow(requireNotNull(captured), owner))
        captured = null
        assertInstanceOf(
            Result.Failed::class.java,
            StoredCollaborationLegacyRuntimeRepository(source).release(lease.copy(releasedAt = at)),
        )
        assertNull(captured)
    }

    @Test
    fun acquisitionFailuresStayMappedWhileCancellationAndInterruptionPropagate() {
        for (failure in
            listOf(
                QueryTimeoutException("owned fixture"),
                CancellationException("owned fixture"),
                InterruptedException("owned fixture"),
            )) {
            val source =
                object : Source() {
                    override fun find(
                        owner: CollaborationLegacyRuntimeCoordinatesData,
                        lock: Boolean,
                    ): CollaborationLegacyRuntimeRecord? = throw failure
                }
            val repository = StoredCollaborationLegacyRuntimeRepository(source)
            when (failure) {
                is CancellationException ->
                    assertThrows(CancellationException::class.java) { repository.find(owner, true) }
                is InterruptedException ->
                    assertThrows(InterruptedException::class.java) { repository.find(owner, true) }
                else -> assertInstanceOf(Result.Failed::class.java, repository.find(owner, true))
            }
        }
    }

    private fun row() =
        CollaborationLegacyRuntimeRecord().apply {
            companyId = owner.companyId
            deviceId = owner.deviceId
            accountId = owner.accountId
            nativeSessionId = owner.nativeSessionId
            phase = "LEGACY"
            version = 0
            createdAt = at.atOffset(UTC)
        }

    private abstract class Source : CollaborationLegacyRuntimeDataSource {
        override fun insertIfAbsent(row: CollaborationLegacyRuntimeRecord): Unit =
            error("Unexpected insert")

        override fun find(
            owner: CollaborationLegacyRuntimeCoordinatesData,
            lock: Boolean,
        ): CollaborationLegacyRuntimeRecord? = error("Unexpected find")

        override fun lease(
            owner: CollaborationLegacyRuntimeCoordinatesData,
            slot: Int,
            lock: Boolean,
        ): CollaborationLegacyRuntimeLeasesRecord? = error("Unexpected lease lookup")

        override fun slots(
            owner: CollaborationLegacyRuntimeCoordinatesData
        ): List<CollaborationLegacyRuntimeLeasesRecord> = error("Unexpected slots")

        override fun claim(
            owner: CollaborationLegacyRuntimeCoordinatesData,
            until: OffsetDateTime,
        ): List<CollaborationLegacyRuntimeLeasesRecord> = error("Unexpected claim")

        override fun release(row: CollaborationLegacyRuntimeLeasesRecord): Boolean =
            error("Unexpected release")

        override fun freeze(row: CollaborationLegacyRuntimeRecord, expectedVersion: Long): Boolean =
            error("Unexpected freeze")
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
