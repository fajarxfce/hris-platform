package dev.fajar.hris.payroll.domain

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.payrollPaymentAvailableActions
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentReviewPolicyTest {
    private val maker = UUID.randomUUID()
    private val checker = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T00:00:00Z")
    private val items =
        (1..2).map { index ->
            PayrollPaymentItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EMP-$index",
                "Employee $index",
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 10, 1),
                BigDecimal("100000.00"),
                PayrollPaymentDestination("014", "001234567890", "Employee $index"),
                PayrollPaymentItemStatus.PREPARED,
                0,
                0,
                null,
                null,
            )
        }
    private val batch =
        PayrollPaymentBatch(
            UUID.randomUUID(),
            PayrollPaymentBatchStatus.PREPARED,
            0,
            "September payroll",
            BigDecimal("200000.00"),
            2,
            maker,
            at,
            null,
            null,
            items,
        )
    private val candidates =
        items.map {
            PayrollPayable(
                it.assessmentId,
                it.employmentId,
                it.employeeNumber,
                it.employeeName,
                it.taxMonth,
                it.plannedPaymentDate,
                it.amount,
                at,
            )
        }

    @Test
    fun releaseRequiresAnIndependentAccountAndTheExactRetainedAssessmentSet() {
        assertEquals(
            setOf(PayrollPaymentAvailableAction.CANCEL),
            payrollPaymentAvailableActions(maker, batch, candidates),
        )
        assertEquals(
            setOf(PayrollPaymentAvailableAction.RELEASE, PayrollPaymentAvailableAction.CANCEL),
            payrollPaymentAvailableActions(checker, batch, candidates),
        )
        for (unavailable in
            listOf(
                emptyList(),
                candidates.take(1),
                candidates.map { it.copy(assessmentId = UUID.randomUUID()) },
            )) {
            assertEquals(
                setOf(PayrollPaymentAvailableAction.CANCEL),
                payrollPaymentAvailableActions(checker, batch, unavailable),
            )
        }
    }

    @Test
    fun exportStopsAfterAnyConfirmedOutcomeWhileUnresolvedItemsCanStillBeReconciled() {
        val released =
            batch.copy(
                status = PayrollPaymentBatchStatus.RELEASED,
                version = 1,
                releasedBy = checker,
                releasedAt = at,
                items =
                    items.map {
                        it.copy(
                            status = PayrollPaymentItemStatus.PENDING,
                            version = 1,
                            batchVersion = 1,
                        )
                    },
            )
        for (account in listOf(maker, checker)) {
            assertEquals(
                setOf(
                    PayrollPaymentAvailableAction.RECONCILE,
                    PayrollPaymentAvailableAction.EXPORT,
                ),
                payrollPaymentAvailableActions(account, released, candidates),
            )
        }
        val partial =
            released.copy(
                version = 2,
                items =
                    listOf(
                        released.items[0].copy(
                            status = PayrollPaymentItemStatus.FAILED,
                            version = 2,
                            batchVersion = 2,
                        ),
                        released.items[1],
                    ),
            )
        assertEquals(
            setOf(PayrollPaymentAvailableAction.RECONCILE),
            payrollPaymentAvailableActions(checker, partial, candidates),
        )
        assertEquals(
            setOf(PayrollPaymentAvailableAction.EXPORT),
            payrollPaymentAvailableActions(checker, released, emptyList()),
        )
        assertTrue(payrollPaymentAvailableActions(checker, partial, emptyList()).isEmpty())
    }

    @Test
    fun closedAndCancelledBatchesCannotBeReopenedThroughReviewActions() {
        for (status in
            listOf(PayrollPaymentBatchStatus.CLOSED, PayrollPaymentBatchStatus.CANCELLED)) {
            assertTrue(
                payrollPaymentAvailableActions(checker, batch.copy(status = status), candidates)
                    .isEmpty()
            )
        }
    }
}
