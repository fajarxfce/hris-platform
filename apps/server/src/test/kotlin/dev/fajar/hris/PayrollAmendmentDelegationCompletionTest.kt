package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PayrollAmendmentDelegationCompletionTest : PayrollAmendmentReviewApiFixture() {
    @ParameterizedTest
    @CsvSource("APPROVE,journal", "REJECT,journal", "APPROVE,evidence")
    fun futureStageAssignmentCannotReplaceExpiredCurrentStageDelegation(
        decision: String,
        stage: String,
    ) {
        val scene = amendmentReviewScene(stages = 2)
        val company = scene.draft.source.payroll.company
        val admittedAt = clock.instant()
        val expires = admittedAt.plusSeconds(60)
        val delegation = UUID.randomUUID()
        fun grant(version: Long?, until: java.time.Instant) {
            payrollBody(
                command(
                    scene.reviewer.client,
                    "/api/v1/companies/$company/approvals/delegations/$delegation",
                    json.writeValueAsString(
                        mapOf(
                            "kind" to "PAYROLL_AMENDMENT",
                            "fromAccount" to scene.reviewer.account,
                            "toAccount" to scene.secondReviewer.account,
                            "validFrom" to admittedAt.minusSeconds(1).toString(),
                            "validUntil" to until.toString(),
                            "expectedVersion" to version,
                            "reason" to "Independent review coverage",
                        )
                    ),
                    scene.reviewer.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
        }
        grant(null, expires)
        val operation = UUID.randomUUID()
        fun snapshot(): Map<String, Any> =
            amendmentReviewCounts(scene) +
                mapOf(
                    "amendments" to
                        database()
                            .queryForList(
                                "select to_jsonb(t)::text as snapshot from payroll_amendments t where company_id=? order by id",
                                company,
                            ),
                    "reviews" to
                        database()
                            .queryForList(
                                "select to_jsonb(t)::text as snapshot from payroll_amendment_reviews t where company_id=? order by id",
                                company,
                            ),
                    "approvals" to
                        database()
                            .queryForList(
                                "select to_jsonb(t)::text as snapshot from approval_requests t where company_id=? order by id",
                                company,
                            ),
                )
        val before = snapshot()
        amendmentProbe.afterRead = { if (stage == "evidence" && it == company) clock.set(expires) }
        payrollProbe.beforeJournal = {
            if (
                stage == "journal" &&
                    it.companyId == company &&
                    it.action == "payroll.amendment_review_decided"
            )
                clock.set(expires)
        }
        try {
            payrollError(
                decideAmendmentReview(
                    scene,
                    member = scene.secondReviewer,
                    decision = decision,
                    key = operation,
                ),
                403,
                "not_assigned_approver",
            )
            assertEquals(before, snapshot())
        } finally {
            amendmentProbe.clear()
            payrollProbe.clear()
        }
        grant(0, clock.instant().plusSeconds(120))
        val committed =
            payrollBody(
                decideAmendmentReview(
                    scene,
                    member = scene.secondReviewer,
                    decision = decision,
                    key = operation,
                )
            )
        val after = snapshot()
        assertEquals(
            committed,
            payrollBody(
                decideAmendmentReview(
                    scene,
                    member = scene.secondReviewer,
                    decision = decision,
                    key = operation,
                )
            ),
        )
        assertEquals(after, snapshot())
    }
}
