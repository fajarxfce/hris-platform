package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class PayrollFinalizationPreparationHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun finalizerCanReviewPublicationWithoutCalculationAdministrationOrEmployeeReads() {
        val f = approved()
        val company = f.calculation.people.payroll.company
        val path = runPath(f.calculation, f.run) + "/finalization-preparation"
        val before = count(company, "background_jobs")
        val review = payrollBody(get(f.finalizer.client, path))
        assertEquals(f.run.toString(), review["run"]["id"].asString())
        assertEquals(f.review.toString(), review["latestReview"]["id"].asString())
        assertEquals(1, review["approvalVersion"].asLong())
        assertEquals(setOf("START"), actions(review))
        assertEquals(0, review["blockingIssues"].size())
        assertTrue(review["latestFinalization"].isNull)
        assertTrue(review["latestJobStatus"].isNull)
        assertEquals(1, review["totals"]["employeeCount"].asInt())
        assertEquals(BigDecimal("10605932.00"), BigDecimal(review["totals"]["takeHome"].asString()))
        assertFalse(review.has("results"))
        assertEquals(before, count(company, "background_jobs"))
        assertEquals(0, count(company, "payroll_finalizations"))
        assertTrue(actions(payrollBody(get(f.calculation.people.preparer.client, path))).isEmpty())
        payrollError(get(f.finalizer.client, runPath(f.calculation, f.run)), 403, "access_denied")
        payrollError(get(f.calculation.people.payroll.admin, path), 403, "access_denied")
        val reader = payrollMember(company, setOf("company.read", "payroll.self.read"))
        payrollError(get(reader.client, path), 403, "access_denied")
        payrollError(
            get(
                f.finalizer.client,
                runPath(f.calculation, UUID.randomUUID()) + "/finalization-preparation",
            ),
            404,
            "payroll_run_not_found",
        )
    }

    @Test
    fun publicationDirectoryFiltersCompletedRunsAndRemainsBoundedAndCompanyScoped() {
        val f = approved()
        val base =
            "/api/v1/companies/${f.calculation.people.payroll.company}/payroll/publication-runs"
        val query = "?from=2026-09&until=2026-09&limit=1"
        val page = payrollBody(get(f.finalizer.client, base + query))
        assertEquals(1, page["items"].size())
        assertEquals(f.run.toString(), page["items"][0]["id"].asString())
        assertTrue(page["nextCursor"].isNull)
        assertFalse(page["items"][0].has("results"))
        assertEquals(
            0,
            payrollBody(get(f.finalizer.client, "$base$query&after=${f.run}"))["items"].size(),
        )
        assertEquals(
            0,
            payrollBody(get(f.finalizer.client, "$base?from=2026-10&until=2026-10"))["items"].size(),
        )
        assertEquals(
            0,
            payrollBody(get(f.finalizer.client, "$base$query&status=FINALIZED"))["items"].size(),
        )
        for (invalid in
            listOf(
                "from=2023-01&until=2024-01",
                "from=2026-09&until=2026-08",
                "from=2024-01&until=2027-01",
                "from=2026-09&until=2026-09&limit=201",
                "from=2026-09&until=2026-09&status=PROCESSING",
            )) payrollError(get(f.finalizer.client, "$base?$invalid"), 422, "invalid_page")
        payrollError(get(f.finalizer.client, "$base$query&status=RUNNING"), 400, "invalid_request")
        payrollError(get(f.calculation.people.payroll.admin, base + query), 403, "access_denied")
        val other = approved()
        val otherBase =
            "/api/v1/companies/${other.calculation.people.payroll.company}/payroll/publication-runs"
        val isolated = payrollBody(get(other.finalizer.client, otherBase + query))
        assertEquals(other.run.toString(), isolated["items"][0]["id"].asString())
        payrollError(
            get(
                other.finalizer.client,
                runPath(other.calculation, f.run) + "/finalization-preparation",
            ),
            404,
            "payroll_run_not_found",
        )
    }

    @Test
    fun cancelledAttemptCanBeReviewedAgainAndPublishedEvidenceRetainsItsOwnProgress() {
        val f = approved()
        val path = runPath(f.calculation, f.run) + "/finalization-preparation"
        val first = beginFinalization(f)
        val active = payrollBody(get(f.finalizer.client, path))
        assertEquals("RUNNING", active["latestJobStatus"].asString())
        assertEquals("payroll_finalization_active", active["blockingIssues"][0]["code"].asString())
        assertTrue(actions(active).isEmpty())
        cancelFinalization(f, first)
        assertEquals(
            Result.Success(Unit),
            abortFinalization.execute(
                first,
                Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
            ),
        )
        val cancelled = payrollBody(get(f.finalizer.client, path))
        assertEquals("CANCELLED", cancelled["latestJobStatus"].asString())
        assertEquals(setOf("START"), actions(cancelled))
        assertEquals(
            finalizationId(first).toString(),
            cancelled["latestFinalization"]["id"].asString(),
        )
        val second = beginFinalization(f)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, second))
        val published = payrollBody(get(f.finalizer.client, path))
        assertEquals("payroll_already_finalized", published["blockingIssues"][0]["code"].asString())
        assertEquals("SUCCEEDED", published["latestJobStatus"].asString())
        assertTrue(actions(published).isEmpty())
        val detail = finalizationView(f, finalizationId(second))
        assertEquals("FINALIZED", detail["run"]["status"].asString())
        assertEquals(published["totals"], detail["totals"])
        assertFalse(detail["finalization"]["publishedAt"].isNull)
        val retained = finalizationView(f, finalizationId(first))
        assertEquals("CANCELLED", retained["job"]["status"].asString())
        assertTrue(retained["finalization"]["publishedAt"].isNull)
        assertEquals(detail["totals"], retained["totals"])
        assertEquals("FINALIZED", retained["run"]["status"].asString())
    }

    @Test
    fun completedCalculationStillNeedsIndependentApproval() {
        val c = calculationFixture()
        val run = calculated(c)
        val finalizer =
            payrollMember(c.people.payroll.company, setOf("company.read", "payroll.finalize"))
        val path = runPath(c, run) + "/finalization-preparation"
        val missing = payrollBody(get(finalizer.client, path))
        assertTrue(missing["latestReview"].isNull)
        assertTrue(missing["approvalVersion"].isNull)
        assertEquals(
            "payroll_approved_review_required",
            missing["blockingIssues"][0]["code"].asString(),
        )
        assertTrue(actions(missing).isEmpty())
        val id = pendingReview(c, run)
        assertTrue(actions(payrollBody(get(finalizer.client, path))).isEmpty())
        payrollBody(decideReview(c, id))
        val ready = payrollBody(get(finalizer.client, path))
        assertEquals(setOf("START"), actions(ready))
        payrollBody(withdrawReview(c, id, 1, 1))
        val withdrawn = payrollBody(get(finalizer.client, path))
        assertEquals("WITHDRAWN", withdrawn["latestReview"]["status"].asString())
        assertTrue(actions(withdrawn).isEmpty())
    }

    @Test
    fun independentPreparationReadersShareResourceGuards() {
        val f = approved()
        val path = runPath(f.calculation, f.run) + "/finalization-preparation"
        val barrier = AccountLockProbe.Barrier(f.finalizer.account)
        accountProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val first = pool.submit<JsonNode> { payrollBody(get(f.finalizer.client, path)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val independent =
                    pool
                        .submit<JsonNode> {
                            payrollBody(get(f.calculation.people.preparer.client, path))
                        }
                        .get(5, TimeUnit.SECONDS)
                assertFalse(first.isDone)
                barrier.release.countDown()
                assertEquals(independent["totals"], first.get(10, TimeUnit.SECONDS)["totals"])
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    private fun actions(value: JsonNode): Set<String> =
        value["availableActions"].asSequence().map { it.asString() }.toSet()
}
