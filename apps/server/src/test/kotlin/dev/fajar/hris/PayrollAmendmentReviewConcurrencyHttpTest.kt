package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentReviewConcurrencyHttpTest : PayrollAmendmentReviewApiFixture() {
    @Test
    fun competingSubmissionRetriesAndDecisionsProduceOnlyOneRevisionPerOperation() {
        val scene = amendmentReviewScene(submitted = false)
        Executors.newFixedThreadPool(2).use { pool ->
            val submitted =
                (1..2)
                    .map { pool.submit<HttpResponse<String>> { submitAmendmentReview(scene) } }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(submitted[0], submitted[1])
            assertEquals(1, count(scene.draft.source.payroll.company, "payroll_amendment_reviews"))
            val before = amendmentReviewCounts(scene)
            val key = UUID.randomUUID()
            val decisions =
                (1..2)
                    .map {
                        pool.submit<HttpResponse<String>> {
                            decideAmendmentReview(scene, key = key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(decisions[0], decisions[1])
            assertEquals(
                before.getValue("approval_decisions") + 1,
                count(scene.draft.source.payroll.company, "approval_decisions"),
            )
            assertEquals(
                before.getValue("payroll_amendment_revisions") + 1,
                count(scene.draft.source.payroll.company, "payroll_amendment_revisions"),
            )
        }
    }

    @Test
    fun approvalAndWithdrawalCannotBothCommitAgainstTheSameObservedReview() {
        val scene = amendmentReviewScene()
        val before = amendmentReviewCounts(scene)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                listOf(
                        pool.submit<HttpResponse<String>> { decideAmendmentReview(scene) },
                        pool.submit<HttpResponse<String>> { withdrawAmendmentReview(scene) },
                    )
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(
                listOf(200, 409),
                results.map { it.statusCode() }.sorted(),
                results.map { it.body() }.toString(),
            )
        }
        assertEquals(
            before.getValue("payroll_amendment_revisions") + 1,
            count(scene.draft.source.payroll.company, "payroll_amendment_revisions"),
        )
        assertEquals(
            before.getValue("operation_receipts") + 1,
            count(scene.draft.source.payroll.company, "operation_receipts"),
        )
    }

    @Test
    fun assignmentChangesRequireRefreshingTheObservedApprovalVersionWithoutChangingDraftHistory() {
        val scene = amendmentReviewScene()
        val f = scene.draft.source.payroll
        val approval = amendmentReviewView(scene)["approval"]["id"].asString()
        payrollBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/$approval/reassign",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "assignees" to listOf(scene.secondReviewer.account),
                        "reason" to "Independent reviewer coverage",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val current = amendmentReviewView(scene, scene.secondReviewer)
        assertEquals(1, current["approval"]["version"].asLong())
        assertEquals(0, current["review"]["version"].asLong())
        assertEquals(2, current["amendment"]["version"].asLong())
        payrollError(
            decideAmendmentReview(scene, member = scene.secondReviewer),
            409,
            "approval_changed",
        )
        payrollError(
            decideAmendmentReview(scene, approvalVersion = 1),
            403,
            "not_assigned_approver",
        )
        payrollBody(
            decideAmendmentReview(scene, approvalVersion = 1, member = scene.secondReviewer)
        )
        assertEquals("APPROVED", amendmentReviewView(scene)["review"]["status"].asString())
    }
}
