package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class PayrollAmendmentFixture : MonthlyPayrollFixture() {
    protected fun sources(
        months: IntRange = 9..12,
        salary: String = "15500000",
        treatment: TaxTreatment = TaxTreatment.GROSS,
        previousEmployerNet: String = "0",
        previousEmployerWithheld: String = "0",
    ): List<PayrollAmendmentSource> {
        val company = UUID.randomUUID()
        val person = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val initial = facts(YearMonth.of(2024, months.first), salary)
        val opening =
            PayrollTaxOpening(
                UUID.randomUUID(),
                employee,
                2024,
                1,
                initial.taxHistory.copy(
                    history =
                        IncomeTaxHistory(
                            previousEmployerNet = amount(previousEmployerNet),
                            previousEmployerWithheld = amount(previousEmployerWithheld),
                        )
                ),
                PayrollTaxOpeningStatus.VERIFIED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2024-01-01T00:00:00Z"),
                "Verified opening",
            )
        var previous: PayrollTaxAssessment? = null
        return months.map { value ->
            val month = YearMonth.of(2024, value)
            val target =
                PayrollRunTarget(
                    1,
                    employee,
                    "E001",
                    "Example employee",
                    0,
                    0,
                    UUID.randomUUID(),
                    1,
                    opening.id,
                    opening.version,
                    previous?.id,
                )
            val base = facts(month, salary)
            val history =
                derivePayrollTaxHistory(target, month, opening, base.compensation.tax, previous)
            assertTrue(history is Result.Success, history.toString())
            val current =
                base.copy(
                    compensation =
                        base.compensation.copy(
                            treatment = treatment,
                            additionalRetirementContribution = amount("100000"),
                        ),
                    employment =
                        listOf(
                            base.employment
                                .single()
                                .copy(
                                    effectiveFrom = initial.month.atDay(1),
                                    startDate = initial.month.atDay(1),
                                )
                        ),
                    taxHistory = (history as Result.Success).value,
                )
            val calculation = calculate(current)
            val id = UUID.randomUUID()
            previous =
                PayrollTaxAssessment(
                    id,
                    employee,
                    month,
                    opening.id,
                    opening.version,
                    current.compensation.tax,
                    calculation.taxInput,
                    calculation.tax,
                )
            PayrollAmendmentSource(company, id, 0, person, target, opening, current, calculation)
        }
    }

    protected fun correction(source: PayrollAmendmentSource, salary: String) =
        PayrollAmendmentCorrection(
            source.assessmentId,
            source.facts.copy(
                compensation = source.facts.compensation.copy(basicSalary = amount(salary))
            ),
        )

    protected fun preview(
        sources: List<PayrollAmendmentSource>,
        corrections: List<PayrollAmendmentCorrection>,
        first: UUID = sources.first().assessmentId,
    ): PayrollAmendmentPreview {
        val result = recalculatePayrollAmendment(first, sources, corrections)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }
}
