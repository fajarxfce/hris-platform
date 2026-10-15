package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant

fun requireOnboardingAccess(actor: Actor, reading: Boolean = false): Result<Unit> {
    val required =
        setOf("people.lifecycle.manage") +
            if (reading) setOf("people.read", "people.lifecycle.read") else emptySet()
    return if (actor.companyId == null || !actor.permissions.containsAll(required))
        Result.Failed(Failure(FailureKind.FORBIDDEN, "onboarding_access_required"))
    else Result.Success(Unit)
}

fun validateOnboardingSession(
    actor: Actor,
    access: AccountAccess?,
    at: Instant,
    security: IdentitySecurityPolicy,
    recent: Boolean = false,
    reading: Boolean = false,
): Result<Actor> =
    validateCompanySessionActor(actor, access, at, security).flatMap { current ->
        requireOnboardingAccess(current, reading).flatMap {
            if (!recent) Result.Success(current)
            else
                (if (security.enforceMfa)
                        requireRecentMfa(current, at, security.recentAuthenticationAge)
                    else requireRecentAuthentication(current, at, security.recentAuthenticationAge))
                    .map { current }
        }
    }

fun requireOnboardingJobActor(actor: Actor, lease: JobLease): Result<Unit> {
    val request = lease.job.request
    return if (
        request.kind != JobKind.PEOPLE_ONBOARD_START ||
            actor.companyId != request.companyId ||
            actor.accountId != request.actorId ||
            actor.credentialVersion != request.credentialVersion ||
            request.values.keys != setOf("scheduleId") ||
            request.totalItems != 1 ||
            request.progressMode != JobProgressMode.FIXED_TOTAL
    )
        Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
    else requireOnboardingAccess(actor)
}

fun onboardingScheduleActions(actor: Actor, details: OnboardingScheduleDetails): Set<String> {
    if (
        requireOnboardingAccess(actor) is Result.Failed ||
            details.schedule.status != OnboardingScheduleStatus.SCHEDULED
    )
        return emptySet()
    return when (details.job.status) {
        JobStatus.QUEUED,
        JobStatus.RUNNING -> if (details.job.cancellationRequested) emptySet() else setOf("cancel")
        JobStatus.FAILED,
        JobStatus.CANCELLED -> setOf("recover")
        JobStatus.SUCCEEDED -> emptySet()
    }
}
