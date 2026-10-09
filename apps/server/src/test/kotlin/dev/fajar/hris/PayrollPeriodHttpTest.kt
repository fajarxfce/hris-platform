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

class PayrollPeriodHttpTest : PayrollPeriodApiFixture() {
    @Test
    fun immutableParticipantsAndPaymentMonthSurviveCancellationAndOriginalReplay() {
        val f = processingFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val second = employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company)
        val body = periodBody(f, id, setOf(f.payroll.employee, second))
        val created = payrollBody(periodCreate(f, body, key))
        val path = periodsPath(f) + "/$id"
        val first = payrollBody(get(f.reviewer.client, "$path?limit=1"))
        assertEquals("2026-09", first["period"]["earningsMonth"].asString())
        assertEquals("2026-10", first["period"]["taxMonth"].asString())
        assertEquals(2, first["period"]["participantCount"].asInt())
        assertTrue(first["members"]["items"][0]["inputStatus"].isNull)
        val next =
            payrollBody(
                get(
                    f.reviewer.client,
                    "$path?limit=1&after=${first["members"]["nextCursor"].asString()}",
                )
            )
        assertEquals(1, next["members"]["items"].size())
        assertNotEquals(
            first["members"]["items"][0]["employeeId"],
            next["members"]["items"][0]["employeeId"],
        )
        val cancelKey = UUID.randomUUID()
        val cancelled = payrollBody(periodCancel(f, id, key = cancelKey))
        assertEquals(cancelled, payrollBody(periodCancel(f, id, key = cancelKey)))
        assertEquals(created, payrollBody(periodCreate(f, body, key)))
        val later = payrollBody(get(f.reviewer.client, path))
        assertEquals("CANCELLED", later["period"]["status"].asString())
        assertEquals(2, later["history"]["items"].size())
        val replacement = UUID.randomUUID()
        payrollBody(periodCreate(f, periodBody(f, replacement)))
        val list =
            payrollBody(
                get(f.reviewer.client, periodsPath(f) + "?from=2026-09&until=2026-09&status=DRAFT")
            )
        assertEquals(1, list["items"].size())
        assertEquals(replacement.toString(), list["items"][0]["id"].asString())
        payrollError(periodCreate(f, periodBody(f, id)), 409, "payroll_period_exists")
    }

    @Test
    fun missingParticipantsAndChangesOrAuditFailuresCannotLeavePartialDrafts() {
        val f = processingFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = periodBody(f, id)
        val company = f.payroll.company
        val receipts = count(company, "operation_receipts")
        val audit = count(company, "audit_entries")
        periodProbe.omitMembers = true
        assertEquals(409, periodCreate(f, body, key).statusCode())
        periodProbe.clear()
        periodProbe.omitPeriodChange = true
        assertEquals(409, periodCreate(f, body, key).statusCode())
        periodProbe.clear()
        assertEquals(0, count(company, "payroll_periods"))
        assertEquals(0, count(company, "payroll_period_members"))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.period_created")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, periodCreate(f, body, key).statusCode())
        payrollProbe.clear()
        assertEquals(receipts, count(company, "operation_receipts"))
        assertEquals(audit, count(company, "audit_entries"))
        payrollBody(periodCreate(f, body, key))
        val cancelKey = UUID.randomUUID()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.period_cancelled")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, periodCancel(f, id, key = cancelKey).statusCode())
        payrollProbe.clear()
        assertEquals(
            "DRAFT",
            payrollBody(get(f.reviewer.client, periodsPath(f) + "/$id"))["period"]["status"]
                .asString(),
        )
        payrollBody(periodCancel(f, id, key = cancelKey))
    }

    @Test
    fun competingStartsAndRepeatedCommandsCannotCreateTwoActivePeriods() {
        val f = processingFixture()
        val body = periodBody(f)
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(4).use { executor ->
            val receipts =
                (1..4)
                    .map {
                        executor.submit<java.net.http.HttpResponse<String>> {
                            periodCreate(f, body, key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, receipts.toSet().size)
        }
        val id = UUID.fromString(json.readTree(body)["id"].asString())
        payrollBody(periodCancel(f, id))
        Executors.newFixedThreadPool(2).use { executor ->
            val replies =
                (1..2)
                    .map { executor.submit<java.net.http.HttpResponse<String>> { periodCreate(f) } }
                    .map { it.get(10, TimeUnit.SECONDS).statusCode() }
            assertEquals(listOf(200, 409), replies.sorted())
        }
        assertEquals(2, count(f.payroll.company, "payroll_periods"))
    }

    @Test
    fun creationBoundsAndPermissionScopeAreExplicit() {
        val f = processingFixture()
        val key = UUID.randomUUID()
        val foreign = processingFixture()
        payrollError(
            periodCreate(f, periodBody(f, employees = emptySet()), key),
            422,
            "invalid_payroll_period",
        )
        payrollError(
            periodCreate(f, periodBody(f, employees = setOf(foreign.payroll.employee)), key),
            422,
            "payroll_period_employee_unavailable",
        )
        payrollError(
            periodCreate(
                f,
                periodBody(f, changes = mapOf("plannedPaymentDate" to "2026-08-31")),
                key,
            ),
            422,
            "invalid_payroll_period",
        )
        payrollError(
            periodCreate(
                f,
                periodBody(f, changes = mapOf("plannedPaymentDate" to "2026-12-31")),
                key,
            ),
            422,
            "invalid_payroll_period",
        )
        payrollError(periodCreate(f, member = f.reviewer), 403, "access_denied")
        val id = UUID.fromString(payrollBody(periodCreate(f, key = key))["id"].asString())
        payrollError(get(f.payroll.owner.client, periodsPath(f) + "/$id"), 403, "access_denied")
        payrollError(get(f.payroll.admin, periodsPath(f) + "/$id"), 403, "access_denied")
        payrollError(get(f.reviewer.client, periodsPath(f) + "/$id?limit=201"), 422, "invalid_page")
        payrollError(
            get(f.reviewer.client, periodsPath(f) + "?from=2026-01&until=2030-01"),
            422,
            "invalid_page",
        )
        payrollError(
            get(foreign.reviewer.client, periodsPath(foreign) + "/$id"),
            404,
            "payroll_period_not_found",
        )
        val hidden =
            transactions.run(payrollActor(foreign.payroll, foreign.preparer)) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_period_members where company_id=?",
                        Int::class.java,
                        f.payroll.company,
                    )!!
                }
            }
        assertEquals(Result.Success(0), hidden)
    }

    @Test
    fun participantSnapshotsRejectLateInsertsAndRetainTerminalEvidence() {
        val f = processingFixture()
        val id = UUID.fromString(payrollBody(periodCreate(f))["id"].asString())
        val extra = employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company)
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into payroll_period_members(company_id,period_id,employment_id) values(?,?,?)",
                    f.payroll.company,
                    id,
                    extra,
                )
        }
        for (statement in
            listOf(
                "delete from payroll_period_members where company_id=?",
                "update payroll_periods set planned_payment_date='2026-10-03' where company_id=?",
                "delete from payroll_period_changes where company_id=?",
                "update payroll_period_changes set reason='changed' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.payroll.company)
        }
        payrollBody(periodCancel(f, id))
        payrollError(periodCancel(f, id, 1), 409, "payroll_period_not_draft")
    }

    @Test
    fun aFiveThousandEmployeeSnapshotHasBoundedPagesAndNoMissingMembers() {
        val f = processingFixture()
        val roster = largeRoster(f, 4999) + f.payroll.employee
        val id =
            UUID.fromString(
                payrollBody(periodCreate(f, periodBody(f, employees = roster)))["id"].asString()
            )
        assertEquals(5000, count(f.payroll.company, "payroll_period_members"))
        var after: String? = null
        val seen = mutableSetOf<String>()
        repeat(25) {
            val suffix = after?.let { "&after=$it" } ?: ""
            val page =
                payrollBody(get(f.reviewer.client, periodsPath(f) + "/$id?limit=200$suffix"))[
                    "members"]
            assertEquals(200, page["items"].size())
            for (item in page["items"]) assertTrue(seen.add(item["employeeId"].asString()))
            after = page["nextCursor"].takeUnless { it.isNull }?.asString()
        }
        assertNull(after)
        assertEquals(5000, seen.size)
    }

    @Test
    fun repeatedCancelledDraftsStopAtTheExplicitPeriodAttemptLimit() {
        val f = processingFixture()
        repeat(20) {
            val id = UUID.fromString(payrollBody(periodCreate(f))["id"].asString())
            payrollBody(periodCancel(f, id))
        }
        payrollError(periodCreate(f), 409, "payroll_period_attempt_limit")
        assertEquals(20, count(f.payroll.company, "payroll_periods"))
    }
}
