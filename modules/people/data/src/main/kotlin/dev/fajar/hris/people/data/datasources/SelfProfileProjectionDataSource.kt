package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.SelfProfileCandidateRow
import dev.fajar.hris.schema.tables.records.SelfProfileRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface SelfProfileProjectionDataSource {
    fun dueCompanies(now: Instant, limit: Int): List<UUID>

    fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID>

    fun candidates(company: UUID, ids: List<UUID>, today: LocalDate): List<SelfProfileCandidateRow>

    fun find(company: UUID, id: UUID): SelfProfileRecordsRecord?

    fun save(record: SelfProfileRecordsRecord)

    fun schedule(company: UUID, id: UUID, next: Instant?)
}
