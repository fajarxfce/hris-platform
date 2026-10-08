package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.DayOfWeek
import java.time.LocalTime
import tools.jackson.databind.ObjectMapper

fun ShiftDetails.toData(): ShiftDetailsData =
    ShiftDetailsData(
        code,
        name,
        startsAt.toString(),
        endsAt.toString(),
        breakMinutes,
        timezone,
        mode.name,
        locationRequired,
        maxAccuracyMeters,
        fence?.let { GeoFenceData(it.latitude, it.longitude, it.radiusMeters) },
    )

fun ShiftDetailsData.toDetails(): ShiftDetails =
    ShiftDetails(
        code,
        name,
        LocalTime.parse(startsAt),
        LocalTime.parse(endsAt),
        breakMinutes,
        timezone,
        WorkMode.valueOf(mode),
        locationRequired,
        maxAccuracyMeters,
        fence?.let { GeoFence(it.latitude, it.longitude, it.radiusMeters) },
    )

fun ShiftSnapshot.toData(): ShiftSnapshotData = ShiftSnapshotData(id, revision, details.toData())

fun ShiftSnapshotData.toSnapshot(): ShiftSnapshot = ShiftSnapshot(id, revision, details.toDetails())

fun ShiftTemplatesRecord.toShift(json: ObjectMapper): ShiftDefinition =
    ShiftDefinition(
        id,
        json.readValue(details.data(), ShiftDetailsData::class.java).toDetails(),
        active,
        version,
    )

fun ScheduleAssignmentsRecord.toAssignment(json: ObjectMapper): ScheduleAssignment =
    ScheduleAssignment(
        effectiveFrom,
        revision,
        json
            .readValue(days.data(), WeeklyPatternData::class.java)
            .days
            .mapKeys { DayOfWeek.of(it.key) }
            .mapValues { it.value.toSnapshot() },
    )

fun RosterDaysRecord.toRoster(json: ObjectMapper): RosterOverride =
    RosterOverride(
        workDate,
        shift?.let { json.readValue(it.data(), ShiftSnapshotData::class.java).toSnapshot() },
        version,
    )

fun WorkHolidaysRecord.toHoliday(): WorkHoliday = WorkHoliday(id, workDate, name, active, version)
