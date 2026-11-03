package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceContactRow
import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRepository
import dev.fajar.hris.core.domain.*
import java.sql.SQLException
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StoredCollaborationSpaceRepositoryTest {
    private class Source : CollaborationSpaceDataSource {
        var calls = 0
        var rows = emptyList<CollaborationSpaceContactRow>()
        var failure: Exception? = null

        override fun current(): UUID? = null

        override fun contacts(
            spaceId: UUID,
            excludedAccountId: UUID,
            query: String,
            after: UUID?,
            accountLimit: Int,
            companyLimit: Int,
        ): List<CollaborationSpaceContactRow> {
            calls++
            failure?.let { throw it }
            return rows
        }
    }

    @Test
    fun rejectsUnboundedOrControlCharacterQueriesBeforeIo() {
        val source = Source()
        val repository = StoredCollaborationSpaceRepository(source)
        for ((query, limit) in listOf("" to 0, "" to 51, "a".repeat(81) to 30, "name\n" to 30)) {
            val result =
                repository.contacts(UUID.randomUUID(), UUID.randomUUID(), query, null, limit)
            assertEquals(
                "invalid_collaboration_contact_query",
                (result as Result.Failed).failure.code,
            )
        }
        assertEquals(0, source.calls)
    }

    @Test
    fun mapsIoAndAcquisitionFailuresWithoutSwallowingCancellationOrForeignMetadata() {
        val source = Source()
        val repository = StoredCollaborationSpaceRepository(source)
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        source.failure = SQLException("Private driver details", "08006")
        assertInstanceOf(
            Result.Failed::class.java,
            repository.contacts(space, account, "", null, 30),
        )
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) {
            repository.contacts(space, account, "", null, 30)
        }
        source.failure = null
        source.rows =
            listOf(
                CollaborationSpaceContactRow(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Peer",
                    UUID.randomUUID(),
                    "Company",
                )
            )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.contacts(space, account, "", null, 30),
        )
        source.rows =
            listOf(
                CollaborationSpaceContactRow(space, account, "Self", UUID.randomUUID(), "Company")
            )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.contacts(space, account, "", null, 30),
        )
    }
}
