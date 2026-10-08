package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.workforce.data.models.ScheduledDayData
import dev.fajar.hris.workforce.domain.entities.*

fun ScheduledDay.toData(): ScheduledDayData =
    when (this) {
        is ScheduledDay.Work ->
            ScheduledDayData(
                "WORK",
                workDate,
                shift.toData(),
                startsAt,
                endsAt,
                plannedMinutes,
                origin.name,
                originVersion,
            )
        is ScheduledDay.Off ->
            ScheduledDayData(
                "OFF",
                workDate,
                origin = origin.name,
                originVersion = originVersion,
                holidayId = holidayId,
            )
        is ScheduledDay.Unassigned -> ScheduledDayData("UNASSIGNED", workDate)
    }

fun ScheduledDayData.toDay(): ScheduledDay =
    when (kind) {
        "WORK" ->
            ScheduledDay.Work(
                workDate,
                requireNotNull(shift).toSnapshot(),
                requireNotNull(startsAt),
                requireNotNull(endsAt),
                requireNotNull(plannedMinutes),
                CalendarOrigin.valueOf(requireNotNull(origin)),
                requireNotNull(originVersion),
            )
        "OFF" ->
            ScheduledDay.Off(
                workDate,
                CalendarOrigin.valueOf(requireNotNull(origin)),
                requireNotNull(originVersion),
                holidayId,
            )
        "UNASSIGNED" -> ScheduledDay.Unassigned(workDate)
        else -> error("Unknown schedule snapshot kind")
    }
