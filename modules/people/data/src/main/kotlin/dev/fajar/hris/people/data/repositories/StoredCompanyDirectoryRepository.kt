package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.people.data.datasources.CompanyDirectoryDataSource
import dev.fajar.hris.people.domain.entities.DirectoryEmployee
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import dev.fajar.hris.people.domain.repositories.CompanyDirectoryRepository
import java.time.LocalDate
import java.util.UUID

class StoredCompanyDirectoryRepository(private val source: CompanyDirectoryDataSource) :
    CompanyDirectoryRepository {
    override fun list(
        companyId: UUID,
        asOf: LocalDate,
        statuses: Set<EmploymentStatus>,
        query: String,
        after: String?,
        limit: Int,
    ) = safeDatabaseCall {
        val rows =
            source.list(companyId, asOf, statuses.map { it.name }.toSet(), query, after, limit + 1)
        Page(
            rows.take(limit).map {
                DirectoryEmployee(it.id, it.employeeNumber, it.name, it.position, it.department)
            },
            if (rows.size > limit) rows[limit - 1].employeeNumber else null,
        )
    }
}
