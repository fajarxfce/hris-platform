package dev.fajar.hris.payroll.data.models

import java.math.BigDecimal
import java.util.UUID

data class PayrollComparisonAmountsRow(
    val gross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
)

data class PayrollComparisonEmployeeRow(
    val number: String,
    val name: String,
    val amounts: PayrollComparisonAmountsRow,
)

data class PayrollComparisonItemRow(
    val employmentId: UUID,
    val baseline: PayrollComparisonEmployeeRow?,
    val current: PayrollComparisonEmployeeRow?,
    val change: String,
)

data class PayrollComparisonRows(
    val baselineCount: Int,
    val currentCount: Int,
    val matched: Int,
    val added: Int,
    val removed: Int,
    val changed: Int,
    val unchanged: Int,
    val invalid: Int,
    val baseline: PayrollComparisonAmountsRow,
    val current: PayrollComparisonAmountsRow,
    val items: List<PayrollComparisonItemRow>,
)
