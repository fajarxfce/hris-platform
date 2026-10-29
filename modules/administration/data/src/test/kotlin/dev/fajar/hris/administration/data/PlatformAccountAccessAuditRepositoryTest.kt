package dev.fajar.hris.administration.data

import dev.fajar.hris.administration.data.datasources.PlatformAuditDataSource
import dev.fajar.hris.administration.data.dto.*
import dev.fajar.hris.administration.data.repositories.StoredPlatformAuditRepository
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlatformAccountAccessAuditRepositoryTest {
    private val id = UUID.randomUUID()
    private val account = UUID.randomUUID()

    private fun repository(read: () -> PlatformAccountAccessAuditRow?) =
        StoredPlatformAuditRepository(
            object : PlatformAuditDataSource {
                override fun accountAccess(id: UUID) = read()

                override fun find(id: UUID): PlatformAuditEventRow? =
                    error("Unexpected metadata read")

                override fun search(query: AuditQueryRow): List<PlatformAuditEventRow> =
                    error("Unexpected page read")
            }
        )

    @Test
    fun recordedGrantsAreDetachedAndImmutableWithoutReconstructingCurrentState() {
        val permissions = mutableListOf("audit.read")
        val record =
            PlatformAccountAccessAuditRow(id, account, false, permissions, "Recorded justification")
        val value = (repository { record }.accountAccess(id) as Result.Success).value!!
        permissions.clear()
        assertEquals(listOf("audit.read"), value.permissions)
        assertFalse(value.active)
        assertEquals(account, value.accountId)
        assertEquals("Recorded justification", value.reason)
        assertThrows(UnsupportedOperationException::class.java) {
            (value.permissions as MutableList).clear()
        }
        assertEquals(Result.Success(null), repository { null }.accountAccess(id))
    }

    @Test
    fun scopeOrShapeMismatchFailsInsideTheMappingBoundary() {
        val record = PlatformAccountAccessAuditRow(id, account, true, listOf("audit.read"), null)
        for (bad in
            listOf(
                record.copy(id = UUID.randomUUID()),
                record.copy(permissions = List(33) { "permission.$it" }),
                record.copy(permissions = listOf("audit.read", "audit.read")),
                record.copy(permissions = listOf("PRIVATE INVALID")),
                record.copy(reason = "x".repeat(1001)),
            )) {
            assertEquals(
                "database_failure",
                (repository { bad }.accountAccess(id) as Result.Failed).failure.code,
            )
        }
        assertEquals(
            "database_failure",
            (repository { error("PRIVATE DIAGNOSTIC") }.accountAccess(id) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun cancellationIsNeverConvertedIntoAReadResult() {
        for (error in listOf(CancellationException(), InterruptedException())) assertThrows(
            error.javaClass
        ) {
            repository { throw error }.accountAccess(id)
        }
    }
}
