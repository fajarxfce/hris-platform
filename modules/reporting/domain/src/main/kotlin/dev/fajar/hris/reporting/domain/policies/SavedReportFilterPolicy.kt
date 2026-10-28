package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.util.UUID

const val MAX_SAVED_REPORT_FILTERS = 50

fun validateSavedReportFilter(
    company: UUID,
    input: SavedReportFilterDefinition,
): Result<SavedReportFilterDefinition> {
    val name = input.name.trim()
    if (input.name.length > 80 || name.isEmpty() || name.any { it.isISOControl() })
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_filter_name"))
    return validateReportSelection(
            company,
            ReportSelection(input.kind, input.definitionVersion, input.parameters),
        )
        .map { input.copy(name = name, parameters = it.parameters) }
}
