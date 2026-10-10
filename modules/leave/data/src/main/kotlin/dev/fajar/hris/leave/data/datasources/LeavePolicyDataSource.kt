package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveTypeRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface LeavePolicyDataSource {
    fun lock(company: UUID, shared: Boolean)

    fun find(company: UUID, id: UUID): LeaveTypeRow?

    fun effective(company: UUID, id: UUID, asOf: LocalDate): LeaveTypeRow?

    fun list(company: UUID, asOf: LocalDate, after: String?, limit: Int): List<LeaveTypeRow>

    fun catalog(company: UUID, active: Boolean?, after: String?, limit: Int): List<LeaveTypeRow>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<LeaveTypeRevisionsRecord>

    fun insert(row: LeaveTypesRecord)

    fun advanceVersion(company: UUID, id: UUID, expectedVersion: Long): Long?

    fun append(row: LeaveTypeRevisionsRecord)
}
