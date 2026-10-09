package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollTaxContinuityPolicyTest : MonthlyPayrollFixture() {
    private fun opening(base: PayrollCalculationFacts) =
        PayrollTaxOpening(
            UUID.randomUUID(),
            UUID.randomUUID(),
            base.month.year,
            1,
            base.taxHistory,
            PayrollTaxOpeningStatus.VERIFIED,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.EPOCH,
            "Reviewed fixture",
        )

    private fun target(opening: PayrollTaxOpening, previous: UUID? = null) =
        PayrollRunTarget(
            1,
            opening.employeeId,
            "E001",
            "Example employee",
            0,
            0,
            UUID.randomUUID(),
            1,
            opening.id,
            opening.version,
            previous,
        )

    private fun assessment(opening: PayrollTaxOpening, facts: PayrollCalculationFacts) =
        calculate(facts).let {
            PayrollTaxAssessment(
                UUID.randomUUID(),
                opening.employeeId,
                facts.month,
                opening.id,
                opening.version,
                facts.compensation.tax,
                it.taxInput,
                it.tax,
            )
        }

    @Test
    fun `September to December derives the PMK168 Tuan B refund from each published month`() {
        val initial = facts(YearMonth.of(2024, 9), "15500000")
        val opening = opening(initial)
        var previous: PayrollTaxAssessment? = null
        for (month in 9..12) {
            val base = facts(YearMonth.of(2024, month), "15500000")
            val history =
                derivePayrollTaxHistory(
                    target(opening, previous?.id),
                    base.month,
                    opening,
                    base.compensation.tax,
                    previous,
                )
            assertTrue(history is Result.Success, history.toString())
            val value =
                base.copy(
                    compensation =
                        base.compensation.copy(additionalRetirementContribution = amount("100000")),
                    employment =
                        listOf(
                            base.employment
                                .single()
                                .copy(
                                    effectiveFrom = LocalDate.of(2024, 9, 1),
                                    startDate = LocalDate.of(2024, 9, 1),
                                )
                        ),
                    taxHistory = (history as Result.Success).value,
                )
            val current = assessment(opening, value)
            if (month == 12) {
                money("46500000", current.input.history.taxableGross)
                money("300000", current.input.history.retirementContributions)
                money("3255000", current.input.history.withheld)
                assertEquals(3, current.input.history.employmentMonths)
                money("280000", requireNotNull(current.calculation.annualTax))
                money("-2975000", current.calculation.withheld)
                money("18375000", current.calculation.takeHome)
                assertTrue(current.input.finalPeriod)
            }
            previous = current
        }
        assertEquals(8, opening.terms.throughMonth)
        assertEquals(0, opening.terms.history.employmentMonths)
    }

    @Test
    fun `gross up includes the assessed allowance and carries previous employer credits once`() {
        val initial = facts(salary = "10000000")
        val base =
            initial.copy(
                compensation = initial.compensation.copy(treatment = TaxTreatment.GROSS_UP),
                taxHistory =
                    initial.taxHistory.copy(
                        history =
                            IncomeTaxHistory(
                                taxableGross = amount("30000000"),
                                employmentMonths = 3,
                                withheld = amount("1000000"),
                                previousEmployerNet = amount("25000000"),
                                previousEmployerWithheld = amount("750000"),
                            )
                    ),
            )
        val opening = opening(base)
        val previous = assessment(opening, base)
        val derived =
            derivePayrollTaxHistory(
                target(opening, previous.id),
                base.month.plusMonths(1),
                opening,
                base.compensation.tax,
                previous,
            )
        assertTrue(derived is Result.Success, derived.toString())
        val h = (derived as Result.Success).value.history
        assertTrue(previous.calculation.taxAllowance.signum() > 0)
        assertEquals(
            0,
            (amount("30000000") + previous.calculation.taxableGross).compareTo(h.taxableGross),
        )
        money("25000000", h.previousEmployerNet)
        money("750000", h.previousEmployerWithheld)
        assertEquals(4, h.employmentMonths)
    }

    @Test
    fun `gaps future periods missing references and changed openings do not reset history`() {
        val base = facts()
        val opening = opening(base)
        val previous = assessment(opening, base)
        val selected = target(opening, previous.id)
        fun code(
            month: YearMonth,
            prior: PayrollTaxAssessment? = previous,
            original: PayrollTaxOpening = opening,
        ): String {
            val result =
                derivePayrollTaxHistory(selected, month, original, base.compensation.tax, prior)
            assertTrue(result is Result.Failed, result.toString())
            return (result as Result.Failed).failure.code
        }
        assertEquals("payroll_tax_history_incomplete", code(base.month.plusMonths(2)))
        assertEquals("payroll_tax_history_out_of_order", code(base.month))
        assertEquals("payroll_tax_assessment_missing", code(base.month.plusMonths(1), null))
        assertEquals(
            "payroll_tax_history_mismatch",
            code(base.month.plusMonths(1), original = opening.copy(version = 2)),
        )
    }

    @Test
    fun `terminal refunds rehire and changed tax status require reviewed continuation`() {
        val base = facts(salary = "15500000")
        val opening = opening(base)
        val previous = assessment(opening, base)
        for (prior in
            listOf(
                previous.copy(employeeId = UUID.randomUUID()),
                previous.copy(
                    input = previous.input.copy(finalPeriod = true),
                    calculation = previous.calculation.copy(withheld = amount("-2000000")),
                ),
            )) {
            val result =
                derivePayrollTaxHistory(
                    target(opening, prior.id),
                    base.month.plusMonths(1),
                    opening,
                    base.compensation.tax,
                    prior,
                )
            assertEquals(
                "payroll_tax_continuation_review_required",
                (result as Result.Failed).failure.code,
            )
        }
        for (registration in
            listOf(
                base.compensation.tax.copy(ptkp = PtkpStatus.K0),
                base.compensation.tax.copy(residency = TaxResidency.NON_RESIDENT),
                base.compensation.tax.copy(subjectiveUntil = LocalDate.of(2026, 12, 1)),
            )) {
            val result =
                derivePayrollTaxHistory(
                    target(opening, previous.id),
                    base.month.plusMonths(1),
                    opening,
                    registration,
                    previous,
                )
            assertEquals("payroll_tax_history_mismatch", (result as Result.Failed).failure.code)
        }
    }

    @Test
    fun `a new year uses a separate verified opening and does not carry an old assessment`() {
        val base = facts(YearMonth.of(2027, 1))
        val opening = opening(base)
        assertEquals(
            Result.Success(opening.terms),
            derivePayrollTaxHistory(
                target(opening),
                base.month,
                opening,
                base.compensation.tax,
                null,
            ),
        )
        assertEquals(0, opening.terms.throughMonth)
        money("0", opening.terms.history.taxableGross)
        val old = assessment(opening, facts(YearMonth.of(2026, 12)))
        val result =
            derivePayrollTaxHistory(
                target(opening, old.id),
                base.month,
                opening,
                base.compensation.tax,
                old,
            )
        assertEquals("payroll_tax_history_out_of_order", (result as Result.Failed).failure.code)
    }

    @Test
    fun `a publication rejects a stale captured tax reference and interruption propagates`() {
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_changed")),
            requirePayrollPublicationSources(PayrollPublicationReadiness(0, 0, 1, 0)),
        )
        val base = facts()
        val opening = opening(base)
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                derivePayrollTaxHistory(
                    target(opening),
                    base.month,
                    opening,
                    base.compensation.tax,
                    null,
                )
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
