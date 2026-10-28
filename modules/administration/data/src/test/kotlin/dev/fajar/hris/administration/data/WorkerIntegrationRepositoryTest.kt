package dev.fajar.hris.administration.data

import dev.fajar.hris.administration.data.datasources.WorkerIntegrationDataSource
import dev.fajar.hris.administration.data.dto.WorkerIntegrationRow
import dev.fajar.hris.administration.data.dto.WorkerIntegrationWriteRow
import dev.fajar.hris.administration.data.repositories.StoredWorkerIntegrationRepository
import dev.fajar.hris.core.domain.Result
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkerIntegrationRepositoryTest {
    private val at = OffsetDateTime.parse("2026-10-28T12:00:00Z")
    private val row =
        WorkerIntegrationRow(at, UUID.randomUUID(), at.minusMinutes(1), at, false, true, true, true)

    private fun repository(read: (Int) -> List<WorkerIntegrationRow>) =
        StoredWorkerIntegrationRepository(
            object : WorkerIntegrationDataSource {
                override fun read(limit: Int) = read.invoke(limit)

                override fun lockRegistry() = error("not called")

                override fun expire(before: OffsetDateTime) = error("not called")

                override fun publish(row: WorkerIntegrationWriteRow) = error("not called")
            }
        )

    @Test
    fun emptyAndPopulatedSnapshotsKeepTheDatabaseObservationTimeAndImmutableRows() {
        val empty =
            repository { limit ->
                    assertEquals(65, limit)
                    listOf(WorkerIntegrationRow(at, null, null, null, null, null, null, null))
                }
                .read() as Result.Success
        assertEquals(at.toInstant(), empty.value.observedAt)
        assertTrue(empty.value.workers.isEmpty())
        val populated = repository { listOf(row) }.read() as Result.Success
        assertEquals(row.instanceId, populated.value.workers.single().instanceId)
        assertThrows(UnsupportedOperationException::class.java) {
            (populated.value.workers as MutableList).clear()
        }
    }

    @Test
    fun oversizedDuplicatedPartialOrMixedTimeSnapshotsNeverBecomeAReview() {
        for (rows in
            listOf(
                emptyList(),
                List(65) { row.copy(instanceId = UUID.randomUUID()) },
                listOf(row, row),
                listOf(row.copy(push = null)),
                listOf(
                    row,
                    row.copy(instanceId = UUID.randomUUID(), observedAt = at.plusSeconds(1)),
                ),
                listOf(row.copy(lastSeenAt = at.minusMinutes(2))),
            )) {
            assertTrue(repository { rows }.read() is Result.Failed)
        }
    }

    @Test
    fun cancellationIsNotTranslatedIntoAHealthyOrEmptyRegistry() {
        for (failure in listOf(CancellationException(), InterruptedException())) {
            assertThrows(failure.javaClass) { repository { throw failure }.read() }
        }
    }
}
