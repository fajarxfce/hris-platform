package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveAccrualScheduleDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveAccrualScheduleRepository
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredLeaveAccrualScheduleRepository(
    private val source: LeaveAccrualScheduleDataSource,
    private val json: ObjectMapper,
) : LeaveAccrualScheduleRepository {
    override fun create(
        company: UUID,
        schedule: LeaveAccrualSchedule,
        entries: List<LeaveAccrualScheduleEntry>,
    ): Result<Unit> = safeDatabaseCall {
        source.insert(schedule.toRow(company), entries.map { it.toRow(company, schedule.id) })
    }

    override fun find(company: UUID, id: UUID): Result<LeaveAccrualSchedule?> = safeDatabaseCall {
        source.find(company, id)?.toAccrualSchedule()
    }

    override fun periods(company: UUID, id: UUID): Result<List<LeaveAccrualSchedulePeriod>> =
        safeDatabaseCall {
            java.util.List.copyOf(source.periods(company, id).map { it.toAccrualPeriod(json) })
        }

    override fun list(company: UUID, after: UUID?, limit: Int): Result<Page<LeaveAccrualSchedule>> =
        safeDatabaseCall {
            val rows = source.list(company, after, limit + 1)
            Page(
                java.util.List.copyOf(rows.take(limit).map { it.toAccrualSchedule() }),
                if (rows.size > limit) rows[limit - 1].id.toString() else null,
            )
        }
}
