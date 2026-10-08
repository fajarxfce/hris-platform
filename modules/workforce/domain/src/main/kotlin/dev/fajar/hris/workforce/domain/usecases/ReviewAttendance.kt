package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class ReviewAttendance(
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        decision: AttendanceReviewDecision,
        reason: String,
    ): Result<MutationReceipt> {
        if (
            "attendance.verify" !in actor.permissions &&
                "attendance.team.verify" !in actor.permissions
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_review"))
        val key =
            OperationKey(
                "attendance.review",
                operationId,
                listOf(id.toString(), version.toString(), decision.name, reason),
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = attendance.find(company, id)
            if (found is Result.Failed) return@run found
            val original =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "attendance_not_found")
                    )
            val capture = original.capture
            val current = people.findAtInstant(company, capture.employeeId, clock.instant())
            if (current is Result.Failed) return@run current
            if (!canReviewAttendance(actor, (current as Result.Success).value, original.accountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "attendance_review_denied"))
            val lock = attendance.lockDay(company, capture.employeeId, capture.workDate)
            if (lock is Result.Failed) return@run lock
            val previous =
                attendance.entries(company, capture.employeeId, capture.workDate, capture.workDate)
            if (previous is Result.Failed) return@run previous
            val entries = (previous as Result.Success).value
            val entry = entries.single { it.capture.id == id }
            if (entry.version != version || entry.status != AttendanceStatus.PENDING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (decision == AttendanceReviewDecision.ACCEPT) {
                val correctionResult =
                    corrections.latest(
                        company,
                        capture.employeeId,
                        capture.workDate,
                        capture.workDate,
                    )
                if (correctionResult is Result.Failed) return@run correctionResult
                if ((correctionResult as Result.Success).value.isNotEmpty())
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "attendance_day_corrected")
                    )
                val sequence = validateAttendanceSequence(capture, entries)
                if (sequence is Result.Failed) return@run sequence
            }
            attendance
                .review(
                    company,
                    id,
                    AttendanceReview(actor.accountId, decision, clock.instant(), reason),
                )
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "attendance_event",
                                    id,
                                    "attendance.reviewed",
                                    mapOf("decision" to decision.name),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
