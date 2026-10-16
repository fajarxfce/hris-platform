package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.YearMonth

/** Every pending capture is an explicit reviewed input, including a rejection. */
fun validateAdjustmentEvidence(
    entries: List<AttendanceEntry>,
    decisions: List<AttendanceEvidenceDecision>,
): Result<Unit> {
    if (entries.size > 32 || entries.map { it.capture.id }.distinct().size != entries.size)
        return Result.Failed(Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid"))
    if (
        decisions.size > 32 ||
            decisions.map { it.eventId }.distinct().size != decisions.size ||
            decisions.any { it.expectedVersion < 0 || it.expectedVersion == Long.MAX_VALUE }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_adjustment"))
    val pending = entries.filter { it.status == AttendanceStatus.PENDING }
    if (
        pending.size != decisions.size ||
            decisions.any { decision ->
                pending.none {
                    it.capture.id == decision.eventId && it.version == decision.expectedVersion
                }
            }
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "attendance_adjustment_evidence_changed")
        )
    return Result.Success(Unit)
}

/** Replace only factual attendance; retain the frozen schedule and approved overtime. */
fun adjustClosedWorkSnapshot(
    source: WorkPeriodSnapshot,
    correction: AttendanceCorrection,
    entries: List<AttendanceEntry>,
): Result<WorkPeriodSnapshot> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (
        source.employeeId != correction.employeeId ||
            source.month != YearMonth.from(correction.workDate) ||
            source.days.size != source.month.lengthOfMonth() ||
            source.days.map { it.workDate }.toSet() !=
                (1..source.month.lengthOfMonth()).map { source.month.atDay(it) }.toSet() ||
            entries.size > 32 ||
            entries.map { it.capture.id }.distinct().size != entries.size ||
            entries.any {
                it.capture.employeeId != source.employeeId ||
                    it.capture.workDate != correction.workDate ||
                    it.status == AttendanceStatus.PENDING
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid"))
    val previous = source.days.single { it.workDate == correction.workDate }
    if (
        previous.schedule != correction.schedule ||
            previous.evidenceIds.any { id -> entries.none { it.capture.id == id } }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid"))
    val summary = summarizeAttendance(correction.workDate, entries, correction)
    val replacement =
        previous.copy(
            fact =
                if (correction.clockIn == null) WorkDayFact.ABSENCE_RECORDED
                else WorkDayFact.WORKED,
            acceptedMinutes = summary.acceptedMinutes,
            evidenceIds =
                java.util.List.copyOf(
                    (previous.evidenceIds + entries.map { it.capture.id }).distinct()
                ),
            correctionId = correction.id,
        )
    return Result.Success(
        source.copy(
            days =
                java.util.List.copyOf(
                    source.days.map { if (it.workDate == correction.workDate) replacement else it }
                )
        )
    )
}
