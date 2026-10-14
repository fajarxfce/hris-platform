package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.CompensationEmployeeRow
import dev.fajar.hris.payroll.domain.entities.CompensationEmployee

fun CompensationEmployeeRow.toCompensationEmployee() =
    CompensationEmployee(id, employeeNumber, name, employmentVersion)
