package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationLegacyDeviceDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyDeviceRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.sql.SQLException
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationLegacyDeviceMappingTest {
    private class Source : CollaborationLegacyDeviceDataSource {
        var calls = 0
        var failure: Exception? = null
        var rows = emptyList<CollaborationSpaceLegacyDevicesRecord>()

        override fun list(
            spaceId: UUID,
            accountId: UUID,
            afterCompanyId: UUID?,
            afterDeviceId: UUID?,
            limit: Int,
        ): List<CollaborationSpaceLegacyDevicesRecord> {
            calls++
            failure?.let { throw it }
            return rows
        }
    }

    @Test
    fun invalidLimitDoesNotAcquireAndDriverFailureNeverBecomesEmptyInventory() {
        val source = Source()
        val repository = StoredCollaborationLegacyDeviceRepository(source)
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        for (limit in listOf(0, 51)) assertEquals(
            "invalid_collaboration_legacy_query",
            (repository.list(space, account, null, limit) as Result.Failed).failure.code,
        )
        assertEquals(0, source.calls)
        source.failure = SQLException("Private driver details", "08006")
        assertInstanceOf(Result.Failed::class.java, repository.list(space, account, null, 30))
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) { repository.list(space, account, null, 30) }
    }

    @Test
    fun foreignOrMalformedIdentityMetadataCannotEscapeMapping() {
        val source = Source()
        val repository = StoredCollaborationLegacyDeviceRepository(source)
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        source.rows =
            listOf(
                CollaborationSpaceLegacyDevicesRecord().apply {
                    spaceId = UUID.randomUUID()
                    accountId = account
                }
            )
        assertInstanceOf(Result.Failed::class.java, repository.list(space, account, null, 30))
        source.rows =
            listOf(
                CollaborationSpaceLegacyDevicesRecord().apply {
                    spaceId = space
                    accountId = account
                    legacyCompanyId = UUID.randomUUID()
                    legacyDeviceId = UUID.randomUUID()
                    matrixUserId = "@another_account:matrix.example.test"
                    matrixDeviceId = "other_device"
                }
            )
        assertInstanceOf(Result.Failed::class.java, repository.list(space, account, null, 30))
    }
}
