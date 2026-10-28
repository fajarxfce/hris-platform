package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReportPolicyTest {
    private val company = UUID.randomUUID()
    private val from = LocalDate.parse("2024-01-01")
    private val until = LocalDate.parse("2024-12-31")
    private val entry =
        ExpenseReportClaim(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            "E1",
            "Example",
            "Travel",
            ExpenseClaimStatus.PENDING,
            Instant.parse("2023-12-01T12:34:56.123456Z"),
            1,
            1,
            BigDecimal("0.01"),
            BigDecimal("100.00"),
        )

    @Test
    fun fullLeapYearsAreBoundedAndReversedOrUnsupportedDatesFail() {
        for ((a, b) in
            listOf(
                from to until,
                from to from,
                LocalDate.parse("2200-01-01") to LocalDate.parse("2200-12-31"),
            )) assertEquals(Result.Success(Unit), validateExpenseReportRange(a, b))
        for ((a, b) in
            listOf(
                from to until.plusDays(1),
                from to from.minusDays(1),
                LocalDate.MIN to LocalDate.MAX,
                LocalDate.parse("1899-12-31") to from,
                until to LocalDate.parse("2201-01-01"),
            )) {
            val error = validateExpenseReportRange(a, b) as Result.Failed
            assertEquals("invalid_report_range", error.failure.code)
            assertEquals("366", error.failure.parameters["maximumDays"])
        }
    }

    @Test
    fun reportAndCompanySourceGrantsAreRequiredTogether() {
        val actor = Actor(UUID.randomUUID(), company, emptySet(), Instant.now(), UUID.randomUUID())
        for (grants in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("expenses.read"),
                setOf(
                    "reports.read",
                    "expenses.team.read",
                    "expenses.self.manage",
                    "expenses.approve",
                    "expenses.pay",
                ),
            )) assertEquals(
            "access_denied",
            (validateExpenseReportAccess(actor.copy(permissions = grants)) as Result.Failed)
                .failure
                .code,
        )
        for (source in listOf("expenses.read", "expenses.manage")) assertEquals(
            Result.Success(Unit),
            validateExpenseReportAccess(actor.copy(permissions = setOf("reports.read", source))),
        )
    }

    @Test
    fun continuationsRoundTripTheirOrderingKeyWithoutRequiringTheEntryToRemainInTheResult() {
        for (status in listOf(null) + ExpenseClaimStatus.entries) {
            val cursor = expenseReportCursor(company, from, until, status, entry)
            assertEquals(
                Result.Success(ExpenseReportCursor(entry.submittedAt, entry.id)),
                parseExpenseReportCursor(company, from, until, status, cursor),
            )
        }
        assertEquals(
            Result.Success(null),
            parseExpenseReportCursor(company, from, until, null, null),
        )
    }

    @Test
    fun malformedOrReusedContinuationsCannotChangeCompanyWindowOrStatus() {
        val cursor = expenseReportCursor(company, from, until, ExpenseClaimStatus.PENDING, entry)
        for (value in
            listOf(
                cursor.replace(company.toString(), UUID.randomUUID().toString()),
                cursor.replace(from.toString(), "2024-01-02"),
                cursor.replace("PENDING", "APPROVED"),
                cursor.replace(entry.id.toString(), "1-1-1-1-1"),
                cursor.replace(entry.submittedAt.toString(), "+10000-01-01T00:00:00Z"),
                cursor.replace("Z|", "+00:00|"),
                cursor + "|extra",
                cursor.replace("expenses.v1|", "1|"),
                "x".repeat(201),
                "",
            )) {
            assertEquals(
                "invalid_page",
                (parseExpenseReportCursor(company, from, until, ExpenseClaimStatus.PENDING, value)
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
    }
}
