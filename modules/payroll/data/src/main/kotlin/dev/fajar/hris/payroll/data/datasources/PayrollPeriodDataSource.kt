package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPeriodMemberRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface PayrollPeriodDataSource {
    fun transition(company: UUID, id: UUID, version: Long, status: String, runId: UUID?): Long?

    fun find(company: UUID, id: UUID): PayrollPeriodsRecord?

    fun active(company: UUID, month: LocalDate): Boolean

    fun count(company: UUID, month: LocalDate): Int

    fun insert(row: PayrollPeriodsRecord)

    fun insertMembers(rows: List<PayrollPeriodMembersRecord>)

    fun cancel(company: UUID, id: UUID, version: Long): Long?

    fun append(row: PayrollPeriodChangesRecord)

    fun members(company: UUID, id: UUID, after: UUID?, limit: Int): List<PayrollPeriodMemberRow>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<PayrollPeriodChangesRecord>

    fun list(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollPeriodsRecord>
}
