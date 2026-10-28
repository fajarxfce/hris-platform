package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SavedReportFilterPolicyTest {
    private val company = UUID.randomUUID()

    private fun definition(kind: ReportKind, parameters: ReportFilterParameters) =
        SavedReportFilterDefinition("  My selection  ", kind, kind.definitionVersion, parameters)

    @Test
    fun everyKindReusesItsCanonicalMainReportSelectionAndNormalizesOnlyThePersonalName() {
        for (kind in ReportKind.entries) {
            val parameters =
                when (kind) {
                    ReportKind.HEADCOUNT -> ReportFilterParameters(asOf = "2026-01-01")
                    ReportKind.ATTENDANCE_CLOSINGS,
                    ReportKind.PAYROLL_COSTS ->
                        ReportFilterParameters(from = "2026-01", until = "2026-12")
                    else -> ReportFilterParameters(from = "2026-01-01", until = "2026-01-31")
                }
            val result =
                validateSavedReportFilter(company, definition(kind, parameters)) as Result.Success
            assertEquals("My selection", result.value.name)
            assertEquals(kind.definitionVersion, result.value.definitionVersion)
            assertEquals(
                if (kind == ReportKind.HEADCOUNT) listOf(company) else emptyList<UUID>(),
                result.value.parameters.companies,
            )
        }
    }

    @Test
    fun explicitGroupSelectionIsDetachedBoundedAndRetainedEvenWithoutTheAnchorCompany() {
        val foreign = UUID.randomUUID()
        val selected = mutableListOf(foreign)
        val input =
            definition(
                ReportKind.HEADCOUNT,
                ReportFilterParameters(asOf = "2026-01-01", companies = selected),
            )
        val result = (validateSavedReportFilter(company, input) as Result.Success).value
        selected.clear()
        assertEquals(listOf(foreign), result.parameters.companies)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.parameters.companies as MutableList).clear()
        }
        for (companies in
            listOf(List(33) { UUID.randomUUID() }, listOf(foreign, foreign))) assertInstanceOf(
            Result.Failed::class.java,
            validateSavedReportFilter(
                company,
                input.copy(parameters = input.parameters.copy(companies = companies)),
            ),
        )
    }

    @Test
    fun irrelevantSelectionsStatusAndNonCanonicalDatesNeverBecomeStoredFilters() {
        val date = ReportFilterParameters(from = "2026-01-01", until = "2026-01-02")
        val invalid =
            listOf(
                definition(ReportKind.HEADCOUNT, date),
                definition(ReportKind.HEADCOUNT, ReportFilterParameters(asOf = "2026-1-01")),
                definition(
                    ReportKind.HEADCOUNT,
                    ReportFilterParameters(asOf = "2026-01-01", status = "ALL"),
                ),
                definition(ReportKind.LEAVE, date.copy(status = "PENDING")),
                definition(ReportKind.EXPENSES, date.copy(companies = listOf(company))),
                definition(ReportKind.OVERTIME, date.copy(status = "pending")),
                definition(ReportKind.OVERTIME, date.copy(status = "ALL")),
                definition(ReportKind.ATTENDANCE_EXCEPTIONS, date.copy(asOf = "2026-01-01")),
                definition(ReportKind.LEAVE, date.copy(from = "2026-02-29")),
                definition(ReportKind.ATTENDANCE_CLOSINGS, date),
                definition(
                    ReportKind.PAYROLL_COSTS,
                    ReportFilterParameters(from = "2026-1", until = "2026-12"),
                ),
            )
        for (input in invalid) assertEquals(
            "invalid_report_filter_parameters",
            (validateSavedReportFilter(company, input) as Result.Failed).failure.code,
            input.kind.name,
        )
        for (status in OvertimeStatus.entries) assertInstanceOf(
            Result.Success::class.java,
            validateSavedReportFilter(
                company,
                definition(ReportKind.OVERTIME, date.copy(status = status.name)),
            ),
        )
    }

    @Test
    fun storedRangesHaveExactlyTheSameBoundsAsTheirReports() {
        val invalid =
            listOf(
                definition(ReportKind.HEADCOUNT, ReportFilterParameters(asOf = "2101-01-01")),
                definition(
                    ReportKind.ATTENDANCE_EXCEPTIONS,
                    ReportFilterParameters(from = "2026-01-01", until = "2026-02-01"),
                ),
                definition(
                    ReportKind.LEAVE,
                    ReportFilterParameters(from = "2026-01-01", until = "2027-01-02"),
                ),
                definition(
                    ReportKind.EXPENSES,
                    ReportFilterParameters(from = "2201-01-01", until = "2201-01-01"),
                ),
                definition(
                    ReportKind.OVERTIME,
                    ReportFilterParameters(from = "1999-01-01", until = "1999-01-01"),
                ),
                definition(
                    ReportKind.ATTENDANCE_CLOSINGS,
                    ReportFilterParameters(from = "1999-12", until = "2000-01"),
                ),
                definition(
                    ReportKind.PAYROLL_COSTS,
                    ReportFilterParameters(from = "2023-12", until = "2024-01"),
                ),
                definition(
                    ReportKind.PAYROLL_COSTS,
                    ReportFilterParameters(from = "2026-01", until = "2027-01"),
                ),
            )
        for (input in invalid) assertInstanceOf(
            Result.Failed::class.java,
            validateSavedReportFilter(company, input),
        )
    }

    @Test
    fun unsupportedDefinitionsAndEmptyOversizedOrControlNamesAreRejected() {
        val input = definition(ReportKind.HEADCOUNT, ReportFilterParameters(asOf = "2026-01-01"))
        for (name in listOf(" ", "x".repeat(81), "Private\nname", "N\u0000ame")) assertEquals(
            "invalid_report_filter_name",
            (validateSavedReportFilter(company, input.copy(name = name)) as Result.Failed)
                .failure
                .code,
        )
        assertInstanceOf(
            Result.Success::class.java,
            validateSavedReportFilter(company, input.copy(name = "x".repeat(80))),
        )
        assertEquals(
            "unsupported_report_definition",
            (validateSavedReportFilter(company, input.copy(definitionVersion = "headcount.v2"))
                    as Result.Failed)
                .failure
                .code,
        )
    }
}
