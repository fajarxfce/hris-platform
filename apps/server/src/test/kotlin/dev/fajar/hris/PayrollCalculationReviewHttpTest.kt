package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class PayrollCalculationReviewHttpTest : PayrollRunApiFixture() {
    @Test
    fun missingSourcesRemainExplicitAndReviewDoesNotCreateWorkOrPayrollRecords() {
        val f = processingFixture()
        val period = UUID.fromString(payrollBody(periodCreate(f, periodBody(f)))["id"].asString())
        val path = periodsPath(f) + "/$period/calculation-review"
        val review = payrollBody(get(f.preparer.client, path))
        assertTrue(review["source"].isNull)
        assertTrue(review["policy"].isNull)
        assertEquals(0, review["runCount"].asInt())
        assertEquals(
            setOf("payroll_workforce_not_closed", "payroll_policy_not_found"),
            issues(review),
        )
        assertEquals(0, review["availableActions"].size())
        assertEquals(0, count(f.payroll.company, "work_periods"))
        assertEquals(0, count(f.payroll.company, "payroll_runs"))
        payrollError(get(f.payroll.admin, path), 403, "access_denied")
    }

    @Test
    fun calculationReviewExposesOnlyNeededPolicyReferencesWithoutRequiringPolicyOrPeopleGrants() {
        val f = calculationFixture(prepare = false)
        val review = payrollBody(get(f.people.preparer.client, reviewPath(f)))
        assertEquals(setOf("START"), actions(review))
        assertEquals(0, review["blockingIssues"].size())
        assertEquals(f.work.version, review["source"]["version"].asLong())
        assertEquals(f.work.job.toString(), review["source"]["jobId"].asString())
        assertEquals(
            setOf(
                "version",
                "appliedRevision",
                "effectiveFrom",
                "effectiveUntil",
                "incomeTaxRuleId",
                "insuranceRuleId",
            ),
            review["policy"].propertyNames().toSet(),
        )
        assertFalse(review["period"].has("employees"))
        assertEquals(1, review["period"]["participantCount"].asInt())
        payrollError(
            get(
                f.people.preparer.client,
                "/api/v1/companies/${f.people.payroll.company}/payroll/policy?asOf=2026-09",
            ),
            403,
            "access_denied",
        )
        payrollError(
            get(
                f.people.preparer.client,
                "/api/v1/companies/${f.people.payroll.company}/employees?asOf=2026-09-01",
            ),
            403,
            "access_denied",
        )
        assertTrue(actions(payrollBody(get(f.people.reviewer.client, reviewPath(f)))).isEmpty())

        val id = UUID.randomUUID()
        payrollBody(
            createRun(
                f,
                runBody(
                    f,
                    id,
                    mapOf(
                        "expectedPeriodVersion" to review["period"]["version"].asLong(),
                        "expectedWorkPeriodVersion" to review["source"]["version"].asLong(),
                        "expectedPolicyVersion" to review["policy"]["version"].asLong(),
                    ),
                ),
            )
        )
        val after = payrollBody(get(f.people.preparer.client, reviewPath(f)))
        assertEquals(1, after["runCount"].asInt())
        assertTrue(actions(after).isEmpty())
        assertTrue("payroll_period_not_draft" in issues(after))
    }

    @Test
    fun anotherCompanyPeriodIsNotDisclosedAndChangedPolicyRequiresANewReview() {
        val f = calculationFixture(prepare = false)
        val other = calculationFixture(prepare = false)
        payrollError(
            get(
                f.people.preparer.client,
                periodsPath(f.people) + "/${other.period}/calculation-review",
            ),
            404,
            "payroll_period_not_found",
        )
        val review = payrollBody(get(f.people.preparer.client, reviewPath(f)))
        payrollBody(
            policy(f.people.payroll, policyBody(0, mapOf("minimumMonthlyWage" to "4100000")))
        )
        payrollError(
            createRun(
                f,
                runBody(
                    f,
                    changes = mapOf("expectedPolicyVersion" to review["policy"]["version"].asLong()),
                ),
            ),
            409,
            "stale_policy_version",
        )
        assertEquals(0, count(f.people.payroll.company, "payroll_runs"))
        val changed = payrollBody(get(f.people.preparer.client, reviewPath(f)))
        assertEquals(1, changed["policy"]["version"].asLong())
        payrollBody(createRun(f, runBody(f, changes = mapOf("expectedPolicyVersion" to 1))))
    }

    @Test
    fun concurrentStartCannotChangeThePeriodHalfwayThroughItsReview() {
        val f = calculationFixture(prepare = false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        runProbe.afterRead = { kind, company ->
            if (
                kind == "review" &&
                    company == f.people.payroll.company &&
                    armed.compareAndSet(true, false)
            ) {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newFixedThreadPool(2).use { pool ->
            val read =
                pool.submit<HttpResponse<String>> { get(f.people.preparer.client, reviewPath(f)) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val write = pool.submit<HttpResponse<String>> { createRun(f) }
                assertThrows(TimeoutException::class.java) { write.get(200, TimeUnit.MILLISECONDS) }
                release.countDown()
                val before = payrollBody(read.get(10, TimeUnit.SECONDS))
                assertEquals("DRAFT", before["period"]["status"].asString())
                assertEquals(0, before["runCount"].asInt())
                assertEquals(setOf("START"), actions(before))
                payrollBody(write.get(10, TimeUnit.SECONDS))
            } finally {
                release.countDown()
                runProbe.clear()
            }
        }
        val after = payrollBody(get(f.people.preparer.client, reviewPath(f)))
        assertEquals("PROCESSING", after["period"]["status"].asString())
        assertEquals(1, after["runCount"].asInt())
        assertTrue(actions(after).isEmpty())
    }

    @Test
    fun grantsAddedDuringAnOldReviewDoNotBecomeNewAuthority() {
        val f = calculationFixture(prepare = false)
        val reader =
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.calculate"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(reader.client, reviewPath(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                        f.people.payroll.company,
                        reader.account,
                    )
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                        f.people.payroll.company,
                        reader.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertTrue(actions(payrollBody(get(reader.client, reviewPath(f)))).isEmpty())
    }

    @Test
    fun runRecoveryHintsTrackJobOwnershipPeriodVersionsAndTerminalState() {
        val f = calculationFixture(prepare = false)
        val lease = beginRun(f)
        val id = runId(lease)
        val path = runPath(f, id)
        val processing = payrollBody(get(f.people.preparer.client, path))
        assertEquals(setOf("CANCEL_JOB"), actions(processing))
        assertEquals(1, processing["period"]["version"].asLong())
        assertTrue(actions(runView(f, id)).isEmpty())
        stopRun(f, lease)
        assertEquals(
            setOf("RESUME", "ABANDON"),
            actions(payrollBody(get(f.people.preparer.client, path))),
        )
        val another =
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.calculate"))
        assertEquals(setOf("ABANDON"), actions(payrollBody(get(another.client, path))))
        payrollBody(abandonRun(f, id, periodVersion = 1))
        val abandoned = payrollBody(get(f.people.preparer.client, path))
        assertTrue(actions(abandoned).isEmpty())
        assertEquals("DRAFT", abandoned["period"]["status"].asString())
        assertTrue(abandoned["period"]["currentRunId"].isNull)
    }

    private fun reviewPath(f: CalculationFixture) =
        periodsPath(f.people) + "/${f.period}/calculation-review"

    private fun actions(view: JsonNode): Set<String> =
        view["availableActions"].asSequence().map { it.asString() }.toSet()

    private fun issues(view: JsonNode): Set<String> =
        view["blockingIssues"].asSequence().map { it["code"].asString() }.toSet()
}
