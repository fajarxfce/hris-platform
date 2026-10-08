package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

interface ScheduleRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun findShift(companyId: UUID, id: UUID): Result<ShiftDefinition?>

    fun shifts(
        companyId: UUID,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<ShiftDefinition>>

    fun saveShift(
        actor: Actor,
        id: UUID,
        details: ShiftDetails,
        active: Boolean,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun assign(
        actor: Actor,
        employeeId: UUID,
        effectiveFrom: LocalDate,
        days: Map<DayOfWeek, ShiftSnapshot>,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun roster(
        actor: Actor,
        employeeId: UUID,
        day: LocalDate,
        shift: ShiftSnapshot?,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun holidays(companyId: UUID, from: LocalDate, until: LocalDate): Result<List<WorkHoliday>>

    fun saveHoliday(
        actor: Actor,
        holiday: WorkHoliday,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun calendar(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<CalendarFacts>
}
