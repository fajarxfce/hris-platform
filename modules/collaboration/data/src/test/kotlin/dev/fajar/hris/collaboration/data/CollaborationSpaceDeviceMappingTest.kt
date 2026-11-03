package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceDeviceDataSource
import dev.fajar.hris.collaboration.data.mappers.spaceDeviceFromRow
import dev.fajar.hris.collaboration.data.mappers.spaceDeviceToRow
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceDeviceRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDevicesRecord
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationSpaceDeviceMappingTest {
    private val id = UUID.randomUUID()
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val at = Instant.parse("2026-11-03T20:00:00Z")
    private val device =
        CollaborationSpaceDevice(
            id,
            space,
            account,
            "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test",
            "HRISG_${compact(id)}",
            CollaborationSpaceDeviceOrigin.SPACE,
            null,
            null,
            parent,
            CollaborationPlatform.ANDROID,
            "Fictional",
            CollaborationDeviceState.PROVISIONING,
            0,
            at,
            at,
            null,
        )

    @Test
    fun newAndLegacyDevicesRetainExactCanonicalUserDeviceKeyAndParentFields() {
        val position = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
        val legacy =
            device.copy(
                origin = CollaborationSpaceDeviceOrigin.LEGACY,
                legacy = position,
                legacyKeyFingerprint = "a".repeat(64),
                matrixUserId =
                    "@hris_${compact(position.companyId)}_${compact(account)}:matrix.example.test",
                matrixDeviceId = "HRIS_${compact(position.deviceId)}",
            )
        for (value in listOf(device, legacy, device.copy(matrixUserId = legacy.matrixUserId))) {
            assertEquals(value, spaceDeviceFromRow(spaceDeviceToRow(value), space, account))
            var saved: CollaborationSpaceDevicesRecord? = null
            var binding: CollaborationSpaceDeviceSessionsRecord? = null
            val source =
                object : Source() {
                    override fun insertDevice(row: CollaborationSpaceDevicesRecord) {
                        saved = row
                    }

                    override fun insertBinding(row: CollaborationSpaceDeviceSessionsRecord) {
                        binding = row
                    }
                }
            assertInstanceOf(
                Result.Success::class.java,
                StoredCollaborationSpaceDeviceRepository(source).register(value),
            )
            assertEquals(value, spaceDeviceFromRow(requireNotNull(saved), space, account))
            assertEquals(parent, binding?.nativeSessionId)
            assertEquals(id, binding?.deviceId)
            assertEquals(account, binding?.accountId)
        }
    }

    @Test
    fun malformedOrForeignRecordsNeverEscapeTheRepositoryBoundary() {
        val changes =
            listOf<(CollaborationSpaceDevicesRecord) -> Unit>(
                { it.spaceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.originNativeSessionId = UUID.randomUUID() },
                {
                    it.matrixUserId =
                        "@hrisg_${compact(UUID.randomUUID())}_${compact(account)}:matrix.example.test"
                },
                { it.matrixDeviceId = "HRISG_${compact(UUID.randomUUID())}" },
                { it.legacyKeyFingerprint = "a".repeat(64) },
                { it.platform = "WEB" },
                { it.label = " padded " },
                { it.version = 1000000 },
                { it.updatedAt = it.createdAt.minusSeconds(1) },
                { it.state = "REVOKED" },
            )
        for (change in changes) {
            val source =
                object : Source() {
                    override fun findBound(spaceId: UUID, accountId: UUID, nativeSessionId: UUID) =
                        spaceDeviceToRow(device).also(change)
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationSpaceDeviceRepository(source).findBound(space, account, parent),
            )
        }
    }

    @Test
    fun absentQuotaScopeIsDeniedAndTechnicalFailureOrCancellationCannotBecomeZero() {
        for (count in listOf(null, -1, 7)) {
            val source =
                object : Source() {
                    override fun otherActiveParents(
                        spaceId: UUID,
                        accountId: UUID,
                        excluding: UUID,
                        at: Instant,
                    ) = count
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationSpaceDeviceRepository(source)
                    .otherActiveParents(space, account, parent, at),
            )
        }
        for (count in listOf(0, 6)) {
            val source =
                object : Source() {
                    override fun otherActiveParents(
                        spaceId: UUID,
                        accountId: UUID,
                        excluding: UUID,
                        at: Instant,
                    ) = count
                }
            assertEquals(
                count,
                (StoredCollaborationSpaceDeviceRepository(source)
                        .otherActiveParents(space, account, parent, at) as Result.Success)
                    .value,
            )
        }
        fun failed(error: Exception) =
            StoredCollaborationSpaceDeviceRepository(
                    object : Source() {
                        override fun otherActiveParents(
                            spaceId: UUID,
                            accountId: UUID,
                            excluding: UUID,
                            at: Instant,
                        ): Int? = throw error
                    }
                )
                .otherActiveParents(space, account, parent, at)
        assertInstanceOf(Result.Failed::class.java, failed(QueryTimeoutException("fixture")))
        assertThrows(CancellationException::class.java) { failed(CancellationException("fixture")) }
        assertThrows(InterruptedException::class.java) { failed(InterruptedException("fixture")) }
    }

    private open class Source : CollaborationSpaceDeviceDataSource {
        override fun findBound(
            spaceId: UUID,
            accountId: UUID,
            nativeSessionId: UUID,
        ): CollaborationSpaceDevicesRecord? = error("Unexpected read")

        override fun otherActiveParents(
            spaceId: UUID,
            accountId: UUID,
            excluding: UUID,
            at: Instant,
        ): Int? = error("Unexpected count")

        override fun insertDevice(row: CollaborationSpaceDevicesRecord) {
            error("Unexpected device write")
        }

        override fun insertBinding(row: CollaborationSpaceDeviceSessionsRecord) {
            error("Unexpected binding write")
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")
}
