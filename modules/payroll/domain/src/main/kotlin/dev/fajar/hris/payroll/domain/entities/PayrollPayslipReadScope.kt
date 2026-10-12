package dev.fajar.hris.payroll.domain.entities

/** SELF narrows an otherwise authorized reader to their own employments. */
enum class PayrollPayslipReadScope {
    AUTHORIZED,
    SELF,
}
