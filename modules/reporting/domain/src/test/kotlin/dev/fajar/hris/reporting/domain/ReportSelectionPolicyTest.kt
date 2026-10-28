package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportSelectionPolicyTest {
    private inline fun <reified T : Any> assertIs(value: Any?): T =
        assertInstanceOf(T::class.java, value)

    private val company = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val selected = UUID.fromString("00000000-0000-4000-8000-000000000002")

    @Test
    fun `shared selection policy applies the exact existing report contracts without a filter name`() {
        for (kind in ReportKind.entries) {
            val months = kind in setOf(ReportKind.ATTENDANCE_CLOSINGS, ReportKind.PAYROLL_COSTS)
            val parameters =
                if (kind == ReportKind.HEADCOUNT)
                    ReportFilterParameters(asOf = "2026-10-01", companies = listOf(selected))
                else
                    ReportFilterParameters(
                        from = if (months) "2026-01" else "2026-10-01",
                        until = if (months) "2026-12" else "2026-10-20",
                        status = if (kind == ReportKind.OVERTIME) "APPROVED" else null,
                    )
            val input = ReportSelection(kind, kind.definitionVersion, parameters)
            assertEquals(
                input,
                assertIs<Result.Success<ReportSelection>>(validateReportSelection(company, input))
                    .value,
            )
            val saved =
                assertIs<Result.Success<SavedReportFilterDefinition>>(
                        validateSavedReportFilter(
                            company,
                            SavedReportFilterDefinition(
                                " Name ",
                                kind,
                                kind.definitionVersion,
                                parameters,
                            ),
                        )
                    )
                    .value
            assertEquals(parameters, saved.parameters)
            assertEquals("Name", saved.name)
            assertEquals(
                "unsupported_report_definition",
                assertIs<Result.Failed>(
                        validateReportSelection(
                            company,
                            input.copy(definitionVersion = "unknown.v0"),
                        )
                    )
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun `canonical companies are detached while a missing group defaults only to its anchor`() {
        val ids = mutableListOf(selected)
        val input =
            ReportSelection(
                ReportKind.HEADCOUNT,
                "headcount.v1",
                ReportFilterParameters(asOf = "2026-10-01", companies = ids),
            )
        val canonical =
            assertIs<Result.Success<ReportSelection>>(validateReportSelection(company, input)).value
        ids.add(company)
        assertEquals(listOf(selected), canonical.parameters.companies)
        assertThrows(UnsupportedOperationException::class.java) {
            (canonical.parameters.companies as MutableList<UUID>).add(company)
        }
        val fallback =
            assertIs<Result.Success<ReportSelection>>(
                    validateReportSelection(
                        company,
                        input.copy(parameters = input.parameters.copy(companies = emptyList())),
                    )
                )
                .value
        assertEquals(listOf(company), fallback.parameters.companies)
    }
}
