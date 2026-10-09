package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class CompensationHttpTest : PayrollApiFixture() {
    @Test
    fun effectiveCompensationHistoryAndOriginalReplayPreservePrivateSnapshots() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val body = compensationBody()
        val first = payrollBody(compensation(f, body, key))
        payrollBody(
            compensation(
                f,
                compensationBody(
                    0,
                    mapOf("effectiveFrom" to "2026-07"),
                    mapOf("basicSalary" to "11000000"),
                ),
            )
        )
        val base = "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation"
        val earlier = payrollBody(get(f.operator.client, "$base?asOf=2026-06"))
        assertEquals("10000000", earlier.get("terms").get("basicSalary").asString())
        assertEquals(1, earlier.get("version").asLong())
        assertEquals(0, earlier.get("appliedRevision").asLong())
        assertEquals(
            "11000000",
            payrollBody(get(f.operator.client, "$base?asOf=2026-07"))
                .get("terms")
                .get("basicSalary")
                .asString(),
        )
        assertEquals(first, payrollBody(compensation(f, body, key)))
        val history = payrollBody(get(f.operator.client, "$base/history?limit=1"))
        assertEquals(1, history.get("items").size())
        assertEquals(
            1,
            payrollBody(
                    get(
                        f.operator.client,
                        "$base/history?after=${history.get("nextCursor").asString()}&limit=1",
                    )
                )
                .get("items")
                .size(),
        )
        payrollError(compensation(f, compensationBody(0)), 409, "stale_version")
    }

    @Test
    fun companyPolicyManagersAndEmployeesDoNotGainSalaryAccess() {
        val f = payrollFixture()
        payrollBody(compensation(f))
        val policyManager = payrollMember(f.company, setOf("company.read", "payroll.policy.manage"))
        val base = "/api/v1/companies/${f.company}/payroll"
        payrollError(
            get(policyManager.client, "$base/employees/${f.employee}/compensation?asOf=2026-01"),
            403,
            "access_denied",
        )
        payrollError(
            get(f.owner.client, "$base/employees/${f.employee}/compensation?asOf=2026-01"),
            403,
            "access_denied",
        )
        payrollError(get(f.admin, "$base/compensations?asOf=2026-01"), 403, "access_denied")
        val ownEmployment = employee(f.admin, f.adminCsrf, f.company, account = f.operator.account)
        payrollError(
            compensation(f, employee = ownEmployment),
            403,
            "self_compensation_change_denied",
        )
    }

    @Test
    fun employmentVersionIsCheckedButPreviouslyCommittedReceiptsStillReplay() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val original = compensationBody()
        val first = payrollBody(compensation(f, original, key))
        assertEquals(
            200,
            revise(f.admin, f.adminCsrf, f.company, f.employee, 0, terms("2026-07-01")).statusCode(),
        )
        payrollError(
            compensation(f, compensationBody(0, mapOf("effectiveFrom" to "2026-07"))),
            409,
            "stale_employment_version",
        )
        assertEquals(first, payrollBody(compensation(f, original, key)))
        payrollBody(
            compensation(
                f,
                compensationBody(
                    0,
                    mapOf("effectiveFrom" to "2026-07", "expectedEmploymentVersion" to 1),
                ),
            )
        )
    }

    @Test
    fun invalidAmountsAndEnrollmentPolicyLeaveTheOperationReusable() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        for (changes in
            listOf(
                mapOf("basicSalary" to "-1"),
                mapOf("basicSalary" to "50000000001"),
                mapOf("insurancePrograms" to emptyList<String>()),
                mapOf(
                    "fixedEarnings" to
                        (1..21).map {
                            mapOf("code" to "A$it", "name" to "Allowance", "amount" to "1")
                        }
                ),
            )) payrollError(
            compensation(f, compensationBody(termChanges = changes), key),
            422,
            "invalid_compensation",
        )
        payrollError(
            compensation(f, compensationBody(termChanges = mapOf("basicSalary" to "1e999")), key),
            422,
            "invalid_decimal_amount",
        )
        assertEquals(0, count(f.company, "employee_compensations"))
        payrollBody(compensation(f, key = key))
    }

    @Test
    fun concurrentReplayAndVersionedEditsCannotOverwriteEachOther() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val body = compensationBody()
        Executors.newFixedThreadPool(4).use { executor ->
            val replies =
                (1..4)
                    .map {
                        executor.submit<java.net.http.HttpResponse<String>> {
                            compensation(f, body, key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, replies.toSet().size)
        }
        assertEquals(1, count(f.company, "employee_compensation_revisions"))
        Executors.newFixedThreadPool(2).use { executor ->
            val replies =
                listOf("12000000", "13000000")
                    .map { salary ->
                        executor.submit<java.net.http.HttpResponse<String>> {
                            compensation(
                                f,
                                compensationBody(0, termChanges = mapOf("basicSalary" to salary)),
                            )
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS).statusCode() }
            assertEquals(listOf(200, 409), replies.sorted())
        }
        assertEquals(2, count(f.company, "employee_compensation_revisions"))
    }

    @Test
    fun missingRevisionsAndJournalFailuresCannotCommitCompensationOrConsumeReceipts() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val receipts = count(f.company, "operation_receipts")
        val audit = count(f.company, "audit_entries")
        val outbox = count(f.company, "outbox_events")
        payrollProbe.omitCompensationRevision = true
        assertEquals(409, compensation(f, key = key).statusCode())
        assertEquals(0, count(f.company, "employee_compensations"))
        assertEquals(receipts, count(f.company, "operation_receipts"))
        payrollProbe.clear()
        payrollProbe.beforeJournal = {
            if (it.action.startsWith("payroll."))
                throw DataIntegrityViolationException("Fixture failure")
        }
        val failed = compensation(f, key = key)
        assertEquals(409, failed.statusCode())
        assertFalse(failed.body().contains("Fixture failure"))
        assertEquals(0, count(f.company, "employee_compensations"))
        assertEquals(0, count(f.company, "employee_compensation_revisions"))
        assertEquals(audit, count(f.company, "audit_entries"))
        assertEquals(outbox, count(f.company, "outbox_events"))
        payrollProbe.clear()
        payrollBody(compensation(f, key = key))
    }

    @Test
    fun salaryHistoryIsImmutableAndEveryPageKeepsCompanyScope() {
        val f = payrollFixture()
        payrollBody(compensation(f))
        val second = employee(f.admin, f.adminCsrf, f.company)
        payrollBody(compensation(f, employee = second))
        val page =
            payrollBody(
                get(
                    f.operator.client,
                    "/api/v1/companies/${f.company}/payroll/compensations?asOf=2026-01&limit=1",
                )
            )
        val next = page.get("nextCursor").asString()
        val following =
            payrollBody(
                get(
                    f.operator.client,
                    "/api/v1/companies/${f.company}/payroll/compensations?asOf=2026-01&limit=1&after=$next",
                )
            )
        assertEquals(1, following.get("items").size())
        assertNotEquals(
            page.get("items").get(0).get("employeeId"),
            following.get("items").get(0).get("employeeId"),
        )
        for (statement in
            listOf(
                "delete from employee_compensations where company_id=?",
                "update employee_compensations set version=2 where company_id=?",
                "delete from employee_compensation_revisions where company_id=?",
                "update employee_compensation_revisions set reason='changed' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.company)
        }
        val other = payrollFixture()
        val rows =
            transactions.run(payrollActor(other)) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from employee_compensation_revisions where company_id=?",
                        Int::class.java,
                        f.company,
                    )!!
                }
            }
        assertEquals(Result.Success(0), rows)
        payrollError(compensation(other, employee = f.employee), 404, "employee_not_found")
    }
}
