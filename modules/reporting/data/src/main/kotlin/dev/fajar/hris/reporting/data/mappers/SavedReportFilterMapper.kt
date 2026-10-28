package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.dto.SavedReportFilterRow
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.validateSavedReportFilter
import java.time.LocalDate
import java.util.UUID

fun SavedReportFilterRow.toSavedReportFilter(company: UUID, account: UUID): SavedReportFilter {
    require(companyId == company && accountId == account && version in 0..10000)
    require(updatedAt >= createdAt)
    val definition =
        if (name == null) {
            require(
                version > 0 &&
                    kind == null &&
                    definitionVersion == null &&
                    asOf == null &&
                    from == null &&
                    until == null &&
                    status == null &&
                    companies.isEmpty()
            )
            null
        } else {
            require(version < 10000)
            val value =
                SavedReportFilterDefinition(
                    name,
                    ReportKind.valueOf(requireNotNull(kind)),
                    requireNotNull(definitionVersion),
                    ReportFilterParameters(
                        asOf?.toString(),
                        from,
                        until,
                        status,
                        java.util.List.copyOf(companies),
                    ),
                )
            val valid = validateSavedReportFilter(company, value)
            require(valid is Result.Success && valid.value == value)
            value
        }
    return SavedReportFilter(id, company, account, version, createdAt, updatedAt, definition)
}

fun SavedReportFilter.toRow(): SavedReportFilterRow {
    val parameters = definition?.parameters
    val row =
        SavedReportFilterRow(
            companyId,
            accountId,
            id,
            version,
            createdAt,
            updatedAt,
            definition?.name,
            definition?.kind?.name,
            definition?.definitionVersion,
            parameters?.asOf?.let(LocalDate::parse),
            parameters?.from,
            parameters?.until,
            parameters?.status,
            parameters?.companies?.toList() ?: emptyList(),
        )
    require(row.toSavedReportFilter(companyId, accountId) == this)
    return row
}
