package dev.fajar.hris

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class PayrollReviewPreparationHttpTest : PayrollReviewApiFixture() {
    @Test
    fun preparationReturnsRetainedTotalsWithoutCreatingAnApprovalOrLoadingEmployeePayloads() {
        val f = calculationFixture()
        val run = calculated(f)
        val path = runPath(f, run) + "/review-preparation"
        val before = count(f.people.payroll.company, "approval_requests")
        val preparation = payrollBody(get(f.people.preparer.client, path))
        assertEquals(run.toString(), preparation["run"]["id"].asString())
        assertTrue(preparation["latestReview"].isNull)
        assertEquals(setOf("SUBMIT"), actions(preparation))
        assertEquals(0, preparation["blockingIssues"].size())
        assertEquals(1, preparation["totals"]["employeeCount"].asInt())
        assertEquals(
            BigDecimal("10605932.00"),
            BigDecimal(preparation["totals"]["takeHome"].asString()),
        )
        assertFalse(preparation.has("results"))
        assertEquals(before, count(f.people.payroll.company, "approval_requests"))
        payrollError(get(f.people.payroll.admin, path), 403, "access_denied")
        assertTrue(actions(payrollBody(get(f.people.reviewer.client, path))).isEmpty())
        reviewTemplate(f)
        val id = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
        val submitted = payrollBody(get(f.people.preparer.client, path))
        assertEquals(id.toString(), submitted["latestReview"]["id"].asString())
        assertEquals("payroll_review_active", submitted["blockingIssues"][0]["code"].asString())
        assertTrue(actions(submitted).isEmpty())
        payrollBody(withdrawReview(f, id))
        assertEquals(setOf("SUBMIT"), actions(payrollBody(get(f.people.preparer.client, path))))
    }

    @Test
    fun missingFailedAndForeignRunsCannotBecomeReadyForReview() {
        val f = calculationFixture(prepare = false)
        val lease = beginRun(f)
        val run = runId(lease)
        val path = runPath(f, run) + "/review-preparation"
        val pending = payrollBody(get(f.people.preparer.client, path))
        assertEquals(
            "payroll_calculation_required",
            pending["blockingIssues"][0]["code"].asString(),
        )
        assertTrue(actions(pending).isEmpty())
        drainRun(f, lease)
        val failed = payrollBody(get(f.people.preparer.client, path))
        assertEquals(
            "payroll_calculation_has_failures",
            failed["blockingIssues"][0]["code"].asString(),
        )
        assertEquals(0, failed["totals"]["employeeCount"].asInt())
        assertTrue(actions(failed).isEmpty())
        payrollError(
            get(f.people.preparer.client, runPath(f, UUID.randomUUID()) + "/review-preparation"),
            404,
            "payroll_run_not_found",
        )
        val other = calculationFixture(prepare = false)
        payrollError(
            get(other.people.preparer.client, runPath(other, run) + "/review-preparation"),
            404,
            "payroll_run_not_found",
        )
    }

    @Test
    fun actionHintsFollowCurrentApprovalStageAndExposeOnlyScopedAccountLabels() {
        val f = calculationFixture()
        val run = calculated(f)
        val second =
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.review"))
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(f.people.reviewer.account),
                            ),
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(second.account)),
                        )
                ),
        )
        val id = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
        val maker = reviewView(f, id, f.people.preparer)
        assertEquals(setOf("WITHDRAW"), actions(maker))
        assertEquals(setOf("APPROVE", "REJECT"), actions(reviewView(f, id)))
        assertTrue(actions(reviewView(f, id, second)).isEmpty())
        assertEquals(run.toString(), maker["run"]["id"].asString())
        assertFalse(maker["finalizationActive"].asBoolean())
        assertEquals(
            setOf(
                f.people.preparer.account.toString(),
                f.people.reviewer.account.toString(),
                second.account.toString(),
            ),
            maker["accounts"].asSequence().map { it["id"].asString() }.toSet(),
        )
        maker["accounts"].forEach {
            assertEquals(setOf("id", "displayName"), it.propertyNames().toSet())
        }
        payrollBody(decideReview(f, id))
        assertTrue(actions(reviewView(f, id)).isEmpty())
        assertEquals(setOf("APPROVE", "REJECT"), actions(reviewView(f, id, second)))
        payrollBody(decideReview(f, id, member = second, version = 1, approvalVersion = 1))
        assertTrue(actions(reviewView(f, id, second)).isEmpty())
        assertEquals(setOf("WITHDRAW"), actions(reviewView(f, id, f.people.preparer)))
        payrollBody(withdrawReview(f, id, 2, 2))
        assertTrue(actions(reviewView(f, id, f.people.preparer)).isEmpty())
    }

    @Test
    fun blockedAndRejectedApprovalsOfferNoDecisionAndNeverHideRetainedHistory() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(f.people.preparer.account),
                            )
                        )
                ),
        )
        val id = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
        val blocked = reviewView(f, id)
        assertTrue(actions(blocked).isEmpty())
        val approval = UUID.fromString(blocked["approval"]["id"].asString())
        payrollBody(reassignReview(f, approval, setOf(f.people.reviewer.account)))
        assertEquals(setOf("APPROVE", "REJECT"), actions(reviewView(f, id)))
        payrollBody(
            decideReview(
                f,
                id,
                approvalVersion = 1,
                decision = "REJECT",
                reason = "Correct evidence",
            )
        )
        assertTrue(actions(reviewView(f, id)).isEmpty())
        val preparation =
            payrollBody(get(f.people.preparer.client, runPath(f, run) + "/review-preparation"))
        assertEquals(
            "payroll_recalculation_required",
            preparation["blockingIssues"][0]["code"].asString(),
        )
        payrollBody(abandonRun(f, run))
        val history = reviewView(f, id, f.people.preparer)
        assertEquals("ABANDONED", history["run"]["status"].asString())
        assertEquals(2, history["changes"].size())
        assertTrue(actions(history).isEmpty())
    }

    @Test
    fun independentReadersSharePayrollAndApprovalGuardsWhileOneWaitsForItsAccount() {
        val f = calculationFixture()
        val run = calculated(f)
        val id = pendingReview(f, run)
        val barrier = AccountLockProbe.Barrier(f.people.preparer.account)
        accountProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val first = pool.submit<JsonNode> { reviewView(f, id, f.people.preparer) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val independent =
                    pool.submit<JsonNode> { reviewView(f, id) }.get(5, TimeUnit.SECONDS)
                assertEquals(id.toString(), independent["review"]["id"].asString())
                assertFalse(first.isDone)
                barrier.release.countDown()
                assertEquals(
                    independent["review"]["totals"],
                    first.get(10, TimeUnit.SECONDS)["review"]["totals"],
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    private fun actions(value: JsonNode): Set<String> =
        value["availableActions"].asSequence().map { it.asString() }.toSet()
}
