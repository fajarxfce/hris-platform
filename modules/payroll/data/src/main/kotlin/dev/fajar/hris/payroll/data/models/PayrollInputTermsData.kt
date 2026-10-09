package dev.fajar.hris.payroll.data.models

data class PayrollInputTermsData(
    val earnings: List<PayrollVariableEarningData>,
    val deductions: List<PayrollNetDeductionData>,
    val nonCashTaxable: String,
    val scheduledMonthUnits: String?,
    val dayResolutions: List<PayrollDayResolutionData>,
    val holidayAllowance: PayrollHolidayInputData?,
    val reviewReference: String,
)
