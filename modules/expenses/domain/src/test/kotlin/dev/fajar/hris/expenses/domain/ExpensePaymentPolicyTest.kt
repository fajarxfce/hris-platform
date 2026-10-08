package dev.fajar.hris.expenses.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpensePaymentPolicyTest {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val actor = UUID.randomUUID()
    private val item =
        ExpensePaymentItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EMP-01",
            "Test Employee",
            BigDecimal("150000.00"),
            ExpensePaymentDestination("014", "001234567890", "Test Employee"),
            ExpensePaymentItemStatus.PENDING,
            1,
            1,
            null,
            null,
        )
    private val batch =
        ExpensePaymentBatch(
            UUID.randomUUID(),
            ExpensePaymentBatchStatus.RELEASED,
            1,
            "Reimbursements",
            item.amount,
            1,
            actor,
            now.minusSeconds(100),
            UUID.randomUUID(),
            now.minusSeconds(60),
            listOf(item),
        )

    @Test
    fun instructionsBoundBankFieldsCountsAndStableIdentifiers() {
        val rows =
            List(100) {
                ExpensePaymentInstruction(UUID.randomUUID(), UUID.randomUUID(), item.destination)
            }
        assertTrue(
            validateExpensePaymentInstructions("Payments", rows, "Verified") is Result.Success
        )
        assertTrue(
            validateExpensePaymentInstructions("Payments", rows + rows.first(), "Verified")
                is Result.Failed
        )
        assertTrue(
            validateExpensePaymentInstructions(
                "Payments",
                listOf(rows.first(), rows.first().copy(id = UUID.randomUUID())),
                "Verified",
            )
                is Result.Failed
        )
        for (destination in
            listOf(
                item.destination.copy(bankCode = "../../014"),
                item.destination.copy(accountNumber = "1".repeat(35)),
                item.destination.copy(accountName = "invalid\u0000name"),
            )) {
            assertTrue(
                validateExpensePaymentInstructions(
                    "Payments",
                    listOf(rows.first().copy(destination = destination)),
                    "Verified",
                )
                    is Result.Failed
            )
        }
    }

    @Test
    fun paymentIndependenceIncludesEveryHistoricalMakerAndBothBeneficiaryIdentities() {
        val candidate =
            ExpensePayable(
                item.claimId,
                item.submissionId,
                item.employmentId,
                item.employeeNumber,
                item.employeeName,
                "Claim",
                item.amount,
                now,
                setOf(UUID.randomUUID()),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        assertTrue(validateExpensePaymentIndependence(actor, listOf(candidate)) is Result.Success)
        for (blocked in
            candidate.makerIds +
                setOf(
                    requireNotNull(candidate.requesterId),
                    requireNotNull(candidate.currentAccountId),
                )) assertTrue(
            validateExpensePaymentIndependence(blocked, listOf(candidate)) is Result.Failed
        )
    }

    @Test
    fun unknownOrUnconfirmedOutcomesNeverReleaseTheClaimForAnotherPayment() {
        val result =
            ExpensePaymentResult(
                item.id,
                ExpensePaymentItemStatus.FAILED,
                null,
                now,
                "Bank rejected transfer",
            )
        assertTrue(validateExpensePaymentResults(batch, listOf(result), now) is Result.Failed)
        assertTrue(
            validateExpensePaymentResults(
                batch,
                listOf(result.copy(confirmedNoTransfer = true)),
                now,
            )
                is Result.Success
        )
        for (status in
            listOf(
                ExpensePaymentItemStatus.PENDING,
                ExpensePaymentItemStatus.PREPARED,
                ExpensePaymentItemStatus.CANCELLED,
            )) assertTrue(
            validateExpensePaymentResults(
                batch,
                listOf(result.copy(status = status, confirmedNoTransfer = true)),
                now,
            )
                is Result.Failed
        )
    }

    @Test
    fun settlementRequiresAPendingItemBoundedBankReferenceAndAnActualPaymentTime() {
        val result =
            ExpensePaymentResult(
                item.id,
                ExpensePaymentItemStatus.SUCCEEDED,
                "BANK/2026-01",
                now,
                "Bank confirmed transfer",
            )
        assertTrue(validateExpensePaymentResults(batch, listOf(result), now) is Result.Success)
        for (invalid in
            listOf(
                result.copy(itemId = UUID.randomUUID()),
                result.copy(transactionReference = null),
                result.copy(transactionReference = "=formula"),
                result.copy(occurredAt = now.plusNanos(1)),
                result.copy(occurredAt = now.minusSeconds(61)),
                result.copy(confirmedNoTransfer = true),
            )) assertTrue(
            validateExpensePaymentResults(batch, listOf(invalid), now) is Result.Failed
        )
        assertTrue(
            validateExpensePaymentResults(
                batch.copy(status = ExpensePaymentBatchStatus.CLOSED),
                listOf(result),
                now,
            )
                is Result.Failed
        )
        assertTrue(
            validateExpensePaymentResults(batch, listOf(result, result), now) is Result.Failed
        )
    }
}
