package dev.fajar.hris.payroll.delivery.responses

data class PayrollInputTermsResponse(
    val earnings: List<PayrollVariableEarningResponse>,
    val deductions: List<PayrollNetDeductionResponse>,
    val nonCashTaxable: String,
    val scheduledMonthUnits: String?,
    val dayResolutions: List<PayrollDayResolutionResponse>,
    val holidayAllowance: PayrollHolidayInputResponse?,
    val reviewReference: String,
)
