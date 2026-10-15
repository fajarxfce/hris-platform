package dev.fajar.hris.payroll.domain

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.payrollPaymentRecoveryReview
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentRecoveryPolicyTest {
    private val published = Instant.parse("2026-10-15T09:00:00.123456Z")
    private val assessment =
        PayrollPayable(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EMP-1",
            "Example Employee",
            YearMonth.of(2026, 9),
            LocalDate.of(2026, 10, 2),
            BigDecimal("-500.25"),
            published,
            1,
            4,
            BigDecimal("1000.75"),
            BigDecimal.ZERO,
        )
    private val transfer =
        PayrollPaymentAttempt(
            UUID.randomUUID(),
            UUID.randomUUID(),
            PayrollPaymentItemStatus.SUCCEEDED,
            published.minusSeconds(60),
            published.minusSeconds(30),
            published.minusSeconds(10),
            BigDecimal("1000.75"),
            0,
        )
    private val progress =
        PayrollPaymentProgress(
            assessment.assessmentId,
            4,
            listOf(transfer),
            PayrollSettlement(1, BigDecimal("500.50"), BigDecimal("1000.75"), BigDecimal.ZERO),
        )

    @Test
    fun overpaymentReviewRetainsExactCurrentBalancesAndOriginalTransferFacts() {
        val review = payrollPaymentRecoveryReview(false, assessment, progress, emptyList())
        assertTrue(review.canConfirm)
        assertEquals(BigDecimal("500.25"), review.payments.settlement.recoverable)
        assertEquals(0, review.payments.attempts.single().assessmentRevision)
        assertEquals(published, review.earliestConfirmationAt)
    }

    @Test
    fun bankConfirmationCannotPrecedeTheLatestSuccessfulTransfer() {
        val later = published.plusNanos(1)
        val review =
            payrollPaymentRecoveryReview(
                false,
                assessment,
                progress.copy(attempts = listOf(transfer.copy(resolvedAt = later))),
                emptyList(),
            )
        assertEquals(later, review.earliestConfirmationAt)
    }

    @Test
    fun allApplicableBlockersAreVisibleWithoutGrantingBeneficiaryAuthority() {
        val recovery =
            PayrollPaymentRecovery(
                UUID.randomUUID(),
                assessment.assessmentId,
                1,
                4,
                BigDecimal.ONE,
                "RETURN-1",
                published,
                published,
                UUID.randomUUID(),
                "Confirmed return",
            )
        val review =
            payrollPaymentRecoveryReview(
                true,
                assessment,
                progress.copy(
                    settlement =
                        progress.settlement.copy(confirmedRecovered = BigDecimal("500.25")),
                    attempts = listOf(transfer.copy(status = PayrollPaymentItemStatus.PENDING)),
                ),
                List(20) { recovery.copy(id = UUID.randomUUID()) },
            )
        assertFalse(review.canConfirm)
        assertEquals(PayrollPaymentRecoveryBlocker.entries.toSet(), review.blockers)
    }

    @Test
    fun preparedInstructionBlocksConfirmationButFailedAndCancelledInstructionsDoNot() {
        for (status in
            listOf(PayrollPaymentItemStatus.PREPARED, PayrollPaymentItemStatus.PENDING)) {
            val review =
                payrollPaymentRecoveryReview(
                    false,
                    assessment,
                    progress.copy(attempts = listOf(transfer.copy(status = status))),
                    emptyList(),
                )
            assertEquals(setOf(PayrollPaymentRecoveryBlocker.PENDING_PAYMENT), review.blockers)
        }
        for (status in
            listOf(PayrollPaymentItemStatus.FAILED, PayrollPaymentItemStatus.CANCELLED)) {
            val review =
                payrollPaymentRecoveryReview(
                    false,
                    assessment,
                    progress.copy(attempts = listOf(transfer.copy(status = status))),
                    emptyList(),
                )
            assertTrue(review.canConfirm)
            assertEquals(published, review.earliestConfirmationAt)
        }
    }
}
