package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.reporting.domain.repositories.ExpenseReportRepository
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ExpenseReportHttpTest : ExpenseReportApiFixture() {
    @Autowired private lateinit var reports: ExpenseReportRepository

    @Test
    fun latestSubmittedLinesRetainTheirDatesAndAmountsUntilAnExplicitResubmissionReplacesThem() {
        val f = reportFixture()
        assertEquals(0, report(f)["totals"]["claims"].asLong())
        body(saveExpense(f.copy(claim = UUID.randomUUID()), lines = listOf(reportLine(f))))
        assertEquals(0, report(f)["totals"]["claims"].asLong())
        val original =
            pendingExpense(
                f,
                listOf(
                    reportLine(f, "100.10", reportFrom().minusDays(1)),
                    reportLine(f, "0.01"),
                    reportLine(f, "123.45", reportUntil()),
                ),
            )
        val value = report(f)
        assertEquals("expenses.v1", value["definitionVersion"].asString())
        assertEquals("IDR", value["currency"].asString())
        assertEquals(1, value["totals"]["claims"].asLong())
        assertEquals(2, value["totals"]["lines"].asLong())
        assertEquals("123.46", value["totals"]["amount"].asString())
        assertEquals("123.46", counts(value, "PENDING")["amount"].asString())
        val frozen = rows(f)["items"].single()
        assertEquals(original.toString(), frozen["submissionId"].asString())
        assertEquals("223.56", frozen["submissionAmount"].asString())
        // Current category changes and current draft edits cannot replace submitted facts.
        expensePolicy(
            f,
            version = 1,
            changes = mapOf("name" to "Changed policy", "receiptRequired" to false),
        )
        body(
            reviewExpense(f, original, reviewer(f), decision = "RETURN", reason = "Clarify amount")
        )
        assertEquals("123.46", counts(report(f), "RETURNED")["amount"].asString())
        body(
            saveExpense(
                f,
                2,
                listOf(reportLine(f, "200.02")),
                changes = mapOf("title" to "Revised travel"),
            )
        )
        assertEquals("123.46", counts(report(f), "DRAFT")["amount"].asString())
        assertEquals("Travel reimbursement", rows(f)["items"].single()["title"].asString())
        val next = UUID.randomUUID()
        val key = UUID.randomUUID()
        val receipt = body(submitExpense(f, next, 3, key))
        assertEquals(receipt, body(submitExpense(f, next, 3, key)))
        assertEquals("200.02", report(f)["totals"]["amount"].asString())
        assertEquals(1, report(f)["totals"]["claims"].asLong())
        val current = rows(f)["items"].single()
        assertEquals(next.toString(), current["submissionId"].asString())
        assertEquals(2, current["submissionNumber"].asInt())
        assertEquals("Revised travel", current["title"].asString())
        assertEquals("223.56", submissionDetails(f, original)["totalAmount"].asString())
        body(withdrawExpense(f, next, 4))
        assertEquals("200.02", counts(report(f), "DRAFT")["amount"].asString())
        // Resubmitting the same frozen draft revision must not multiply its submitted-line joins.
        val third = UUID.randomUUID()
        body(submitExpense(f, third, 5))
        assertEquals(1, report(f)["totals"]["claims"].asLong())
        assertEquals("200.02", report(f)["totals"]["amount"].asString())
        assertEquals(3, rows(f)["items"].single()["submissionNumber"].asInt())
        body(withdrawExpense(f, third, 6))
        body(cancelExpense(f, 7))
        assertEquals("200.02", counts(report(f), "CANCELLED")["amount"].asString())
    }

    @Test
    fun onlyTheFinalIndependentStageMovesTheSubmittedAmountToApproved() {
        val f = expenseFixture()
        expensePolicy(f, changes = mapOf("receiptRequired" to false))
        val manager = reviewer(f)
        val finance = reviewer(f, adminAccount())
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf("assignment" to "MANAGER"),
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(finance.account)),
                        )
                ),
        )
        val id = pendingExpense(f, listOf(reportLine(f, "0.01")))
        val key = UUID.randomUUID()
        val first = body(reviewExpense(f, id, manager, key = key))
        assertEquals("0.01", counts(report(f), "PENDING")["amount"].asString())
        assertEquals("0.00", counts(report(f), "APPROVED")["amount"].asString())
        body(reviewExpense(f, id, finance, 2, 1))
        assertEquals("0.01", counts(report(f), "APPROVED")["amount"].asString())
        assertEquals(first, body(reviewExpense(f, id, manager, key = key)))
        assertEquals("0.01", report(f)["totals"]["amount"].asString())
    }

    @Test
    fun allStatusesAndExactCentsReconcileWithScopedClaimsWithoutSummingDistinctEmployees() {
        val f = reportFixture()
        val otherAccount = expenseMember(f.company, listOf("expenses.self.manage"))
        val otherEmployee =
            employee(f.admin, f.adminCsrf, f.company, manager = f.manager, account = otherAccount)
        val otherClient = client()
        val otherCsrf = login(otherClient, "$otherAccount@example.test")
        val amounts = listOf("1.01", "2.02", "3.03", "4.04", "5.05", "6.06")
        for ((index, status) in ExpenseClaimStatus.entries.withIndex()) {
            val owner =
                if (index < 3) f
                else
                    f.copy(
                        employee = otherEmployee,
                        account = otherAccount,
                        worker = otherClient,
                        workerCsrf = otherCsrf,
                    )
            val claim = owner.copy(claim = UUID.randomUUID())
            val id = pendingExpense(claim, listOf(reportLine(claim, amounts[index])))
            when (status) {
                ExpenseClaimStatus.DRAFT -> body(withdrawExpense(claim, id, 1))
                ExpenseClaimStatus.RETURNED ->
                    body(
                        reviewExpense(
                            claim,
                            id,
                            reviewer(f),
                            decision = "RETURN",
                            reason = "Clarify expense",
                        )
                    )
                ExpenseClaimStatus.APPROVED -> body(reviewExpense(claim, id, reviewer(f)))
                ExpenseClaimStatus.REJECTED ->
                    body(
                        reviewExpense(
                            claim,
                            id,
                            reviewer(f),
                            decision = "REJECT",
                            reason = "Not reimbursable",
                        )
                    )
                ExpenseClaimStatus.CANCELLED -> {
                    body(withdrawExpense(claim, id, 1))
                    body(cancelExpense(claim, 2))
                }
                else -> Unit
            }
        }
        val report = report(f)
        assertEquals(6, report["totals"]["claims"].asLong())
        assertEquals(2, report["totals"]["employments"].asLong())
        assertEquals(6, report["totals"]["lines"].asLong())
        assertEquals("21.21", report["totals"]["amount"].asString())
        for ((index, status) in ExpenseClaimStatus.entries.withIndex()) {
            assertEquals(amounts[index], counts(report, status.name)["amount"].asString())
            val page = rows(f, status.name)
            assertEquals("IDR", page["currency"].asString())
            assertEquals(1, page["items"].size())
            assertEquals(amounts[index], page["items"].single()["amount"].asString())
            assertEquals(1, counts(report, status.name)["employments"].asLong())
        }
        assertEquals(
            BigDecimal("21.21"),
            rows(f)["items"].fold(BigDecimal.ZERO) { total, row ->
                total + BigDecimal(row["amount"].asString())
            },
        )
    }

    @Test
    fun continuationsRetainTheirOrderingKeyAfterThePreviousClaimLeavesTheStatusFilter() {
        val f = reportFixture()
        val originals =
            (1..3).map {
                val claim = f.copy(claim = UUID.randomUUID())
                claim to pendingExpense(claim, listOf(reportLine(claim)))
            }
        val first = rows(f, "PENDING", limit = 1)
        val id = UUID.fromString(first["items"].single()["id"].asString())
        val changed = originals.single { it.first.claim == id }
        body(withdrawExpense(changed.first, changed.second, 1))
        val cursor = first["nextCursor"].asString()
        val second = rows(f, "PENDING", cursor, 1)
        val third = rows(f, "PENDING", second["nextCursor"].asString(), 1)
        assertTrue(third["nextCursor"] == null || third["nextCursor"].isNull)
        assertEquals(
            3,
            listOf(first, second, third)
                .map { it["items"].single()["id"].asString() }
                .distinct()
                .size,
        )
        error(get(f.admin, reportPath(f, true, "DRAFT", cursor, 1)), 422, "invalid_page")
        for (limit in listOf(0, 201)) error(
            get(f.admin, reportPath(f, true, limit = limit)),
            422,
            "invalid_page",
        )
        error(get(f.admin, reportPath(f, true, after = "broken")), 422, "invalid_page")
    }

    @Test
    fun submittedLineGapsAndEmptyAnnualWindowsRemainZeroAndMaximumClaimAmountsStayExact() {
        val f = reportFixture()
        pendingExpense(
            f,
            listOf(
                reportLine(f, "1.01", reportFrom().minusDays(1)),
                reportLine(f, "2.02", reportUntil().plusDays(1)),
            ),
        )
        assertEquals(0, report(f)["totals"]["claims"].asLong())
        assertTrue(rows(f)["items"].isEmpty)
        expensePolicy(
            f,
            version = 1,
            changes =
                mapOf(
                    "receiptRequired" to false,
                    "maximumLineAmount" to "999999999999.99",
                    "maximumClaimAmount" to "999999999999.99",
                ),
        )
        repeat(2) {
            val claim = f.copy(claim = UUID.randomUUID())
            pendingExpense(claim, listOf(reportLine(claim, "999999999999.99")))
        }
        assertEquals("1999999999999.98", report(f)["totals"]["amount"].asString())
        val empty =
            body(
                get(
                    f.admin,
                    "/api/v1/companies/${f.company}/reports/expenses?from=2024-01-01&until=2024-12-31",
                )
            )
        assertEquals(6, empty["statuses"].size())
        assertEquals(0, empty["totals"]["claims"].asLong())
    }

    @Test
    fun reportAndSourceGrantsCompanyIsolationAndDateBoundsProtectBothProjections() {
        val f = reportFixture()
        pendingExpense(f, listOf(reportLine(f)))
        val other = reportFixture()
        for (rows in listOf(false, true)) {
            for (grants in
                listOf(
                    listOf("expenses.read"),
                    listOf("reports.read"),
                    listOf("reports.read", "expenses.self.manage"),
                    listOf("reports.read", "expenses.team.read", "expenses.approve", "expenses.pay"),
                )) {
                val account = expenseMember(f.company, grants)
                val browser = client()
                login(browser, "$account@example.test")
                error(get(browser, reportPath(f, rows)), 403, "access_denied")
            }
            for (source in listOf("expenses.read", "expenses.manage")) {
                val account = expenseMember(f.company, listOf("reports.read", source))
                val browser = client()
                login(browser, "$account@example.test")
                body(get(browser, reportPath(f, rows)))
            }
            assertEquals(403, get(other.worker, reportPath(f, rows)).statusCode())
            val base =
                "/api/v1/companies/${f.company}/reports/expenses${if(rows) "/claims" else ""}"
            for ((from, until) in
                listOf(
                    "2024-01-01" to "2025-01-01",
                    "2026-01-02" to "2026-01-01",
                    "1899-12-31" to "1900-01-01",
                    "2200-12-31" to "2201-01-01",
                )) error(get(f.admin, "$base?from=$from&until=$until"), 422, "invalid_report_range")
        }
        val actor =
            Actor(
                adminAccount(),
                other.company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
            )
        val hidden =
            transactions.run(actor) { reports.aggregate(f.company, reportFrom(), reportUntil()) }
                as Result.Success
        assertEquals(0, hidden.value.totals.claims)
        assertTrue(
            (transactions.run(actor) {
                    reports.claims(f.company, reportFrom(), reportUntil(), null, null, 50)
                } as Result.Success)
                .value
                .items
                .isEmpty()
        )
    }

    @Test
    fun reportingAvailabilityCoversBothAggregateAndScopedClaims() {
        val f = reportFixture()
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reports"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        for (rows in listOf(false, true)) error(
            get(f.admin, reportPath(f, rows)),
            403,
            "company_module_disabled",
        )
    }
}
