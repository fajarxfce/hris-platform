package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class EmploymentCancellationHttpTest : PeopleApiFixture() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var people: dev.fajar.hris.people.domain.repositories.PeopleRepository
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var transactions: TransactionRunner

    @Test
    fun cancelledFutureChangesStayInHistoryButDoNotAffectEffectiveReadsOrReplayedOutcomes() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        assertEquals(
            200,
            revise(browser, csrf, company, employee, 0, terms("2026-11-01", status = "SUSPENDED"))
                .statusCode(),
        )
        val key = UUID.randomUUID()
        val cancelled = cancellation(browser, csrf, company, employee, 1, 1, key)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(2, json.readTree(cancelled.body()).get("version").asLong())
        clock.set(Instant.parse("2026-12-01T00:00:00Z"))
        assertEquals(
            cancelled.body(),
            cancellation(browser, csrf, company, employee, 1, 1, key).body(),
        )
        val effective =
            get(browser, "/api/v1/companies/$company/employees/$employee?asOf=2026-12-01")
        assertEquals(
            "ACTIVE",
            json.readTree(effective.body()).get("terms").get("status").asString(),
        )
        assertEquals(0, json.readTree(effective.body()).get("appliedRevision").asLong())
        val scope =
            Actor(UUID.randomUUID(), company, emptySet(), clock.instant(), UUID.randomUUID())
        assertEquals(
            Result.Success(listOf(0L)),
            transactions.run(scope) {
                people
                    .effectiveRevisions(
                        company,
                        employee,
                        java.time.LocalDate.parse("2026-12-01"),
                        java.time.LocalDate.parse("2026-12-31"),
                    )
                    .map { it.map { revision -> revision.revision } }
            },
        )
        assertEquals(
            Result.Success(false),
            transactions.run(scope) {
                people.hasRevisionsAfter(company, employee, java.time.LocalDate.parse("2026-10-01"))
            },
        )
        val history = get(browser, "/api/v1/companies/$company/employees/$employee/history")
        val rows = json.readTree(history.body()).get("items")
        assertEquals(2, rows.size())
        assertEquals(
            "Cancelled scheduled change",
            rows[0].get("cancellation").get("reason").asString(),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "delete from employment_revision_cancellations where employment_id=?",
                    employee,
                )
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employment_revisions where employment_id=?",
                    Int::class.java,
                    employee,
                ),
        )
    }

    @Test
    fun competingCancellationCannotAdvanceVersionTwiceOrConsumeAFailedOperation() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        assertEquals(
            200,
            revise(browser, csrf, company, employee, 0, terms("2026-11-01")).statusCode(),
        )
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val calls =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        cancellation(browser, csrf, company, employee, 1, 1).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), calls.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select version from employments where id=?",
                    Int::class.java,
                    employee,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='people.revision_cancelled'",
                    Int::class.java,
                    employee,
                ),
        )
    }

    @Test
    fun cancellationRejectsCurrentRevisionsCompanyScopeAndCycleRestoration() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val other = company(browser, csrf)
        val b = employee(browser, csrf, company)
        val a = employee(browser, csrf, company, manager = b)
        assertEquals(200, revise(browser, csrf, company, a, 0, terms("2026-11-01")).statusCode())
        assertEquals(
            200,
            revise(browser, csrf, company, b, 0, terms("2026-12-01", manager = a)).statusCode(),
        )
        val cycle = cancellation(browser, csrf, company, a, 1, 1)
        assertEquals(409, cycle.statusCode(), cycle.body())
        assertEquals("reporting_cycle_or_depth", json.readTree(cycle.body()).get("code").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employment_revision_cancellations where employment_id=?",
                    Int::class.java,
                    a,
                ),
        )
        assertEquals(409, cancellation(browser, csrf, other, a, 1, 1).statusCode())
        assertEquals(200, cancellation(browser, csrf, company, b, 1, 1).statusCode())
        assertEquals(200, cancellation(browser, csrf, company, a, 1, 1).statusCode())
        val current = employee(browser, csrf, company)
        assertEquals(
            200,
            revise(browser, csrf, company, current, 0, terms("2026-11-01")).statusCode(),
        )
        clock.set(
            Instant.parse("2026-10-31T18:00:00Z")
        ) // November 1 in the owning company's timezone.
        assertEquals(409, cancellation(browser, csrf, company, current, 1, 1).statusCode())
        assertEquals(422, cancellation(browser, csrf, company, current, 1, 0).statusCode())
    }

    @Test
    fun auditFailureRollsBackCancellationAndAllowsTheSameCommandToRetry() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company, start = "2027-01-01")
        assertEquals(
            200,
            revise(browser, csrf, company, employee, 0, terms("2027-02-01", start = "2027-01-01"))
                .statusCode(),
        )
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_revision_cancel() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.resource_id='$employee'::uuid and new.action='people.revision_cancelled' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger revision_cancel_probe before insert on audit_entries for each row execute function fail_revision_cancel()"
            )
        try {
            assertEquals(
                409,
                cancellation(browser, csrf, company, employee, 1, 1, key).statusCode(),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select version from employments where id=?",
                        Int::class.java,
                        employee,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from employment_revision_cancellations where employment_id=?",
                        Int::class.java,
                        employee,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            database().execute("drop trigger revision_cancel_probe on audit_entries")
            database().execute("drop function fail_revision_cancel()")
        }
        assertEquals(200, cancellation(browser, csrf, company, employee, 1, 1, key).statusCode())
    }
}
