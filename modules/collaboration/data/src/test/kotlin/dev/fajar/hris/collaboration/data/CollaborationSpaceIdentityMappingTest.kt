package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceIdentityRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationSpaceMatrixLocalpart
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceIdentitiesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceIdentityMappingTest {
    private class Source : CollaborationSpaceIdentityDataSource {
        var reads = 0
        var writes = 0
        var row: CollaborationSpaceIdentitySelectionRow? = null
        var failure: Exception? = null

        override fun selection(
            spaceId: UUID,
            accountId: UUID,
            serverName: String,
        ): CollaborationSpaceIdentitySelectionRow? {
            reads++
            failure?.let { throw it }
            return row
        }

        override fun insert(record: CollaborationSpaceIdentitiesRecord) {
            writes++
            failure?.let { throw it }
        }
    }

    @Test
    fun invalidQueriesForeignSnapshotsAndDriverFailuresCannotBecomeAdmittedAbsence() {
        val source = Source()
        val repository = StoredCollaborationSpaceIdentityRepository(source)
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        assertInstanceOf(
            Result.Failed::class.java,
            repository.selection(space, account, "user@matrix.example.test"),
        )
        assertEquals(0, source.reads)
        source.row = CollaborationSpaceIdentitySelectionRow(UUID.randomUUID(), account, null, null)
        assertInstanceOf(
            Result.Failed::class.java,
            repository.selection(space, account, "matrix.example.test"),
        )
        source.row =
            CollaborationSpaceIdentitySelectionRow(
                space,
                account,
                CollaborationSpaceIdentitiesRecord(),
                CollaborationSpaceLegacyDevicesRecord(),
            )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.selection(space, account, "matrix.example.test"),
        )
        source.failure = SQLException("Private driver details", "08006")
        assertInstanceOf(
            Result.Failed::class.java,
            repository.selection(space, account, "matrix.example.test"),
        )
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) {
            repository.selection(space, account, "matrix.example.test")
        }
    }

    @Test
    fun malformedIdentityIsRejectedBeforeWriteAndWriteInterruptionPropagates() {
        val source = Source()
        val repository = StoredCollaborationSpaceIdentityRepository(source)
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        val identity =
            CollaborationSpaceIdentity(
                space,
                account,
                "matrix.example.test",
                "@${collaborationSpaceMatrixLocalpart(space, account)}:matrix.example.test",
                CollaborationSpaceIdentityOrigin.SPACE,
                null,
                Instant.parse("2026-11-03T20:00:00Z"),
            )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.create(identity.copy(matrixUserId = "@foreign:matrix.example.test")),
        )
        assertEquals(0, source.writes)
        source.failure = SQLException("Private write outcome", "08006")
        assertInstanceOf(Result.Failed::class.java, repository.create(identity))
        assertEquals(1, source.writes)
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) { repository.create(identity) }
        assertEquals(2, source.writes)
    }
}
