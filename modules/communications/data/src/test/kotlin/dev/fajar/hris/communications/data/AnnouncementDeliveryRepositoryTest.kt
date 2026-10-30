package dev.fajar.hris.communications.data

import dev.fajar.hris.communications.data.datasources.AnnouncementDeliveryDataSource
import dev.fajar.hris.communications.data.models.AnnouncementDeliveryRow
import dev.fajar.hris.communications.data.repositories.StoredAnnouncementDeliveryRepository
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementDeliveryRepositoryTest {
    private fun repository(load: () -> AnnouncementDeliveryRow) =
        StoredAnnouncementDeliveryRepository(
            object : AnnouncementDeliveryDataSource {
                override fun emailCounts(companyId: UUID, announcementId: UUID) =
                    error("Not exercised by push tests")

                override fun counts(companyId: UUID, announcementId: UUID) = load()
            }
        )

    @Test
    fun boundedAggregatesIncludeZeroAndMaximumCapacity() {
        for (count in listOf(0, 5000)) {
            val result =
                repository {
                        AnnouncementDeliveryRow(
                            count,
                            0,
                            0,
                            0,
                            0,
                            count * 500,
                            count * 250,
                            count * 250,
                        )
                    }
                    .counts(UUID.randomUUID(), UUID.randomUUID())
            assertTrue(result is Result.Success)
            assertEquals(count, (result as Result.Success).value.trackedRecipients)
        }
    }

    @Test
    fun acquisitionAndMappingShareAFailureBoundary() {
        val valid = AnnouncementDeliveryRow(1, 0, 0, 0, 0, 2, 1, 1)
        for (row in
            listOf(
                valid.copy(pending = 5001),
                valid.copy(pending = -1),
                valid.copy(processing = 5000),
                valid.copy(processed = 501),
                valid.copy(accepted = 2),
                valid.copy(rejected = -1),
                valid.copy(processed = Int.MAX_VALUE),
            )) {
            assertTrue(
                repository { row }.counts(UUID.randomUUID(), UUID.randomUUID()) is Result.Failed
            )
        }
        assertTrue(
            repository { error("technical detail") }.counts(UUID.randomUUID(), UUID.randomUUID())
                is Result.Failed
        )
    }

    @Test
    fun cancellationAndInterruptionEscapeInsteadOfBecomingAnEmptyCount() {
        val cancelled = CancellationException("cancelled")
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) {
                repository { throw cancelled }.counts(UUID.randomUUID(), UUID.randomUUID())
            },
        )
        try {
            assertThrows(InterruptedException::class.java) {
                repository { throw InterruptedException() }
                    .counts(UUID.randomUUID(), UUID.randomUUID())
            }
        } finally {
            Thread.interrupted()
        }
    }
}
