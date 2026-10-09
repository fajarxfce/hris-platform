package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.EmployeeCompensationRevisionsRecord

data class CompensationRow(val version: Long, val revision: EmployeeCompensationRevisionsRecord)
