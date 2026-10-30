package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PayrollReviewDelegationCompletionTest : PayrollReviewApiFixture() {
    @ParameterizedTest
    @CsvSource("APPROVE,evidence", "REJECT,evidence", "APPROVE,journal", "REJECT,journal")
    fun futureStageAssignmentCannotReplaceExpiredCurrentStageDelegation(
        decision: String,
        stage: String,
    ) {
        val f = calculationFixture()
        val company = f.people.payroll.company
        val run = calculated(f)
        val delegate =
            payrollMember(company, setOf("company.read", "payroll.review", "approvals.read"))
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(f.people.reviewer.account, delegate.account).map {
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(it))
                        }
                ),
        )
        val id = UUID.randomUUID()
        payrollBody(submitReview(f, run, id))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'approvals.read')",
                company,
                f.people.reviewer.account,
            )
        val delegation = UUID.randomUUID()
        val admittedAt = clock.instant()
        val expires = admittedAt.plusSeconds(60)
        fun grant(version: Long?, until: java.time.Instant) {
            payrollBody(
                command(
                    f.people.reviewer.client,
                    "/api/v1/companies/$company/approvals/delegations/$delegation",
                    json.writeValueAsString(
                        mapOf(
                            "kind" to "PAYROLL",
                            "fromAccount" to f.people.reviewer.account,
                            "toAccount" to delegate.account,
                            "validFrom" to admittedAt.minusSeconds(1).toString(),
                            "validUntil" to until.toString(),
                            "expectedVersion" to version,
                            "reason" to "Independent review coverage",
                        )
                    ),
                    f.people.reviewer.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
        }
        grant(null, expires)
        val operation = UUID.randomUUID()
        fun snapshot(): Map<String, Any> =
            listOf(
                    "payroll_reviews",
                    "payroll_review_changes",
                    "approval_requests",
                    "approval_decisions",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith { count(company, it) } +
                mapOf(
                    "reviews" to
                        database()
                            .queryForList(
                                "select to_jsonb(t)::text as snapshot from payroll_reviews t where company_id=? order by id",
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
        runProbe.afterRead = { kind, scope ->
            if (stage == "evidence" && kind == "find" && scope == company) clock.set(expires)
        }
        payrollProbe.beforeJournal = {
            if (
                stage == "journal" &&
                    it.companyId == company &&
                    it.action == "payroll.review_decided"
            )
                clock.set(expires)
        }
        try {
            payrollError(
                decideReview(f, id, member = delegate, decision = decision, key = operation),
                403,
                "not_assigned_approver",
            )
            assertEquals(before, snapshot())
        } finally {
            runProbe.clear()
            payrollProbe.clear()
        }
        grant(0, clock.instant().plusSeconds(120))
        val committed =
            payrollBody(
                decideReview(f, id, member = delegate, decision = decision, key = operation)
            )
        val after = snapshot()
        assertEquals(
            committed,
            payrollBody(
                decideReview(f, id, member = delegate, decision = decision, key = operation)
            ),
        )
        assertEquals(after, snapshot())
    }
}
