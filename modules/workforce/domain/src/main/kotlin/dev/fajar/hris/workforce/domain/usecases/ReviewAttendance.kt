package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class ReviewAttendance(
    private val periods: WorkPeriodRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val found = attendance.find(company, id)
            if (found is Result.Failed) return@run found
            val observed = (found as Result.Success).value
            val periodResult =
                if (observed != null)
                    periods.lockMonth(company, YearMonth.from(observed.capture.workDate), false)
                else Result.Success(null)
            if (periodResult is Result.Failed) return@run periodResult
            if (observed != null) {
                val dayGuard =
                    attendance.lockDay(
                        company,
                        observed.capture.employeeId,
                        observed.capture.workDate,
                    )
                if (dayGuard is Result.Failed) return@run dayGuard
            }
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            if (
                "attendance.verify" !in live.permissions &&
                    "attendance.team.verify" !in live.permissions
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val original =
                observed
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "attendance_not_found")
                    )
            val capture = original.capture
            val current = people.findAtInstant(company, capture.employeeId, clock.instant())
            if (current is Result.Failed) return@run current
            if (!canReviewAttendance(live, (current as Result.Success).value, original.accountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "attendance_review_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val period = requireNotNull((periodResult as Result.Success).value)
            if (
                period.status == WorkPeriodStatus.PROCESSING ||
                    (period.status == WorkPeriodStatus.CLOSED &&
                        (decision != AttendanceReviewDecision.REJECT ||
                            original.initial.closingJobId != period.jobId))
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "work_period_locked"))
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
