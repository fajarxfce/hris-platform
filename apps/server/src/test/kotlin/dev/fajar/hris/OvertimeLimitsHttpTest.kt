package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeLimitsHttpTest : OvertimeApiFixture() {
    @Test
    fun employeeMonthCapacityDoesNotConsumeAFailedOperation() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        database()
            .update(
                """with seeded as (
            insert into overtime_requests(company_id,id,employment_id,employee_number,employee_name,requester_account_id,author_id,created_at,work_date,timezone,schedule,requested_start,requested_end,requested_break,reason,status,version)
            select r.company_id,gen_random_uuid(),r.employment_id,r.employee_number,r.employee_name,r.requester_account_id,r.author_id,r.created_at,r.work_date,r.timezone,r.schedule,
                timestamptz '2026-09-01 05:00:00+00'+n*interval '1 minute',timestamptz '2026-09-01 05:00:00+00'+(n+1)*interval '1 minute',0,'Capacity fixture','PLANNED',0
            from overtime_requests r cross join generate_series(1,127) n where r.company_id=? and r.id=? returning *
        ) insert into overtime_changes(company_id,request_id,revision,kind,status,approved_minutes,actor_id,recorded_at,reason)
            select company_id,id,0,'PLANNED','PLANNED',0,author_id,created_at,reason from seeded""",
                f.company,
                id,
            )
        assertEquals(128, rows(f, "overtime_requests"))
        val receipts = rows(f, "operation_receipts")
        assertCode(
            plan(
                f,
                changes =
                    mapOf("requested" to interval("2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z")),
            ),
            409,
            "overtime_month_capacity",
        )
        assertEquals(receipts, rows(f, "operation_receipts"))
        assertEquals(128, rows(f, "overtime_changes"))
    }

    @Test
    fun closingAndNewPlansCannotCrossTheSameMonthGate() {
        val f = overtimeFixture()
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val closing = pool.submit<java.net.http.HttpResponse<String>> { close(f) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val planning = pool.submit<java.net.http.HttpResponse<String>> { plan(f) }
                barrier.release.countDown()
                overtimeBody(closing.get(10, TimeUnit.SECONDS))
                assertCode(planning.get(10, TimeUnit.SECONDS), 409, "work_period_locked")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, rows(f, "overtime_requests"))
        assertEquals(1, rows(f, "background_jobs"))
    }

    @Test
    fun listsAndHistoryHaveFiniteStableCursors() {
        val f = overtimeFixture()
        val first = pending(f)
        overtimeBody(withdraw(f, first, 1))
        val second = UUID.randomUUID()
        overtimeBody(plan(f, second))
        val prefix =
            "${f.path}/overtime?employeeId=${f.employee}&from=2026-09-01&until=2026-09-30&limit=1"
        val page = overtimeBody(get(f.employeeClient, prefix))
        assertEquals(1, page["items"].size())
        val after = page["nextCursor"].asString()
        val next = overtimeBody(get(f.employeeClient, "$prefix&after=$after"))
        assertEquals(1, next["items"].size())
        assertNotEquals(page["items"][0]["id"], next["items"][0]["id"])
        assertTrue(next["nextCursor"].isNull)
        val history =
            overtimeBody(get(f.employeeClient, "${f.path}/overtime/$first?historyLimit=1"))[
                "history"]
        assertEquals(2, history["items"][0]["revision"].asLong())
        val previous =
            overtimeBody(
                get(
                    f.employeeClient,
                    "${f.path}/overtime/$first?historyLimit=1&historyAfter=${history["nextCursor"].asString()}",
                )
            )["history"]
        assertEquals(1, previous["items"][0]["revision"].asLong())
        assertCode(
            get(f.employeeClient, "${f.path}/overtime/$first?historyLimit=201"),
            422,
            "invalid_page",
        )
    }
}
