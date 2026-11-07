package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRoomRequestFenceDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRoomRequestFenceRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.database.commandFingerprint
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomRequestFencesRecord
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomRequestFenceMappingTest {
    private val original =
        CollaborationSpaceRoomRequestIntent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            7,
            CollaborationSpaceRoomObservedAttempt(UUID.randomUUID(), 3),
        )
    private val fence =
        CollaborationSpaceRoomRequestFence(
            UUID.randomUUID(),
            original,
            UUID.randomUUID(),
            Instant.parse("2026-11-07T00:00:00.123456Z"),
        )

    @Test
    fun exactOriginalAndMicrosecondEvidenceUseTheExistingStableFingerprint() {
        for (value in listOf(fence, fence.copy(original = original.copy(expectedAttempt = null)))) {
            val row = value.toRoomRequestFenceRecord()
            assertEquals(
                commandFingerprint(collaborationSpaceRoomReceiptKey(value.original).payload),
                row.payloadHash,
            )
            assertEquals(value, row.toRoomRequestFence())
            assertEquals(value.createdAt.nano, row.createdAt.nano)
        }
    }

    @Test
    fun corruptDescriptorHashOrTimeCannotBePublishedAsTypedEvidence() {
        val changes =
            listOf<(CollaborationSpaceRoomRequestFencesRecord) -> Unit>(
                { it.deviceId = UUID.randomUUID() },
                { it.expectedDeviceVersion = 8 },
                { it.expectedRequestAccountId = null },
                { it.expectedRequestRevision = null },
                { it.payloadHash = "a".repeat(64) },
                { it.fenceId = UUID(0, 0) },
                { it.accountId = UUID(0, 0) },
                { it.createdAt = it.createdAt.plusNanos(1) },
            )
        for (change in changes) {
            val row = fence.toRoomRequestFenceRecord().also(change)
            assertThrows(IllegalArgumentException::class.java) { row.toRoomRequestFence() }
        }
    }

    @Test
    fun repositoryVerifiesExactLookupScopeAndOnlyWritesTheMappedOriginal() {
        var row: CollaborationSpaceRoomRequestFencesRecord? = fence.toRoomRequestFenceRecord()
        var inserted: CollaborationSpaceRoomRequestFencesRecord? = null
        val source =
            object : CollaborationSpaceRoomRequestFenceDataSource {
                override fun find(accountId: UUID, operationId: UUID) = row

                override fun insert(value: CollaborationSpaceRoomRequestFencesRecord) {
                    inserted = value
                }
            }
        val repository = StoredCollaborationSpaceRoomRequestFenceRepository(source)
        assertEquals(Result.Success(fence), repository.find(fence.accountId, original.operationId))
        assertInstanceOf(
            Result.Failed::class.java,
            repository.find(UUID.randomUUID(), original.operationId),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.find(fence.accountId, UUID.randomUUID()),
        )
        row = null
        assertEquals(
            Result.Success<CollaborationSpaceRoomRequestFence?>(null),
            repository.find(fence.accountId, original.operationId),
        )
        assertEquals(Result.Success(Unit), repository.record(fence))
        assertEquals(fence, requireNotNull(inserted).toRoomRequestFence())
    }

    @Test
    fun cancellationAndInterruptionRemainTechnicalFailuresForTheTransactionOwner() {
        for (interrupted in listOf(false, true)) {
            fun stop(): Nothing =
                if (interrupted) throw InterruptedException("fixture")
                else throw CancellationException("fixture")
            val source =
                object : CollaborationSpaceRoomRequestFenceDataSource {
                    override fun find(
                        accountId: UUID,
                        operationId: UUID,
                    ): CollaborationSpaceRoomRequestFencesRecord? = stop()

                    override fun insert(value: CollaborationSpaceRoomRequestFencesRecord) = stop()
                }
            val repository = StoredCollaborationSpaceRoomRequestFenceRepository(source)
            val expected =
                if (interrupted) InterruptedException::class.java
                else CancellationException::class.java
            assertThrows(expected) { repository.find(fence.accountId, original.operationId) }
            assertThrows(expected) { repository.record(fence) }
        }
    }
}
