package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.security.MessageDigest
import java.time.*
import java.util.HexFormat

fun validateOnboardingSelection(selection: OnboardingScheduleSelection): Result<Unit> =
    if (
        selection.targetDate.year !in 1900..2200 ||
            selection.startsOn.year !in 1900..2200 ||
            selection.startsOn > selection.targetDate
    )
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_onboarding_schedule"))
    else Result.Success(Unit)

/** The worker may prepare the checklist for seven days, but never after the first working day. */
fun onboardingWindow(
    selection: OnboardingScheduleSelection,
    timezone: String,
    now: Instant,
): OnboardingWindow {
    val zone = ZoneId.of(timezone)
    // Resolve a repeated midnight to its later offset, matching the persisted PostgreSQL boundary.
    val startsAt = selection.startsOn.atStartOfDay(zone).withLaterOffsetAtOverlap().toInstant()
    val firstDayEnds =
        selection.targetDate.plusDays(1).atStartOfDay(zone).withLaterOffsetAtOverlap().toInstant()
    return OnboardingWindow(
        timezone,
        now.atZone(zone).toLocalDate(),
        startsAt,
        minOf(startsAt.plus(Duration.ofDays(7)), firstDayEnds),
    )
}

fun onboardingScheduleBlockers(
    employee: Employee,
    template: LifecycleTemplate,
    selection: OnboardingScheduleSelection,
    window: OnboardingWindow,
    now: Instant,
    attempts: List<OnboardingScheduleDetails>,
): List<String> = buildList {
    if (employee.version == Long.MAX_VALUE || template.version == Long.MAX_VALUE)
        add("onboarding_revision_limit")
    if (
        !template.active ||
            template.kind != LifecycleKind.ONBOARDING ||
            template.id != selection.templateId
    )
        add("onboarding_template_unavailable")
    if (
        employee.terms.status == EmploymentStatus.ENDED ||
            employee.terms.startDate != selection.targetDate
    )
        add("onboarding_terms_changed")
    if (!window.scheduledFor.isAfter(now)) add("onboarding_schedule_not_future")
    if (selection.targetDate > window.today.plusDays(365)) add("onboarding_schedule_too_far")
    if (attempts.size >= 8) add("onboarding_schedule_limit")
    if (attempts.any { it.schedule.status == OnboardingScheduleStatus.SCHEDULED })
        add("onboarding_schedule_active")
}

/**
 * Versions reference immutable employment/template history. The opaque digest exposes no profile
 * data.
 */
fun onboardingBasisRevision(
    employee: Employee,
    template: LifecycleTemplate,
    selection: OnboardingScheduleSelection,
    window: OnboardingWindow,
): String {
    val values =
        listOf(
            "hris-onboarding-v1",
            employee.companyId.toString(),
            employee.id.toString(),
            employee.version.toString(),
            employee.appliedRevision.toString(),
            employee.terms.startDate.toString(),
            template.id.toString(),
            template.version.toString(),
            selection.targetDate.toString(),
            selection.startsOn.toString(),
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

fun validateOnboardingExecution(
    schedule: OnboardingSchedule,
    employee: Employee,
    template: LifecycleTemplate,
    timezone: String,
    now: Instant,
): Result<Unit> {
    if (
        schedule.status != OnboardingScheduleStatus.SCHEDULED ||
            schedule.employmentId != employee.id ||
            schedule.employmentVersion != employee.version ||
            schedule.appliedEmploymentRevision != employee.appliedRevision ||
            schedule.template.id != template.id ||
            schedule.template.version != template.version ||
            schedule.selection.templateId != template.id ||
            schedule.companyTimezone != timezone
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "onboarding_schedule_changed"))
    if (!template.active || template.kind != LifecycleKind.ONBOARDING)
        return Result.Failed(Failure(FailureKind.CONFLICT, "onboarding_template_unavailable"))
    if (
        employee.terms.status == EmploymentStatus.ENDED ||
            employee.terms.startDate != schedule.selection.targetDate
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "onboarding_terms_changed"))
    val window = onboardingWindow(schedule.selection, timezone, now)
    if (window.scheduledFor != schedule.scheduledFor || window.validUntil != schedule.validUntil)
        return Result.Failed(Failure(FailureKind.CONFLICT, "onboarding_schedule_changed"))
    if (now.isBefore(schedule.scheduledFor))
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "onboarding_schedule_not_due"))
    if (!now.isBefore(schedule.validUntil))
        return Result.Failed(Failure(FailureKind.CONFLICT, "onboarding_schedule_review_required"))
    return Result.Success(Unit)
}

/**
 * Creates pending tasks from reviewed definitions; callers own authorization and the transaction.
 */
fun scheduledOnboardingCase(schedule: OnboardingSchedule, at: Instant): LifecycleCase =
    LifecycleCase(
        schedule.caseId,
        schedule.employmentId,
        LifecycleKind.ONBOARDING,
        schedule.selection.targetDate,
        schedule.template.id,
        schedule.template.version,
        schedule.template.name,
        LifecycleStatus.OPEN,
        0,
        schedule.createdBy,
        at,
        schedule.template.tasks.map { task ->
            LifecycleTask(
                task.key,
                task.title,
                task.required,
                schedule.selection.targetDate.plusDays(task.dueDays.toLong()),
                schedule.assignees[task.key],
                LifecycleTaskStatus.PENDING,
                null,
                null,
            )
        },
    )
