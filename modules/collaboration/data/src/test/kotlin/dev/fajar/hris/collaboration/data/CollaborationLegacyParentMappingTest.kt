package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationLegacyParentDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyParentRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyDevicePosition
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyParentsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationLegacyParentMappingTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val position = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
    private val at = Instant.parse("2026-11-03T20:00:00Z").atOffset(ZoneOffset.UTC)

    @Test
    fun completePublicKeyStatesAndAbsentMetadataKeepExactOwnership() {
        for (published in listOf(false, true)) {
            val row =
                row().apply {
                    if (published) {
                        keyVersion = 1
                        keyPublishedAt = at.plusSeconds(1)
                    }
                }
            val found = requireNotNull(success(read { row }))
            assertEquals(position, found.device.position)
            assertEquals(account, found.device.accountId)
            assertEquals(parent, found.nativeSessionId)
            assertEquals(at.toInstant(), found.boundAt)
            assertEquals("a".repeat(64), found.key?.fingerprint)
            assertEquals(if (published) 1L else 0L, found.key?.version)
        }
        val noKeys =
            row().apply {
                keyFingerprint = null
                keyVersion = null
                keyReservedAt = null
            }
        assertNull(requireNotNull(success(read { noKeys })).key)
        assertNull(success(read { null }))
    }

    @Test
    fun foreignIncompleteOrInconsistentMetadataIsRejectedInsideTheAcquisitionBoundary() {
        val changes =
            listOf<(CollaborationSpaceLegacyParentsRecord) -> Unit>(
                { it.spaceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.legacyCompanyId = UUID.randomUUID() },
                { it.legacyDeviceId = UUID.randomUUID() },
                { it.matrixUserId = "@foreign:matrix.example.test" },
                { it.boundAt = null },
                { it.boundAt = at.minusSeconds(2) },
                { it.keyFingerprint = "invalid" },
                { it.keyFingerprint = null },
                { it.keyVersion = null },
                { it.keyVersion = 2 },
                { it.keyReservedAt = null },
                { it.keyReservedAt = at.minusSeconds(2) },
                { it.keyVersion = 1 },
                { it.keyPublishedAt = at },
                {
                    it.keyVersion = 1
                    it.keyPublishedAt = at.minusSeconds(1)
                },
            )
        for (change in changes) assertInstanceOf(
            Result.Failed::class.java,
            read { row().also(change) },
        )
    }

    @Test
    fun driverFailureIsMappedWhileCancellationAndInterruptionEscape() {
        assertInstanceOf(Result.Failed::class.java, read { throw QueryTimeoutException("fixture") })
        assertThrows(CancellationException::class.java) {
            read { throw CancellationException("fixture") }
        }
        assertThrows(InterruptedException::class.java) {
            read { throw InterruptedException("fixture") }
        }
    }

    private fun read(acquire: () -> CollaborationSpaceLegacyParentsRecord?) =
        StoredCollaborationLegacyParentRepository(
                object : CollaborationLegacyParentDataSource {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        nativeSessionId: UUID,
                        companyId: UUID,
                        deviceId: UUID,
                    ): CollaborationSpaceLegacyParentsRecord? {
                        assertEquals(space, spaceId)
                        assertEquals(account, accountId)
                        assertEquals(parent, nativeSessionId)
                        assertEquals(position.companyId, companyId)
                        assertEquals(position.deviceId, deviceId)
                        return acquire()
                    }
                }
            )
            .find(space, account, parent, position)

    private fun row() =
        CollaborationSpaceLegacyParentsRecord().apply {
            spaceId = space
            accountId = account
            legacyCompanyId = position.companyId
            legacyDeviceId = position.deviceId
            platform = "ANDROID"
            state = "ACTIVE"
            deviceVersion = 1
            deviceCreatedAt = at.minusSeconds(1)
            identityObservedAt = at
            credentialGeneration = 0
            matrixUserId =
                "@hris_${compact(position.companyId)}_${compact(account)}:matrix.example.test"
            matrixDeviceId = "HRIS_${compact(position.deviceId)}"
            nativeSessionId = parent
            boundAt = at
            keyFingerprint = "a".repeat(64)
            keyVersion = 0
            keyReservedAt = at
        }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
