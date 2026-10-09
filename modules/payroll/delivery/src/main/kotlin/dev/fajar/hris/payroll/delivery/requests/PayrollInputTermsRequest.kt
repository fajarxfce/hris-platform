package dev.fajar.hris.payroll.delivery.requests

data class PayrollInputTermsRequest(
    val earnings: List<PayrollVariableEarningRequest>,
    val deductions: List<PayrollNetDeductionRequest>,
    val nonCashTaxable: String,
    val scheduledMonthUnits: String? = null,
    val dayResolutions: List<PayrollDayResolutionRequest>,
    val holidayAllowance: PayrollHolidayInputRequest? = null,
    val reviewReference: String,
)
