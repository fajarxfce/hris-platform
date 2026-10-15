package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.people.domain.entities.*
import java.security.MessageDigest
import java.time.*
import java.util.HexFormat
import java.util.UUID

/** Last working day is inclusive; execution starts at the following company-local midnight. */
fun offboardingWindow(
    lastWorkingDate: LocalDate,
    timezone: String,
    now: Instant,
): OffboardingWindow {
    val zone = ZoneId.of(timezone)
    val scheduledFor = lastWorkingDate.plusDays(1).atStartOfDay(zone).toInstant()
    return OffboardingWindow(
        timezone,
        now.atZone(zone).toLocalDate(),
        scheduledFor,
        scheduledFor.plus(Duration.ofDays(7)),
    )
}

fun offboardingBasisBlockers(case: LifecycleCase, employee: Employee, actorId: UUID): List<String> =
    buildList {
        if (case.kind != LifecycleKind.OFFBOARDING || case.status != LifecycleStatus.OPEN)
            add("lifecycle_case_not_open")
        if (case.version == Long.MAX_VALUE || employee.version == Long.MAX_VALUE)
            add("offboarding_revision_limit")
        if (case.tasks.any { it.required && it.status != LifecycleTaskStatus.DONE })
            add("required_lifecycle_tasks_pending")
        else if (case.tasks.any { it.status == LifecycleTaskStatus.PENDING })
            add("lifecycle_tasks_unresolved")
        if (employee.person.accountId == actorId) add("cannot_complete_own_offboarding")
        if (
            employee.id != case.employmentId ||
                employee.terms.status == EmploymentStatus.ENDED ||
                case.targetDate < employee.terms.startDate ||
                (employee.terms.endDate != null && employee.terms.endDate != case.targetDate)
        )
            add("offboarding_terms_changed")
    }

fun offboardingScheduleBlockers(
    case: LifecycleCase,
    window: OffboardingWindow,
    now: Instant,
    attempts: List<OffboardingScheduleDetails>,
): List<String> = buildList {
    if (!window.scheduledFor.isAfter(now)) add("offboarding_schedule_not_future")
    if (case.targetDate.isAfter(window.today.plusDays(365))) add("offboarding_schedule_too_far")
    if (attempts.size >= 8) add("offboarding_schedule_limit")
    if (attempts.any { it.schedule.status == OffboardingScheduleStatus.SCHEDULED })
        add("offboarding_schedule_active")
}

fun validateOffboardingExecution(
    schedule: OffboardingSchedule,
    case: LifecycleCase,
    employee: Employee,
    timezone: String,
    now: Instant,
): Result<LocalDate> {
    if (
        schedule.status != OffboardingScheduleStatus.SCHEDULED ||
            schedule.caseId != case.id ||
            schedule.caseVersion != case.version ||
            schedule.employmentId != employee.id ||
            schedule.employmentVersion != employee.version ||
            schedule.appliedEmploymentRevision != employee.appliedRevision ||
            schedule.lastWorkingDate != case.targetDate ||
            schedule.companyTimezone != timezone
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "offboarding_schedule_changed"))
    if (schedule.employeeAccountId != employee.person.accountId)
        return Result.Failed(Failure(FailureKind.CONFLICT, "offboarding_identity_changed"))
    val window = offboardingWindow(case.targetDate, timezone, now)
    if (window.scheduledFor != schedule.scheduledFor || window.validUntil != schedule.validUntil)
        return Result.Failed(Failure(FailureKind.CONFLICT, "offboarding_schedule_changed"))
    if (now.isBefore(schedule.scheduledFor))
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "offboarding_schedule_not_due"))
    if (!now.isBefore(schedule.validUntil))
        return Result.Failed(Failure(FailureKind.CONFLICT, "offboarding_schedule_review_required"))
    val blockers = offboardingBasisBlockers(case, employee, schedule.createdBy)
    if (blockers.isNotEmpty()) return Result.Failed(Failure(FailureKind.CONFLICT, blockers.first()))
    return Result.Success(window.today)
}

/**
 * Binds the reviewed account linkage and effective revision without exposing private identifiers.
 */
fun offboardingBasisRevision(
    case: LifecycleCase,
    employee: Employee,
    window: OffboardingWindow,
): String {
    val values =
        listOf(
            "hris-offboarding-v1",
            case.id.toString(),
            case.version.toString(),
            employee.companyId.toString(),
            employee.id.toString(),
            employee.version.toString(),
            employee.appliedRevision.toString(),
            employee.person.id.toString(),
            employee.person.accountId?.toString() ?: "",
            case.targetDate.toString(),
            window.companyTimezone,
            window.scheduledFor.toString(),
            window.validUntil.toString(),
        )
    val canonical = values.joinToString("") { "${it.length}:$it;" }
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        )
}

/** A committed cancellation fences execution even while a stopped worker awaits cleanup. */
fun offboardingExecutionPending(attempts: List<OffboardingScheduleDetails>): Boolean =
    attempts.any {
        it.schedule.status == OffboardingScheduleStatus.SCHEDULED &&
            it.job.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
            !it.job.cancellationRequested
    }
