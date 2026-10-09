package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.records.WorkPeriodsRecord
import java.time.LocalDate
import java.util.UUID

interface PayrollWorkSourceDataSource {
    fun period(company: UUID, month: LocalDate, lock: Boolean): WorkPeriodsRecord?

    fun includesEmployee(company: UUID, job: UUID, employee: UUID): Boolean
}
