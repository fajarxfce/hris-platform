package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.data.datasources.LeaveReportDataSource
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.leaveReportCursor
import dev.fajar.hris.reporting.domain.repositories.LeaveReportRepository
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class StoredLeaveReportRepository(private val source: LeaveReportDataSource) :
    LeaveReportRepository {
    override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) = safeDatabaseCall {
        source.aggregate(company, from, until).toLeaveReportAggregate()
    }

    override fun requests(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: LeaveStatus?,
        after: LeaveReportCursor?,
        limit: Int,
    ): Result<Page<LeaveReportRequest>> = safeDatabaseCall {
        check(limit in 1..200)
        val rows =
            source.requests(
                company,
                from,
                until,
                status?.name,
                after?.submittedAt?.atOffset(ZoneOffset.UTC),
                after?.id,
                limit + 1,
            )
        check(rows.size <= limit + 1 && rows.map { it.id }.distinct().size == rows.size)
        val mapped = rows.map { it.toLeaveReportRequest() }
        check(status == null || mapped.all { it.status == status })
        val order = compareBy<LeaveReportCursor>({ it.submittedAt }, { it.id.toString() })
        val keys = listOfNotNull(after) + mapped.map { LeaveReportCursor(it.submittedAt, it.id) }
        check(keys.zipWithNext().all { (a, b) -> order.compare(a, b) > 0 })
        val items = mapped.take(limit)
        Page(
            items,
            if (mapped.size > limit) leaveReportCursor(company, from, until, status, items.last())
            else null,
        )
    }
}
