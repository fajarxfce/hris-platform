package dev.fajar.hris.payroll.data

import dev.fajar.hris.payroll.data.mappers.toAttempt
import dev.fajar.hris.payroll.data.models.PayrollPaymentProgressRow
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PayrollPaymentMappingTest {
    private val at = OffsetDateTime.parse("2026-10-15T10:00:00.123456Z")
    private val row =
        PayrollPaymentProgressRow(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "SUCCEEDED",
            at,
            at,
            at,
            BigDecimal("1000.25"),
            2,
        )

    @Test
    fun confirmedPaymentRetainsItsExactBankTimeAndAssessmentRevision() {
        val mapped = row.toAttempt()
        assertEquals(at.toInstant(), mapped.resolvedAt)
        assertEquals(BigDecimal("1000.25"), mapped.amount)
        assertEquals(2, mapped.assessmentRevision)
    }

    @ParameterizedTest
    @ValueSource(strings = ["SUCCEEDED", "FAILED"])
    fun confirmedOutcomesCannotHideMissingBankOrReleaseTimes(status: String) {
        assertThrows(IllegalArgumentException::class.java) {
            row.copy(status = status, resolvedAt = null).toAttempt()
        }
        assertThrows(IllegalArgumentException::class.java) {
            row.copy(status = status, releasedAt = null).toAttempt()
        }
    }

    @Test
    fun pendingAndCancelledInstructionsDoNotInventAConfirmationTime() {
        for (status in listOf("PREPARED", "PENDING", "CANCELLED")) {
            assertNull(row.copy(status = status, resolvedAt = null).toAttempt().resolvedAt)
        }
    }
}
