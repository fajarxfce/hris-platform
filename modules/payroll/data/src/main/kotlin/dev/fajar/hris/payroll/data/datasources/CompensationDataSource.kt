package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.CompensationRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface CompensationDataSource {
    fun revision(company: UUID, employee: UUID, revision: Long): CompensationRow?

    fun current(company: UUID, employee: UUID): CompensationRow?

    fun effective(company: UUID, employee: UUID, month: LocalDate): CompensationRow?

    fun list(company: UUID, month: LocalDate, after: UUID?, limit: Int): List<CompensationRow>

    fun history(company: UUID, employee: UUID, after: Long?, limit: Int): List<CompensationRow>

    fun insert(row: EmployeeCompensationsRecord)

    fun advance(company: UUID, employee: UUID, expectedVersion: Long): Long?

    fun append(row: EmployeeCompensationRevisionsRecord)
}
