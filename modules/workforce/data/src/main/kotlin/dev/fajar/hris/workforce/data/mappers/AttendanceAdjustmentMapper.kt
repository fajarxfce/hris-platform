package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.AttendanceAdjustmentsRecord
import dev.fajar.hris.workforce.data.models.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AttendanceAdjustmentSummaryRow.toSummary() =
    AttendanceAdjustmentSummary(
        id,
        revision,
        workDate,
        correctionId,
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun AttendanceAdjustmentsRecord.toAdjustment(json: ObjectMapper): AttendanceAdjustment {
    val snapshot =
        json
            .readValue(requireNotNull(payload).data(), WorkPeriodSnapshotData::class.java)
            .toSnapshot()
    val date = requireNotNull(workDate)
    require(snapshot.employeeId == employmentId && snapshot.month == YearMonth.from(date))
    require(snapshot.days.size == snapshot.month.lengthOfMonth())
    require(
        snapshot.days.map { it.workDate }.toSet() ==
            (1..snapshot.month.lengthOfMonth()).map { snapshot.month.atDay(it) }.toSet()
    )
    require(snapshot.days.single { it.workDate == date }.correctionId == correctionId)
    return AttendanceAdjustment(
        AttendanceAdjustmentSummary(
            requireNotNull(id),
            requireNotNull(revision),
            date,
            requireNotNull(correctionId),
            requireNotNull(actorId),
            requireNotNull(recordedAt).toInstant(),
            requireNotNull(reason),
        ),
        requireNotNull(jobId),
        snapshot,
    )
}

fun AttendanceAdjustment.toRow(companyId: UUID, json: ObjectMapper) =
    AttendanceAdjustmentsRecord().also {
        it.companyId = companyId
        it.id = summary.id
        it.jobId = closingJobId
        it.employmentId = snapshot.employeeId
        it.revision = summary.revision
        it.previousRevision = if (summary.revision == 1L) null else summary.revision - 1
        it.workDate = summary.workDate
        it.correctionId = summary.correctionId
        it.actorId = summary.actorId
        it.recordedAt = summary.recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = summary.reason
        it.payload = JSONB.valueOf(json.writeValueAsString(snapshot.toData()))
    }
