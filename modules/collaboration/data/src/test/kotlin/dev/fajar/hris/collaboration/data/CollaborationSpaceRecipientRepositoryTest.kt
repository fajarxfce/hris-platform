package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRecipientDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRecipientRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRecipientDevicesRecord
import java.sql.SQLException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRecipientRepositoryTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val at = Instant.now().truncatedTo(ChronoUnit.MICROS)

    @Test
    fun malformedQueryNeverAcquiresTheDatasource() {
        var calls = 0
        val repository = stored { _, _, _, _ ->
            calls++
            emptyList()
        }
        for ((id, accounts, time) in
            listOf(
                Triple(UUID(0, 0), setOf(account), at),
                Triple(space, emptySet(), at),
                Triple(space, setOf(UUID(0, 0)), at),
                Triple(space, List(101) { UUID.randomUUID() }.toSet(), at),
                Triple(space, setOf(account), Instant.MAX),
            )) assertInstanceOf(Result.Failed::class.java, repository.devices(id, accounts, time))
        assertEquals(0, calls)
    }

    @Test
    fun boundedAccountSnapshotCannotBeChangedWhileAcquiringRows() {
        val accounts = linkedSetOf(account)
        val original = row()
        val repository = stored { requestedSpace, requested, time, limit ->
            assertEquals(space, requestedSpace)
            assertEquals(setOf(account), requested)
            assertEquals(at.atOffset(UTC), time)
            assertEquals(501, limit)
            accounts.clear()
            accounts.add(UUID.randomUUID())
            assertEquals(setOf(account), requested)
            listOf(original)
        }
        val result = repository.devices(space, accounts, at)
        val value = success(result).single()
        assertEquals(account, value.accountId)
        assertEquals(original.matrixUserId, value.matrixUserId)
        assertEquals(original.matrixDeviceId, value.matrixDeviceId)
        assertFalse(value.publicationConfirmed)
        assertEquals("CollaborationSpaceRecipientDevice(<metadata>)", value.toString())
    }

    @Test
    fun retainedMatrixDeviceCoordinatesArePreservedWithoutDerivingThemFromGroupDeviceId() {
        val value = row()
        val originalDevice = UUID.randomUUID()
        value.matrixUserId =
            "@hris_${compact(UUID.randomUUID())}_${compact(account)}:matrix.example.test"
        value.matrixDeviceId = "HRIS_${compact(originalDevice)}"
        value.publicationConfirmed = true
        val output =
            success(stored { _, _, _, _ -> listOf(value) }.devices(space, setOf(account), at))
                .single()
        assertEquals(value.deviceId, output.deviceId)
        assertNotEquals(originalDevice, output.deviceId)
        assertEquals(value.matrixDeviceId, output.matrixDeviceId)
        assertEquals(value.matrixUserId, output.matrixUserId)
        assertTrue(output.publicationConfirmed)
    }

    @Test
    fun foreignMalformedExpiredAndDuplicateReadbackIsRejected() {
        val edits: List<(CollaborationSpaceRecipientDevicesRecord) -> Unit> =
            listOf(
                { it.spaceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.deviceId = UUID(0, 0) },
                { it.matrixUserId = "@foreign:matrix.example.test" },
                { it.matrixServerName = "different.example.test" },
                { it.matrixDeviceId = "HRISG_wrong" },
                { it.matrixDeviceId = "HRIS_${compact(UUID.randomUUID())}" },
                { it.fingerprint = "A".repeat(64) },
                { it.receiveUntil = at.atOffset(UTC) },
                { it.receiveUntil = at.plusNanos(1).atOffset(UTC) },
                { it.publicationConfirmed = null },
            )
        for (edit in edits) {
            val bad = row().also(edit)
            assertInstanceOf(
                Result.Failed::class.java,
                stored { _, _, _, _ -> listOf(bad) }.devices(space, setOf(account), at),
            )
        }
        val first = row()
        assertInstanceOf(
            Result.Failed::class.java,
            stored { _, _, _, _ -> listOf(first, first) }.devices(space, setOf(account), at),
        )
        val oldUser = "@hris_${compact(UUID.randomUUID())}_${compact(account)}:matrix.example.test"
        val oldDevice = "HRIS_${compact(UUID.randomUUID())}"
        val colliding =
            List(2) {
                row().also {
                    it.matrixUserId = oldUser
                    it.matrixDeviceId = oldDevice
                }
            }
        assertInstanceOf(
            Result.Failed::class.java,
            stored { _, _, _, _ -> colliding }.devices(space, setOf(account), at),
        )
    }

    @Test
    fun oneHundredAccountsAndFiveHundredDevicesAreBoundedWithoutSilentlyTruncatingRecipients() {
        val accounts = List(100) { UUID.randomUUID() }.toSet()
        val rows = accounts.flatMap { selected -> List(5) { row(selected) } }
        var supplied = rows
        val repository = stored { _, requested, _, limit ->
            assertEquals(accounts, requested)
            assertEquals(501, limit)
            supplied
        }
        assertEquals(500, success(repository.devices(space, accounts, at)).size)
        supplied = rows + row(accounts.first())
        assertEquals(
            "collaboration_recipient_device_limit",
            (repository.devices(space, accounts, at) as Result.Failed).failure.code,
        )
        supplied = supplied + row(accounts.first())
        assertInstanceOf(Result.Failed::class.java, repository.devices(space, accounts, at))
        val six = List(6) { row() }
        assertEquals(
            "collaboration_recipient_device_limit",
            (stored { _, _, _, _ -> six }.devices(space, setOf(account), at) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun acquisitionFailureIsSanitizedWhileCancellationAndInterruptionPropagate() {
        val result =
            stored { _, _, _, _ -> throw SQLException("fixture-private-details") }
                .devices(space, setOf(account), at)
        assertInstanceOf(Result.Failed::class.java, result)
        assertFalse(result.toString().contains("fixture-private-details"))
        assertThrows(CancellationException::class.java) {
            stored { _, _, _, _ -> throw CancellationException("fixture") }
                .devices(space, setOf(account), at)
        }
        assertThrows(InterruptedException::class.java) {
            stored { _, _, _, _ -> throw InterruptedException("fixture") }
                .devices(space, setOf(account), at)
        }
    }

    private fun row(selected: UUID = account) =
        CollaborationSpaceRecipientDevicesRecord().also {
            it.spaceId = space
            it.accountId = selected
            it.deviceId = UUID.randomUUID()
            it.matrixServerName = "matrix.example.test"
            it.matrixUserId = "@hrisg_${compact(space)}_${compact(selected)}:matrix.example.test"
            it.matrixDeviceId = "HRISG_${compact(requireNotNull(it.deviceId))}"
            it.fingerprint = "a".repeat(64)
            it.receiveUntil = at.plusSeconds(60).atOffset(UTC)
            it.publicationConfirmed = false
        }

    private fun stored(
        read:
            (UUID, Set<UUID>, OffsetDateTime, Int) -> List<CollaborationSpaceRecipientDevicesRecord>
    ) =
        StoredCollaborationSpaceRecipientRepository(
            object : CollaborationSpaceRecipientDataSource {
                override fun devices(
                    spaceId: UUID,
                    accountIds: Set<UUID>,
                    at: OffsetDateTime,
                    limit: Int,
                ) = read(spaceId, accountIds, at, limit)
            }
        )

    private fun compact(value: UUID) = value.toString().replace("-", "")

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
