package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface LeaveEntitlementDataSource {
    fun frequency(company: UUID, employee: UUID, type: UUID, year: Int): String?

    fun ensureFrequency(company: UUID, employee: UUID, type: UUID, year: Int, frequency: String)

    fun posting(
        company: UUID,
        employee: UUID,
        type: UUID,
        period: LocalDate,
    ): LeaveAccrualPostingsRecord?

    fun postings(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
    ): List<LeaveAccrualPostingsRecord>

    fun insertPosting(row: LeaveAccrualPostingsRecord)

    fun closing(company: UUID, employee: UUID, type: UUID, year: Int): LeaveYearClosingsRecord?

    fun insertClosing(row: LeaveYearClosingsRecord)

    fun ensureAccount(company: UUID, employee: UUID, type: UUID, year: Int)

    fun closeAccount(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
        expectedVersion: Long,
        closingId: UUID,
    ): Long?
}
