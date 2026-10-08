package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.*

fun ShiftDetails.toResponse(): ShiftDetailsResponse =
    ShiftDetailsResponse(
        code,
        name,
        startsAt,
        endsAt,
        breakMinutes,
        timezone,
        mode.name,
        locationRequired,
        maxAccuracyMeters,
        fence?.let { GeoFenceResponse(it.latitude, it.longitude, it.radiusMeters) },
    )

fun ShiftDefinition.toResponse(): ShiftResponse =
    ShiftResponse(id, details.toResponse(), active, version)

fun ShiftSnapshot.toResponse(): ShiftSnapshotResponse =
    ShiftSnapshotResponse(id, revision, details.toResponse())

fun EmployeeCalendar.toResponse(): EmployeeCalendarResponse =
    EmployeeCalendarResponse(scheduleVersion, days.map { it.toResponse() })

fun ScheduledDay.toResponse(): ScheduledDayResponse =
    when (this) {
        is ScheduledDay.Work ->
            ScheduledDayResponse(
                workDate,
                "WORK",
                shift.toResponse(),
                startsAt,
                endsAt,
                plannedMinutes,
                origin.name,
                originVersion,
            )
        is ScheduledDay.Off ->
            ScheduledDayResponse(
                workDate,
                "OFF",
                origin = origin.name,
                originVersion = originVersion,
                holidayId = holidayId,
            )
        is ScheduledDay.Unassigned -> ScheduledDayResponse(workDate, "UNASSIGNED")
    }

fun WorkHoliday.toResponse(): WorkHolidayResponse =
    WorkHolidayResponse(id, workDate, name, active, version)
