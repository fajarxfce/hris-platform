package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InsuranceContributionPolicyTest {
    private fun policy() =
        PayrollPolicy(
            0,
            0,
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 12),
            INDONESIAN_INCOME_TAX_2024,
            INDONESIAN_INSURANCE_PU_V1,
            BigDecimal("4000000"),
            BigDecimal("12000000"),
            BigDecimal("10000000"),
            ContributionRounding.HALF_UP,
            listOf("https://example.test/reviewed-fixture"),
        )

    private fun terms(wage: String) =
        CompensationTerms(
            BigDecimal(wage),
            emptyList(),
            TaxTreatment.GROSS,
            TaxRegistration(
                TaxResidency.RESIDENT,
                PtkpStatus.TK0,
                "ID",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                "Verified fixture",
            ),
            BigDecimal(wage),
            InsuranceProgram.entries.toSet(),
            null,
            AccidentRisk.VERY_LOW,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )

    @Test
    fun `health floor and separately configured annual caps determine contribution bases`() {
        val low =
            (calculateInsuranceContributions(policy(), terms("3000000")) as Result.Success)
                .value
                .associateBy { it.program }
        assertEquals(BigDecimal("4000000"), low.getValue(InsuranceProgram.HEALTH).base)
        assertEquals(BigDecimal("40000"), low.getValue(InsuranceProgram.HEALTH).employeeAmount)
        val high =
            (calculateInsuranceContributions(policy(), terms("20000000")) as Result.Success)
                .value
                .associateBy { it.program }
        assertEquals(BigDecimal("120000"), high.getValue(InsuranceProgram.HEALTH).employeeAmount)
        assertEquals(BigDecimal("480000"), high.getValue(InsuranceProgram.HEALTH).employerAmount)
        assertEquals(BigDecimal("100000"), high.getValue(InsuranceProgram.PENSION).employeeAmount)
        assertEquals(BigDecimal("200000"), high.getValue(InsuranceProgram.PENSION).employerAmount)
        assertEquals(BigDecimal("400000"), high.getValue(InsuranceProgram.OLD_AGE).employeeAmount)
        assertEquals(BigDecimal("740000"), high.getValue(InsuranceProgram.OLD_AGE).employerAmount)
        assertEquals(BigDecimal("48000"), high.getValue(InsuranceProgram.ACCIDENT).employerAmount)
        assertEquals(BigDecimal("60000"), high.getValue(InsuranceProgram.DEATH).employerAmount)
    }

    @Test
    fun `additional family and workplace risk are explicit and rounding is snapshotted`() {
        val adjusted =
            terms("20000000")
                .copy(accidentRisk = AccidentRisk.VERY_HIGH, additionalHealthDependents = 2)
        val result =
            (calculateInsuranceContributions(policy(), adjusted) as Result.Success)
                .value
                .associateBy { it.program }
        assertEquals(BigDecimal("360000"), result.getValue(InsuranceProgram.HEALTH).employeeAmount)
        assertEquals(
            BigDecimal("348000"),
            result.getValue(InsuranceProgram.ACCIDENT).employerAmount,
        )
        val wage = terms("10000001")
        val rounded =
            (calculateInsuranceContributions(
                    policy().copy(contributionRounding = ContributionRounding.UP),
                    wage,
                )
                    as Result.Success)
                .value
                .associateBy { it.program }
        assertEquals(BigDecimal("100001"), rounded.getValue(InsuranceProgram.HEALTH).employeeAmount)
        assertInstanceOf(
            Result.Failed::class.java,
            calculateInsuranceContributions(policy().copy(healthWageCap = BigDecimal.ONE), wage),
        )
    }
}
