package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.AttendanceCorrectionsRecord
import dev.fajar.hris.workforce.data.models.ScheduledDayData
import dev.fajar.hris.workforce.domain.entities.AttendanceCorrection
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AttendanceCorrectionsRecord.toCorrection(json: ObjectMapper): AttendanceCorrection =
    AttendanceCorrection(
        id,
        employmentId,
        workDate,
        clockIn?.toInstant(),
        clockOut?.toInstant(),
        breakMinutes,
        json.readValue(schedule.data(), ScheduledDayData::class.java).toDay(),
        actorId,
        recordedAt.toInstant(),
        reason,
        revision,
    )

fun AttendanceCorrection.toRow(company: UUID, json: ObjectMapper): AttendanceCorrectionsRecord =
    AttendanceCorrectionsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.workDate = workDate
        it.clockIn = clockIn?.atOffset(ZoneOffset.UTC)
        it.clockOut = clockOut?.atOffset(ZoneOffset.UTC)
        it.breakMinutes = breakMinutes
        it.schedule = JSONB.valueOf(json.writeValueAsString(schedule.toData()))
        it.actorId = actorId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.revision = version
    }
