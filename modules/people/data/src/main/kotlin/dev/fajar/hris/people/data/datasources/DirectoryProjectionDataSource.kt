package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.DirectoryCandidateRow
import dev.fajar.hris.schema.tables.records.DirectoryEmployeeRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface DirectoryProjectionDataSource {
    fun dueCompanies(now: Instant, limit: Int): List<UUID>

    fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID>

    fun candidates(company: UUID, ids: List<UUID>, today: LocalDate): List<DirectoryCandidateRow>

    fun find(company: UUID, id: UUID): DirectoryEmployeeRecordsRecord?

    fun save(record: DirectoryEmployeeRecordsRecord)

    fun schedule(company: UUID, id: UUID, next: Instant?)
}
