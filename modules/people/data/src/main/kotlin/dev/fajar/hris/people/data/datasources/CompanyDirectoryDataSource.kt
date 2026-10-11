package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.DirectoryDepartmentRow
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
        departmentId: UUID?,
    ): List<DirectoryEmployeeRow>

    fun departments(
        companyId: UUID,
        asOf: LocalDate,
        statuses: Set<String>,
        query: String,
        limit: Int,
    ): List<DirectoryDepartmentRow>
}
