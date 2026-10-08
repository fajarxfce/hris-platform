package dev.fajar.hris.expenses.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.people.domain.entities.ContractKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReviewPolicyTest {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val owner = UUID.randomUUID()
    private val reviewer = UUID.randomUUID()
    private val delegate = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val claim =
        ExpenseClaim(
            UUID.randomUUID(),
            UUID.randomUUID(),
            owner,
            now,
            1,
            0,
            ExpenseClaimStatus.PENDING,
            1,
            id,
        )
    private val approval =
        ApprovalRequest(
            UUID.randomUUID(),
            ApprovalKind.EXPENSE,
            id,
            owner,
            owner,
            UUID.randomUUID(),
            0,
            listOf(ApprovalStage(setOf(reviewer))),
            0,
            ApprovalStatus.PENDING,
            0,
            now,
        )
    private val submission =
        ExpenseSubmission(
            id,
            claim.id,
            1,
            0,
            approval.id,
            owner,
            "E1",
            "Example Employee",
            "Travel",
            "",
            BigDecimal.ONE,
            emptyList(),
            setOf(owner),
            owner,
            now,
            "Submit",
        )
    private val actor =
        Actor(reviewer, UUID.randomUUID(), setOf("expenses.approve"), now, UUID.randomUUID())
    private val grant =
        MemberAccount(
            reviewer,
            "reviewer@example.test",
            "Reviewer",
            true,
            true,
            setOf("expenses.approve"),
            0,
        )
    private val delegation =
        Delegation(
            UUID.randomUUID(),
            ApprovalKind.EXPENSE,
            reviewer,
            delegate,
            now.minusSeconds(60),
            now.plusSeconds(60),
            true,
            0,
        )

    @Test
    fun previousMakersAndCurrentBeneficiariesCannotReviewDirectlyOrByDelegation() {
        val made = submission.copy(makerIds = setOf(owner, reviewer))
        val direct =
            planExpenseReview(
                claim,
                made,
                approval,
                actor,
                null,
                ExpenseDecision.APPROVE,
                "",
                emptyList(),
                listOf(grant),
                emptySet(),
                false,
                now,
            )
        assertEquals("self_approval_denied", (direct as Result.Failed).failure.code)
        assertFalse(
            hasExpenseReviewScope(
                made,
                approval,
                actor.copy(accountId = delegate),
                null,
                listOf(delegation),
                listOf(grant),
                now,
            )
        )
        val linked =
            planExpenseReview(
                claim,
                submission.copy(requesterId = null),
                approval.copy(requesterId = null),
                actor,
                reviewer,
                ExpenseDecision.APPROVE,
                "",
                emptyList(),
                listOf(grant),
                emptySet(),
                false,
                now,
            )
        assertEquals("self_approval_denied", (linked as Result.Failed).failure.code)
        assertFalse(
            hasExpenseReviewScope(
                submission,
                approval,
                actor.copy(accountId = delegate),
                reviewer,
                listOf(delegation),
                listOf(grant),
                now,
            )
        )
    }

    @Test
    fun returnsRetainTheReviewerDecisionAndApprovalsAdvanceOneStageAtATime() {
        val returned =
            planExpenseReview(
                claim,
                submission,
                approval,
                actor,
                null,
                ExpenseDecision.RETURN,
                "Clarify evidence",
                emptyList(),
                listOf(grant),
                emptySet(),
                false,
                now,
            )
                as Result.Success
        assertEquals(ExpenseClaimStatus.RETURNED, returned.value.review.status)
        assertEquals(ApprovalDecision.REJECT, returned.value.transition.decision)
        assertEquals(ApprovalStatus.REJECTED, returned.value.transition.status)
        val stages =
            approval.copy(
                stages = listOf(ApprovalStage(setOf(reviewer)), ApprovalStage(setOf(delegate)))
            )
        val partial =
            planExpenseReview(
                claim,
                submission,
                stages,
                actor,
                null,
                ExpenseDecision.APPROVE,
                "",
                emptyList(),
                listOf(grant),
                emptySet(),
                false,
                now,
            )
                as Result.Success
        assertEquals(ExpenseClaimStatus.PENDING, partial.value.review.status)
        assertEquals(1, partial.value.transition.currentStep)
        val old =
            planExpenseReview(
                claim.copy(latestSubmissionId = UUID.randomUUID()),
                submission,
                approval,
                actor,
                null,
                ExpenseDecision.APPROVE,
                "",
                emptyList(),
                listOf(grant),
                emptySet(),
                false,
                now,
            )
        assertEquals("expense_submission_not_current", (old as Result.Failed).failure.code)
    }

    @Test
    fun overflowCannotBeHiddenByFilteringIneligibleDelegatorsAndExpiryIsExplicit() {
        val made = submission.copy(makerIds = setOf(owner, reviewer))
        val overflow = List(201) { delegation.copy(id = UUID.randomUUID()) }
        assertFalse(
            hasExpenseReviewScope(
                made,
                approval,
                actor.copy(accountId = delegate),
                null,
                overflow,
                listOf(grant),
                now,
            )
        )
        val result =
            planExpenseReview(
                claim,
                made,
                approval,
                actor.copy(accountId = delegate),
                null,
                ExpenseDecision.APPROVE,
                "",
                overflow,
                listOf(grant),
                emptySet(),
                false,
                now,
            )
        assertEquals("approval_delegation_capacity", (result as Result.Failed).failure.code)
        assertTrue(
            hasExpenseReviewScope(
                submission,
                approval,
                actor.copy(accountId = delegate),
                null,
                listOf(delegation),
                listOf(grant),
                now,
            )
        )
        assertFalse(
            hasExpenseReviewScope(
                submission,
                approval,
                actor.copy(accountId = delegate),
                null,
                listOf(delegation),
                listOf(grant),
                delegation.validUntil,
            )
        )
        assertFalse(
            hasExpenseReviewScope(
                submission,
                approval,
                actor.copy(accountId = delegate),
                null,
                listOf(delegation),
                listOf(grant.copy(accountActive = false)),
                now,
            )
        )
    }

    @Test
    fun currentDuplicateEvidenceRequiresAcknowledgementAndAnExplanation() {
        val hashes = setOf("a".repeat(64))
        for ((ack, reason) in listOf(false to "", true to "", false to "Checked")) {
            val result =
                planExpenseReview(
                    claim,
                    submission,
                    approval,
                    actor,
                    null,
                    ExpenseDecision.APPROVE,
                    reason,
                    emptyList(),
                    listOf(grant),
                    hashes,
                    ack,
                    now,
                )
            assertEquals(
                "expense_duplicate_acknowledgement_required",
                (result as Result.Failed).failure.code,
            )
        }
        val accepted =
            planExpenseReview(
                claim,
                submission,
                approval,
                actor,
                null,
                ExpenseDecision.APPROVE,
                "Distinct reimbursable portion",
                emptyList(),
                listOf(grant),
                hashes,
                true,
                now,
            )
                as Result.Success
        assertEquals(hashes, accepted.value.review.duplicateDigests)
        assertTrue(accepted.value.review.duplicatesAcknowledged)
    }

    @Test
    fun signalsUseCurrentReceiptBytesAndIgnoreStaleOrUnrelatedFlags() {
        val category =
            ExpenseCategory(
                UUID.randomUUID(),
                "TRAVEL",
                0,
                0,
                LocalDate.of(2026, 1, 1),
                ExpensePolicy(
                    "Travel",
                    BigDecimal.TEN,
                    BigDecimal.TEN,
                    true,
                    false,
                    30,
                    setOf(ContractKind.PERMANENT),
                ),
                true,
            )
        val receipt =
            ExpenseSubmittedReceipt(
                UUID.randomUUID(),
                "receipt.pdf",
                "application/pdf",
                1,
                "a".repeat(64),
                true,
            )
        val line =
            ExpenseSubmittedLine(
                UUID.randomUUID(),
                LocalDate.of(2026, 10, 9),
                BigDecimal.ONE,
                "Travel",
                category,
                null,
                listOf(receipt),
            )
        assertEquals(emptySet<String>(), duplicateExpenseReceiptDigests(listOf(line), emptySet()))
        assertEquals(
            setOf(receipt.sha256),
            duplicateExpenseReceiptDigests(
                listOf(
                    line,
                    line.copy(
                        id = UUID.randomUUID(),
                        receipts = listOf(receipt.copy(revisionId = UUID.randomUUID())),
                    ),
                ),
                emptySet(),
            ),
        )
        assertEquals(
            setOf(receipt.sha256),
            duplicateExpenseReceiptDigests(listOf(line), setOf(receipt.sha256, "b".repeat(64))),
        )
    }
}
