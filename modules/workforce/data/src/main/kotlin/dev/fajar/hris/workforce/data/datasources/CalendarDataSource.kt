package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface CalendarDataSource {
    fun lock(companyId: UUID)

    fun scheduleVersion(companyId: UUID, employeeId: UUID): Long?

    fun insertScheduleVersion(companyId: UUID, employeeId: UUID)

    fun advanceScheduleVersion(companyId: UUID, employeeId: UUID, expectedVersion: Long): Long?

    fun appendAssignment(row: ScheduleAssignmentsRecord)

    fun assignments(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<ScheduleAssignmentsRecord>

    fun roster(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<RosterDaysRecord>

    fun insertRoster(row: RosterDaysRecord)

    fun updateRoster(row: RosterDaysRecord, expectedVersion: Long): Long?

    fun appendRoster(row: RosterRevisionsRecord)
}
