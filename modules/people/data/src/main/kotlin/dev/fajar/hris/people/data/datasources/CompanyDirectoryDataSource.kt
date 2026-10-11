package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.DirectoryEmployeeRow
import java.time.LocalDate
import java.util.UUID

interface CompanyDirectoryDataSource {
    fun list(
        companyId: UUID,
        asOf: LocalDate,
        statuses: Set<String>,
        query: String,
        after: String?,
        limit: Int,
    ): List<DirectoryEmployeeRow>
}
