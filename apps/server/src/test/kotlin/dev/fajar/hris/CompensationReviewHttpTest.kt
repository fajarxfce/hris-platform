package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompensationReviewHttpTest : PayrollApiFixture() {
    @Test
    fun reviewSeparatesEmploymentHeadCurrentSalaryAndTheSelectedMonth() {
        val f = payrollFixture()
        val route =
            "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation/review"
        val empty = payrollBody(get(f.operator.client, "$route?asOf=2025-12"))
        assertTrue(empty.get("current").isNull)
        assertTrue(empty.get("effective").isNull)
        assertEquals(0, empty.get("history").get("items").size())
        assertTrue(empty.get("canChange").asBoolean())
        assertEquals(f.employee.toString(), empty.get("employee").get("id").asString())
        assertEquals(0, empty.get("employee").get("employmentVersion").asLong())
        payrollBody(compensation(f))
        payrollBody(
            compensation(
                f,
                compensationBody(
                    0,
                    mapOf("effectiveFrom" to "2026-07"),
                    mapOf("basicSalary" to "11000000.25"),
                ),
            )
        )
        payrollBody(revise(f.admin, f.adminCsrf, f.company, f.employee, 0, terms("2027-01-01")))
        val earlier = payrollBody(get(f.operator.client, "$route?asOf=2026-06&historyLimit=1"))
        assertEquals(1, earlier.get("employee").get("employmentVersion").asLong())
        assertEquals(1, earlier.get("current").get("version").asLong())
        assertEquals(1, earlier.get("current").get("appliedRevision").asLong())
        assertEquals(
            "11000000.25",
            earlier.get("current").get("terms").get("basicSalary").asString(),
        )
        assertEquals(1, earlier.get("effective").get("version").asLong())
        assertEquals(0, earlier.get("effective").get("appliedRevision").asLong())
        assertEquals(
            "10000000",
            earlier.get("effective").get("terms").get("basicSalary").asString(),
        )
        assertEquals("0", earlier.get("history").get("nextCursor").asString())
        val next =
            payrollBody(get(f.operator.client, "$route?asOf=2026-06&historyLimit=1&historyAfter=0"))
        assertEquals(
            1,
            next
                .get("history")
                .get("items")
                .get(0)
                .get("compensation")
                .get("appliedRevision")
                .asLong(),
        )
        assertTrue(next.get("history").get("nextCursor").isNull)
        val beforeStart = payrollBody(get(f.operator.client, "$route?asOf=2025-12"))
        assertTrue(beforeStart.get("effective").isNull)
        assertEquals(earlier.get("current"), beforeStart.get("current"))
        assertEquals(2, beforeStart.get("history").get("items").size())
    }

    @Test
    fun minimalLookupIncludesUnconfiguredAndFutureEmploymentWithLiteralSearch() {
        val f = payrollFixture()
        val second = employee(f.admin, f.adminCsrf, f.company, start = "2027-01-01")
        val route = "/api/v1/companies/${f.company}/payroll/compensation-employees"
        val first = payrollBody(get(f.operator.client, "$route?limit=1"))
        val next =
            payrollBody(
                get(f.operator.client, "$route?limit=1&after=${first.get("nextCursor").asString()}")
            )
        val rows = listOf(first.get("items").get(0), next.get("items").get(0))
        assertEquals(
            setOf(f.employee.toString(), second.toString()),
            rows.map { it.get("id").asString() }.toSet(),
        )
        assertTrue(next.get("nextCursor").isNull)
        for (row in rows) assertEquals(
            setOf("id", "employeeNumber", "name", "employmentVersion"),
            row.propertyNames().toSet(),
        )
        val number = rows.first().get("employeeNumber").asString()
        val searched = payrollBody(get(f.operator.client, "$route?query=$number"))
        assertEquals(1, searched.get("items").size())
        assertEquals(rows.first(), searched.get("items").get(0))
        for (literal in listOf("%25", "%5F")) assertEquals(
            0,
            payrollBody(get(f.operator.client, "$route?query=$literal")).get("items").size(),
        )
        assertEquals(0, count(f.company, "employee_compensations"))
    }

    @Test
    fun companyAdministratorsPolicyManagersAndEmployeeScopeDoNotGainCompensationAccess() {
        val f = payrollFixture()
        val manager = payrollMember(f.company, setOf("company.read", "payroll.policy.manage"))
        val reader = payrollMember(f.company, setOf("company.read", "payroll.read"))
        val base = "/api/v1/companies/${f.company}/payroll"
        val review = "$base/employees/${f.employee}/compensation/review?asOf=2026-06"
        for (browser in listOf(f.admin, f.owner.client, manager.client)) {
            payrollError(get(browser, review), 403, "access_denied")
            payrollError(get(browser, "$base/compensation-employees"), 403, "access_denied")
        }
        assertFalse(payrollBody(get(reader.client, review)).get("canChange").asBoolean())
        payrollBody(get(reader.client, "$base/compensation-employees"))
        payrollError(compensation(f, member = reader), 403, "access_denied")
        val other = payrollFixture()
        payrollError(get(other.operator.client, review), 403, "company_access_denied")
        payrollError(
            get(
                f.operator.client,
                "$base/employees/${other.employee}/compensation/review?asOf=2026-06",
            ),
            404,
            "employee_not_found",
        )
        val own = employee(f.admin, f.adminCsrf, f.company, account = f.operator.account)
        assertFalse(
            payrollBody(
                    get(f.operator.client, "$base/employees/$own/compensation/review?asOf=2026-06")
                )
                .get("canChange")
                .asBoolean()
        )
        payrollError(compensation(f, employee = own), 403, "self_compensation_change_denied")
    }

    @Test
    fun queryAndHistoryBoundsRejectInvalidOrNonprogressingSelections() {
        val f = payrollFixture()
        val base = "/api/v1/companies/${f.company}/payroll"
        val review = "$base/employees/${f.employee}/compensation/review"
        payrollError(
            get(f.operator.client, "$review?asOf=2026-01&historyAfter=0"),
            422,
            "invalid_page",
        )
        payrollBody(compensation(f))
        for (query in
            listOf(
                "historyLimit=0",
                "historyLimit=201",
                "historyAfter=-1",
                "historyAfter=1000",
                "historyAfter=1",
            )) payrollError(
            get(f.operator.client, "$review?asOf=2026-01&$query"),
            422,
            "invalid_page",
        )
        for (month in listOf("2023-12", "2101-01")) payrollError(
            get(f.operator.client, "$review?asOf=$month"),
            422,
            "invalid_payroll_month",
        )
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"a".repeat(121)}",
                "after=%20",
                "after=${"a".repeat(33)}",
            )) payrollError(
            get(f.operator.client, "$base/compensation-employees?$query"),
            422,
            "invalid_page",
        )
    }

    @Test
    fun concurrentSalaryRevisionCannotMixReviewSnapshots() {
        val f = payrollFixture()
        payrollBody(compensation(f))
        val route =
            "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation/review?asOf=2026-06"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val held = AtomicBoolean()
        payrollProbe.afterCompensationRead = { kind, company ->
            if (company == f.company && kind == "current" && held.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == f.company && !shared) writerEntered.countDown()
        }
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.operator.client, route) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    0,
                    payrollBody(get(f.operator.client, route))
                        .get("current")
                        .get("version")
                        .asLong(),
                )
                val writer =
                    pool.submit<HttpResponse<String>> {
                        compensation(
                            f,
                            compensationBody(0, termChanges = mapOf("basicSalary" to "12000000")),
                        )
                    }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val reviewed = payrollBody(pending.get(10, TimeUnit.SECONDS))
                assertEquals(0, reviewed.get("current").get("version").asLong())
                assertEquals(0, reviewed.get("effective").get("version").asLong())
                assertEquals(1, reviewed.get("history").get("items").size())
                assertEquals(
                    1,
                    payrollBody(writer.get(10, TimeUnit.SECONDS)).get("version").asLong(),
                )
            } finally {
                release.countDown()
                payrollProbe.clear()
            }
        }
        assertEquals(
            2,
            payrollBody(get(f.operator.client, route)).get("history").get("items").size(),
        )
    }

    @Test
    fun aNewBeneficiaryBindingAlsoPreventsReplayingOnesOwnSalaryChange() {
        val initial = payrollFixture()
        val f = initial.copy(employee = employee(initial.admin, initial.adminCsrf, initial.company))
        val operation = UUID.randomUUID()
        payrollBody(compensation(f, key = operation))
        payrollBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to f.operator.account,
                        "expectedVersion" to 0,
                        "reason" to "Identity independently verified",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val receipts = count(f.company, "operation_receipts")
        payrollError(compensation(f, key = operation), 403, "self_compensation_change_denied")
        assertEquals(receipts, count(f.company, "operation_receipts"))
        assertEquals(1, count(f.company, "employee_compensation_revisions"))
        val route =
            "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation/review?asOf=2026-06"
        assertFalse(payrollBody(get(f.operator.client, route)).get("canChange").asBoolean())
    }
}
