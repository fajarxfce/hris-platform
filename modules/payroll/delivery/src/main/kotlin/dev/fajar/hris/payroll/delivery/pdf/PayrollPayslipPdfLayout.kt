package dev.fajar.hris.payroll.delivery.pdf

import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.payroll.domain.entities.InsuranceProgram
import dev.fajar.hris.payroll.domain.entities.PayrollEarningKind
import dev.fajar.hris.payroll.domain.entities.PayrollPayslip
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Formats retained facts only; money, tax, and contribution rules remain in domain. */
internal fun writePayslipLayout(
    canvas: PayslipPdfCanvas,
    payslip: PayrollPayslip,
    language: PayrollDocumentLanguage,
) {
    val summary = payslip.summary
    val calculation = payslip.calculation
    if (calculation.earnings.size > 63 || calculation.contributions.size > 5)
        throw DomainFailureException(PAYSLIP_PDF_LIMIT)
    val format = DecimalFormat("#,##0.00", DecimalFormatSymbols(language.locale))
    fun text(value: PayslipText) = value.value(language)
    fun money(value: BigDecimal) = "IDR ${format.format(value)}"

    canvas.paragraph(payslip.companyName, 16f)
    canvas.paragraph("${text(PayslipText.TITLE)} - ${summary.taxMonth}", 13f)
    canvas.paragraph("${text(PayslipText.EMPLOYEE)}: ${summary.employeeName}")
    canvas.paragraph("${text(PayslipText.EMPLOYEE_NUMBER)}: ${summary.employeeNumber}")
    canvas.paragraph("${text(PayslipText.PERIOD)}: ${summary.taxMonth}")
    canvas.paragraph("${text(PayslipText.PAYMENT_DATE)}: ${summary.plannedPaymentDate}")

    canvas.section(text(PayslipText.EARNINGS))
    for (earning in calculation.earnings) {
        val label =
            earning.name
                ?: text(
                    when (earning.kind) {
                        PayrollEarningKind.BASIC -> PayslipText.BASIC
                        PayrollEarningKind.FIXED -> PayslipText.FIXED
                        PayrollEarningKind.VARIABLE -> PayslipText.VARIABLE
                        PayrollEarningKind.OVERTIME -> PayslipText.OVERTIME
                        PayrollEarningKind.HOLIDAY -> PayslipText.HOLIDAY
                    }
                )
        canvas.row(label, money(earning.amount))
    }
    if (calculation.tax.taxAllowance.signum() != 0)
        canvas.row(text(PayslipText.TAX_ALLOWANCE), money(calculation.tax.taxAllowance))
    if (calculation.tax.deductionAllowance.signum() != 0)
        canvas.row(text(PayslipText.DEDUCTION_ALLOWANCE), money(calculation.tax.deductionAllowance))

    canvas.section(text(PayslipText.DEDUCTIONS))
    canvas.row(text(PayslipText.RETIREMENT), money(calculation.taxInput.retirementContributions))
    if (calculation.taxInput.qualifiedDonations.signum() != 0)
        canvas.row(text(PayslipText.DONATIONS), money(calculation.taxInput.qualifiedDonations))
    canvas.row(text(PayslipText.OTHER_DEDUCTIONS), money(calculation.taxInput.otherNetDeductions))
    canvas.row(text(PayslipText.TOTAL_DEDUCTIONS), money(calculation.employeeDeductions))
    canvas.row(text(PayslipText.INCOME_TAX), money(summary.withheld))
    canvas.total(text(PayslipText.TAKE_HOME), money(summary.takeHome))

    if (calculation.contributions.isNotEmpty()) {
        canvas.section(text(PayslipText.EMPLOYER_CONTRIBUTIONS))
        for (contribution in calculation.contributions) {
            val label =
                when (contribution.program) {
                    InsuranceProgram.HEALTH -> PayslipText.HEALTH
                    InsuranceProgram.OLD_AGE -> PayslipText.OLD_AGE
                    InsuranceProgram.PENSION -> PayslipText.PENSION
                    InsuranceProgram.ACCIDENT -> PayslipText.ACCIDENT
                    InsuranceProgram.DEATH -> PayslipText.DEATH
                }
            canvas.row(text(label), money(contribution.employerAmount))
        }
    }
    canvas.section(text(PayslipText.TAX_SUMMARY))
    canvas.row(text(PayslipText.TAXABLE_GROSS), money(summary.taxableGross))
    if (calculation.taxInput.nonCashTaxable.signum() != 0)
        canvas.row(text(PayslipText.NON_CASH), money(calculation.taxInput.nonCashTaxable))
    canvas.paragraph("${text(PayslipText.RULE)}: ${calculation.tax.ruleId}", 9f)
    canvas.paragraph("${text(PayslipText.REFERENCE)}: ${summary.id}", 8f)
    canvas.paragraph("${text(PayslipText.PUBLISHED)}: ${summary.publishedAt}", 8f)
}
