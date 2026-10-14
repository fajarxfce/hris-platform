package dev.fajar.hris.people.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.SelfProfileProjectionDataSource
import dev.fajar.hris.people.data.models.*
import dev.fajar.hris.people.data.repositories.StoredSelfProfileProjectionRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.SelfProfileRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper

class SelfProfileRepositoryTest {
    private val json = jacksonObjectMapper()
    private val source = Source()
    private val repository = StoredSelfProfileProjectionRepository(source, json)
    private val company = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val date = LocalDate.parse("2026-10-01")
    private val employee =
        Employee(
            id,
            company,
            "E001",
            PersonProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Private profile",
                LocalDate.parse("1990-02-03"),
                "ID",
                "private@example.test",
            ),
            EmploymentTerms(
                date,
                ContractKind.PERMANENT,
                date,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
            ),
            null,
            4,
            2,
        )
    private val snapshot =
        SelfProfileSnapshot(
            EmploymentDetails(date, employee, null, null, null, null, null),
            company,
            7,
            "Asia/Jakarta",
        )

    private fun store(): SelfProfileRecordsRecord {
        assertEquals(
            Result.Success(Unit),
            repository.save(company, SelfProfileRecord(id, 12, snapshot)),
        )
        return requireNotNull(source.row)
    }

    @Test
    fun storageRoundTripRetainsIndependentVersionsAndTombstonesWithoutLoggingPrivateValues() {
        store()
        val result = repository.find(company, id)
        assertEquals(SelfProfileRecord(id, 12, snapshot), (result as Result.Success).value)
        assertFalse(result.toString().contains("private@example.test"))
        assertFalse(result.toString().contains("1990-02-03"))
        assertEquals(
            Result.Success(Unit),
            repository.save(company, SelfProfileRecord(id, 13, null)),
        )
        assertNull(source.row?.payload)
        assertNull(source.row?.accountId)
        assertEquals(Result.Success(SelfProfileRecord(id, 13, null)), repository.find(company, id))
    }

    @ParameterizedTest
    @ValueSource(strings = ["id", "company", "account", "schema", "reference", "size", "format"])
    fun corruptedPayloadOrOwnerMismatchIsMappedAtTheRepositoryBoundary(kind: String) {
        val row = store()
        val payload =
            json.readValue(requireNotNull(row.payload).data(), SelfProfilePayload::class.java)
        val corrupted =
            when (kind) {
                "id" -> payload.copy(id = UUID.randomUUID())
                "company" -> payload.copy(companyId = UUID.randomUUID())
                "account" -> payload.copy(accountId = UUID.randomUUID())
                "schema" -> payload.copy(schema = 2)
                "reference" ->
                    payload.copy(
                        branchId = UUID.randomUUID(),
                        branch = SelfProfileUnitData(UUID.randomUUID(), "B", "Branch", true),
                    )
                "size" -> payload.copy(email = "p".repeat(33000))
                else -> payload
            }
        row.payload =
            JSONB.valueOf(
                if (kind == "format") "{\"schema\":" else json.writeValueAsString(corrupted)
            )
        val result = repository.find(company, id)
        assertEquals(Result.Failed(Failure(FailureKind.UNEXPECTED, "database_failure")), result)
        assertFalse(result.toString().contains("private@example.test"))
    }

    @Test
    fun aDatasourceReturningAnotherPartitionCannotPassThroughTheBoundary() {
        store().companyId = UUID.randomUUID()
        assertEquals(
            Result.Failed(Failure(FailureKind.UNEXPECTED, "database_failure")),
            repository.find(company, id),
        )
    }

    @Test
    fun cancellationDuringAcquisitionIsNotTurnedIntoAnOrdinaryFailure() {
        val cancelled = CancellationException("fixture cancellation")
        source.failure = cancelled
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) { repository.find(company, id) },
        )
    }

    private class Source : SelfProfileProjectionDataSource {
        var row: SelfProfileRecordsRecord? = null
        var failure: RuntimeException? = null

        override fun find(company: UUID, id: UUID): SelfProfileRecordsRecord? {
            failure?.let { throw it }
            return row
        }

        override fun save(record: SelfProfileRecordsRecord) {
            row = record
        }

        override fun dueCompanies(now: Instant, limit: Int): List<UUID> = error("not used")

        override fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID> =
            error("not used")

        override fun candidates(
            company: UUID,
            ids: List<UUID>,
            today: LocalDate,
        ): List<SelfProfileCandidateRow> = error("not used")

        override fun schedule(company: UUID, id: UUID, next: Instant?) = error("not used")
    }
}
