package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollTaxOpeningPolicyTest {
    private val zero =
        PayrollTaxOpeningTerms(
            0,
            TaxResidency.RESIDENT,
            PtkpStatus.K0,
            IncomeTaxHistory(),
            "Verified no previous current-year payroll",
        )

    @Test
    fun explicitZeroAndImportedEmployerHistoryAreValidatedIndependently() {
        assertEquals(Result.Success(Unit), validatePayrollTaxOpening(2026, zero, "Start January"))
        val previous =
            zero.copy(
                throughMonth = 6,
                history =
                    IncomeTaxHistory(
                        previousEmployerNet = BigDecimal("55000000"),
                        previousEmployerWithheld = BigDecimal("1000000"),
                    ),
            )
        assertEquals(
            Result.Success(Unit),
            validatePayrollTaxOpening(2026, previous, "Imported withholding certificate"),
        )
        assertTrue(
            validatePayrollTaxOpening(2026, previous.copy(throughMonth = 0), "Start")
                is Result.Failed
        )
        assertTrue(
            validatePayrollTaxOpening(
                2026,
                previous.copy(residency = TaxResidency.NON_RESIDENT),
                "Start",
            )
                is Result.Failed
        )
    }

    @Test
    fun unboundedMoneyAndInconsistentMonthsCannotEnterOpeningEvidence() {
        for (terms in
            listOf(
                zero.copy(throughMonth = 12),
                zero.copy(reference = ""),
                zero.copy(history = IncomeTaxHistory(taxableGross = BigDecimal("1E+1000000"))),
                zero.copy(history = IncomeTaxHistory(withheld = BigDecimal("-1"))),
                zero.copy(
                    throughMonth = 1,
                    history =
                        IncomeTaxHistory(
                            taxableGross = BigDecimal("60000000000"),
                            employmentMonths = 1,
                        ),
                ),
                zero.copy(throughMonth = 1, history = IncomeTaxHistory(employmentMonths = 2)),
                zero.copy(
                    throughMonth = 1,
                    history =
                        IncomeTaxHistory(
                            taxableGross = BigDecimal.ONE,
                            retirementContributions = BigDecimal.TEN,
                            employmentMonths = 1,
                        ),
                ),
            )) {
            val result = validatePayrollTaxOpening(2026, terms, "Reviewed")
            assertTrue(result is Result.Failed, result.toString())
            assertEquals("invalid_payroll_tax_opening", (result as Result.Failed).failure.code)
        }
    }

    @Test
    fun numericallyEqualDecimalEvidenceHasTheSameOperationFingerprint() {
        val original =
            zero.copy(
                throughMonth = 1,
                history =
                    IncomeTaxHistory(taxableGross = BigDecimal("10000000"), employmentMonths = 1),
            )
        val same =
            original.copy(history = original.history.copy(taxableGross = BigDecimal("10000000.00")))
        assertEquals(payrollTaxOpeningParts(original), payrollTaxOpeningParts(same))
        assertNotEquals(
            payrollTaxOpeningParts(original),
            payrollTaxOpeningParts(original.copy(reference = "Another reviewed certificate")),
        )
    }
}
