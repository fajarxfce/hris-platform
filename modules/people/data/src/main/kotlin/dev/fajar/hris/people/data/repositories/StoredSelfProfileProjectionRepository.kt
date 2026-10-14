package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.people.data.datasources.SelfProfileProjectionDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.data.models.SelfProfilePayload
import dev.fajar.hris.people.domain.entities.SelfProfileRecord
import dev.fajar.hris.people.domain.repositories.SelfProfileProjectionRepository
import dev.fajar.hris.schema.tables.records.SelfProfileRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredSelfProfileProjectionRepository(
    private val source: SelfProfileProjectionDataSource,
    private val json: ObjectMapper,
) : SelfProfileProjectionRepository {
    override fun dueCompanies(now: Instant, limit: Int) = safeDatabaseCall {
        source.dueCompanies(now, limit)
    }

    override fun lockDue(company: UUID, now: Instant, limit: Int) = safeDatabaseCall {
        source.lockDue(company, now, limit)
    }

    override fun candidates(company: UUID, ids: List<UUID>, today: LocalDate) = safeDatabaseCall {
        source.candidates(company, ids, today).map { row ->
            require(
                row.employee == null ||
                    (row.employee.companyId == company && row.employee.id == row.id)
            )
            row.toSelfProfileCandidate()
        }
    }

    override fun find(company: UUID, id: UUID) = safeDatabaseCall {
        source.find(company, id)?.let { row ->
            require(row.companyId == company && row.id == id)
            val snapshot =
                row.payload?.let {
                    require(it.data().toByteArray(Charsets.UTF_8).size <= 32768)
                    val payload = json.readValue(it.data(), SelfProfilePayload::class.java)
                    require(
                        payload.id == id &&
                            payload.companyId == company &&
                            payload.accountId == row.accountId
                    )
                    payload.toSelfProfileSnapshot()
                }
            require((snapshot == null) == (row.accountId == null))
            SelfProfileRecord(id, requireNotNull(row.version), snapshot)
        }
    }

    override fun save(company: UUID, record: SelfProfileRecord) = safeDatabaseCall {
        val payload = record.snapshot?.toSelfProfilePayload()
        if (payload != null) require(payload.id == record.id && payload.companyId == company)
        val encoded =
            payload?.let {
                it.toSelfProfileSnapshot()
                json.writeValueAsString(it).also { text ->
                    require(text.toByteArray(Charsets.UTF_8).size <= 32768)
                }
            }
        source.save(
            SelfProfileRecordsRecord().apply {
                companyId = company
                id = record.id
                version = record.version
                accountId = payload?.accountId
                this.payload = encoded?.let(JSONB::valueOf)
            }
        )
    }

    override fun schedule(company: UUID, id: UUID, next: Instant?) = safeDatabaseCall {
        source.schedule(company, id, next)
    }
}
