package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.legacyGatewayFromRow
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyGatewayRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyGatewaysRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationLegacyGatewayMappingTest {
    private val space = UUID.randomUUID()
    private val owner =
        CollaborationLegacyRuntimeOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
    private val id = UUID.randomUUID()
    private val at = Instant.parse("2026-11-04T02:55:00Z")

    @Test
    fun exactLiveAndRetiredMetadataRemainDistinctFromAbsenceOrWrongParent() {
        assertEquals(
            CollaborationLegacyGateway(space, owner, id, 0, at, at, null),
            legacyGatewayFromRow(row(), space, owner),
        )
        val retired =
            legacyGatewayFromRow(
                row().apply {
                    version = 1000000
                    revokedAt = at.plusSeconds(1).atOffset(UTC)
                },
                space,
                owner,
            )
        assertEquals(1000000, retired.version)
        assertNotNull(retired.revokedAt)
        val mutations =
            listOf<(CollaborationSpaceLegacyGatewaysRecord) -> Unit>(
                { it.spaceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.legacyCompanyId = UUID.randomUUID() },
                { it.legacyDeviceId = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.gatewayId = null },
                { it.version = -1 },
                { it.version = 1000000 },
                { it.revokedAt = at.atOffset(UTC) },
                { it.rotatedAt = at.minusSeconds(1).atOffset(UTC) },
                {
                    it.version = 1
                    it.revokedAt = at.minusSeconds(1).atOffset(UTC)
                },
                { it.createdAt = null },
                { it.rotatedAt = at.plusNanos(1).atOffset(UTC) },
            )
        for (mutation in mutations) {
            val repository =
                StoredCollaborationLegacyGatewayRepository(
                    object : CollaborationLegacyGatewayDataSource {
                        override fun find(
                            spaceId: UUID,
                            owner: CollaborationLegacyRuntimeCoordinatesData,
                        ) = row().also(mutation)
                    }
                )
            assertInstanceOf(Result.Failed::class.java, repository.find(space, owner))
        }
        val absent =
            StoredCollaborationLegacyGatewayRepository(
                object : CollaborationLegacyGatewayDataSource {
                    override fun find(
                        spaceId: UUID,
                        owner: CollaborationLegacyRuntimeCoordinatesData,
                    ): CollaborationSpaceLegacyGatewaysRecord? = null
                }
            )
        assertEquals(Result.Success<CollaborationLegacyGateway?>(null), absent.find(space, owner))
    }

    @Test
    fun readsPreserveTimeoutCancellationAndInterruptionWithoutRetries() {
        for (mode in listOf("timeout", "cancel", "interrupt", "flag")) {
            var reads = 0
            val cancel = CancellationException("Owned gateway metadata cancellation")
            val interrupt = InterruptedException("Owned gateway metadata interruption")
            val source =
                object : CollaborationLegacyGatewayDataSource {
                    override fun find(
                        spaceId: UUID,
                        owner: CollaborationLegacyRuntimeCoordinatesData,
                    ): CollaborationSpaceLegacyGatewaysRecord {
                        reads++
                        when (mode) {
                            "timeout" -> throw QueryTimeoutException("Owned metadata timeout")
                            "cancel" -> throw cancel
                            "interrupt" -> throw interrupt
                            else -> Thread.currentThread().interrupt()
                        }
                        return row()
                    }
                }
            val repository = StoredCollaborationLegacyGatewayRepository(source)
            when (mode) {
                "timeout" ->
                    assertEquals(
                        "database_busy",
                        (repository.find(space, owner) as Result.Failed).failure.code,
                    )
                "cancel" ->
                    assertSame(
                        cancel,
                        assertThrows(CancellationException::class.java) {
                            repository.find(space, owner)
                        },
                    )
                "interrupt" ->
                    assertSame(
                        interrupt,
                        assertThrows(InterruptedException::class.java) {
                            repository.find(space, owner)
                        },
                    )
                else ->
                    try {
                        assertThrows(InterruptedException::class.java) {
                            repository.find(space, owner)
                        }
                        assertTrue(Thread.currentThread().isInterrupted)
                    } finally {
                        Thread.interrupted()
                    }
            }
            assertEquals(1, reads)
        }
    }

    private fun row() =
        CollaborationSpaceLegacyGatewaysRecord().apply {
            spaceId = space
            accountId = owner.accountId
            legacyCompanyId = owner.companyId
            legacyDeviceId = owner.deviceId
            nativeSessionId = owner.nativeSessionId
            gatewayId = id
            version = 0
            createdAt = at.atOffset(UTC)
            rotatedAt = at.atOffset(UTC)
        }
}
