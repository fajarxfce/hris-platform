package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.DirectoryEmployee
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.time.LocalDate
import java.util.UUID

interface CompanyDirectoryRepository {
    fun list(
        companyId: UUID,
        asOf: LocalDate,
        statuses: Set<EmploymentStatus>,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<DirectoryEmployee>>
}
