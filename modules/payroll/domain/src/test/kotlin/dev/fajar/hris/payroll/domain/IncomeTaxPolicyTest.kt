package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.Duration
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IncomeTaxPolicyTest {
    private fun amount(value: String) = BigDecimal(value)

    private fun input(gross: String = "10000000") =
        IncomeTaxInput(
            YearMonth.of(2024, 12),
            TaxResidency.RESIDENT,
            PtkpStatus.K0,
            TaxTreatment.GROSS,
            amount(gross),
        )

    private fun calculate(value: IncomeTaxInput): IncomeTaxCalculation {
        val result = calculateIncomeTax(INDONESIAN_INCOME_TAX_2024, value)
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun money(expected: String, actual: BigDecimal?) =
        assertEquals(
            0,
            amount(expected).compareTo(requireNotNull(actual)),
            "Expected $expected; received $actual",
        )

    @Test
    fun `PP58 Tuan R reconciles the final month using actual annual net`() {
        // PP58/2023 explanation pp.8-9, K0, salary10m and employee pension100k per month.
        money(
            "200000",
            calculate(
                    input()
                        .copy(
                            month = YearMonth.of(2024, 1),
                            retirementContributions = amount("100000"),
                        )
                )
                .withheld,
        )
        val result =
            calculate(
                input()
                    .copy(
                        finalPeriod = true,
                        retirementContributions = amount("100000"),
                        history =
                            IncomeTaxHistory(
                                taxableGross = amount("110000000"),
                                retirementContributions = amount("1100000"),
                                withheld = amount("2200000"),
                                employmentMonths = 11,
                            ),
                    )
            )
        money("112800000", result.annualNet)
        money("54300000", result.annualTaxable)
        money("2715000", result.annualTax)
        money("515000", result.withheld)
    }

    @Test
    fun `PMK168 Tuan A includes THR bonus and employer insurance in final reconciliation`() {
        // PMK168/2023 pp.33-35, December cash90m plus JKK and JKM80k.
        val result =
            calculate(
                input("90000000")
                    .copy(
                        nonCashTaxable = amount("80000"),
                        finalPeriod = true,
                        retirementContributions = amount("100000"),
                        qualifiedDonations = amount("200000"),
                        history =
                            IncomeTaxHistory(
                                amount("360880000"),
                                amount("1100000"),
                                amount("2200000"),
                                amount("50120000"),
                                11,
                            ),
                    )
            )
        money("441360000", result.annualNet)
        money("382860000", result.annualTaxable)
        money("64715000", result.annualTax)
        money("14595000", result.withheld)
    }

    @Test
    fun `ordinary resident starting September receives the official final refund`() {
        // PMK168/2023 p.36, Tuan B; employment starts September but subjective obligation is a full
        // year.
        val result =
            calculate(
                input("15500000")
                    .copy(
                        ptkp = PtkpStatus.TK0,
                        finalPeriod = true,
                        retirementContributions = amount("100000"),
                        history =
                            IncomeTaxHistory(
                                taxableGross = amount("46500000"),
                                retirementContributions = amount("300000"),
                                withheld = amount("3255000"),
                                employmentMonths = 3,
                            ),
                    )
            )
        money("59600000", result.annualNet)
        money("59600000", result.annualizedNet)
        money("280000", result.annualTax)
        money("-2975000", result.withheld)
        money("18375000", result.takeHome)
    }

    @Test
    fun `new subjective obligation for resident expatriate annualizes net and proportions tax`() {
        // PMK168/2023 pp.37-38, Tuan C, Australian resident from1September; mandatory donations775k
        // per month.
        val result =
            calculate(
                input("15500000")
                    .copy(
                        ptkp = PtkpStatus.TK0,
                        finalPeriod = true,
                        subjectiveMonths = 4,
                        qualifiedDonations = amount("775000"),
                        history =
                            IncomeTaxHistory(
                                taxableGross = amount("46500000"),
                                qualifiedDonations = amount("2325000"),
                                withheld = amount("3255000"),
                                employmentMonths = 3,
                            ),
                    )
            )
        money("56900000", result.annualNet)
        money("170700000", result.annualizedNet)
        money("116700000", result.annualTaxable)
        money("3835000", result.annualTax)
        money("580000", result.withheld)
    }

    @Test
    fun `nonresident uses domestic PPh26 without resident allowances`() {
        val result =
            calculate(
                input()
                    .copy(
                        residency = TaxResidency.NON_RESIDENT,
                        finalPeriod = true,
                        ptkp = PtkpStatus.K3,
                    )
            )
        money("2000000", result.withheld)
        money("8000000", result.takeHome)
        assertNull(result.category)
        assertNull(result.annualTax)
    }

    @Test
    fun `TER upper boundaries are inclusive for each allowance category`() {
        for ((status, upper, after) in
            listOf(
                Triple(PtkpStatus.TK0, "5400000", "13500"),
                Triple(PtkpStatus.TK2, "6200000", "15500"),
                Triple(PtkpStatus.K3, "6600000", "16500"),
            )) {
            money("0", calculate(input(upper).copy(ptkp = status)).withheld)
            money(
                after,
                calculate(
                        input((amount(upper) + BigDecimal.ONE).toPlainString()).copy(ptkp = status)
                    )
                    .withheld,
            )
        }
        for (status in listOf(PtkpStatus.TK0, PtkpStatus.TK1, PtkpStatus.K0)) money(
            "45000",
            calculate(input("6000000").copy(ptkp = status)).withheld,
        )
        for (status in
            listOf(
                PtkpStatus.TK2,
                PtkpStatus.TK3,
                PtkpStatus.K1,
                PtkpStatus.K2,
                PtkpStatus.K3,
            )) money("0", calculate(input("6000000").copy(ptkp = status)).withheld)
    }

    @Test
    fun `gross up crosses a TER boundary and covers only withholding`() {
        val result =
            calculate(
                input()
                    .copy(
                        treatment = TaxTreatment.GROSS_UP,
                        retirementContributions = amount("300000"),
                    )
            )
        money("230179", result.taxAllowance)
        money("230179", result.withheld)
        money("9700000", result.takeHome)
    }

    @Test
    fun `net treatment covers employee deductions as taxable compensation`() {
        val result =
            calculate(
                input()
                    .copy(treatment = TaxTreatment.NET, retirementContributions = amount("300000"))
            )
        money("264102", result.taxAllowance)
        money("300000", result.deductionAllowance)
        money("10000000", result.takeHome)
        val nonresident =
            calculate(
                input()
                    .copy(
                        residency = TaxResidency.NON_RESIDENT,
                        treatment = TaxTreatment.NET,
                        retirementContributions = amount("300000"),
                    )
            )
        money("2574999", nonresident.withheld)
        money("10000000", nonresident.takeHome)
    }

    @Test
    fun `non taxable cash stays in take home but is excluded from the TER base`() {
        val result =
            calculate(
                input("12000000")
                    .copy(nonTaxableCash = amount("2000000"), nonCashTaxable = amount("80000"))
            )
        money("10080000", result.taxableGross)
        money("226800", result.withheld)
        money("11773200", result.takeHome)
    }

    @Test
    fun `allowance calculation terminates at all reviewed bracket transitions`() {
        val rules = requireNotNull(incomeTaxRules(INDONESIAN_INCOME_TAX_2024))
        assertTimeout(Duration.ofSeconds(5)) {
            for ((category, bands) in rules.monthly) {
                val status = rules.categories.entries.first { it.value == category }.key
                for (band in bands) {
                    val bound = band.upperInclusive ?: continue
                    for (offset in
                        listOf(BigDecimal.ONE.negate(), BigDecimal.ZERO, BigDecimal.ONE)) {
                        val result =
                            calculate(
                                input((bound + offset).toPlainString())
                                    .copy(ptkp = status, treatment = TaxTreatment.GROSS_UP)
                            )
                        assertEquals(0, result.taxAllowance.compareTo(result.withheld))
                        assertEquals(0, (bound + offset).compareTo(result.takeHome))
                    }
                }
            }
        }
    }

    @Test
    fun `unknown expired and malformed inputs return stable failures without expansion`() {
        val invalid =
            listOf(
                input("-1"),
                input().copy(cashEarnings = BigDecimal("1E+999999")),
                input().copy(cashEarnings = amount("50000000001")),
                input().copy(subjectiveMonths = 0),
                input().copy(history = IncomeTaxHistory(taxableGross = BigDecimal.ONE)),
                input().copy(nonTaxableCash = amount("20000000")),
            )
        for (value in invalid) assertEquals(
            "invalid_payroll_tax_input",
            (calculateIncomeTax(INDONESIAN_INCOME_TAX_2024, value) as Result.Failed).failure.code,
        )
        assertEquals(
            "payroll_tax_rule_unavailable",
            (calculateIncomeTax("unknown", input()) as Result.Failed).failure.code,
        )
        assertEquals(
            "payroll_tax_rule_not_effective",
            (calculateIncomeTax(
                    INDONESIAN_INCOME_TAX_2024,
                    input().copy(month = YearMonth.of(2023, 12)),
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun `cancellation remains interruption and clears no caller signal`() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                calculateIncomeTax(INDONESIAN_INCOME_TAX_2024, input())
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
