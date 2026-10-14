package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.people.data.datasources.DirectoryProjectionDataSource
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.DirectoryProjectionRepository
import dev.fajar.hris.schema.tables.records.DirectoryEmployeeRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class StoredDirectoryProjectionRepository(private val source: DirectoryProjectionDataSource) :
    DirectoryProjectionRepository {
    override fun dueCompanies(now: Instant, limit: Int) = safeDatabaseCall {
        source.dueCompanies(now, limit)
    }

    override fun lockDue(company: UUID, now: Instant, limit: Int) = safeDatabaseCall {
        source.lockDue(company, now, limit)
    }

    override fun candidates(company: UUID, ids: List<UUID>, today: LocalDate) = safeDatabaseCall {
        source.candidates(company, ids, today).map { row ->
            DirectoryCandidate(
                row.id,
                DirectoryEntry(
                    row.employeeNumber,
                    row.name,
                    row.positionId,
                    row.position,
                    row.departmentId,
                    row.department,
                ),
                row.status?.let(EmploymentStatus::valueOf),
                row.start,
                row.end,
                row.nextRevision,
            )
        }
    }

    override fun find(company: UUID, id: UUID) = safeDatabaseCall {
        source.find(company, id)?.let { row ->
            require(row.companyId == company && row.id == id)
            DirectoryRecord(
                id,
                requireNotNull(row.version),
                row.name?.let { name ->
                    DirectoryEntry(
                        requireNotNull(row.employeeNumber),
                        name,
                        row.positionId,
                        row.position,
                        row.departmentId,
                        row.department,
                    )
                },
            )
        }
    }

    override fun save(company: UUID, record: DirectoryRecord) = safeDatabaseCall {
        source.save(
            DirectoryEmployeeRecordsRecord().apply {
                companyId = company
                id = record.id
                version = record.version
                employeeNumber = record.employee?.employeeNumber
                name = record.employee?.name
                positionId = record.employee?.positionId
                position = record.employee?.position
                departmentId = record.employee?.departmentId
                department = record.employee?.department
            }
        )
    }

    override fun schedule(company: UUID, id: UUID, next: Instant?) = safeDatabaseCall {
        source.schedule(company, id, next)
    }
}
