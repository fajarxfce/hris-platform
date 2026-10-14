package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollEmployeeRow
import dev.fajar.hris.payroll.domain.entities.PayrollEmployee

fun PayrollEmployeeRow.toPayrollEmployee() =
    PayrollEmployee(id, employeeNumber, name, employmentVersion)
