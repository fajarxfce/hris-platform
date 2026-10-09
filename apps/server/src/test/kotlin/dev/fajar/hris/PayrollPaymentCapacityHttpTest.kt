package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentCapacityHttpTest : PayrollPaymentApiFixture() {
    @Test
    fun cancelledAttemptsAreRetainedAndTheHistoryHasAFiniteLimit() {
        val f = paymentFixture()
        val firstId = UUID.randomUUID()
        val firstKey = UUID.randomUUID()
        val firstItems = listOf(instruction(f.ownedAssessment))
        val first = payrollBody(preparePayrollPayment(f, firstId, firstItems, firstKey))
        payrollBody(payrollPaymentAction(f, firstId, "cancel", 0))
        repeat(19) {
            val id = UUID.randomUUID()
            payrollBody(preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment))))
            payrollBody(payrollPaymentAction(f, id, "cancel", 0))
        }
        val status = progress(f)
        assertEquals(20, status["attempts"].size())
        assertEquals(40, status["version"].asLong())
        payrollError(
            preparePayrollPayment(f, UUID.randomUUID(), listOf(instruction(f.ownedAssessment))),
            409,
            "payroll_payment_attempt_limit",
        )
        assertEquals(
            0,
            payrollBody(get(f.maker.client, "${f.path}/payables?${dateRange()}"))["items"].size(),
        )
        assertEquals(first, payrollBody(preparePayrollPayment(f, firstId, firstItems, firstKey)))
        assertEquals(20, count(f.payroll.company, "payroll_payment_batches"))
    }
}
