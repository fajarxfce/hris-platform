package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PayrollComparisonAmounts(
    val taxableGross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
) {
    operator fun minus(other: PayrollComparisonAmounts) =
        PayrollComparisonAmounts(
            taxableGross - other.taxableGross,
            withheld - other.withheld,
            takeHome - other.takeHome,
        )

    companion object {
        val ZERO = PayrollComparisonAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

enum class PayrollComparisonChange {
    ADDED,
    REMOVED,
    CHANGED,
    UNCHANGED,
}

data class PayrollComparisonEmployee(
    val number: String,
    val name: String,
    val amounts: PayrollComparisonAmounts,
)

data class PayrollComparisonItem(
    val employmentId: UUID,
    val baseline: PayrollComparisonEmployee?,
    val current: PayrollComparisonEmployee?,
    val change: PayrollComparisonChange,
) {
    val delta: PayrollComparisonAmounts
        get() =
            (current?.amounts ?: PayrollComparisonAmounts.ZERO) -
                (baseline?.amounts ?: PayrollComparisonAmounts.ZERO)
}

data class PayrollComparisonSummary(
    val baselineEmployees: Int,
    val currentEmployees: Int,
    val matched: Int,
    val added: Int,
    val removed: Int,
    val changed: Int,
    val unchanged: Int,
    val baseline: PayrollComparisonAmounts,
    val current: PayrollComparisonAmounts,
) {
    val delta: PayrollComparisonAmounts
        get() = current - baseline
}

data class PayrollComparisonData(
    val summary: PayrollComparisonSummary,
    val employees: Page<PayrollComparisonItem>,
)

data class PayrollRunComparison(
    val baseline: PayrollRun,
    val current: PayrollRun,
    val data: PayrollComparisonData,
    val changedOnly: Boolean,
    val observedAt: Instant,
)
