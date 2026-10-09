package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.time.Instant
import java.time.LocalDate

fun validateAnnouncementPublicationInput(version: Long, reason: String): Result<Unit> =
    if (version < 0 || reason.isBlank() || reason.length > 1000)
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_announcement_command"))
    else Result.Success(Unit)

fun validateAnnouncementSchedule(scheduledFor: Instant?, now: Instant): Result<Unit> =
    if (
        scheduledFor != null &&
            (scheduledFor.isBefore(now) ||
                scheduledFor.isAfter(now.plusSeconds(365L * 86400)) ||
                scheduledFor.nano % 1000 != 0)
    )
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_announcement_schedule",
                parameters = mapOf("maximumDays" to "365"),
            )
        )
    else Result.Success(Unit)

fun announcementRecipientSelection(audience: AnnouncementAudience, date: LocalDate) =
    AnnouncementRecipientSelection(
        audience,
        date,
        setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION),
        true,
        true,
        "announcements.read",
    )

fun requireAnnouncementJobActor(actor: Actor, lease: JobLease): Result<Unit> {
    val request = lease.job.request
    if (
        request.kind != JobKind.ANNOUNCEMENT_PUBLISH ||
            actor.accountId != request.actorId ||
            actor.companyId != request.companyId ||
            actor.credentialVersion != request.credentialVersion
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
    return actor.requirePermission("announcements.manage")
}
