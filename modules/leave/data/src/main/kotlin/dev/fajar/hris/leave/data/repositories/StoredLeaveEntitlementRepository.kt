package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveEntitlementDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveEntitlementRepository
import java.time.YearMonth
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredLeaveEntitlementRepository(
    private val source: LeaveEntitlementDataSource,
    private val json: ObjectMapper,
) : LeaveEntitlementRepository {
    override fun frequency(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<LeaveAccrualFrequency?> = safeDatabaseCall {
        source.frequency(companyId, employeeId, typeId, year)?.let(LeaveAccrualFrequency::valueOf)
    }

    override fun posting(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        period: YearMonth,
    ): Result<LeaveAccrualPosting?> = safeDatabaseCall {
        source.posting(companyId, employeeId, typeId, period.atDay(1))?.toPosting(json)
    }

    override fun postings(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<List<LeaveAccrualPosting>> = safeDatabaseCall {
        source.postings(companyId, employeeId, typeId, year).map { it.toPosting(json) }
    }

    override fun createPosting(companyId: UUID, posting: LeaveAccrualPosting): Result<Unit> =
        safeDatabaseCall {
            source.ensureFrequency(
                companyId,
                posting.employeeId,
                posting.typeId,
                posting.processedMonth.year,
                posting.frequency.name,
            )
            source.insertPosting(posting.toRow(companyId, json))
        }

    override fun closing(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<LeaveYearClosing?> = safeDatabaseCall {
        source.closing(companyId, employeeId, typeId, year)?.toClosing(json)
    }

    override fun createClosing(companyId: UUID, closing: LeaveYearClosing): Result<Unit> =
        safeDatabaseCall {
            source.insertClosing(closing.toRow(companyId, json))
        }

    override fun closeAccount(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        expectedVersion: Long,
        closingId: UUID,
    ): Result<Unit> =
        safeDatabaseCall {
                source.ensureAccount(companyId, employeeId, typeId, year)
                source.closeAccount(companyId, employeeId, typeId, year, expectedVersion, closingId)
            }
            .requireCurrentVersion()
            .map { Unit }
}
