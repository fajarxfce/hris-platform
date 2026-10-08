package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.datasources.*
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.data.models.WeeklyPatternData
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredScheduleRepository(
    private val shifts: ShiftDataSource,
    private val calendars: CalendarDataSource,
    private val holidaySource: HolidayDataSource,
    private val json: ObjectMapper,
) : ScheduleRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall {
        calendars.lock(companyId)
    }

    override fun findShift(companyId: UUID, id: UUID): Result<ShiftDefinition?> = safeDatabaseCall {
        shifts.find(companyId, id)?.toShift(json)
    }

    override fun shifts(
        companyId: UUID,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<ShiftDefinition>> = safeDatabaseCall {
        val rows = shifts.list(companyId, query, after, limit + 1)
        Page(
            rows.take(limit).map { it.toShift(json) },
            if (rows.size > limit) rows[limit - 1].code else null,
        )
    }

    override fun saveShift(
        actor: Actor,
        id: UUID,
        details: ShiftDetails,
        active: Boolean,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                val row =
                    ShiftTemplatesRecord().also {
                        it.companyId = company
                        it.id = id
                        it.details = JSONB.valueOf(json.writeValueAsString(details.toData()))
                        it.active = active
                        it.version = expectedVersion ?: 0
                    }
                if (expectedVersion == null) {
                    shifts.insert(row)
                    0L
                } else shifts.update(row, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    shifts.append(
                        ShiftRevisionsRecord().also {
                            it.companyId = company
                            it.shiftId = id
                            it.revision = version
                            it.details = JSONB.valueOf(json.writeValueAsString(details.toData()))
                            it.active = active
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(id, version)
                }
            }
    }

    override fun assign(
        actor: Actor,
        employeeId: UUID,
        effectiveFrom: LocalDate,
        days: Map<DayOfWeek, ShiftSnapshot>,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                if (expectedVersion == null) {
                    calendars.insertScheduleVersion(company, employeeId)
                    0L
                } else calendars.advanceScheduleVersion(company, employeeId, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    calendars.appendAssignment(
                        ScheduleAssignmentsRecord().also {
                            it.companyId = company
                            it.employmentId = employeeId
                            it.revision = version
                            it.effectiveFrom = effectiveFrom
                            it.days =
                                JSONB.valueOf(
                                    json.writeValueAsString(
                                        WeeklyPatternData(
                                            days
                                                .mapKeys { entry -> entry.key.value }
                                                .mapValues { entry -> entry.value.toData() }
                                        )
                                    )
                                )
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(employeeId, version)
                }
            }
    }

    override fun roster(
        actor: Actor,
        employeeId: UUID,
        day: LocalDate,
        shift: ShiftSnapshot?,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                val row =
                    RosterDaysRecord().also {
                        it.companyId = company
                        it.employmentId = employeeId
                        it.workDate = day
                        it.shift =
                            shift?.let { snapshot ->
                                JSONB.valueOf(json.writeValueAsString(snapshot.toData()))
                            }
                        it.version = expectedVersion ?: 0
                    }
                if (expectedVersion == null) {
                    calendars.insertRoster(row)
                    0L
                } else calendars.updateRoster(row, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    calendars.appendRoster(
                        RosterRevisionsRecord().also {
                            it.companyId = company
                            it.employmentId = employeeId
                            it.workDate = day
                            it.revision = version
                            it.shift =
                                shift?.let { snapshot ->
                                    JSONB.valueOf(json.writeValueAsString(snapshot.toData()))
                                }
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(employeeId, version)
                }
            }
    }

    override fun findHoliday(companyId: UUID, id: UUID): Result<WorkHoliday?> = safeDatabaseCall {
        holidaySource.find(companyId, id)?.toHoliday()
    }

    override fun holidays(
        companyId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<WorkHoliday>> = safeDatabaseCall {
        holidaySource.list(companyId, from, until).map { it.toHoliday() }
    }

    override fun saveHoliday(
        actor: Actor,
        holiday: WorkHoliday,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                val row =
                    WorkHolidaysRecord().also {
                        it.companyId = company
                        it.id = holiday.id
                        it.workDate = holiday.workDate
                        it.name = holiday.name
                        it.active = holiday.active
                        it.version = expectedVersion ?: 0
                    }
                if (expectedVersion == null) {
                    holidaySource.insert(row)
                    0L
                } else holidaySource.update(row, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    holidaySource.append(
                        HolidayRevisionsRecord().also {
                            it.companyId = company
                            it.holidayId = holiday.id
                            it.revision = version
                            it.workDate = holiday.workDate
                            it.name = holiday.name
                            it.active = holiday.active
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(holiday.id, version)
                }
            }
    }

    override fun calendar(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<CalendarFacts> = safeDatabaseCall {
        CalendarFacts(
            from,
            until,
            calendars.scheduleVersion(companyId, employeeId),
            calendars.assignments(companyId, employeeId, from, until).map { it.toAssignment(json) },
            calendars.roster(companyId, employeeId, from, until).map { it.toRoster(json) },
            holidaySource.list(companyId, from, until).map { it.toHoliday() },
        )
    }
}
