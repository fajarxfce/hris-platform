package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollSettlementPolicyTest {
    @Test
    fun settlementUsesConfirmedMoneyAndSeparatesPayableFromRecoverable() {
        val current =
            PayrollSettlement(
                1,
                BigDecimal("8000.50"),
                BigDecimal("10000.00"),
                BigDecimal("500.25"),
            )
        assertEquals(BigDecimal("-1499.25"), current.balance)
        assertEquals(0, current.payable.signum())
        assertEquals(BigDecimal("1499.25"), current.recoverable)
        val added = current.copy(entitlement = BigDecimal("12000.00"))
        assertEquals(BigDecimal("2500.25"), added.payable)
        assertEquals(0, added.recoverable.signum())
    }

    @Test
    fun modernPaymentCommandsBindAllThreeObservedFieldsAndLegacyRequestsCannotRepaySettledMoney() {
        val payable =
            PayrollPayable(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "E001",
                "Test employee",
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 30),
                BigDecimal("500.00"),
                Instant.EPOCH,
                1,
                4,
                BigDecimal("10000.00"),
                BigDecimal.ZERO,
            )
        val instruction =
            PayrollPaymentInstruction(
                UUID.randomUUID(),
                payable.assessmentId,
                PayrollPaymentDestination("014", "001234567890", "Test employee"),
            )
        assertEquals(
            "payroll_payment_observation_required",
            (requirePayrollPaymentObservation(instruction, payable) as Result.Failed).failure.code,
        )
        val observed =
            instruction.copy(
                assessmentRevision = 1,
                paymentVersion = 4,
                amount = BigDecimal("500.00"),
            )
        assertEquals(Result.Success(Unit), requirePayrollPaymentObservation(observed, payable))
        assertEquals(
            "payroll_payment_balance_changed",
            (requirePayrollPaymentObservation(observed.copy(paymentVersion = 3), payable)
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "invalid_payroll_payment_observation",
            (validatePayrollPaymentInstructions(
                    "Payroll",
                    listOf(observed.copy(amount = null)),
                    "Reviewed",
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            requirePayrollPaymentObservation(
                instruction,
                payable.copy(assessmentRevision = 0, confirmedPaid = BigDecimal.ZERO),
            ),
        )
        assertEquals(
            "payroll_assessment_not_payable",
            (requirePayrollPaymentObservation(observed, payable.copy(amount = BigDecimal.ZERO))
                    as Result.Failed)
                .failure
                .code,
        )
    }
}
