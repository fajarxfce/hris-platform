package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.util.UUID

interface LeaveAccrualScheduleRepository {
    fun create(
        company: UUID,
        schedule: LeaveAccrualSchedule,
        entries: List<LeaveAccrualScheduleEntry>,
    ): Result<Unit>

    fun find(company: UUID, id: UUID): Result<LeaveAccrualSchedule?>

    fun periods(company: UUID, id: UUID): Result<List<LeaveAccrualSchedulePeriod>>

    fun list(company: UUID, after: UUID?, limit: Int): Result<Page<LeaveAccrualSchedule>>
}
